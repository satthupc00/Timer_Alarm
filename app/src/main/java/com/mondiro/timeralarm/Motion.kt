package com.mondiro.timeralarm

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator

/** Animation dùng chung theo chuẩn Mondiro (ease-out / spring, ngắn gọn). */
object Motion {
    val EASE_OUT = PathInterpolator(0.22f, 1f, 0.36f, 1f)

    /** Các card hiện dần + trượt lên 8dp, lệch nhau 60ms. */
    fun rise(views: List<View>) {
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = 8f * v.resources.displayMetrics.density
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(60L * i).setDuration(450).setInterpolator(EASE_OUT).start()
        }
    }

    /** Lắc nhẹ icon badge khi bật công tắc. */
    fun wiggle(v: View) {
        val rot = PropertyValuesHolder.ofKeyframe(
            View.ROTATION,
            Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(0.3f, -12f),
            Keyframe.ofFloat(0.6f, 9f), Keyframe.ofFloat(1f, 0f)
        )
        val sc = Keyframe.ofFloat(0.3f, 1.08f)
        val sx = PropertyValuesHolder.ofKeyframe(View.SCALE_X, Keyframe.ofFloat(0f, 1f), sc, Keyframe.ofFloat(1f, 1f))
        val sy = PropertyValuesHolder.ofKeyframe(View.SCALE_Y, Keyframe.ofFloat(0f, 1f), sc.clone(), Keyframe.ofFloat(1f, 1f))
        ObjectAnimator.ofPropertyValuesHolder(v, rot, sx, sy).setDuration(500).start()
    }

    /** Hiện ra kiểu "pop" (scale 0 -> 1, spring). */
    fun pop(v: View, from: Float = 0f) {
        v.scaleX = from
        v.scaleY = from
        v.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(OvershootInterpolator(2.2f)).start()
    }

    /** Xoay liên tục 1 view (vd icon refresh khi đang kiểm tra). Trả về animator để dừng. */
    fun spin(v: View): ObjectAnimator =
        ObjectAnimator.ofFloat(v, View.ROTATION, 0f, 360f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

    /** Xoay drawable (RotateDrawable) bằng level - dùng cho icon trong MaterialButton. */
    fun spinDrawable(d: Drawable?): ValueAnimator? {
        d ?: return null
        return ValueAnimator.ofInt(0, 10000).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { d.level = it.animatedValue as Int }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) { d.level = 0 }
            })
            start()
        }
    }

    /** Nhịp đập nhẹ lặp lại (nút "Cập nhật") cho tới khi bị huỷ. */
    fun pulse(v: View): ObjectAnimator =
        ObjectAnimator.ofPropertyValuesHolder(
            v,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f)
        ).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
}
