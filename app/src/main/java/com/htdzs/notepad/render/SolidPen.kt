package com.htdzs.notepad.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.htdzs.notepad.ink.Grain
import com.htdzs.notepad.ink.PathBuilder
import com.htdzs.notepad.model.PenType
import com.htdzs.notepad.model.Stroke
import kotlin.math.max

/**
 * 恒宽笔：铅笔、马克笔、荧光笔、橡皮。
 *
 * 整笔构造成一个 Path 一次画完，这对半透明的马克笔和铅笔是必须的 —— 分段画会让
 * 段与段的重叠处 alpha 叠加，笔画变成一串深色珠子。所以这两种笔型
 * （[PenType.overlapSafe] = false）只会以 fromIndex=0 调用。
 *
 * 荧光笔和橡皮可以增量画：不透明白直接覆盖，DARKEN 在不透明色下就是取 min，
 * 重复画的结果一样（抗锯齿边缘的偏差见 [PenType.HIGHLIGHTER]）。
 */
internal class SolidPen {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val path = Path()

    fun draw(canvas: Canvas, stroke: Stroke, fromIndex: Int) {
        if (fromIndex >= stroke.size) return
        configure(stroke.type)
        paint.strokeWidth = stroke.widthPx

        if (stroke.size == 1) {
            Dot.draw(canvas, paint, stroke.x(0), stroke.y(0), stroke.widthPx)
            return
        }
        // 二次贝塞尔：加一个点会挪动前一段的终点（中点），往回退一个点接缝才对得上
        PathBuilder.build(stroke, path, max(0, fromIndex - StrokeRenderer.SEAM_LOOKBACK))
        canvas.drawPath(path, paint)
    }

    private fun configure(type: PenType) {
        paint.color = type.argb
        // shader 生效时 paint.color 被忽略，颜色来自噪声图 —— 铅笔要的就是这个
        paint.shader = if (type == PenType.PENCIL) Grain.shader else null
        // DARKEN：荧光笔只会把底下变深，不会把黑字盖成灰
        paint.xfermode = if (type == PenType.HIGHLIGHTER) darken else null
    }

    private companion object {
        val darken = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
    }
}
