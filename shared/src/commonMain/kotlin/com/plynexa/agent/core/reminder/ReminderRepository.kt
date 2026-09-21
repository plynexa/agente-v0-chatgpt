package com.plynexa.agent.core.reminder

import com.plynexa.agent.core.model.Reminder
import com.plynexa.agent.core.model.ReminderStatus

interface ReminderRepository {
    fun save(reminder: Reminder)
    fun reminder(id: String): Reminder?
    fun reminders(): List<Reminder>
    fun reminders(status: ReminderStatus): List<Reminder>
    fun due(atOrBefore: Long): List<Reminder>
    fun setStatus(id: String, status: ReminderStatus)
    fun update(reminder: Reminder)
    fun delete(id: String)
}
