package com.plynexa.agent.core.backup

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.plynexa.agent.core.conversation.SqlDelightConversationRepository
import com.plynexa.agent.core.events.InMemoryEventBus
import com.plynexa.agent.core.model.Conversation
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import com.plynexa.agent.db.AgentDatabase
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalBackupProviderTest {
    @Test
    fun consistentBackupValidatesAndRestoresOriginalData() = runTest {
        val directory = Files.createTempDirectory("agent-v0-backup-test")
        val databasePath = directory.resolve("agent.db")
        val backupsPath = directory.resolve("backups")
        createDatabaseWithConversation(databasePath, "original", "Original")
        var sequence = 0
        val ids = IdGenerator { prefix -> "$prefix-${sequence++}" }
        val clock = TimeProvider { 2_100_000_000_000L + sequence }
        val lifecycle = object : DatabaseLifecycle {
            override suspend fun stopForRestore() = Unit
            override suspend fun startAfterRestore() = Unit
        }
        val provider = LocalBackupProvider(
            databasePath, backupsPath, lifecycle, InMemoryEventBus(), ids, clock,
        )

        val backup = provider.create()
        assertTrue(provider.validate(backup))
        assertEquals(backup.id, provider.list().single().id)
        insertConversation(databasePath, "changed", "Changed after backup")
        assertNotNull(readConversation(databasePath, "changed"))

        provider.restore(backup)

        assertNotNull(readConversation(databasePath, "original"))
        assertNull(readConversation(databasePath, "changed"))
        directory.toFile().deleteRecursively()
    }

    private fun createDatabaseWithConversation(path: java.nio.file.Path, id: String, title: String) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${path.toAbsolutePath()}")
        AgentDatabase.Schema.create(driver)
        val repository = SqlDelightConversationRepository(AgentDatabase(driver))
        repository.createConversation(Conversation(id, title, 1, 1))
        driver.close()
    }

    private fun insertConversation(path: java.nio.file.Path, id: String, title: String) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${path.toAbsolutePath()}")
        SqlDelightConversationRepository(AgentDatabase(driver)).createConversation(Conversation(id, title, 2, 2))
        driver.close()
    }

    private fun readConversation(path: java.nio.file.Path, id: String): Conversation? {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${path.toAbsolutePath()}")
        return try {
            SqlDelightConversationRepository(AgentDatabase(driver)).findConversation(id)
        } finally {
            driver.close()
        }
    }
}
