package com.plynexa.agent.core.task

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.plynexa.agent.core.conversation.ConversationManager
import com.plynexa.agent.core.conversation.SqlDelightConversationRepository
import com.plynexa.agent.core.events.InMemoryEventBus
import com.plynexa.agent.core.model.MessageSource
import com.plynexa.agent.core.model.TaskStatus
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import com.plynexa.agent.db.AgentDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class TaskManagerTest {
    @Test
    fun longTaskDoesNotBlockConversation() = runTest {
        val fixture = fixture(this)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val task = fixture.tasks.submit("artificial-long", "Long provider simulation") {
            started.complete(Unit)
            release.await()
            "done"
        }
        started.await()

        val conversation = fixture.conversations.createConversation("Concurrent chat")
        val message = fixture.conversations.appendUserMessage(
            conversation.id, "Qual minha próxima tarefa?", MessageSource.CHAT,
        )

        assertEquals("Qual minha próxima tarefa?", message.content)
        assertFalse(fixture.tasks.task(task.id)?.status == TaskStatus.COMPLETED)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(TaskStatus.COMPLETED, fixture.tasks.task(task.id)?.status)
        fixture.close()
    }

    @Test
    fun failedTaskIsIsolatedAndNextTaskCompletes() = runTest {
        val fixture = fixture(this)
        val failed = fixture.tasks.submit("forced-failure", "Failure isolation") {
            error("mock provider failed")
        }
        val healthy = fixture.tasks.submit("healthy", "Must still execute") { "healthy-result" }

        advanceUntilIdle()

        assertEquals(TaskStatus.FAILED, fixture.tasks.task(failed.id)?.status)
        assertEquals("mock provider failed", fixture.tasks.task(failed.id)?.error)
        assertEquals(TaskStatus.COMPLETED, fixture.tasks.task(healthy.id)?.status)
        assertEquals("healthy-result", fixture.tasks.task(healthy.id)?.result)
        fixture.close()
    }

    @Test
    fun lifecyclePersistsTruthfulEventsInOrder() = runTest {
        val fixture = fixture(this)
        val task = fixture.tasks.submit("event-test", "Lifecycle") { "ok" }
        advanceUntilIdle()

        assertEquals(
            listOf("TASK_CREATED", "TASK_STARTED", "TASK_COMPLETED"),
            fixture.tasks.events(task.id).map { it.eventType },
        )
        fixture.close()
    }

    private data class Fixture(
        val driver: JdbcSqliteDriver,
        val tasks: TaskManager,
        val conversations: ConversationManager,
    ) {
        suspend fun close() {
            tasks.close()
            driver.close()
        }
    }

    private fun fixture(scope: kotlinx.coroutines.CoroutineScope): Fixture {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AgentDatabase.Schema.create(driver)
        val database = AgentDatabase(driver)
        var sequence = 0
        val ids = IdGenerator { prefix -> "$prefix-${sequence++}" }
        val clock = TimeProvider { 2_000_000_000_000L + sequence }
        val events = InMemoryEventBus()
        return Fixture(
            driver,
            TaskManager(SqlDelightTaskRepository(database), events, ids, clock, scope, workerCount = 2),
            ConversationManager(SqlDelightConversationRepository(database), events, ids, clock),
        )
    }
}
