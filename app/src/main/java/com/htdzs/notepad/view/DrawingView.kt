package com.htdzs.notepad.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.htdzs.notepad.ink.PalmRejector
import com.htdzs.notepad.model.PenConfig
import com.htdzs.notepad.model.Stroke
import com.htdzs.notepad.render.StrokeRenderer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * 画布。已完成的笔画烘进一张页面位图，只有正在写的那一笔每帧重绘。
 *
 * 电纸书要点：整个 application 关了硬件加速（见 AndroidManifest），
 * 局部失效才真的只重绘那一小块 —— 开着硬件加速会退化成整屏重绘。
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val strokes = ArrayList<Stroke>()
    private val renderer = StrokeRenderer()
    private val palmRejector = PalmRejector()
    private val density = resources.displayMetrics.density

    /** 位图 1:1 拷屏，不要抗锯齿也不要过滤 */
    private val blitPaint = Paint()
    private val dirty = RectF()

    private var page: Bitmap? = null
    private var pageCanvas: Canvas? = null

    private var liveStroke: Stroke? = null
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var activeIsStylus = false

    var pen: PenConfig = PenConfig.DEFAULT

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return
        // 先分配新的再回收旧的：万一 createBitmap 失败，page 不能悬着一张已回收的位图
        val fresh = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        page?.recycle()
        page = fresh
        pageCanvas = Canvas(fresh)
        redrawPage()
    }

    override fun onDraw(canvas: Canvas) {
        val page = this.page ?: return
        canvas.drawBitmap(page, 0f, 0f, blitPaint)
        liveStroke?.let { renderer.render(canvas, it) }
    }

    fun undo() {
        if (strokes.isEmpty()) return
        strokes.removeAt(strokes.size - 1)
        redrawPage()
    }

    /** 擦掉全部笔画。笔的复位在 MainActivity 里做，View 不管选笔的事。 */
    fun clear() {
        if (strokes.isEmpty() && liveStroke == null) return
        strokes.clear()
        liveStroke = null
        activePointerId = MotionEvent.INVALID_POINTER_ID
        redrawPage()
    }

    private fun redrawPage() {
        val canvas = pageCanvas ?: return
        canvas.drawColor(PAPER_COLOR)
        for (stroke in strokes) {
            renderer.render(canvas, stroke)
        }
        invalidate()
    }

    // ---- 触摸 ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        palmRejector.observe(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> discardStroke()
        }
        return true
    }

    private fun handleDown(event: MotionEvent) {
        val index = event.actionIndex
        val isStylus = PalmRejector.isStylus(event.getToolType(index))
        if (liveStroke != null) {
            // 已经在画了。唯一值得换手的情形：手掌先落画了几笔，笔随后才落下
            if (!isStylus || activeIsStylus) return
            discardStroke()
        } else if (!palmRejector.acceptDown(event, index)) {
            return
        }
        startStroke(event, index, isStylus)
    }

    private fun startStroke(event: MotionEvent, pointerIndex: Int, isStylus: Boolean) {
        activePointerId = event.getPointerId(pointerIndex)
        activeIsStylus = isStylus
        val stroke = Stroke(pen.type, pen.widthPx(density))
        stroke.add(
            event.getX(pointerIndex),
            event.getY(pointerIndex),
            event.getPressure(pointerIndex),
        )
        liveStroke = stroke
        invalidateStroke(stroke, fromIndex = 0)
    }

    private fun handleMove(event: MotionEvent) {
        val stroke = liveStroke ?: return
        val index = event.findPointerIndex(activePointerId)
        if (index < 0) return

        val fromIndex = max(0, stroke.size - DIRTY_LOOKBACK)
        // 高刷设备一个事件里能攒好几个点，不取历史点笔迹会变成折线
        for (h in 0 until event.historySize) {
            stroke.add(
                event.getHistoricalX(index, h),
                event.getHistoricalY(index, h),
                event.getHistoricalPressure(index, h),
            )
        }
        stroke.add(event.getX(index), event.getY(index), event.getPressure(index))
        invalidateStroke(stroke, fromIndex)
    }

    private fun handleUp(event: MotionEvent) {
        if (event.getPointerId(event.actionIndex) != activePointerId) return
        val stroke = liveStroke ?: return
        pageCanvas?.let { renderer.render(it, stroke) }
        strokes.add(stroke)
        liveStroke = null
        activePointerId = MotionEvent.INVALID_POINTER_ID
        // 这里故意不 invalidate：实时预览和刚提交的位图内容出自同一个 renderer，
        // 屏上像素已经是对的。多刷一次只会给电纸书添一次闪。
    }

    private fun discardStroke() {
        val stroke = liveStroke ?: return
        liveStroke = null
        activePointerId = MotionEvent.INVALID_POINTER_ID
        // 这一笔没进位图，屏上那道墨得擦掉
        invalidateStroke(stroke, fromIndex = 0)
    }

    /**
     * 只重画 [fromIndex] 起的新增部分。平滑曲线会往回牵动前面一两个点，
     * 所以调用方传进来的 fromIndex 要留 [DIRTY_LOOKBACK] 的余量。
     */
    @Suppress("DEPRECATION") // invalidate(l,t,r,b) 在 API 28 标了废弃，但软件渲染下仍然只刷这块，正是电纸书要的
    private fun invalidateStroke(stroke: Stroke, fromIndex: Int) {
        if (stroke.size == 0) return
        dirty.set(stroke.x(fromIndex), stroke.y(fromIndex), stroke.x(fromIndex), stroke.y(fromIndex))
        for (i in fromIndex + 1 until stroke.size) {
            dirty.union(stroke.x(i), stroke.y(i))
        }
        val pad = stroke.widthPx * 0.5f + DIRTY_PADDING_PX
        dirty.inset(-pad, -pad)
        invalidate(
            floor(dirty.left).toInt(),
            floor(dirty.top).toInt(),
            ceil(dirty.right).toInt(),
            ceil(dirty.bottom).toInt(),
        )
    }

    private companion object {
        val PAPER_COLOR = Color.WHITE

        /** 平滑曲线的回溯点数：加一个点会改动前面三个点决定的那段 */
        const val DIRTY_LOOKBACK = 3

        /** 抗锯齿会溢出笔宽一点，多留几像素 */
        const val DIRTY_PADDING_PX = 4f
    }
}
