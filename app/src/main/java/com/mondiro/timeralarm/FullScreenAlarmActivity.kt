package com.mondiro.timeralarm

import android.media.AudioAttributes
import android.media.RingtoneManager
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
        const val TYPE_BREAKFAST = "breakfast"
        const val TYPE_DATA_EXPIRY = "data_expiry"
    }

    private var mediaPlayer: android.media.MediaPlayer? = null
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
        val tvTitle = findViewById<TextView>(R.id.tvAlarmTitle)
        val tvSubtitle = findViewById<TextView>(R.id.tvAlarmSubtitle)

        if (type == TYPE_DATA_EXPIRY) {
            tvTitle.text = getString(R.string.data_title)
            tvSubtitle.text = "Gói 3G sắp hoặc đã hết hạn. Kiểm tra và mua gói mới nhé."
            // Nhắc data: chỉ hiện màn hình, không kêu chuông (theo yêu cầu)
        } else {
            tvTitle.text = getString(R.string.breakfast_title)
            tvSubtitle.text = "Đã 11:00 — nhớ đặt/mua đồ ăn sáng cho Ba."
            playAlarmSoundAndVibrate()
        }

        findViewById<Button>(R.id.btnDismiss).setOnClickListener {
            stopAlarmEffects()
            if (type != TYPE_DATA_EXPIRY) {
                // Bấm "Đã hiểu" cho mục đồ ăn sáng -> đánh dấu đã tắt hôm nay, dừng luôn vòng lặp nhắc mỗi 10 phút
                Prefs.get(this).edit()
                    .putString(Prefs.KEY_BREAKFAST_DISMISSED_DATE, BreakfastAlarmReceiver.todayDateString())
                    .apply()
                AlarmScheduler.cancelBreakfastRepeat(this)
            }
            finish()
        }
        findViewById<Button>(R.id.btnSnooze).setOnClickListener {
            stopAlarmEffects()
            finish()
        }
    }

    private fun playAlarmSoundAndVibrate() {
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@FullScreenAlarmActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) { /* thiết bị không có ringtone hợp lệ - bỏ qua */ }

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

    private fun stopAlarmEffects() {
        mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() }
        mediaPlayer = null
        vibrator?.cancel()
    }

    override fun onDestroy() {
        stopAlarmEffects()
        super.onDestroy()
    }
}
