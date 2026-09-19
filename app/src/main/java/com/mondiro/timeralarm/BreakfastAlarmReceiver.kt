package com.mondiro.timeralarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BreakfastAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Mở màn hình nhắc toàn màn hình
        val fullScreenIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            putExtra(FullScreenAlarmActivity.EXTRA_TYPE, FullScreenAlarmActivity.TYPE_BREAKFAST)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        context.startActivity(fullScreenIntent)

        // Đặt lại lịch cho ngày mai (nếu vẫn đang bật)
        val prefs = Prefs.get(context)
        if (prefs.getBoolean(Prefs.KEY_BREAKFAST_ON, false)) {
            AlarmScheduler.scheduleBreakfastAlarm(context)
        }
    }
}
