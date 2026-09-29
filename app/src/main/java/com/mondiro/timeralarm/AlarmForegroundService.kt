package com.mondiro.timeralarm

import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.media.MediaPlayer
import android.os.Vibrator
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

/**
 * Hiện màn hình báo TOÀN MÀN HÌNH tuỳ theo trạng thái máy lúc báo:
 *
 * - Máy đang được dùng (sáng + đã mở khoá): Android CỐ Ý không cho app ở nền tự mở Activity đè lên app
 *   khác, và thông báo full-screen chỉ hiện dạng banner. Cách duy nhất là vẽ thẳng 1 cửa sổ overlay
 *   toàn màn hình (cần quyền "Hiển thị trên các ứng dụng khác") - overlay đè được lên mọi app.
 *   Service phải sống tới khi người dùng bấm "Đã hiểu" để giữ overlay.
 * - Máy tắt màn hình / đang khoá: full-screen intent của thông báo (+ startActivity) sẽ tự bật
 *   Activity lên trên màn hình khoá. Service làm xong việc thì tự dừng ngay.
 */
class AlarmForegroundService : Service() {

    private var overlayView: View? = null
    private var vibrator: Vibrator? = null
    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = intent?.getStringExtra(FullScreenAlarmActivity.EXTRA_TYPE) ?: FullScreenAlarmActivity.TYPE_BREAKFAST
        val notifId = intent?.getIntExtra(FullScreenAlarmActivity.EXTRA_NOTIF_ID, -1) ?: -1
        DiagLog.add(this, "Service bắt đầu chạy (loại=$type)")

        // Bắt buộc gọi startForeground() ngay để hệ thống công nhận đây là foreground service
        val notification = AlarmNotificationHelper.buildNotification(this, type, notifId)
        try {
            startForeground(if (notifId > 0) notifId else 9999, notification)
        } catch (e: Exception) {
            DiagLog.add(this, "startForeground LỖI: ${e.javaClass.simpleName}: ${e.message}")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (overlayView != null) {
            DiagLog.add(this, "Overlay đang hiển thị sẵn - bỏ qua lần báo này")
            return START_NOT_STICKY
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val deviceInUse = pm.isInteractive && !km.isKeyguardLocked
        val canOverlay = Settings.canDrawOverlays(this)
        DiagLog.add(this, "Máy đang được dùng (sáng & đã mở khoá)=$deviceInUse | quyền hiển thị trên app khác=$canOverlay")

        if (deviceInUse && canOverlay && showOverlay(type, notifId)) {
            return START_NOT_STICKY // giữ service sống tới khi bấm "Đã hiểu"
        }
        if (deviceInUse && !canOverlay) {
            DiagLog.add(this, "⚠ Máy đang dùng nhưng THIẾU quyền 'Hiển thị trên các ứng dụng khác' → Android chỉ cho hiện banner thông báo, không cho màn hình full tự bật đè lên app khác")
        }

        // Màn hình tắt / đang khoá (hoặc overlay không dùng được): mở Activity, kết hợp full-screen intent
        val activityIntent = Intent(this, FullScreenAlarmActivity::class.java)
        activityIntent.putExtra(FullScreenAlarmActivity.EXTRA_TYPE, type)
        activityIntent.putExtra(FullScreenAlarmActivity.EXTRA_NOTIF_ID, notifId)
        activityIntent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
        )
        try {
            startActivity(activityIntent)
            DiagLog.add(this, "Đã gọi startActivity (Android vẫn có thể chặn nếu app đang ở nền)")
        } catch (e: Exception) {
            DiagLog.add(this, "startActivity LỖI: ${e.javaClass.simpleName}: ${e.message}")
        }

        // Giữ lại thông báo (chứa full-screen intent), dừng trạng thái foreground rồi tự dừng service
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun showOverlay(type: String, notifId: Int): Boolean {
        return try {
            val themed = ContextThemeWrapper(applicationContext, R.style.Theme_TimerAlarm)
            val view = LayoutInflater.from(themed).inflate(R.layout.activity_full_screen_alarm, null)
            val tvTitle = view.findViewById<TextView>(R.id.tvAlarmTitle)
            val tvSubtitle = view.findViewById<TextView>(R.id.tvAlarmSubtitle)

            if (type == FullScreenAlarmActivity.TYPE_DATA_EXPIRY) {
                tvTitle.text = getString(R.string.data_title)
                tvSubtitle.text = "Gói 3G sắp hoặc đã hết hạn. Kiểm tra và mua gói mới nhé."
            } else {
                tvTitle.text = getString(R.string.breakfast_title)
                tvSubtitle.text = "Đã 11:00 — nhớ đặt/mua đồ ăn sáng cho Ba."
            }
            // Luôn rung; chuông báo thì tuỳ theo công tắc "Âm thanh khi báo" (áp dụng cho cả 2 mục)
            vibrator = AlarmVibration.start(this)
            if (Prefs.get(this).getBoolean(Prefs.KEY_SOUND_ON, false)) {
                mediaPlayer = AlarmSound.start(this)
            }
            view.findViewById<Button>(R.id.btnDismiss).setOnClickListener { dismissOverlay(notifId) }

            val layoutFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                layoutFlags,
                PixelFormat.TRANSLUCENT
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(view, params)
            overlayView = view
            DiagLog.add(this, "✅ Đã hiển thị màn hình full bằng overlay (đè lên app đang dùng)")
            true
        } catch (e: Exception) {
            vibrator?.cancel()
            vibrator = null
            AlarmSound.stop(mediaPlayer)
            mediaPlayer = null
            DiagLog.add(this, "Overlay LỖI: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    private fun dismissOverlay(notifId: Int) {
        removeOverlay()
        vibrator?.cancel()
        vibrator = null
        AlarmSound.stop(mediaPlayer)
        mediaPlayer = null
        if (notifId != -1) AlarmNotificationHelper.cancel(this, notifId)
        DiagLog.add(this, "Đã bấm 'Đã hiểu' trên overlay")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeOverlay() {
        val v = overlayView ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Exception) {
            // view đã bị gỡ trước đó - bỏ qua
        }
        overlayView = null
    }

    override fun onDestroy() {
        removeOverlay()
        vibrator?.cancel()
        AlarmSound.stop(mediaPlayer)
        super.onDestroy()
    }
}
