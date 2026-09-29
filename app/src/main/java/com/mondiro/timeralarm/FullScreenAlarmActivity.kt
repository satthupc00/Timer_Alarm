package com.mondiro.timeralarm

import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView

/** Màn hình báo dạng Activity - dùng khi máy đang tắt màn hình / khoá (hiện đè lên màn hình khoá). */
class FullScreenAlarmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val TYPE_BREAKFAST = "breakfast"
        const val TYPE_DATA_EXPIRY = "data_expiry"
    }

    private var vibrator: Vibrator? = null
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hiện đè lên màn khóa + sáng màn hình
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_full_screen_alarm)

        val type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_BREAKFAST
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        DiagLog.add(this, "✅ Màn hình full (Activity) đã hiển thị (loại=$type)")
        val tvTitle = findViewById<TextView>(R.id.tvAlarmTitle)
        val tvSubtitle = findViewById<TextView>(R.id.tvAlarmSubtitle)

        if (type == TYPE_DATA_EXPIRY) {
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

        // Chỉ 1 nút "Đã hiểu": tắt màn hình hiện tại tạm thời, KHÔNG dừng lịch nhắc.
        // Muốn ngưng hẳn thì tắt công tắc tính năng tương ứng ở màn hình chính.
        findViewById<Button>(R.id.btnDismiss).setOnClickListener {
            vibrator?.cancel()
            AlarmSound.stop(mediaPlayer)
            mediaPlayer = null
            if (notifId != -1) AlarmNotificationHelper.cancel(this, notifId)
            finish()
        }
    }

    override fun onDestroy() {
        vibrator?.cancel()
        AlarmSound.stop(mediaPlayer)
        super.onDestroy()
    }
}
