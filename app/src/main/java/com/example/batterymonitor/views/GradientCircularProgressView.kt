package com.example.batterymonitor.views

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import kotlin.math.min

class GradientCircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress = 0f // 0 to 100
    private val strokeWidth = 40f
    private val rect = RectF()
    private var firstDraw = true
    private var backgroundRingColor: Int = Color.LTGRAY

    init {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.strokeCap = Paint.Cap.ROUND // smoother edges
    }

    fun setProgress(newProgress: Float) {
        val clampedProgress = newProgress.coerceIn(0f, 100f)
        if (firstDraw) {
            progress = clampedProgress
            invalidate()
            firstDraw = false
        } else {
            val animator = ValueAnimator.ofFloat(progress, clampedProgress)
            animator.duration = 800
            animator.addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            animator.start()
        }
    }

    fun setBackgroundRingColor(color: Int) {
        backgroundRingColor = color
        invalidate()
    }


    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - strokeWidth / 2
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Draw background circle
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = this@GradientCircularProgressView.strokeWidth
            color = backgroundRingColor

        }
        canvas.drawArc(rect, 0f, 360f, false, bgPaint)


        // Determine color based on SOC
        val progressColor = when {
            progress <= 30f -> "#D32F2F".toColorInt() // Red
            progress <= 40f -> "#FBC02D".toColorInt() // Amber
            progress <= 50f -> "#F9A825".toColorInt() // Yellow
            else -> "#22C55E".toColorInt()            // Green
        }

        // Set stroke color (no gradient shader)
        paint.shader = null
        paint.color = progressColor

        // Draw progress arc starting at top (-90 degrees)
        val sweepAngle = -360f * (progress / 100f) // negative for counter-clockwise
        canvas.drawArc(rect, -90f, sweepAngle, false, paint)

        // Draw moving head circle
        if (progress > 0f) {
            val angleRad = Math.toRadians((-90f + sweepAngle).toDouble())
            val headX = (cx + radius * Math.cos(angleRad)).toFloat()
            val headY = (cy + radius * Math.sin(angleRad)).toFloat()

            val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = progressColor
                setShadowLayer(10f, 0f, 0f, progressColor)
            }
            setLayerType(LAYER_TYPE_SOFTWARE, headPaint) // enable shadow

            val headRadius = strokeWidth / 2.5f
            canvas.drawCircle(headX, headY, headRadius, headPaint)
        }
    }
}
