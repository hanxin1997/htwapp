package com.htdzs.notepad.model

/**
 * 一笔。点存在三个平行 FloatArray 里，不给每个点建对象 —— 一笔几百上千个点，
 * 每帧都要重绘，对象开销扛不住。
 *
 * @param widthPx 这一笔的宽度。钢笔按压感/笔速在此之下浮动，其余笔型恒等于它。
 */
class Stroke(val type: PenType, val widthPx: Float) {

    private var xs = FloatArray(INITIAL_CAPACITY)
    private var ys = FloatArray(INITIAL_CAPACITY)
    private var pressures = FloatArray(INITIAL_CAPACITY)

    private var minPressure = Float.MAX_VALUE
    private var maxPressure = -Float.MAX_VALUE

    var size = 0
        private set

    /**
     * 设备真的报压感吗？不报的机器会把每个点都填成恒定值（通常 1.0），
     * 这时候钢笔改用笔速估宽度。
     */
    val varyingPressure: Boolean
        get() = size > 1 && (maxPressure - minPressure) > PRESSURE_EPSILON

    fun add(x: Float, y: Float, pressure: Float) {
        if (size == xs.size) grow()
        xs[size] = x
        ys[size] = y
        pressures[size] = pressure
        size++
        if (pressure < minPressure) minPressure = pressure
        if (pressure > maxPressure) maxPressure = pressure
    }

    fun x(index: Int): Float = xs[index]

    fun y(index: Int): Float = ys[index]

    fun pressure(index: Int): Float = pressures[index]

    private fun grow() {
        val capacity = xs.size * 2
        xs = xs.copyOf(capacity)
        ys = ys.copyOf(capacity)
        pressures = pressures.copyOf(capacity)
    }

    private companion object {
        const val INITIAL_CAPACITY = 128
        const val PRESSURE_EPSILON = 0.02f
    }
}
