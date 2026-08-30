package com.htdzs.notepad.ink

/**
 * 钢笔的逐点宽度系数。有压感就用压感，没有就用笔速 —— 写得快则细，收笔慢则粗。
 * 两种来源都过一遍指数平滑，否则相邻点宽度跳变，笔画边缘会起毛刺。
 *
 * 一个点算一次，算完就冻结（见 [com.htdzs.notepad.model.Stroke]）。不做整笔重算：
 * 边写边把线段烘进位图，已经烘进去的宽度改不了，重算会在接缝处露出台阶。
 */
object InkWidth {

    private const val MIN_FACTOR = 0.35f
    private const val MAX_FACTOR = 1f

    /** 平滑系数，越小越平缓 */
    private const val SMOOTHING = 0.3f

    /** 相邻点间距小于此值算「慢」，给最粗；大于 SPEED_THIN_PX 算「快」，给最细 */
    private const val SPEED_THICK_PX = 1.5f
    private const val SPEED_THIN_PX = 26f

    /** 传给 [nextFactor] 表示「这是第一个点，没有前驱」 */
    const val NO_PREVIOUS = -1f

    /**
     * 算下一个点的宽度系数，乘上这一笔的上限宽度就是实际宽度。
     *
     * @param previousFactor 前一个点的系数，第一个点传 [NO_PREVIOUS]
     * @param usePressure 用压感还是用笔速，一笔之内不变
     * @param pressure 这个点的压感，usePressure=false 时忽略
     * @param distanceFromPrevious 与前一个点的间距，usePressure=true 时忽略
     */
    fun nextFactor(
        previousFactor: Float,
        usePressure: Boolean,
        pressure: Float,
        distanceFromPrevious: Float,
    ): Float {
        val target = if (usePressure) {
            lerpFactor(pressure.coerceIn(0f, 1f))
        } else {
            speedFactor(distanceFromPrevious)
        }
        // 第一个点直接用目标值起步，没有可平滑的前值。
        // 笔速档此时间距为 0，落在「慢」那头 —— 起笔粗，正好像真笔落纸
        if (previousFactor == NO_PREVIOUS) return target
        return previousFactor + (target - previousFactor) * SMOOTHING
    }

    private fun speedFactor(distance: Float): Float {
        val fast = ((distance - SPEED_THICK_PX) / (SPEED_THIN_PX - SPEED_THICK_PX)).coerceIn(0f, 1f)
        return lerpFactor(1f - fast)
    }

    private fun lerpFactor(t: Float): Float = MIN_FACTOR + (MAX_FACTOR - MIN_FACTOR) * t
}
