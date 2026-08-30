package com.htdzs.notepad.model

import androidx.annotation.StringRes
import com.htdzs.notepad.R

enum class WidthLevel(@StringRes val labelRes: Int, val scale: Float) {
    THIN(R.string.width_thin, 0.5f),
    MEDIUM(R.string.width_medium, 1f),
    THICK(R.string.width_thick, 2f),
}

/** 当前选的笔。[DEFAULT] 只用于启动时的初值，清空按钮不动它。 */
data class PenConfig(
    val type: PenType = PenType.FOUNTAIN,
    val level: WidthLevel = WidthLevel.MEDIUM,
) {
    fun widthPx(density: Float): Float = type.baseWidthDp * level.scale * density

    companion object {
        val DEFAULT = PenConfig()
    }
}
