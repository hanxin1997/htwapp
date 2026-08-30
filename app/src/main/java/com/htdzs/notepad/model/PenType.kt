package com.htdzs.notepad.model

import androidx.annotation.StringRes
import com.htdzs.notepad.R

/**
 * 五种笔。baseWidthDp 是「中」档的宽度，颜色的 alpha 通道直接编进 argb。
 *
 * variableWidth=true 走 FountainPen（逐段变宽），false 走 SolidPen（恒宽整笔一个 Path）。
 *
 * overlapSafe=true 的笔可以边写边把新增线段烘进页面位图，每帧开销与笔画长度无关。
 * false 的笔只能整笔画完再提交，写的时候每帧重画整笔。
 */
enum class PenType(
    @StringRes val labelRes: Int,
    val baseWidthDp: Float,
    val argb: Int,
    val variableWidth: Boolean,
    val overlapSafe: Boolean,
) {
    /** 不透明黑，重叠画还是黑 */
    FOUNTAIN(
        R.string.pen_fountain,
        baseWidthDp = 3f,
        argb = 0xFF000000.toInt(),
        variableWidth = true,
        overlapSafe = true,
    ),

    /**
     * 靠噪声 shader 出颗粒感，恒宽 —— 变宽会让 shader 在笔画重叠处叠深，反而脏。
     * 颗粒本身是逐像素半透明的，分段画会在接缝处叠深，所以不能增量提交。
     */
    PENCIL(
        R.string.pen_pencil,
        baseWidthDp = 3.5f,
        argb = 0xFF000000.toInt(),
        variableWidth = false,
        overlapSafe = false,
    ),

    /** 半透明黑，分段画会让重叠处 alpha 叠加，笔画变成一串深色珠子 */
    MARKER(
        R.string.pen_marker,
        baseWidthDp = 9f,
        argb = 0xB0000000.toInt(),
        variableWidth = false,
        overlapSafe = false,
    ),

    /**
     * 浅灰 + DARKEN 混合，压在字上只会变深不会盖白。
     *
     * 不透明色，笔画内部 DARKEN 就是取 min，重复画结果完全一样。抗锯齿边缘那一圈覆盖率
     * 不足 1，重画会再暗一点，但收敛到笔自己的灰、不会无限变深 —— 代价是接缝处 1 像素
     * 边缘略硬，换来每帧开销与笔画长度无关。
     */
    HIGHLIGHTER(
        R.string.pen_highlighter,
        baseWidthDp = 22f,
        argb = 0xFFB4B4B4.toInt(),
        variableWidth = false,
        overlapSafe = true,
    ),

    /** 底图本来就是白的，橡皮就是不透明白笔，不需要 xfermode */
    ERASER(
        R.string.pen_eraser,
        baseWidthDp = 26f,
        argb = 0xFFFFFFFF.toInt(),
        variableWidth = false,
        overlapSafe = true,
    ),
}
