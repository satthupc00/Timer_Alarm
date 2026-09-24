package com.mondiro.timeralarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs.get(context)

        if (prefs.getBoolean(Prefs.KEY_BREAKFAST_ON, false)) {
            AlarmScheduler.scheduleBreakfastAlarm(context)
            NotificationHelper.showBreakfastStatus(context)
        }
        if (prefs.getBoolean(Prefs.KEY_DATA_ON, false)) {
            AlarmScheduler.rescheduleDataExpiryFromStoredValue(context)
        }
    }
}
