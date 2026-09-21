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
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HostApiIntegrationTest {
    @Test
    fun restAndWebSocketUseTheSamePersistentCore() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AgentDatabase.Schema.create(driver)
        val database = AgentDatabase(driver)
        var sequence = 0
        val ids = IdGenerator { prefix -> "$prefix-${sequence++}" }
        val clock = TimeProvider { 2_200_000_000_000L + sequence }
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
        val services = HostApiServices(core, runtime, events, memories, tasks, reminders, projects)

        testApplication {
            application { configureHostApi(services) }
            val client = createClient {
                install(ContentNegotiation) { json() }
                install(WebSockets)
            }
            val conversationResponse = client.post("/v1/conversations") {
                contentType(ContentType.Application.Json)
                setBody(CreateConversationRequest("Windows ↔ S10"))
            }
            assertEquals(HttpStatusCode.Created, conversationResponse.status)

            client.webSocket("/v1/events") {
                delay(100)
                val memoryResponse = client.post("/v1/memories") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateMemoryRequest("Yasmin é minha modelo YA.", MemoryType.SEMANTIC, 0.9))
                }
                assertEquals(HttpStatusCode.Created, memoryResponse.status)
                val event = withTimeout(5_000) { (incoming.receive() as Frame.Text).readText() }
                assertTrue(event.contains("MEMORY_CREATED"))
            }
        }

        assertEquals("Yasmin é minha modelo YA.", memories.search("Yasmin").single().memory.content)
        tasks.close()
        core.close()
        driver.close()
    }
}
