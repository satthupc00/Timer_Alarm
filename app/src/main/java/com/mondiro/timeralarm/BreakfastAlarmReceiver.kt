package com.mondiro.timeralarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BreakfastAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs.get(context)
        val breakfastOn = prefs.getBoolean(Prefs.KEY_BREAKFAST_ON, false)

        // Luôn đặt lại lịch nhắc chính cho lần 11:00 kế tiếp (hôm nay nếu chưa tới, hoặc ngày mai) -
        // gọi lại nhiều lần trong ngày (do các lần nhắc lặp 10p) cũng an toàn vì luôn tính đúng ra
        // đúng 11:00 gần nhất còn chưa tới.
        if (breakfastOn) {
            AlarmScheduler.scheduleBreakfastAlarm(context)
        }
        if (!breakfastOn) return

        val today = todayDateString()
        val dismissedDate = prefs.getString(Prefs.KEY_BREAKFAST_DISMISSED_DATE, null)
        if (dismissedDate == today) return // đã bấm "Đã hiểu" hôm nay rồi - không nhắc nữa

        val now = Calendar.getInstance()
        val pastNoon = now.get(Calendar.HOUR_OF_DAY) >= 12
        if (pastNoon) return // đã qua 12:00 - dừng nhắc lặp cho hôm nay

        // Mở màn hình nhắc toàn màn hình
        val fullScreenIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            putExtra(FullScreenAlarmActivity.EXTRA_TYPE, FullScreenAlarmActivity.TYPE_BREAKFAST)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        context.startActivity(fullScreenIntent)

        // Chưa tắt & chưa quá 12:00 -> hẹn nhắc lại sau 10 phút nữa
        AlarmScheduler.scheduleBreakfastRepeat(context, System.currentTimeMillis() + 10 * 60 * 1000)
    }

    companion object {
        fun todayDateString(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    }
}
