package com.plynexa.agent.core.reminder

import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.EventBus
import com.plynexa.agent.core.model.Reminder
import com.plynexa.agent.core.model.ReminderStatus
import com.plynexa.agent.core.skill.ReminderPort
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun interface ReminderNotificationAdapter {
    suspend fun show(reminder: Reminder)
}

object NoOpReminderNotificationAdapter : ReminderNotificationAdapter {
    override suspend fun show(reminder: Reminder) = Unit
}

class ReminderManager(
    private val repository: ReminderRepository,
    private val eventBus: EventBus,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
    private val notifications: ReminderNotificationAdapter = NoOpReminderNotificationAdapter,
) : ReminderPort {
    override suspend fun create(text: String, triggerAt: Long, conversationId: String?): String {
        require(text.isNotBlank())
        val now = timeProvider.nowEpochMillis()
        require(triggerAt > now) { "Reminder trigger must be in the future" }
        val reminder = Reminder(
            idGenerator.nextId("reminder"), text.trim(), triggerAt,
            ReminderStatus.SCHEDULED, conversationId, now, "LOCAL",
        )
        repository.save(reminder)
        publish(AgentEventType.REMINDER_CREATED, reminder)
        return reminder.id
    }

    override fun listOpen(): List<String> = repository.reminders(ReminderStatus.SCHEDULED).map(Reminder::text)
    fun reminder(id: String): Reminder? = repository.reminder(id)
    fun scheduled(): List<Reminder> = repository.reminders(ReminderStatus.SCHEDULED)
    fun reminders(): List<Reminder> = repository.reminders()

    suspend fun update(
        id: String,
        text: String,
        triggerAt: Long,
        status: ReminderStatus = ReminderStatus.SCHEDULED,
        conversationId: String? = null,
    ): Reminder {
        require(text.isNotBlank())
        require(status != ReminderStatus.SCHEDULED || triggerAt > timeProvider.nowEpochMillis()) {
            "Reminder trigger must be in the future"
        }
        val current = requireNotNull(repository.reminder(id)) { "Reminder not found" }
        val updated = current.copy(
            text = text.trim(), triggerAt = triggerAt, status = status,
            conversationId = conversationId ?: current.conversationId,
        )
        repository.update(updated)
        publish(AgentEventType.REMINDER_UPDATED, updated)
        return updated
    }

    suspend fun delete(id: String) {
        val reminder = requireNotNull(repository.reminder(id)) { "Reminder not found" }
        repository.delete(id)
        publish(AgentEventType.REMINDER_DELETED, reminder)
    }

    suspend fun triggerDue(now: Long = timeProvider.nowEpochMillis()): List<Reminder> {
        val due = repository.due(now)
        due.forEach { reminder ->
            notifications.show(reminder)
            repository.setStatus(reminder.id, ReminderStatus.TRIGGERED)
            publish(AgentEventType.REMINDER_TRIGGERED, reminder.copy(status = ReminderStatus.TRIGGERED))
        }
        return due.map { it.copy(status = ReminderStatus.TRIGGERED) }
    }

    fun startScheduler(scope: CoroutineScope, intervalMillis: Long = 30_000): Job {
        require(intervalMillis > 0)
        return scope.launch {
            while (isActive) {
                triggerDue()
                delay(intervalMillis)
            }
        }
    }

    private suspend fun publish(type: AgentEventType, reminder: Reminder) {
        eventBus.publish(AgentEvent(
            idGenerator.nextId("event"), type, timeProvider.nowEpochMillis(), reminder.id,
            "ReminderManager", mapOf("reminderId" to reminder.id, "status" to reminder.status.name),
        ))
    }
}
