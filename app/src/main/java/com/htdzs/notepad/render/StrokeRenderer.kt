package com.htdzs.notepad.render

import android.graphics.Canvas
import com.htdzs.notepad.model.Stroke

/**
 * 全app唯一的画笔画入口。三处共用：正在写的实时预览、抬笔后提交到页面位图、
 * 撤销后整页重放。共用一个函数，就不会出现「写的时候和最终结果不一样」。
 */
class StrokeRenderer {

    private val fountainPen = FountainPen()
    private val solidPen = SolidPen()

    fun render(canvas: Canvas, stroke: Stroke) {
        if (stroke.type.variableWidth) {
            fountainPen.draw(canvas, stroke)
        } else {
            solidPen.draw(canvas, stroke)
        }
    }
}
