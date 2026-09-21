package com.plynexa.agent.core.backup

import com.plynexa.agent.core.model.BackupRecord

interface BackupProvider {
    val id: String
    suspend fun create(): BackupRecord
    fun list(): List<BackupRecord>
    fun validate(backup: BackupRecord): Boolean
    suspend fun restore(backup: BackupRecord)
}

interface R2BackupProvider : BackupProvider
interface GoogleDriveBackupProvider : BackupProvider

interface DatabaseLifecycle {
    suspend fun stopForRestore()
    suspend fun startAfterRestore()
}
