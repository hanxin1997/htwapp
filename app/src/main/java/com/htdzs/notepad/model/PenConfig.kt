package com.htdzs.notepad.model

import androidx.annotation.StringRes
import com.htdzs.notepad.R

enum class WidthLevel(@StringRes val labelRes: Int, val scale: Float) {
    THIN(R.string.width_thin, 0.5f),
    MEDIUM(R.string.width_medium, 1f),
    THICK(R.string.width_thick, 2f),
}

/** 当前选的笔。清空按钮会把它复位成 [DEFAULT]。 */
data class PenConfig(
    val type: PenType = PenType.FOUNTAIN,
    val level: WidthLevel = WidthLevel.MEDIUM,
) {
    fun widthPx(density: Float): Float = type.baseWidthDp * level.scale * density

    companion object {
        val DEFAULT = PenConfig()
    }
}
