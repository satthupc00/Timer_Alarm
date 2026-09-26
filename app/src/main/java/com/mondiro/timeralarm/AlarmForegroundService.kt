package com.mondiro.timeralarm

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Service ngắn (sống vài trăm ms) chỉ để "mượn" quyền mở Activity từ nền.
 * Khi máy đang khoá/tắt màn hình, việc mở thẳng Activity từ AlarmManager vẫn thường thành công.
 * Nhưng khi máy đang MỞ KHOÁ và người dùng đang dùng app khác, Android sẽ CHẶN việc 1 app ở nền
 * tự mở Activity đè lên app đang dùng (background activity launch restriction) - đây chính là lý do
 * lúc bấm nút Test (app đang mở, tức đang ở foreground) thì full màn hình hiện ra ngay, nhưng tới
 * giờ báo thật (app ở nền) thì chỉ thấy thông báo chứ màn hình không tự bật.
 *
 * Cách khắc phục đúng chuẩn Android: bắt đầu 1 Foreground Service (được phép khởi động từ nền),
 * rồi gọi mở Activity TỪ BÊN TRONG chính service đó - lúc này tiến trình app đã được xem là đang hoạt
 * động ở foreground nên được phép mở Activity, không bị chặn nữa.
 */
class AlarmForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = intent?.getStringExtra(FullScreenAlarmActivity.EXTRA_TYPE) ?: FullScreenAlarmActivity.TYPE_BREAKFAST
        val notifId = intent?.getIntExtra(FullScreenAlarmActivity.EXTRA_NOTIF_ID, -1) ?: -1

        // Bắt buộc phải gọi startForeground() ngay để hệ thống công nhận đây là foreground service -
        // dùng lại đúng notification báo toàn màn hình đã có sẵn (ongoing, không âm thanh).
        val notification = AlarmNotificationHelper.buildNotification(this, type, notifId)
        startForeground(if (notifId > 0) notifId else 9999, notification)

        val activityIntent = Intent(this, FullScreenAlarmActivity::class.java).apply {
            putExtra(FullScreenAlarmActivity.EXTRA_TYPE, type)
            putExtra(FullScreenAlarmActivity.EXTRA_NOTIF_ID, notifId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        try {
            startActivity(activityIntent)
        } catch (_: Exception) {
            // Trường hợp hiếm gặp máy vẫn chặn - thông báo (đã hiện ở bước startForeground) vẫn còn đó
            // để người dùng bấm vào mở thủ công.
        }

        // Xong việc - dừng trạng thái foreground nhưng GIỮ LẠI notification (để còn hiện icon & có thể
        // bấm mở lại), rồi tự dừng service (không cần sống tiếp).
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
