package com.plynexa.agent.android.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.plynexa.agent.android.MainActivity
import com.plynexa.agent.core.model.Reminder
import com.plynexa.agent.core.reminder.ReminderNotificationAdapter

class AndroidReminderNotificationAdapter(
    private val context: Context,
) : ReminderNotificationAdapter {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Lembretes do agente", NotificationManager.IMPORTANCE_HIGH,
        ))
    }

    override suspend fun show(reminder: Reminder) {
        val open = PendingIntent.getActivity(
            context, reminder.id.hashCode(), Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Lembrete do agente")
            .setContentText(reminder.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(reminder.id.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "agent-reminders"
    }
}
