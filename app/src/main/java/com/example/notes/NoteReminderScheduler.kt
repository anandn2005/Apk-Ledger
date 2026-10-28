package com.example.notes

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.LedgerDatabase
import com.example.data.NoteEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object NoteReminderScheduler {
    const val CHANNEL_ID = "ledgermate_note_reminders"
    const val ACTION_TRIGGER_NOTE_REMINDER = "com.example.ACTION_TRIGGER_NOTE_REMINDER"
    const val ACTION_OPEN_NOTE = "com.example.ACTION_OPEN_NOTE"
    const val EXTRA_NOTE_ID = "extra_note_id"
    const val EXTRA_NOTE_TITLE = "extra_note_title"
    const val EXTRA_NOTE_BODY = "extra_note_body"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Note Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Reminders for notes, purchase lists, and important dates"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun scheduleReminder(context: Context, note: NoteEntity) {
        if (note.reminderTime <= 0L || note.isDone) {
            cancelReminder(context, note.id)
            return
        }
        val now = System.currentTimeMillis()
        if (note.reminderTime <= now) {
            // Already in the past; don't schedule a new alarm
            return
        }
        ensureNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, NoteReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER_NOTE_REMINDER
            putExtra(EXTRA_NOTE_ID, note.id)
            putExtra(EXTRA_NOTE_TITLE, note.titleText.ifBlank { "Note Reminder" })
            putExtra(EXTRA_NOTE_BODY, note.previewText.ifBlank { note.label })
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            note.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        note.reminderTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        note.reminderTime,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    note.reminderTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    note.reminderTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, note.reminderTime, pendingIntent)
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    fun cancelReminder(context: Context, noteId: String) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, NoteReminderReceiver::class.java).apply {
                action = ACTION_TRIGGER_NOTE_REMINDER
                putExtra(EXTRA_NOTE_ID, noteId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                noteId.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (_: Exception) {}
    }

    fun rescheduleAllFutureReminders(context: Context, notes: List<NoteEntity>) {
        val now = System.currentTimeMillis()
        notes.forEach { note ->
            if (!note.isDone && note.reminderTime > now) {
                scheduleReminder(context, note)
            }
        }
    }
}

class NoteReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val noteId = intent.getStringExtra(NoteReminderScheduler.EXTRA_NOTE_ID) ?: return
        val title = intent.getStringExtra(NoteReminderScheduler.EXTRA_NOTE_TITLE)?.ifBlank { "Note Reminder" } ?: "Note Reminder"
        val body = intent.getStringExtra(NoteReminderScheduler.EXTRA_NOTE_BODY)?.ifBlank { "Tap to view your note" } ?: "Tap to view your note"

        NoteReminderScheduler.ensureNotificationChannel(context)
        if (!NoteReminderScheduler.hasNotificationPermission(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = NoteReminderScheduler.ACTION_OPEN_NOTE
            putExtra(NoteReminderScheduler.EXTRA_NOTE_ID, noteId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val contentPendingIntent = PendingIntent.getActivity(
            context,
            noteId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NoteReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(noteId.hashCode(), notification)
        } catch (_: SecurityException) {}
    }
}

class NoteBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = LedgerDatabase.getDatabase(context.applicationContext)
                val allNotes = db.ledgerDao().getAllNotesList()
                NoteReminderScheduler.rescheduleAllFutureReminders(context.applicationContext, allNotes)
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
