package com.plynexa.agent.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.plynexa.agent.core.agent.AgentCore
import com.plynexa.agent.core.agent.AgentStateStore
import com.plynexa.agent.core.backup.DatabaseLifecycle
import com.plynexa.agent.core.backup.LocalBackupProvider
import com.plynexa.agent.core.conversation.ConversationManager
import com.plynexa.agent.core.conversation.SqlDelightConversationRepository
import com.plynexa.agent.core.events.InMemoryEventBus
import com.plynexa.agent.core.memory.MemoryManager
import com.plynexa.agent.core.memory.SqlDelightMemoryRepository
import com.plynexa.agent.core.model.MemoryType
import com.plynexa.agent.core.model.MessageSource
import com.plynexa.agent.core.model.TaskStatus
import com.plynexa.agent.core.reminder.ReminderManager
import com.plynexa.agent.core.reminder.SqlDelightReminderRepository
import com.plynexa.agent.core.task.SqlDelightTaskRepository
import com.plynexa.agent.core.task.TaskManager
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import com.plynexa.agent.db.AgentDatabase
import java.nio.file.Files
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoExternalAiIntegrationTest {
    @Test
    fun testJLocalCoreWorksWithoutAnyExternalProviderOrKey() = runTest {
        val directory = Files.createTempDirectory("agent-v0-no-external-ai")
        val databasePath = directory.resolve("agent.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databasePath.toAbsolutePath()}")
        AgentDatabase.Schema.create(driver)
        val database = AgentDatabase(driver)
        var sequence = 0
        var now = 2_500_000_000_000L
        val ids = IdGenerator { prefix -> "$prefix-${sequence++}" }
        val clock = TimeProvider { now++ }
        val events = InMemoryEventBus()
        val conversations = ConversationManager(SqlDelightConversationRepository(database), events, ids, clock)
        val core = AgentCore(AgentStateStore(events, ids, clock), conversations)
        val memories = MemoryManager(SqlDelightMemoryRepository(database), events, ids, clock)
        val tasks = TaskManager(SqlDelightTaskRepository(database), events, ids, clock, this)
        val reminders = ReminderManager(SqlDelightReminderRepository(database), events, ids, clock)
        val backups = LocalBackupProvider(
            databasePath, directory.resolve("backups"),
            object : DatabaseLifecycle {
                override suspend fun stopForRestore() = Unit
                override suspend fun startAfterRestore() = Unit
            },
            events, ids, clock,
        )

        core.start()
        val conversation = conversations.createConversation("Local only")
        core.receiveChat(conversation.id, "Status do agente", "test-device")
        conversations.appendAgentMessage(conversation.id, "Funcionando localmente.", MessageSource.SYSTEM)
        memories.remember("Yasmin é minha modelo YA.", MemoryType.SEMANTIC)
        val task = tasks.submit("LOCAL", "Tarefa sem provider externo") { "resultado local" }
        withTimeout(5_000) {
            while (tasks.task(task.id)?.status != TaskStatus.COMPLETED) delay(10)
        }
        val reminderId = reminders.create("Verificar a Trendo", now + 60_000, conversation.id)
        val backup = backups.create()

        assertTrue(core.stateStore.status.value.databaseAvailable)
        assertEquals(2, conversations.messages(conversation.id).size)
        assertEquals("Yasmin é minha modelo YA.", memories.search("Yasmin").single().memory.content)
        assertEquals("resultado local", tasks.task(task.id)?.result)
        assertEquals("Verificar a Trendo", reminders.reminder(reminderId)?.text)
        assertTrue(backups.validate(backup))

        tasks.close()
        core.close()
        driver.close()
        directory.toFile().deleteRecursively()
    }
}
