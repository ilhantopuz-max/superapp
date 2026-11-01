package com.ceoasistanim.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import com.ceoasistanim.app.data.Customer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val trLocale = Locale("tr", "TR")

fun fmt(ms: Long?): String =
    if (ms == null) "—" else SimpleDateFormat("d MMM yyyy HH:mm", trLocale).format(Date(ms))

fun endOfDay(ms: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59)
    return c.timeInMillis
}

fun needsFollowUp(c: Customer, now: Long): Boolean {
    if (c.status == "Kazanıldı" || c.status == "Kaybedildi") return false
    val next = c.nextContact
    val last = c.lastContact
    return (next != null && next <= now) ||
        (next == null && last != null && now - last > 14L * 24 * 3600 * 1000) ||
        (next == null && last == null)
}

fun pickDateTime(ctx: Context, initial: Long?, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { if (initial != null) timeInMillis = initial }
    DatePickerDialog(ctx, { _, y, m, d ->
        TimePickerDialog(ctx, { _, h, min ->
            val r = Calendar.getInstance()
            r.set(y, m, d, h, min, 0)
            r.set(Calendar.MILLISECOND, 0)
            onPicked(r.timeInMillis)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
}

private fun safeStart(ctx: Context, intent: Intent, err: String) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
    }
}

fun addToCalendar(ctx: Context, title: String, start: Long, desc: String = "") {
    val i = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, title)
        .putExtra(CalendarContract.Events.DESCRIPTION, desc)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 3600_000)
    safeStart(ctx, i, "Takvim uygulaması bulunamadı.")
}

fun dial(ctx: Context, phone: String) =
    safeStart(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.trim())), "Arama başlatılamadı.")

/** Resmî wa.me bağlantısı: WhatsApp'ı mesaj hazır şekilde açar; gönderme kullanıcının dokunuşuyla yapılır. */
fun openWhatsApp(ctx: Context, phone: String, text: String) {
    var d = phone.filter { it.isDigit() }
    if (d.startsWith("0")) d = "90" + d.drop(1) else if (d.length == 10) d = "90$d"
    val url = "https://wa.me/$d?text=" + Uri.encode(text)
    safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)), "WhatsApp açılamadı.")
}

/** E-posta uygulamasında taslak açar. Gönderim kullanıcının onayıyla e-posta uygulamasında yapılır. */
fun composeEmail(ctx: Context, to: String, subject: String, body: String) {
    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, body)
    safeStart(ctx, i, "E-posta uygulaması bulunamadı.")
}

fun shareText(ctx: Context, title: String, text: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    safeStart(ctx, Intent.createChooser(i, title), "Paylaşım açılamadı.")
}
