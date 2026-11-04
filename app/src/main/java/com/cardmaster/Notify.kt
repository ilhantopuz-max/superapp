package com.cardmaster

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object Repeat {
    /** Tekrarlı kayıt için bir sonraki tarih (şu andan sonra). Tekrar yoksa 0. */
    fun next(from: Long, rule: String): Long {
        if (rule.isBlank() || from <= 0) return 0
        val c = Calendar.getInstance().apply { timeInMillis = from }
        val now = System.currentTimeMillis()
        var guard = 0
        do {
            when (rule) {
                "Günlük" -> c.add(Calendar.DAY_OF_YEAR, 1)
                "Haftalık" -> c.add(Calendar.WEEK_OF_YEAR, 1)
                "Aylık" -> c.add(Calendar.MONTH, 1)
                else -> return 0
            }
            guard++
        } while (c.timeInMillis <= now && guard < 1000)
        return c.timeInMillis
    }
}

object Notify {
    private const val CH = "reminders"

    fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH, "Hatırlatmalar", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private fun pi(ctx: Context, code: Int, kind: String, id: Long): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction("alarm.$kind.$id").putExtra("kind", kind).putExtra("id", id)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun schedule(ctx: Context, code: Int, kind: String, id: Long, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = pi(ctx, code, kind, id)
        am.cancel(p)
        if (at <= System.currentTimeMillis()) return
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    fun scheduleEvent(ctx: Context, e: EventItem) =
        schedule(ctx, e.id.toInt(), "event", e.id, e.startAt - e.remindMin * 60_000L)

    fun scheduleTask(ctx: Context, t: TaskItem) {
        if (t.done || t.dueAt <= 0) cancelTask(ctx, t.id)
        else schedule(ctx, 1_000_000 + t.id.toInt(), "task", t.id, t.dueAt)
    }

    fun cancelEvent(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, id.toInt(), "event", id))
    }

    fun cancelTask(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, 1_000_000 + id.toInt(), "task", id))
    }

    fun post(ctx: Context, nid: Int, title: String, text: String) {
        channel(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx, nid,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("route", "takvim"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build()
        try { NotificationManagerCompat.from(ctx).notify(nid, n) } catch (e: SecurityException) { }
    }

    suspend fun rescheduleAll(ctx: Context) {
        val dao = CeoDb.get(ctx).dao()
        dao.eventsSnap().forEach { scheduleEvent(ctx, it) }
        dao.openTasksSnap().forEach { scheduleTask(ctx, it) }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val pending = goAsync()
        val kind = i.getStringExtra("kind") ?: ""
        val id = i.getLongExtra("id", 0)
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = CeoDb.get(app).dao()
                if (kind == "event") {
                    val e = dao.eventById(id) ?: return@launch
                    Notify.post(app, ("e$id").hashCode(), "⏰ ${e.title}",
                        fmtDT(e.startAt) + (if (e.location.isNotBlank()) " • ${e.location}" else ""))
                    val nx = Repeat.next(e.startAt, e.repeatRule)
                    if (nx > 0) {
                        val ne = e.copy(startAt = nx, endAt = if (e.endAt > 0) nx + (e.endAt - e.startAt) else 0)
                        dao.putEvent(ne)
                        Notify.scheduleEvent(app, ne)
                    }
                } else if (kind == "task") {
                    val t = dao.taskById(id) ?: return@launch
                    if (t.done) return@launch
                    Notify.post(app, ("t$id").hashCode(), "✅ Görev: ${t.title}", "Vade: ${fmtDT(t.dueAt)}")
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try { Notify.rescheduleAll(app) } finally { pending.finish() }
        }
    }
}
