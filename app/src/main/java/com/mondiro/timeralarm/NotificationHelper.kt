package com.mondiro.timeralarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Icon trạng thái thường trực trên thanh thông báo: hiện khi tính năng "Nhắc mua đồ sáng cho Ba"
 * đang bật, để người dùng biết tính năng đang hoạt động; tự ẩn khi tắt tính năng.
 */
object NotificationHelper {

    private const val CHANNEL_ID = "breakfast_status_channel"
    private const val NOTIFICATION_ID = 2001

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trạng thái nhắc đồ ăn sáng",
                NotificationManager.IMPORTANCE_LOW // im lặng, chỉ hiện icon trạng thái
            ).apply {
                description = "Hiện icon khi tính năng nhắc mua đồ sáng cho Ba đang bật"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showBreakfastStatus(context: Context) {
        ensureChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_breakfast)
            .setContentTitle(context.getString(R.string.breakfast_title))
            .setContentText("Đang bật — nhắc lúc 11:00 mỗi ngày")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun hideBreakfastStatus(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
