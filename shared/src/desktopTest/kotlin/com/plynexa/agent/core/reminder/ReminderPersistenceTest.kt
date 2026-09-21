package com.plynexa.agent.core.reminder

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.InMemoryEventBus
import com.plynexa.agent.core.model.ReminderStatus
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import com.plynexa.agent.db.AgentDatabase
import java.nio.file.Files
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderPersistenceTest {
    @Test
    fun reminderSurvivesRestartAndTriggersOnce() = runTest {
        val file = Files.createTempFile("agent-v0-reminder", ".db").toFile()
        file.delete()
        val url = "jdbc:sqlite:${file.absolutePath}"
        var now = 2_000_000_000_000L
        var sequence = 0
        val clock = TimeProvider { now }
        val ids = IdGenerator { prefix -> "$prefix-${sequence++}" }

        val firstDriver = JdbcSqliteDriver(url)
        AgentDatabase.Schema.create(firstDriver)
        val first = ReminderManager(
            SqlDelightReminderRepository(AgentDatabase(firstDriver)), InMemoryEventBus(), ids, clock,
        )
        val reminderId = first.create("verificar a Trendo", now + 60_000, "conversation-1")
        firstDriver.close()

        val reopenedDriver = JdbcSqliteDriver(url)
        val bus = InMemoryEventBus()
        val delivered = mutableListOf<String>()
        val reopened = ReminderManager(
            SqlDelightReminderRepository(AgentDatabase(reopenedDriver)), bus, ids, clock,
            ReminderNotificationAdapter { delivered += it.id },
        )
        assertEquals(ReminderStatus.SCHEDULED, reopened.reminder(reminderId)?.status)
        assertEquals(listOf("verificar a Trendo"), reopened.listOpen())

        val triggeredEvent = async(start = CoroutineStart.UNDISPATCHED) {
            bus.events.first { it.type == AgentEventType.REMINDER_TRIGGERED }
        }
        now += 60_001
        assertEquals(1, reopened.triggerDue().size)
        assertEquals(reminderId, triggeredEvent.await().correlationId)
        assertEquals(listOf(reminderId), delivered)
        assertEquals(ReminderStatus.TRIGGERED, reopened.reminder(reminderId)?.status)
        assertEquals(0, reopened.triggerDue().size)

        reopenedDriver.close()
        file.delete()
    }
}
