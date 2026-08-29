package com.htdzs.notepad.model

import androidx.annotation.StringRes
import com.htdzs.notepad.R

/**
 * 五种笔。baseWidthDp 是「中」档的宽度，颜色的 alpha 通道直接编进 argb。
 *
 * variableWidth=true 走 FountainPen（逐段变宽），false 走 SolidPen（恒宽整笔一个 Path）。
 */
enum class PenType(
    @StringRes val labelRes: Int,
    val baseWidthDp: Float,
    val argb: Int,
    val variableWidth: Boolean,
) {
    FOUNTAIN(R.string.pen_fountain, baseWidthDp = 3f, argb = 0xFF000000.toInt(), variableWidth = true),

    /** 靠噪声 shader 出颗粒感，恒宽 —— 变宽会让 shader 在笔画重叠处叠深，反而脏 */
    PENCIL(R.string.pen_pencil, baseWidthDp = 3.5f, argb = 0xFF000000.toInt(), variableWidth = false),

    MARKER(R.string.pen_marker, baseWidthDp = 9f, argb = 0xB0000000.toInt(), variableWidth = false),

    /** 浅灰 + DARKEN 混合，压在字上只会变深不会盖白 */
    HIGHLIGHTER(R.string.pen_highlighter, baseWidthDp = 22f, argb = 0xFFB4B4B4.toInt(), variableWidth = false),

    /** 底图本来就是白的，橡皮就是不透明白笔，不需要 xfermode */
    ERASER(R.string.pen_eraser, baseWidthDp = 26f, argb = 0xFFFFFFFF.toInt(), variableWidth = false),
}
