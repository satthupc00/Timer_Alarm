package com.mondiro.timeralarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Kích hoạt màn hình nhắc TOÀN MÀN HÌNH đúng chuẩn Android: thay vì gọi thẳng
 * startActivity() từ 1 BroadcastReceiver (bị hệ thống chặn/giới hạn khi app đang ở nền,
 * chỉ hiện thoáng qua rồi biến mất, không đè được lên app khác đang mở), ta đăng 1
 * Notification có "full-screen intent" - đây là cơ chế chính thức Android dành cho báo
 * thức/cuộc gọi đến, được phép mở đè lên bất kỳ app nào đang chạy (kể cả khi máy đang mở
 * khoá và dùng app khác), miễn app có quyền USE_FULL_SCREEN_INTENT (đã khai báo).
 * Màn hình sẽ đứng yên cho tới khi người dùng tự bấm nút (không tự tắt).
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

    fun fireFullScreenAlarm(context: Context, type: String) {
        ensureChannel(context)

        val notifId = if (type == FullScreenAlarmActivity.TYPE_DATA_EXPIRY) NOTIF_ID_DATA_EXPIRY else NOTIF_ID_BREAKFAST

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

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
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

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId, builder.build())

        // Thử mở thẳng màn hình full ngay lập tức (không chờ người dùng bấm vào thông báo).
        // Cách này thường thành công khi màn hình đang tắt/khoá; khi máy đang mở khoá và dùng app
        // khác, hệ điều hành có thể chặn - lúc đó full-screen intent ở trên vẫn là phương án chính.
        try {
            context.startActivity(fullScreenActivityIntent)
        } catch (_: Exception) {
            // Bị chặn do hạn chế "background activity start" của Android - bỏ qua, đã có
            // full-screen intent của Notification lo phần này.
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
