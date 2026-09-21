package com.plynexa.agent.core.runtime

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.plynexa.agent.core.agent.AgentCore
import com.plynexa.agent.core.agent.AgentStateStore
import com.plynexa.agent.core.context.ContextManager
import com.plynexa.agent.core.conversation.ConversationManager
import com.plynexa.agent.core.conversation.SqlDelightConversationRepository
import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.InMemoryEventBus
import com.plynexa.agent.core.memory.MemoryManager
import com.plynexa.agent.core.memory.SqlDelightMemoryRepository
import com.plynexa.agent.core.model.MessageSource
import com.plynexa.agent.core.model.TaskStatus
import com.plynexa.agent.core.project.ProjectManager
import com.plynexa.agent.core.project.SqlDelightProjectRepository
import com.plynexa.agent.core.reminder.ReminderManager
import com.plynexa.agent.core.reminder.SqlDelightReminderRepository
import com.plynexa.agent.core.task.SqlDelightTaskRepository
import com.plynexa.agent.core.task.TaskManager
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import com.plynexa.agent.db.AgentDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AgentConversationRuntimeTest {
    @Test
    fun test1PersistentFactAnswersWhoIsYasmin() = runTest {
        fixture().use { f ->
            val conversation = f.conversations.createConversation("Yasmin")
            assertEquals("Entendido, salvei na memória.", f.ask(conversation.id, "Yasmin é minha modelo YA."))
            assertEquals("Yasmin é sua modelo YA.", f.ask(conversation.id, "Quem é Yasmin?"))
        }
    }

    @Test
    fun test2ProjectRelationAnswersTrendo() = runTest {
        fixture().use { f ->
            val conversation = f.conversations.createConversation("Projetos")
            f.ask(conversation.id, "Airfry pertence à Trendo.")
            assertEquals("A Airfry pertence à Trendo.", f.ask(conversation.id, "De qual projeto a Airfry faz parte?"))
        }
    }

    @Test
    fun test3RecentContextResolvesAirfry() = runTest {
        fixture().use { f ->
            val conversation = f.conversations.createConversation("Contexto")
            f.ask(conversation.id, "Airfry pertence à Trendo.")
            f.ask(conversation.id, "Estamos falando da Airfry.")
            assertEquals("A Airfry pertence à Trendo.", f.ask(conversation.id, "E a qual projeto ela pertence?"))
        }
    }

    @Test
    fun test4RecentContextResolvesYasminPronoun() = runTest {
        fixture().use { f ->
            val conversation = f.conversations.createConversation("Pronome")
            f.ask(conversation.id, "Yasmin é minha modelo YA.")
            assertEquals("Yasmin é sua modelo YA.", f.ask(conversation.id, "Quem é ela?"))
        }
    }

    @Test
    fun test5ReminderIsCreatedByRouter() = runTest {
        fixture().use { f ->
            val conversation = f.conversations.createConversation("Lembrete")
            assertEquals(
                "Lembrete criado.",
                f.ask(conversation.id, "Me lembra amanhã às 15h de continuar a Airfry."),
            )
            assertEquals("continuar a Airfry", f.reminders.scheduled().single().text)
        }
    }

    @Test
    fun test6LongTaskDoesNotBlockConversation() = runTest {
        fixture().use { f ->
            val gate = CompletableDeferred<Unit>()
            val task = f.tasks.submit("LONG", "Tarefa artificial") {
                gate.await()
                "feito"
            }
            runCurrent()
            assertEquals(TaskStatus.RUNNING, f.tasks.task(task.id)?.status)
            val conversation = f.conversations.createConversation("Multitarefa")
            assertEquals("O agente está funcionando localmente.", f.ask(conversation.id, "Status do agente"))
            assertEquals(TaskStatus.RUNNING, f.tasks.task(task.id)?.status)
            gate.complete(Unit)
            runCurrent()
        }
    }

    @Test
    fun test7ReopenedRuntimeKeepsHistoryAndMemory() = runTest {
        fixture().use { f ->
            val conversation = f.conversations.createConversation("Reabertura")
            f.ask(conversation.id, "Yasmin é minha modelo YA.")
            val reopened = f.newRuntime()
            val result = reopened.process(conversation.id, "Quem é Yasmin?", MessageSource.CHAT, "windows")
            assertEquals("Yasmin é sua modelo YA.", result.replyMessage.content)
            assertEquals(4, f.conversations.messages(conversation.id).size)
        }
    }

    @Test
    fun test8MissingProviderReturnsExplicitCapabilityMessage() = runTest {
        fixture().use { f ->
            val conversation = f.conversations.createConversation("Provider")
            val response = f.ask(conversation.id, "Gere uma imagem da Yasmin.")
            assertTrue(response.contains("gerador de imagens"))
            assertFalse(response.contains("Mensagem registrada localmente"))
        }
    }

    @Test
    fun runtimeEmitsTruthfulConversationLifecycleEvents() = runTest {
        fixture().use { f ->
            val observed = mutableListOf<AgentEvent>()
            val collector = backgroundScope.launch { f.events.events.collect(observed::add) }
            runCurrent()
            val conversation = f.conversations.createConversation("Eventos")
            f.ask(conversation.id, "Yasmin é minha modelo YA.")
            f.ask(conversation.id, "Quem é Yasmin?")
            runCurrent()
            collector.cancelAndJoin()
            val types = observed.map(AgentEvent::type)
            assertTrue(AgentEventType.MESSAGE_RECEIVED in types)
            assertTrue(AgentEventType.CONTEXT_RESOLVING in types)
            assertTrue(AgentEventType.MEMORY_SEARCH_STARTED in types)
            assertTrue(AgentEventType.MEMORY_FOUND in types)
            assertTrue(AgentEventType.ROUTE_SELECTED in types)
            assertTrue(AgentEventType.RESPONSE_CREATED in types)
            assertTrue(AgentEventType.MESSAGE_PERSISTED in types)
        }
    }

    @Test
    fun naturalPetFactsAreStoredAndRetrieved() = runTest {
        fixture().use { f ->
            val conversation = f.conversations.createConversation("Cachorras")
            assertEquals(
                "Entendido, salvei na memória.",
                f.ask(conversation.id, "Belinha é o nome da minha cachorrinha."),
            )
            assertEquals(
                "Belinha é o nome da sua cachorrinha",
                f.ask(conversation.id, "Quem é Belinha?"),
            )
            assertEquals(
                "Entendido, salvei na memória.",
                f.ask(conversation.id, "Lembre que minhas cachorras são Belinha e Safira."),
            )
            assertTrue(f.ask(conversation.id, "Quais são minhas cachorras?").contains("Belinha"))
        }
    }

    private fun TestScope.fixture() = RuntimeFixture(this)
}

private class RuntimeFixture(private val scope: TestScope) : AutoCloseable {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private var sequence = 0
    private var now = 1_700_000_000_000L
    val ids = IdGenerator { prefix -> "$prefix-${sequence++}" }
    val clock = TimeProvider { now++ }
    val events = InMemoryEventBus(512)
    private val database: AgentDatabase
    private val conversationRepository: SqlDelightConversationRepository
    val conversations: ConversationManager
    val core: AgentCore
    val memories: MemoryManager
    val projects: ProjectManager
    val tasks: TaskManager
    val reminders: ReminderManager
    val runtime: AgentConversationRuntime

    init {
        AgentDatabase.Schema.create(driver)
        database = AgentDatabase(driver)
        conversationRepository = SqlDelightConversationRepository(database)
        conversations = ConversationManager(conversationRepository, events, ids, clock)
        core = AgentCore(AgentStateStore(events, ids, clock), conversations)
        memories = MemoryManager(SqlDelightMemoryRepository(database), events, ids, clock)
        projects = ProjectManager(SqlDelightProjectRepository(database), ids, clock)
        tasks = TaskManager(SqlDelightTaskRepository(database), events, ids, clock, scope.backgroundScope)
        reminders = ReminderManager(SqlDelightReminderRepository(database), events, ids, clock)
        runtime = newRuntime()
    }

    fun newRuntime(): AgentConversationRuntime = DefaultConversationRuntime.create(
        core, ContextManager(conversationRepository), memories, projects, tasks, reminders,
        events, ids, clock, TimeZone.UTC,
    )

    suspend fun ask(conversationId: String, content: String): String =
        runtime.process(conversationId, content, MessageSource.CHAT, "windows").replyMessage.content

    override fun close() {
        core.close()
        driver.close()
    }
}
