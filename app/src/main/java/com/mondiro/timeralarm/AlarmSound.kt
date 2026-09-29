package com.mondiro.timeralarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager

/** Chuông báo lặp lại - chỉ phát khi người dùng đã bật công tắc "Âm thanh khi báo". */
object AlarmSound {
    fun start(context: Context): MediaPlayer? {
        return try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            null // thiết bị không có ringtone hợp lệ - bỏ qua, vẫn còn rung
        }
    }

    fun stop(player: MediaPlayer?) {
        try {
            if (player != null && player.isPlaying) player.stop()
            player?.release()
        } catch (_: Exception) { /* bỏ qua */ }
    }
}
