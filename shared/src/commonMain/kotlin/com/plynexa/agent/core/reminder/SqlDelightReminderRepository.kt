package com.plynexa.agent.core.reminder

import com.plynexa.agent.core.model.Reminder
import com.plynexa.agent.core.model.ReminderStatus
import com.plynexa.agent.db.AgentDatabase
import com.plynexa.agent.db.Reminders

class SqlDelightReminderRepository(private val database: AgentDatabase) : ReminderRepository {
    private val queries get() = database.agentQueries

    override fun save(reminder: Reminder) = queries.insertReminder(
        reminder.id, reminder.text, reminder.triggerAt, reminder.status.name,
        reminder.conversationId, reminder.createdAt, reminder.source, reminder.metadata,
    )

    override fun reminder(id: String): Reminder? = queries.selectReminderById(id).executeAsOneOrNull()?.toDomain()
    override fun reminders(): List<Reminder> = queries.selectReminders().executeAsList().map { it.toDomain() }
    override fun reminders(status: ReminderStatus): List<Reminder> =
        queries.selectRemindersByStatus(status.name).executeAsList().map { it.toDomain() }
    override fun due(atOrBefore: Long): List<Reminder> =
        queries.selectDueReminders(atOrBefore).executeAsList().map { it.toDomain() }
    override fun setStatus(id: String, status: ReminderStatus) = queries.updateReminderStatus(status.name, id)
    override fun update(reminder: Reminder) = queries.updateReminder(
        reminder.text, reminder.triggerAt, reminder.status.name, reminder.conversationId,
        reminder.metadata, reminder.id,
    )
    override fun delete(id: String) = queries.deleteReminder(id)

    private fun Reminders.toDomain() = Reminder(
        id, text, trigger_at, ReminderStatus.valueOf(status), conversation_id, created_at, source, metadata,
    )
}
