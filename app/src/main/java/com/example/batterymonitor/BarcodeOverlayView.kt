package com.example.batterymonitor


import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class BarcodeOverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var barcodeBounds: List<Rect> = emptyList()
    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    fun setBarcodes(bounds: List<Rect>) {
        barcodeBounds = bounds
        invalidate() // redraw
    }

    fun updateBoundingBoxes(bounds: List<Rect>) {
        setBarcodes(bounds)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (bound in barcodeBounds) {
            canvas.drawRect(bound, paint)
        }
    }
}
