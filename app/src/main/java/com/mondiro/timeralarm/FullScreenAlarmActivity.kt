package com.mondiro.timeralarm

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView

class FullScreenAlarmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val TYPE_BREAKFAST = "breakfast"
        const val TYPE_DATA_EXPIRY = "data_expiry"
    }

    private var vibrator: Vibrator? = null

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
        val tvTitle = findViewById<TextView>(R.id.tvAlarmTitle)
        val tvSubtitle = findViewById<TextView>(R.id.tvAlarmSubtitle)

        if (type == TYPE_DATA_EXPIRY) {
            tvTitle.text = getString(R.string.data_title)
            tvSubtitle.text = "Gói 3G sắp hoặc đã hết hạn. Kiểm tra và mua gói mới nhé."
            // Nhắc data: chỉ hiện màn hình, không rung không kêu (theo yêu cầu)
        } else {
            tvTitle.text = getString(R.string.breakfast_title)
            tvSubtitle.text = "Đã 11:00 — nhớ đặt/mua đồ ăn sáng cho Ba."
            // Không phát âm thanh nữa (theo yêu cầu) - chỉ rung nhẹ để báo hiệu
            vibrate()
        }

        // Chỉ còn 1 nút "Đã hiểu": tắt màn hình hiện tại tạm thời, KHÔNG dừng lịch nhắc -
        // app vẫn tiếp tục nhắc lại theo đúng lịch (10p/lần tới 12h, hoặc 6h/lần cho tới khi gia hạn).
        // Muốn ngưng hẳn thì tắt công tắc tính năng tương ứng ở màn hình chính.
        findViewById<Button>(R.id.btnDismiss).setOnClickListener {
            vibrator?.cancel()
            if (notifId != -1) AlarmNotificationHelper.cancel(this, notifId)
            finish()
        }
    }

    private fun vibrate() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    override fun onDestroy() {
        vibrator?.cancel()
        super.onDestroy()
    }
}
