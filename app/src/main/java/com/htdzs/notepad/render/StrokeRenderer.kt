package com.htdzs.notepad.render

import android.graphics.Canvas
import com.htdzs.notepad.model.Stroke

/**
 * 全app唯一的画笔画入口。三处共用：正在写的实时预览、把新增线段烘进页面位图、
 * 撤销后整页重放。共用一个函数，就不会出现「写的时候和最终结果不一样」。
 */
class StrokeRenderer {

    private val fountainPen = FountainPen()
    private val solidPen = SolidPen()

    /**
     * @param fromIndex 从第几个点起画。0 = 整笔。增量提交时传「已经画到哪个点」，
     *   渲染器自己决定要不要往回多画几段来对齐接缝，最多往回 [SEAM_LOOKBACK] 个点。
     *   只有 [com.htdzs.notepad.model.PenType.overlapSafe] 的笔型能传非 0 值。
     */
    fun render(canvas: Canvas, stroke: Stroke, fromIndex: Int = 0) {
        if (stroke.type.variableWidth) {
            fountainPen.draw(canvas, stroke, fromIndex)
        } else {
            solidPen.draw(canvas, stroke, fromIndex)
        }
    }

    companion object {
        /** 渲染器为对齐接缝最多往回重画的点数，调用方算脏矩形要留够这个余量 */
        const val SEAM_LOOKBACK = 1
    }
}
