package com.mondiro.timeralarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class BreakfastAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isTest = intent.getBooleanExtra(AlarmScheduler.EXTRA_TEST, false)
        DiagLog.add(context, "⏰ Receiver đồ ăn sáng chạy${if (isTest) " [TEST]" else ""} | ${DiagLog.deviceState(context)}")

        if (isTest) {
            // TEST: bỏ qua kiểm tra giờ/công tắc, không đụng tới lịch thật
            AlarmNotificationHelper.fireFullScreenAlarm(context, FullScreenAlarmActivity.TYPE_BREAKFAST)
            return
        }

        val prefs = Prefs.get(context)
        if (!prefs.getBoolean(Prefs.KEY_BREAKFAST_ON, false)) {
            DiagLog.add(context, "→ bỏ qua: công tắc nhắc đồ ăn sáng đang TẮT")
            return
        }

        // Luôn đặt lại lịch nhắc chính cho lần 11:00 kế tiếp (hôm nay nếu chưa tới, hoặc ngày mai) -
        // gọi lại nhiều lần trong ngày (do các lần nhắc lặp 10p) cũng an toàn vì luôn tính đúng ra
        // đúng 11:00 gần nhất còn chưa tới.
        AlarmScheduler.scheduleBreakfastAlarm(context)

        val now = Calendar.getInstance()
        if (now.get(Calendar.HOUR_OF_DAY) >= 12) {
            DiagLog.add(context, "→ bỏ qua: đã qua 12:00, dừng nhắc lặp hôm nay")
            return
        }

        // Bấm "Đã hiểu" chỉ tắt màn hình hiện tại, KHÔNG dừng vòng lặp - vẫn tiếp tục nhắc mỗi 15 phút
        // cho tới 12:00. Muốn ngưng hẳn thì tắt công tắc tính năng này.
        AlarmNotificationHelper.fireFullScreenAlarm(context, FullScreenAlarmActivity.TYPE_BREAKFAST)
        AlarmScheduler.scheduleBreakfastRepeat(context, System.currentTimeMillis() + 15 * 60 * 1000)
        DiagLog.add(context, "→ đã hẹn nhắc lại sau 15 phút")
    }
}
