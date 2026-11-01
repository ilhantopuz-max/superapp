package com.ceoasistanim.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ceoasistanim.app.CeoApp
import com.ceoasistanim.app.data.AppDatabase
import com.ceoasistanim.app.data.Task
import com.ceoasistanim.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object Reminders {
    private fun pending(ctx: Context, id: Long, title: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).putExtra("id", id).putExtra("title", title)
        return PendingIntent.getBroadcast(
            ctx, id.toInt(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Tam-zamanlı alarm izni gerektirmeyen (SCHEDULE_EXACT_ALARM yok) güç tasarruflu alarm; birkaç dakika sapabilir. */
    fun schedule(ctx: Context, t: Task) {
        val due = t.dueAt ?: return
        if (t.done || due <= System.currentTimeMillis()) return
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending(ctx, t.id, t.title))
    }

    fun cancel(ctx: Context, t: Task) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, t.id, t.title))
    }

    fun nextOccurrence(from: Long, repeat: String): Long {
        val c = Calendar.getInstance().apply { timeInMillis = from }
        val now = System.currentTimeMillis()
        do {
            when (repeat) {
                "daily" -> c.add(Calendar.DAY_OF_YEAR, 1)
                "weekly" -> c.add(Calendar.WEEK_OF_YEAR, 1)
                "monthly" -> c.add(Calendar.MONTH, 1)
                else -> return from
            }
        } while (c.timeInMillis <= now)
        return c.timeInMillis
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra("id", 0)
        val title = intent.getStringExtra("title") ?: "Hatırlatma"
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CeoApp.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("CEO ASİSTANIM")
            .setContentText(title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id.toInt(), n)
        } catch (_: SecurityException) {
        }

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(ctx).tasks()
                val t = dao.getById(id)
                val due = t?.dueAt
                if (t != null && due != null && t.repeat != "none") {
                    val nt = t.copy(dueAt = Reminders.nextOccurrence(due, t.repeat))
                    dao.update(nt)
                    Reminders.schedule(ctx, nt)
                }
            } finally {
                result.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.get(ctx).tasks().getPending().forEach { Reminders.schedule(ctx, it) }
            } finally {
                result.finish()
            }
        }
    }
}
