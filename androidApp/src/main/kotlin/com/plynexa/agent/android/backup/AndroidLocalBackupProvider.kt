package com.plynexa.agent.android.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.plynexa.agent.core.backup.BackupProvider
import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.EventBus
import com.plynexa.agent.core.model.BackupRecord
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import com.plynexa.agent.db.AgentDatabase
import com.plynexa.agent.db.Backups
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

class AndroidLocalBackupProvider(
    context: Context,
    private val database: AgentDatabase,
    private val eventBus: EventBus,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
    private val scheduleRestore: (BackupRecord) -> Unit,
) : BackupProvider {
    override val id = "android-local"
    private val databaseFile = context.getDatabasePath(DATABASE_NAME)
    private val backupDirectory = File(context.noBackupFilesDir, "agent-backups")

    override suspend fun create(): BackupRecord {
        publish(AgentEventType.BACKUP_STARTED)
        return try {
            backupDirectory.mkdirs()
            check(backupDirectory.isDirectory) { "Cannot create Android backup directory" }
            val recordId = idGenerator.nextId("backup")
            val target = File(backupDirectory, "agent-${timeProvider.nowEpochMillis()}-$recordId.db")
            val db = SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { while (it.moveToNext()) Unit }
                val escaped = target.absolutePath.replace("'", "''")
                db.execSQL("VACUUM INTO '$escaped'")
            } finally {
                db.close()
            }
            check(AndroidBackupFiles.integrityCheck(target)) { "Android SQLite snapshot failed integrity check" }
            val record = BackupRecord(
                recordId, id, target.absolutePath, timeProvider.nowEpochMillis(), target.length(),
                AndroidBackupFiles.sha256(target), "VALID", 1,
            )
            database.agentQueries.insertBackup(
                record.id, record.provider, record.path, record.createdAt, record.sizeBytes,
                record.checksum, record.status, record.schemaVersion, record.metadata,
            )
            audit("BACKUP_CREATED", record.id, "SUCCESS")
            publish(AgentEventType.BACKUP_COMPLETED, record.id)
            record
        } catch (error: Throwable) {
            publish(AgentEventType.BACKUP_FAILED, metadata = mapOf("errorType" to (error::class.simpleName ?: "Error")))
            throw error
        }
    }

    override fun list(): List<BackupRecord> = database.agentQueries.selectBackups().executeAsList().map { it.toDomain() }

    override fun validate(backup: BackupRecord): Boolean {
        val file = File(backup.path)
        return file.isFile && file.length() == backup.sizeBytes &&
            AndroidBackupFiles.sha256(file) == backup.checksum && AndroidBackupFiles.integrityCheck(file)
    }

    override suspend fun restore(backup: BackupRecord) {
        require(validate(backup)) { "Backup validation failed before Android restore" }
        publish(AgentEventType.BACKUP_STARTED, backup.id, mapOf("operation" to "restore"))
        scheduleRestore(backup)
    }

    fun recordRestoreResult(backup: BackupRecord, success: Boolean) {
        if (success) {
            database.agentQueries.insertBackup(
                backup.id, backup.provider, backup.path, backup.createdAt, backup.sizeBytes,
                backup.checksum, backup.status, backup.schemaVersion, backup.metadata,
            )
            audit("BACKUP_RESTORED", backup.id, "SUCCESS")
        } else {
            audit("BACKUP_RESTORE_FAILED", backup.id, "FAILED")
        }
    }

    private fun audit(action: String, targetId: String, result: String) {
        database.agentQueries.insertAuditLog(
            idGenerator.nextId("audit"), timeProvider.nowEpochMillis(), action,
            "SYSTEM", "android-host", "BACKUP", targetId, result, null,
        )
    }

    private suspend fun publish(
        type: AgentEventType,
        correlationId: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ) {
        eventBus.publish(AgentEvent(
            idGenerator.nextId("event"), type, timeProvider.nowEpochMillis(),
            correlationId, "AndroidLocalBackupProvider", metadata,
        ))
    }

    private fun Backups.toDomain() = BackupRecord(
        id, provider, path, created_at, size_bytes, checksum, status, schema_version, metadata,
    )

    companion object {
        const val DATABASE_NAME = "agent-v0.db"
    }
}

object AndroidBackupFiles {
    fun restore(databaseFile: File, backup: BackupRecord) {
        val source = File(backup.path)
        require(source.isFile && source.length() == backup.sizeBytes && sha256(source) == backup.checksum)
        require(integrityCheck(source)) { "Backup integrity check failed before restore" }
        val staging = File(databaseFile.parentFile, "${databaseFile.name}.restore-staging")
        val rollback = File(databaseFile.parentFile, "${databaseFile.name}.pre-restore")
        val wal = File("${databaseFile.path}-wal")
        val shm = File("${databaseFile.path}-shm")
        staging.delete()
        rollback.delete()
        copyDurably(source, staging)
        require(integrityCheck(staging)) { "Staged Android restore failed integrity check" }
        wal.delete()
        shm.delete()
        try {
            if (databaseFile.exists()) check(databaseFile.renameTo(rollback)) { "Cannot stage current database" }
            check(staging.renameTo(databaseFile)) { "Cannot activate restored database" }
            require(integrityCheck(databaseFile)) { "Restored Android database failed integrity check" }
            rollback.delete()
        } catch (error: Throwable) {
            databaseFile.delete()
            if (rollback.exists()) rollback.renameTo(databaseFile)
            throw error
        } finally {
            staging.delete()
        }
    }

    fun integrityCheck(file: File): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0) == "ok"
            }
        }
    }.getOrDefault(false)

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyDurably(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }
}
