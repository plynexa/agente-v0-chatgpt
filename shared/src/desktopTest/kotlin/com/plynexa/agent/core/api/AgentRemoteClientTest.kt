package com.plynexa.agent.core.api

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.plynexa.agent.core.agent.AgentCore
import com.plynexa.agent.core.agent.AgentStateStore
import com.plynexa.agent.core.conversation.ConversationManager
import com.plynexa.agent.core.conversation.SqlDelightConversationRepository
import com.plynexa.agent.core.context.ContextManager
import com.plynexa.agent.core.events.InMemoryEventBus
import com.plynexa.agent.core.memory.MemoryManager
import com.plynexa.agent.core.memory.SqlDelightMemoryRepository
import com.plynexa.agent.core.model.MemoryType
import com.plynexa.agent.core.reminder.ReminderManager
import com.plynexa.agent.core.reminder.SqlDelightReminderRepository
import com.plynexa.agent.core.project.ProjectManager
import com.plynexa.agent.core.project.SqlDelightProjectRepository
import com.plynexa.agent.core.runtime.DefaultConversationRuntime
import com.plynexa.agent.core.task.SqlDelightTaskRepository
import com.plynexa.agent.core.task.TaskManager
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import com.plynexa.agent.db.AgentDatabase
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentRemoteClientTest {
    @Test
    fun windowsContractCreatesChatReplyAndReadsHostData() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AgentDatabase.Schema.create(driver)
        val database = AgentDatabase(driver)
        var sequence = 0
        val ids = IdGenerator { prefix -> "$prefix-${sequence++}" }
        val clock = TimeProvider { 2_300_000_000_000L + sequence }
        val events = InMemoryEventBus()
        val conversationRepository = SqlDelightConversationRepository(database)
        val conversations = ConversationManager(conversationRepository, events, ids, clock)
        val core = AgentCore(AgentStateStore(events, ids, clock), conversations)
        val memories = MemoryManager(SqlDelightMemoryRepository(database), events, ids, clock)
        val tasks = TaskManager(SqlDelightTaskRepository(database), events, ids, clock, this)
        val reminders = ReminderManager(SqlDelightReminderRepository(database), events, ids, clock)
        val projects = ProjectManager(SqlDelightProjectRepository(database), ids, clock)
        val runtime = DefaultConversationRuntime.create(
            core, ContextManager(conversationRepository), memories, projects, tasks, reminders, events, ids, clock,
        )

        testApplication {
            application { configureHostApi(HostApiServices(core, runtime, events, memories, tasks, reminders, projects)) }
            val http = createClient {
                install(ContentNegotiation) { json() }
                install(WebSockets)
            }
            val remote = AgentRemoteClient(http, "")
            assertEquals("ok", remote.health().status)
            val conversation = remote.createConversation("Windows")
            val response = remote.chat(conversation.id, "Agente")
            assertTrue(response.accepted)
            assertEquals("Estou aqui.", response.reply?.content)
            assertEquals(2, remote.conversation(conversation.id).messages.size)
            assertEquals("Entendido, salvei na memória.", remote.chat(conversation.id, "Yasmin é minha modelo YA.").reply?.content)
            assertEquals("Yasmin é sua modelo YA.", remote.chat(conversation.id, "Quem é Yasmin?").reply?.content)
            assertTrue(remote.memories("Yasmin").any { it.memory.content == "Yasmin é minha modelo YA." })
            val renamed = remote.updateConversation(conversation.id, "Família")
            assertEquals("Família", renamed.title)
            val manual = remote.createMemory(CreateMemoryRequest("Safira é minha cachorrinha"))
            val edited = remote.updateMemory(manual.id, UpdateMemoryRequest(
                "Safira e Belinha são suas cachorrinhas", MemoryType.SEMANTIC, 0.9,
            ))
            assertEquals(0.9, edited.importance)
            remote.deleteMemory(manual.id)
            assertTrue(remote.memories().none { it.memory.id == manual.id })
            val reminderId = remote.createReminder(CreateReminderRequest(
                "Passear com as cachorras", 2_400_000_000_000L, conversation.id,
            )).id
            assertEquals("Passear amanhã", remote.updateReminder(reminderId, UpdateReminderRequest(
                "Passear amanhã", 2_500_000_000_000L, conversationId = conversation.id,
            )).text)
            remote.deleteReminder(reminderId)
            assertTrue(remote.reminders().none { it.id == reminderId })
            assertTrue(remote.devices().single().capabilities.contains("WEBSOCKET"))
        }

        tasks.close()
        core.close()
        driver.close()
    }
}
