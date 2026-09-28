package com.mondiro.timeralarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DataExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isTest = intent.getBooleanExtra(AlarmScheduler.EXTRA_TEST, false)
        DiagLog.add(context, "⏰ Receiver hết hạn 3G chạy${if (isTest) " [TEST]" else ""} | ${DiagLog.deviceState(context)}")

        if (isTest) {
            AlarmNotificationHelper.fireFullScreenAlarm(context, FullScreenAlarmActivity.TYPE_DATA_EXPIRY)
            return
        }

        val prefs = Prefs.get(context)
        if (!prefs.getBoolean(Prefs.KEY_DATA_ON, false)) {
            DiagLog.add(context, "→ bỏ qua: công tắc nhắc 3G đang TẮT")
            return
        }

        val expiry = prefs.getLong(Prefs.KEY_EXPIRY_TIMESTAMP, 0L)
        if (expiry <= 0L) {
            DiagLog.add(context, "→ bỏ qua: chưa có ngày hết hạn nào")
            return
        }

        // Hiện nhắc nhở toàn màn hình, KHÔNG kêu chuông (theo yêu cầu: screen-only, no sound)
        AlarmNotificationHelper.fireFullScreenAlarm(context, FullScreenAlarmActivity.TYPE_DATA_EXPIRY)

        // Đặt lịch kiểm tra/nhắc kế tiếp (mỗi 6h cho tới khi phát hiện SMS gia hạn)
        AlarmScheduler.rescheduleDataExpiryFromStoredValue(context)
    }
}
