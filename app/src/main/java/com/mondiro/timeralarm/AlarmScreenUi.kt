package com.mondiro.timeralarm

import android.animation.Animator
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gắn nội dung + animation cho màn báo toàn màn hình (activity_full_screen_alarm),
 * dùng chung cho FullScreenAlarmActivity và overlay trong AlarmForegroundService.
 */
object AlarmScreenUi {

    fun bind(context: Context, root: View, type: String) {
        val isData = type == FullScreenAlarmActivity.TYPE_DATA_EXPIRY
        val ink = ContextCompat.getColor(context, if (isData) R.color.sky_ink else R.color.peach_ink)

        root.setBackgroundResource(if (isData) R.drawable.bg_alarm_data else R.drawable.bg_alarm_breakfast)
        val pulse1 = root.findViewById<View>(R.id.pulse1)
        val pulse2 = root.findViewById<View>(R.id.pulse2)
        val pulseBg = if (isData) R.drawable.bg_pulse_sky else R.drawable.bg_pulse_peach
        pulse1.setBackgroundResource(pulseBg)
        pulse2.setBackgroundResource(pulseBg)

        val icon = root.findViewById<ImageView>(R.id.ivAlarmIcon)
        icon.setImageResource(if (isData) R.drawable.ic_wifi else R.drawable.ic_cup)
        icon.imageTintList = ColorStateList.valueOf(ink)

        root.findViewById<TextView>(R.id.tvAlarmClock).text =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val title = root.findViewById<TextView>(R.id.tvAlarmTitle)
        val sub = root.findViewById<TextView>(R.id.tvAlarmSubtitle)
        if (isData) {
            title.setText(R.string.alarm_data_title)
            val expiry = Prefs.get(context).getLong(Prefs.KEY_EXPIRY_TIMESTAMP, 0L)
            sub.text = if (expiry > 0L) {
                context.getString(
                    R.string.alarm_data_sub_fmt,
                    SimpleDateFormat("HH:mm 'ngày' dd/MM", Locale.getDefault()).format(Date(expiry))
                )
            } else {
                context.getString(R.string.alarm_data_sub)
            }
        } else {
            title.setText(R.string.breakfast_title)
            sub.setText(R.string.alarm_breakfast_sub)
        }

        // Animation lặp: 2 vòng sóng lan ra lệch nhau 1s + icon rung lắc. Tự huỷ khi view bị gỡ.
        val anims = listOf(
            ripple(pulse1, 0L),
            ripple(pulse2, 1000L),
            shake(icon)
        )
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                anims.forEach { it.cancel() }
            }
        })
    }

    private fun ripple(v: View, delay: Long): Animator =
        ObjectAnimator.ofPropertyValuesHolder(
            v,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.75f, 1.4f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.75f, 1.4f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.35f, 0f)
        ).apply {
            duration = 2000
            startDelay = delay
            repeatCount = ValueAnimator.INFINITE
            interpolator = Motion.EASE_OUT
            start()
        }

    private fun shake(v: View): Animator {
        v.pivotY = 0f
        v.post { v.pivotX = v.width / 2f; v.pivotY = v.height * 0.2f }
        val rot = PropertyValuesHolder.ofKeyframe(
            View.ROTATION,
            Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(0.1f, -12f), Keyframe.ofFloat(0.2f, 10f),
            Keyframe.ofFloat(0.3f, -8f), Keyframe.ofFloat(0.4f, 5f), Keyframe.ofFloat(0.5f, 0f),
            Keyframe.ofFloat(1f, 0f)
        )
        return ObjectAnimator.ofPropertyValuesHolder(v, rot).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }
}
