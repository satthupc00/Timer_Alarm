package com.mondiro.timeralarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Nhật ký chẩn đoán: ghi lại từng bước mỗi lần báo chạy (receiver, thông báo, service, overlay,
 * activity) cùng trạng thái máy & quyền tại thời điểm đó, để khi màn hình không tự bật thì xem
 * được chính xác nó dừng ở bước nào (xem trong app: nút "Xem nhật ký chẩn đoán").
 */
object DiagLog {
    private const val KEY_LOG = "diag_log"
    private const val MAX_LINES = 80

    @Synchronized
    fun add(context: Context, message: String) {
        val prefs = Prefs.get(context)
        val ts = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date())
        val old = prefs.getString(KEY_LOG, "") ?: ""
        val lines = old.split("\n").filter { it.isNotBlank() } + "$ts  $message"
        prefs.edit().putString(KEY_LOG, lines.takeLast(MAX_LINES).joinToString("\n")).commit()
    }

    @Synchronized
    fun read(context: Context): String = Prefs.get(context).getString(KEY_LOG, "") ?: ""

    @Synchronized
    fun clear(context: Context) {
        Prefs.get(context).edit().remove(KEY_LOG).commit()
    }

    fun deviceState(context: Context): String {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val fsi = if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true
        val overlay = Settings.canDrawOverlays(context)
        val battery = pm.isIgnoringBatteryOptimizations(context.packageName)
        val notif = NotificationManagerCompat.from(context).areNotificationsEnabled()
        return "Android ${Build.VERSION.SDK_INT} | màn hình sáng=${pm.isInteractive}, đang khoá=${km.isKeyguardLocked} | " +
            "thông báo=$notif, full-screen-intent=$fsi, hiển thị-trên-app-khác=$overlay, bỏ-hạn-chế-pin=$battery"
    }
}
