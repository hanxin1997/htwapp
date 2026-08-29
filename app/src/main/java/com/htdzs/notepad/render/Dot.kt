package com.htdzs.notepad.render

import android.graphics.Canvas
import android.graphics.Paint

/** 点一下就抬笔的情况，画个圆点。两种笔都要用，抽出来免得抄两遍。 */
internal object Dot {

    fun draw(canvas: Canvas, paint: Paint, x: Float, y: Float, width: Float) {
        val savedStyle = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, width * 0.5f, paint)
        paint.style = savedStyle
    }
}
