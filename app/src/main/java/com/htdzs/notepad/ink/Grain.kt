package com.htdzs.notepad.ink

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import kotlin.random.Random

/**
 * 铅笔的颗粒质感：一张 64×64 的噪声图平铺当 shader。
 *
 * 种子固定。撤销之后要整页重放，颗粒必须和原来长得一样，否则用户会看到
 * 剩下的笔画「变了样」。
 */
object Grain {

    private const val TILE = 64
    private const val SEED = 20260829L

    /** 颗粒基础浓度和浮动范围，合起来落在 alpha 110~235 */
    private const val BASE_ALPHA = 110
    private const val ALPHA_RANGE = 125

    /** 挖成全透明的像素数，颗粒感主要靠这些空洞 */
    private const val HOLE_COUNT = 420

    val shader: BitmapShader by lazy {
        BitmapShader(buildTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private fun buildTile(): Bitmap {
        val random = Random(SEED)
        val pixels = IntArray(TILE * TILE)
        for (i in pixels.indices) {
            // rgb 全 0 = 黑，只让 alpha 抖动
            pixels[i] = (BASE_ALPHA + random.nextInt(ALPHA_RANGE)) shl 24
        }
        repeat(HOLE_COUNT) {
            pixels[random.nextInt(pixels.size)] = 0
        }
        return Bitmap.createBitmap(pixels, TILE, TILE, Bitmap.Config.ARGB_8888)
    }
}
