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
import com.plynexa.agent.core.project.ProjectManager
import com.plynexa.agent.core.project.SqlDelightProjectRepository
import com.plynexa.agent.core.reminder.ReminderManager
import com.plynexa.agent.core.reminder.SqlDelightReminderRepository
import com.plynexa.agent.core.runtime.DefaultConversationRuntime
import com.plynexa.agent.core.task.SqlDelightTaskRepository
import com.plynexa.agent.core.task.TaskManager
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import com.plynexa.agent.db.AgentDatabase
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentApiTest {
    @Test
    fun healthStatusAndChatRoutesUseSharedCore() = testApplication {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AgentDatabase.Schema.create(driver)
        var sequence = 0
        val ids = IdGenerator { prefix -> "$prefix-${sequence++}" }
        val clock = TimeProvider { 1_900_000_000_000L + sequence }
        val events = InMemoryEventBus()
        val database = AgentDatabase(driver)
        val conversationRepository = SqlDelightConversationRepository(database)
        val conversations = ConversationManager(conversationRepository, events, ids, clock)
        val state = AgentStateStore(events, ids, clock)
        val core = AgentCore(state, conversations)
        val memories = MemoryManager(SqlDelightMemoryRepository(database), events, ids, clock)
        val projects = ProjectManager(SqlDelightProjectRepository(database), ids, clock)
        val taskScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tasks = TaskManager(SqlDelightTaskRepository(database), events, ids, clock, taskScope)
        val reminders = ReminderManager(SqlDelightReminderRepository(database), events, ids, clock)
        val runtime = DefaultConversationRuntime.create(
            core, ContextManager(conversationRepository), memories, projects, tasks, reminders, events, ids, clock,
        )
        val conversation = conversations.createConversation("API test")
        application { configureAgentApi(core, runtime) }
        val jsonClient = createClient {
            install(ContentNegotiation) { json() }
        }

        assertEquals(HttpStatusCode.OK, jsonClient.get("/v1/health").status)
        assertEquals(HttpStatusCode.OK, jsonClient.get("/v1/status").status)
        val response = jsonClient.post("/v1/chat") {
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(conversation.id, "Status do agente."))
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertTrue(response.body<ChatResponse>().accepted)
        assertEquals(2, conversations.messages(conversation.id).size)
        assertEquals("O agente está funcionando localmente.", response.body<ChatResponse>().reply?.content)
        tasks.close()
        core.close()
        driver.close()
    }
}
