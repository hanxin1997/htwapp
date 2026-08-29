package com.htdzs.notepad.ink

import com.htdzs.notepad.model.Stroke
import kotlin.math.hypot

/**
 * 钢笔的逐点宽度。有压感就用压感，没有就用笔速 —— 写得快则细，收笔慢则粗。
 * 两种来源都过一遍指数平滑，否则相邻点宽度跳变，笔画边缘会起毛刺。
 */
object InkWidth {

    private const val MIN_FACTOR = 0.35f
    private const val MAX_FACTOR = 1f

    /** 平滑系数，越小越平缓 */
    private const val SMOOTHING = 0.3f

    /** 相邻点间距小于此值算「慢」，给最粗；大于 SPEED_THIN_PX 算「快」，给最细 */
    private const val SPEED_THICK_PX = 1.5f
    private const val SPEED_THIN_PX = 26f

    /**
     * 算出每个点的宽度写进 [out]，长度必须 >= stroke.size。
     * @param maxWidth 这一笔的上限宽度
     */
    fun compute(stroke: Stroke, maxWidth: Float, out: FloatArray) {
        val usePressure = stroke.varyingPressure
        var smoothed = -1f
        for (i in 0 until stroke.size) {
            val target = if (usePressure) pressureFactor(stroke, i) else speedFactor(stroke, i)
            smoothed = if (smoothed < 0f) target else smoothed + (target - smoothed) * SMOOTHING
            out[i] = maxWidth * smoothed
        }
    }

    private fun pressureFactor(stroke: Stroke, index: Int): Float =
        lerpFactor(stroke.pressure(index).coerceIn(0f, 1f))

    /** 第 0 个点没有前驱，间距按 0 算 —— 起笔粗，正好像真笔落纸 */
    private fun speedFactor(stroke: Stroke, index: Int): Float {
        if (index == 0) return MAX_FACTOR
        val distance = hypot(
            stroke.x(index) - stroke.x(index - 1),
            stroke.y(index) - stroke.y(index - 1),
        )
        val fast = ((distance - SPEED_THICK_PX) / (SPEED_THIN_PX - SPEED_THICK_PX)).coerceIn(0f, 1f)
        return lerpFactor(1f - fast)
    }

    private fun lerpFactor(t: Float): Float = MIN_FACTOR + (MAX_FACTOR - MIN_FACTOR) * t
}
