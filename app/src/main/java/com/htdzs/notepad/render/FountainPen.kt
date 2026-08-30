package com.htdzs.notepad.render

import android.graphics.Canvas
import android.graphics.Paint
import com.htdzs.notepad.model.Stroke

/**
 * 钢笔：逐段变宽的圆头线段。
 *
 * 为什么不用 Path 一次画完 —— Path 只能有一个 strokeWidth，出不来提按变化。
 * 每段单独 drawLine，圆头帽让接缝看不出来。钢笔不透明，自身重叠也不会叠深。
 *
 * 线段之间互不影响，所以增量画不用往回退点：从 fromIndex 起画出来的像素，
 * 和整笔画一遍在这一段上完全一致。
 */
internal class FountainPen {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun draw(canvas: Canvas, stroke: Stroke, fromIndex: Int) {
        if (fromIndex >= stroke.size) return
        paint.color = stroke.type.argb

        // 点一下就抬笔：只有一个点，没有线段可画
        if (stroke.size == 1) {
            Dot.draw(canvas, paint, stroke.x(0), stroke.y(0), stroke.width(0))
            return
        }
        for (i in fromIndex until stroke.size - 1) {
            paint.strokeWidth = (stroke.width(i) + stroke.width(i + 1)) * 0.5f
            canvas.drawLine(stroke.x(i), stroke.y(i), stroke.x(i + 1), stroke.y(i + 1), paint)
        }
    }
}
