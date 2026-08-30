package com.htdzs.notepad.ink

import android.graphics.Path
import com.htdzs.notepad.model.Stroke

/**
 * 把采样点串成平滑曲线：以每个点为控制点，走到相邻两点的中点。
 * 直接 lineTo 连点会在拐弯处出现折角，写字时肉眼可见。
 */
object PathBuilder {

    /**
     * 构造 [fromIndex] 到末尾这段曲线，结果写进 [out]，调用方复用同一个 Path 避免每帧分配。
     *
     * 注意曲线是二次贝塞尔：加一个点会改动前一段的形状（前一段的终点是中点，中点会挪）。
     * 所以增量画的时候 [fromIndex] 要比「已经画到哪」再往回退一个点，接缝才对得上。
     */
    fun build(stroke: Stroke, out: Path, fromIndex: Int = 0) {
        out.reset()
        if (fromIndex >= stroke.size) return

        out.moveTo(stroke.x(fromIndex), stroke.y(fromIndex))
        for (i in fromIndex + 1 until stroke.size - 1) {
            val midX = (stroke.x(i) + stroke.x(i + 1)) * 0.5f
            val midY = (stroke.y(i) + stroke.y(i + 1)) * 0.5f
            out.quadTo(stroke.x(i), stroke.y(i), midX, midY)
        }
        val last = stroke.size - 1
        if (last > fromIndex) out.lineTo(stroke.x(last), stroke.y(last))
    }
}
