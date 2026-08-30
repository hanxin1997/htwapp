package com.htdzs.notepad.ink

import android.view.MotionEvent
import kotlin.math.abs

/**
 * 这台机器的笔到底报不报压感？
 *
 * 不报的机器会把每个点都填成恒定值（通常 1.0）。这个判定必须**跨笔画**记住：
 * 一笔之内的宽度算法不能中途换 —— 边写边把线段烘进位图，已经烘进去的宽度改不了，
 * 换算法会让前面所有点的宽度追溯改变，接缝处露出台阶。
 *
 * 代价：压感设备上第一笔用笔速估宽度，从第二笔起用压感。换来任何一笔内部宽度都不跳变。
 */
class PressureProbe {

    private var firstPressure = UNSET
    private var sawVariation = false

    /** 观察到过压感变化就用压感，否则用笔速 */
    val usePressure: Boolean
        get() = sawVariation

    /** 每个事件都要调一次。只看手写笔，手指的压感是接触面积，不是笔压 */
    fun observe(event: MotionEvent) {
        if (sawVariation) return
        for (index in 0 until event.pointerCount) {
            if (!PalmRejector.isStylus(event.getToolType(index))) continue
            record(event.getPressure(index))
            if (sawVariation) return
        }
    }

    private fun record(pressure: Float) {
        if (firstPressure == UNSET) {
            firstPressure = pressure
            return
        }
        if (abs(pressure - firstPressure) > PRESSURE_EPSILON) sawVariation = true
    }

    private companion object {
        const val UNSET = -1f
        const val PRESSURE_EPSILON = 0.02f
    }
}
