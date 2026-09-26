package com.mondiro.timeralarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.util.Calendar

object Prefs {
    private const val NAME = "timer_alarm_prefs"
    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    const val KEY_BREAKFAST_ON = "breakfast_on"
    const val KEY_DATA_ON = "data_on"
    const val KEY_EXPIRY_TIMESTAMP = "expiry_timestamp" // millis, 0 = none
    const val KEY_LAST_REMINDER_STAGE = "last_reminder_stage" // 0 = none sent yet
}

/**
 * Quản lý lịch báo thức bằng AlarmManager.
 * Vì setRepeating không còn chính xác trên Android hiện đại, mỗi lần alarm bắn
 * xong sẽ tự đặt lại lịch cho lần kế tiếp (trong các Receiver tương ứng).
 */
object AlarmScheduler {

    private const val REQ_BREAKFAST = 1001
    private const val REQ_DATA_EXPIRY = 1002
    private const val REQ_BREAKFAST_REPEAT = 1003

    // ---------- Breakfast: nhắc hằng ngày lúc 11:00 ----------

    fun scheduleBreakfastAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = nextOccurrenceAt(11, 0)

        val intent = Intent(context, BreakfastAlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, REQ_BREAKFAST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
    }

    fun cancelBreakfastAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BreakfastAlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, REQ_BREAKFAST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        cancelBreakfastRepeat(context)
    }

    // ---------- Breakfast: nhắc lặp lại mỗi 10 phút (từ 11:00 tới 12:00) nếu chưa bấm "Đã hiểu" ----------

    fun scheduleBreakfastRepeat(context: Context, atMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BreakfastAlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, REQ_BREAKFAST_REPEAT, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
    }

    fun cancelBreakfastRepeat(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BreakfastAlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, REQ_BREAKFAST_REPEAT, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    private fun nextOccurrenceAt(hour: Int, minute: Int): Calendar {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal
    }

    // ---------- Data expiry: nhắc 1 lần lúc (hết hạn - 24h), sau đó lặp mỗi 6h ----------

    fun scheduleDataExpiryCheck(context: Context, atMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DataExpiryReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, REQ_DATA_EXPIRY, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
    }

    fun cancelDataExpiryCheck(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DataExpiryReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, REQ_DATA_EXPIRY, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    /**
     * Dựa vào expiryTimestamp đã lưu, tính và đặt lại lịch check kế tiếp:
     * - Nếu còn hơn 24h tới hạn -> đặt alarm đúng lúc (hạn - 24h)
     * - Nếu còn dưới 24h -> đặt alarm 6h nữa (vòng lặp nhắc lại)
     * - Nếu đã hết hạn từ lâu (quá 3 ngày) -> dừng, không đặt lịch nữa
     */
    fun rescheduleDataExpiryFromStoredValue(context: Context) {
        val prefs = Prefs.get(context)
        if (!prefs.getBoolean(Prefs.KEY_DATA_ON, false)) return

        val expiry = prefs.getLong(Prefs.KEY_EXPIRY_TIMESTAMP, 0L)
        if (expiry <= 0L) return

        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L
        val sixHours = 6 * 60 * 60 * 1000L
        val threeDays = 3 * oneDay

        when {
            now > expiry + threeDays -> {
                // Quá hạn lâu rồi mà vẫn chưa gia hạn -> thôi không nhắc nữa
                cancelDataExpiryCheck(context)
            }
            now >= expiry - oneDay -> {
                // Đã trong vùng dưới 24h (hoặc đã quá hạn) -> nhắc lại sau 6h
                scheduleDataExpiryCheck(context, now + sixHours)
            }
            else -> {
                // Còn hơn 24h -> đặt đúng thời điểm (hạn - 24h)
                scheduleDataExpiryCheck(context, expiry - oneDay)
            }
        }
    }
}
