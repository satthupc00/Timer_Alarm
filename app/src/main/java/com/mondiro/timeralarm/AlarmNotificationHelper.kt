package com.mondiro.timeralarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Đăng thông báo báo thức + khởi động AlarmForegroundService để hiện màn hình full.
 *
 * Lưu ý quan trọng (đúng theo tài liệu Android): full-screen intent của thông báo CHỈ tự bật Activity
 * khi máy đang tắt màn hình / đang khoá. Khi máy đang được dùng bình thường, hệ thống chỉ hiện banner
 * và KHÔNG tự bật Activity. Lúc đó AlarmForegroundService sẽ vẽ overlay toàn màn hình (cần quyền
 * "Hiển thị trên các ứng dụng khác") để vẫn đè được lên app đang dùng.
 */
object AlarmNotificationHelper {

    private const val CHANNEL_ID = "fullscreen_alarm_channel"
    const val NOTIF_ID_BREAKFAST = 3001
    const val NOTIF_ID_DATA_EXPIRY = 3002

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Báo toàn màn hình",
                NotificationManager.IMPORTANCE_HIGH // bắt buộc phải HIGH thì full-screen intent mới được phép bật lên
            ).apply {
                description = "Các nhắc nhở hiện toàn màn hình (đồ ăn sáng, hết hạn gói data)"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(context: Context, type: String, notifId: Int): Notification {
        ensureChannel(context)

        val fullScreenActivityIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            putExtra(FullScreenAlarmActivity.EXTRA_TYPE, type)
            putExtra(FullScreenAlarmActivity.EXTRA_NOTIF_ID, notifId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val fullScreenPi = PendingIntent.getActivity(
            context, notifId, fullScreenActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (type == FullScreenAlarmActivity.TYPE_DATA_EXPIRY)
            context.getString(R.string.data_title)
        else
            context.getString(R.string.breakfast_title)
        val text = if (type == FullScreenAlarmActivity.TYPE_DATA_EXPIRY)
            "Gói 3G sắp hoặc đã hết hạn"
        else
            "Đã 11:00 - nhớ mua/đặt đồ ăn sáng cho Ba"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_breakfast)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true) // không cho vuốt tắt thông báo tuỳ tiện, phải bấm "Đã hiểu" trong màn hình
            .setSilent(true) // không cần âm thanh/rung ở lớp thông báo - phần rung (nếu có) do màn hình full tự xử lý
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi, true)
            .build()
    }

    fun fireFullScreenAlarm(context: Context, type: String) {
        val notifId = if (type == FullScreenAlarmActivity.TYPE_DATA_EXPIRY) NOTIF_ID_DATA_EXPIRY else NOTIF_ID_BREAKFAST

        val notification = buildNotification(context, type, notifId)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId, notification)
        DiagLog.add(context, "Đã đăng thông báo báo (loại=$type)")

        // Khởi động Foreground Service để mở thẳng màn hình full ngay lập tức, kể cả khi app
        // đang ở nền và người dùng đang dùng app khác - xem giải thích chi tiết trong
        // AlarmForegroundService.
        val serviceIntent = Intent(context, AlarmForegroundService::class.java).apply {
            putExtra(FullScreenAlarmActivity.EXTRA_TYPE, type)
            putExtra(FullScreenAlarmActivity.EXTRA_NOTIF_ID, notifId)
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
            DiagLog.add(context, "Đã gửi lệnh khởi động service")
        } catch (e: Exception) {
            DiagLog.add(context, "startForegroundService LỖI: ${e.javaClass.simpleName}: ${e.message}")
            // Trường hợp hiếm gặp máy vẫn chặn - thông báo full-screen intent ở trên vẫn là
            // phương án dự phòng, người dùng bấm vào thông báo vẫn mở được màn hình.
        }
    }

    fun cancel(context: Context, notifId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notifId)
    }

    /**
     * Từ Android 14 (API 34) trở lên, quyền USE_FULL_SCREEN_INTENT không tự động có hiệu lực -
     * nhiều máy mặc định TẮT, người dùng phải tự bật trong Cài đặt hệ thống thì màn hình full mới
     * tự bật lên được (nếu không, chỉ bấm vào thông báo mới mở được).
     */
    fun hasFullScreenIntentPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true // các bản cũ hơn không cần xin riêng
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.canUseFullScreenIntent()
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 34) return
        try {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) { /* máy không hỗ trợ màn hình cài đặt này - bỏ qua */ }
    }
}
