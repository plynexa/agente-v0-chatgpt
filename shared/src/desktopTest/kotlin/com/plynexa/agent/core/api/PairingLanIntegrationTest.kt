package com.plynexa.agent.core.api

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.plynexa.agent.core.agent.AgentCore
import com.plynexa.agent.core.agent.AgentStateStore
import com.plynexa.agent.core.conversation.ConversationManager
import com.plynexa.agent.core.conversation.SqlDelightConversationRepository
import com.plynexa.agent.core.context.ContextManager
import com.plynexa.agent.core.device.PairingManager
import com.plynexa.agent.core.device.PlatformTokenHasher
import com.plynexa.agent.core.device.SecureTokenGenerator
import com.plynexa.agent.core.device.SqlDelightDeviceRepository
import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.InMemoryEventBus
import com.plynexa.agent.core.memory.MemoryManager
import com.plynexa.agent.core.memory.SqlDelightMemoryRepository
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
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PairingLanIntegrationTest {
    @Test
    fun testKPairAuthenticateChatEventsAndReconnectState() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AgentDatabase.Schema.create(driver)
        val database = AgentDatabase(driver)
        var sequence = 0
        val ids = IdGenerator { prefix -> "$prefix-${sequence++}" }
        val clock = TimeProvider { 2_400_000_000_000L + sequence }
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
        val devices = SqlDelightDeviceRepository(database, ids)
        val tokenGenerator = object : SecureTokenGenerator {
            override fun token(bytes: Int) = "secret-device-token"
            override fun numericCode(digits: Int) = "123456"
        }
        val pairing = PairingManager(
            devices, PlatformTokenHasher(), tokenGenerator, "host-pepper", events, ids, clock,
        )
        val services = HostApiServices(
            core, runtime, events, memories, tasks, reminders,
            projectManager = projects, pairingManager = pairing,
        )

        testApplication {
            application { configureHostApi(services) }
            val http = createClient {
                install(ContentNegotiation) { json() }
                install(WebSockets)
            }
            assertEquals(HttpStatusCode.Unauthorized, http.get("/v1/status").status)

            pairing.beginPairing()
            val publicClient = AgentRemoteClient(http, "")
            val paired = publicClient.pair("123456", "Notebook Windows")
            assertEquals("Notebook Windows", paired.device.name)
            assertEquals("secret-device-token", paired.token)
            val stored = database.agentQueries.selectDevices().executeAsOne()
            assertNotEquals(paired.token, stored.token_hash)

            val authenticated = AgentRemoteClient(http, "", tokenProvider = { paired.token })
            lateinit var conversationId: String
            http.webSocket("/v1/events", request = {
                header(HttpHeaders.Authorization, "Bearer ${paired.token}")
            }) {
                val conversation = authenticated.createConversation("S10 ↔ Windows")
                conversationId = conversation.id
                val reply = authenticated.chat(conversation.id, "Agente")
                assertEquals("Estou aqui.", reply.reply?.content)
                var messageEvent: com.plynexa.agent.core.events.AgentEvent? = null
                withTimeout(5_000) {
                    while (messageEvent == null) {
                        val frame = incoming.receive()
                        if (frame is Frame.Text) {
                            val event = Json.decodeFromString<com.plynexa.agent.core.events.AgentEvent>(frame.readText())
                            if (event.type == AgentEventType.MESSAGE_CREATED) messageEvent = event
                        }
                    }
                }
                assertEquals(AgentEventType.MESSAGE_CREATED, messageEvent?.type)
            }

            val reconnected = AgentRemoteClient(http, "", tokenProvider = { paired.token })
            assertTrue(reconnected.conversations().any { it.id == conversationId })
            assertEquals(2, reconnected.conversation(conversationId).messages.size)
            assertNotNull(pairing.authenticate(paired.token))
        }

        tasks.close()
        core.close()
        driver.close()
    }
}
