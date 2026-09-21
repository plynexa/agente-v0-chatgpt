package com.plynexa.agent.core.backup

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.EventBus
import com.plynexa.agent.core.model.BackupRecord
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import com.plynexa.agent.db.AgentDatabase
import com.plynexa.agent.db.Backups
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.sql.DriverManager

class LocalBackupProvider(
    private val databasePath: Path,
    private val backupDirectory: Path,
    private val lifecycle: DatabaseLifecycle,
    private val eventBus: EventBus,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
    private val schemaVersion: Long = 1,
) : BackupProvider {
    override val id = "local"

    override suspend fun create(): BackupRecord {
        publish(AgentEventType.BACKUP_STARTED)
        return try {
            require(Files.isRegularFile(databasePath)) { "Database file does not exist" }
            Files.createDirectories(backupDirectory)
            val recordId = idGenerator.nextId("backup")
            val destination = backupDirectory.resolve("agent-${timeProvider.nowEpochMillis()}-$recordId.db")
            val escaped = destination.toAbsolutePath().toString().replace("'", "''")
            sqliteConnection(databasePath).use { connection ->
                connection.createStatement().use { it.execute("VACUUM INTO '$escaped'") }
            }
            require(integrityCheck(destination)) { "SQLite backup integrity check failed" }
            val record = BackupRecord(
                recordId, id, destination.toAbsolutePath().toString(), timeProvider.nowEpochMillis(),
                Files.size(destination), sha256(destination), "VALID", schemaVersion,
            )
            register(record)
            audit("BACKUP_CREATED", record.id, "SUCCESS")
            publish(AgentEventType.BACKUP_COMPLETED, record.id)
            record
        } catch (error: Throwable) {
            publish(AgentEventType.BACKUP_FAILED, metadata = mapOf("errorType" to (error::class.simpleName ?: "Error")))
            throw error
        }
    }

    override fun list(): List<BackupRecord> {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databasePath.toAbsolutePath()}")
        return try {
            AgentDatabase(driver).agentQueries.selectBackups().executeAsList().map { it.toDomain() }
        } finally {
            driver.close()
        }
    }

    override fun validate(backup: BackupRecord): Boolean {
        val path = Path.of(backup.path)
        return Files.isRegularFile(path) && Files.size(path) == backup.sizeBytes &&
            sha256(path) == backup.checksum && integrityCheck(path)
    }

    override suspend fun restore(backup: BackupRecord) {
        require(validate(backup)) { "Backup validation failed before restore" }
        publish(AgentEventType.BACKUP_STARTED, backup.id, mapOf("operation" to "restore"))
        val staging = databasePath.resolveSibling("${databasePath.fileName}.restore-staging")
        val rollback = databasePath.resolveSibling("${databasePath.fileName}.pre-restore")
        lifecycle.stopForRestore()
        try {
            Files.deleteIfExists(staging)
            Files.deleteIfExists(rollback)
            Files.copy(Path.of(backup.path), staging, StandardCopyOption.COPY_ATTRIBUTES)
            require(integrityCheck(staging)) { "Staged restore integrity check failed" }
            Files.move(databasePath, rollback, StandardCopyOption.REPLACE_EXISTING)
            moveAtomicallyOrReplace(staging, databasePath)
            require(integrityCheck(databasePath)) { "Restored database integrity check failed" }
            Files.deleteIfExists(rollback)
            audit("BACKUP_RESTORED", backup.id, "SUCCESS")
            publish(AgentEventType.BACKUP_COMPLETED, backup.id, mapOf("operation" to "restore"))
        } catch (error: Throwable) {
            if (Files.isRegularFile(rollback)) {
                Files.deleteIfExists(databasePath)
                moveAtomicallyOrReplace(rollback, databasePath)
            }
            publish(AgentEventType.BACKUP_FAILED, backup.id, mapOf("operation" to "restore", "errorType" to (error::class.simpleName ?: "Error")))
            throw error
        } finally {
            Files.deleteIfExists(staging)
            lifecycle.startAfterRestore()
        }
    }

    private fun register(record: BackupRecord) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databasePath.toAbsolutePath()}")
        try {
            AgentDatabase(driver).agentQueries.insertBackup(
                record.id, record.provider, record.path, record.createdAt, record.sizeBytes,
                record.checksum, record.status, record.schemaVersion, record.metadata,
            )
        } finally {
            driver.close()
        }
    }

    private fun audit(action: String, targetId: String, result: String) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databasePath.toAbsolutePath()}")
        try {
            AgentDatabase(driver).agentQueries.insertAuditLog(
                idGenerator.nextId("audit"), timeProvider.nowEpochMillis(), action,
                "SYSTEM", null, "BACKUP", targetId, result, null,
            )
        } finally {
            driver.close()
        }
    }

    private fun integrityCheck(path: Path): Boolean = runCatching {
        sqliteConnection(path).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA integrity_check").use { result ->
                    result.next() && result.getString(1) == "ok"
                }
            }
        }
    }.getOrDefault(false)

    private fun sqliteConnection(path: Path) = run {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun moveAtomicallyOrReplace(source: Path, destination: Path) {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private suspend fun publish(
        type: AgentEventType,
        correlationId: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ) {
        eventBus.publish(AgentEvent(
            idGenerator.nextId("event"), type, timeProvider.nowEpochMillis(),
            correlationId, "LocalBackupProvider", metadata,
        ))
    }

    private fun Backups.toDomain() = BackupRecord(
        id, provider, path, created_at, size_bytes, checksum, status, schema_version, metadata,
    )
}
