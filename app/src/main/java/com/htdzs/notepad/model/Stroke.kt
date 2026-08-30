package com.htdzs.notepad.model

import com.htdzs.notepad.ink.InkWidth
import kotlin.math.hypot

/**
 * 一笔。点存在几个平行 FloatArray 里，不给每个点建对象 —— 一笔几百上千个点，对象开销扛不住。
 *
 * 逐点宽度在 [add] 时就算好冻结，不在渲染时算：边写边把线段烘进页面位图，
 * 已经烘进去的宽度改不了，渲染时重算会在接缝处露出台阶。
 *
 * @param widthPx 这一笔的上限宽度。钢笔按压感/笔速在此之下浮动，其余笔型恒等于它。
 * @param usePressure 用压感还是用笔速估宽度。落笔时定死，一笔之内不变 —— 中途换算法
 *   会让前面所有点的宽度追溯改变。判定见 [com.htdzs.notepad.ink.PressureProbe]。
 */
class Stroke(
    val type: PenType,
    val widthPx: Float,
    private val usePressure: Boolean,
) {

    private var xs = FloatArray(INITIAL_CAPACITY)
    private var ys = FloatArray(INITIAL_CAPACITY)
    private var widths = FloatArray(INITIAL_CAPACITY)

    /** 上一个点的宽度系数，喂给下一次平滑 */
    private var lastFactor = InkWidth.NO_PREVIOUS

    var size = 0
        private set

    fun add(x: Float, y: Float, pressure: Float) {
        if (size == xs.size) grow()
        xs[size] = x
        ys[size] = y
        widths[size] = nextWidth(x, y, pressure)
        size++
    }

    fun x(index: Int): Float = xs[index]

    fun y(index: Int): Float = ys[index]

    /** 第 [index] 个点冻结下来的宽度。恒宽笔型也填，省掉渲染时的分支 */
    fun width(index: Int): Float = widths[index]

    private fun nextWidth(x: Float, y: Float, pressure: Float): Float {
        if (!type.variableWidth) return widthPx

        val distance = if (size == 0) 0f else hypot(x - xs[size - 1], y - ys[size - 1])
        lastFactor = InkWidth.nextFactor(lastFactor, usePressure, pressure, distance)
        return widthPx * lastFactor
    }

    private fun grow() {
        val capacity = xs.size * 2
        xs = xs.copyOf(capacity)
        ys = ys.copyOf(capacity)
        widths = widths.copyOf(capacity)
    }

    private companion object {
        const val INITIAL_CAPACITY = 128
    }
}
