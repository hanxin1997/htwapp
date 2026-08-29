package com.htdzs.notepad.ink

import android.graphics.Path
import com.htdzs.notepad.model.Stroke

/**
 * 把采样点串成平滑曲线：以每个点为控制点，走到相邻两点的中点。
 * 直接 lineTo 连点会在拐弯处出现折角，写字时肉眼可见。
 */
object PathBuilder {

    /** 结果写进 [out]，调用方复用同一个 Path，避免每帧分配。 */
    fun build(stroke: Stroke, out: Path) {
        out.reset()
        if (stroke.size == 0) return

        out.moveTo(stroke.x(0), stroke.y(0))
        for (i in 1 until stroke.size - 1) {
            val midX = (stroke.x(i) + stroke.x(i + 1)) * 0.5f
            val midY = (stroke.y(i) + stroke.y(i + 1)) * 0.5f
            out.quadTo(stroke.x(i), stroke.y(i), midX, midY)
        }
        if (stroke.size > 1) {
            val last = stroke.size - 1
            out.lineTo(stroke.x(last), stroke.y(last))
        }
    }
}
