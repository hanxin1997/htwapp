package com.htdzs.notepad.render

import android.graphics.Canvas
import android.graphics.Paint
import com.htdzs.notepad.ink.InkWidth
import com.htdzs.notepad.model.Stroke

/**
 * 钢笔：逐段变宽的圆头线段。
 *
 * 为什么不用 Path 一次画完 —— Path 只能有一个 strokeWidth，出不来提按变化。
 * 每段单独 drawLine，圆头帽让接缝看不出来。钢笔不透明，自身重叠也不会叠深。
 */
internal class FountainPen {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var widths = FloatArray(INITIAL_CAPACITY)

    fun draw(canvas: Canvas, stroke: Stroke) {
        if (stroke.size == 0) return
        paint.color = stroke.type.argb

        if (widths.size < stroke.size) widths = FloatArray(stroke.size * 2)
        InkWidth.compute(stroke, stroke.widthPx, widths)

        if (stroke.size == 1) {
            Dot.draw(canvas, paint, stroke.x(0), stroke.y(0), widths[0])
            return
        }
        for (i in 0 until stroke.size - 1) {
            paint.strokeWidth = (widths[i] + widths[i + 1]) * 0.5f
            canvas.drawLine(stroke.x(i), stroke.y(i), stroke.x(i + 1), stroke.y(i + 1), paint)
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 256
    }
}
