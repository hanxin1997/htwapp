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
import com.htdzs.notepad.device.EinkFastRefresh
import com.htdzs.notepad.ink.PalmRejector
import com.htdzs.notepad.ink.PressureProbe
import com.htdzs.notepad.model.PenConfig
import com.htdzs.notepad.model.Stroke
import com.htdzs.notepad.render.StrokeRenderer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * 画布。已完成的笔画烘进一张页面位图。
 *
 * 电纸书要点：
 * - 整个 application 关了硬件加速（见 AndroidManifest），局部失效才真的只重绘那一小块
 * - 每帧开销必须与笔画长度无关，见 [commitTail]
 * - 刷新波形交给 [EinkFastRefresh]，那是延迟的大头
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val strokes = ArrayList<Stroke>()
    private val renderer = StrokeRenderer()
    private val palmRejector = PalmRejector()
    private val pressureProbe = PressureProbe()
    private val density = resources.displayMetrics.density
    private val fastRefresh = EinkFastRefresh.probe(context)

    /** 位图 1:1 拷屏，不要抗锯齿也不要过滤 */
    private val blitPaint = Paint()
    private val dirty = RectF()

    private var page: Bitmap? = null
    private var pageCanvas: Canvas? = null

    private var liveStroke: Stroke? = null

    /**
     * 正在写的这一笔已经烘进页面位图到第几个点。-1 = 一点没烘。
     *
     * 不变式：页面位图 == 白纸 + [strokes] 全部 + liveStroke 的 0..committedIndex 段。
     */
    private var committedIndex = NOTHING_COMMITTED

    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var activeIsStylus = false

    private val leaveFastRefresh = Runnable { fastRefresh?.leave() }

    var pen: PenConfig = PenConfig.DEFAULT

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return
        // 页面要重建，正在写的那一笔留不住
        if (liveStroke != null) endLiveStroke()
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
        // 能增量提交的笔型，墨已经在位图里了，不必再画一遍
        liveStroke?.takeIf { !it.type.overlapSafe }?.let { renderer.render(canvas, it) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseFastRefresh()
    }

    /** 切回正常刷新波形。App 进后台必须调，别把机器留在快刷模式上 */
    fun releaseFastRefresh() {
        removeCallbacks(leaveFastRefresh)
        fastRefresh?.leave()
    }

    fun undo() {
        // 正在写的这一笔就是「最后一笔」，撤销它 = 取消它
        if (cancelLiveStroke()) return
        if (strokes.isEmpty()) return
        strokes.removeAt(strokes.size - 1)
        redrawPage()
    }

    /** 擦掉全部笔画。选的笔不动 —— 擦内容和换笔是两件事 */
    fun clear() {
        // 本来就是白纸就别刷了，白刷一次整屏
        if (strokes.isEmpty() && liveStroke == null) return
        if (liveStroke != null) endLiveStroke()
        strokes.clear()
        redrawPage()
    }

    /**
     * 丢弃正在写的那一笔并把屏上那道墨擦掉。
     *
     * @return 本来有没有笔在写
     */
    private fun cancelLiveStroke(): Boolean {
        val stroke = liveStroke ?: return false
        val wasCommitted = committedIndex != NOTHING_COMMITTED
        endLiveStroke()
        // 已经烘进位图的部分只能整页重放才擦得掉；没烘的只在屏上，刷一下那块就行
        if (wasCommitted) redrawPage() else invalidateStroke(stroke, fromIndex = 0)
        return true
    }

    /**
     * 一笔结束（提交、取消、被清空都算）。**所有让 liveStroke 归 null 的路径都得走这儿**，
     * 否则切回正常波形的定时器排不上，机器会一直留在快刷模式上，画质降级。
     */
    private fun endLiveStroke() {
        liveStroke = null
        committedIndex = NOTHING_COMMITTED
        activePointerId = MotionEvent.INVALID_POINTER_ID
        removeCallbacks(leaveFastRefresh)
        postDelayed(leaveFastRefresh, FAST_REFRESH_LINGER_MS)
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
        pressureProbe.observe(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> cancelLiveStroke()
        }
        return true
    }

    private fun handleDown(event: MotionEvent) {
        val index = event.actionIndex
        val isStylus = PalmRejector.isStylus(event.getToolType(index))
        if (liveStroke != null) {
            // 已经在画了。唯一值得换手的情形：手掌先落画了几笔，笔随后才落下
            if (!isStylus || activeIsStylus) return
            cancelLiveStroke()
        } else if (!palmRejector.acceptDown(event, index)) {
            return
        }
        startStroke(event, index, isStylus)
    }

    private fun startStroke(event: MotionEvent, pointerIndex: Int, isStylus: Boolean) {
        removeCallbacks(leaveFastRefresh)
        fastRefresh?.enter()
        // 触摸事件默认按帧攒批下发，白等一帧
        requestUnbufferedDispatch(event)

        activePointerId = event.getPointerId(pointerIndex)
        activeIsStylus = isStylus
        val stroke = Stroke(pen.type, pen.widthPx(density), pressureProbe.usePressure)
        stroke.add(
            event.getX(pointerIndex),
            event.getY(pointerIndex),
            event.getPressure(pointerIndex),
        )
        liveStroke = stroke
        committedIndex = NOTHING_COMMITTED
        if (stroke.type.overlapSafe) commitTail(stroke)
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
        if (stroke.type.overlapSafe) commitTail(stroke)
        invalidateStroke(stroke, fromIndex)
    }

    private fun handleUp(event: MotionEvent) {
        if (event.getPointerId(event.actionIndex) != activePointerId) return
        val stroke = liveStroke ?: return
        // 不能增量提交的笔型，整笔一次画完 —— 半透明的墨分段画会在接缝处叠深
        if (!stroke.type.overlapSafe) {
            pageCanvas?.let { renderer.render(it, stroke) }
        }
        strokes.add(stroke)
        endLiveStroke()
        // 这里故意不 invalidate：屏上像素已经是对的 —— 增量提交的笔型每段都刷过了，
        // 整笔提交的笔型实时预览和刚烘进去的内容出自同一个 renderer。多刷一次只会添一次闪
    }

    /**
     * 把还没烘进页面位图的那几段烘进去。**每帧只画新增的一小段，开销与笔画长度无关。**
     *
     * 每帧重画整笔是单帧 O(N)、整笔 O(N²)：一笔几百上千个点，写到后面每帧要跑几百次
     * drawLine 才画出笔尖那一毫米，事件队列堵住，墨就落在笔后面。
     */
    private fun commitTail(stroke: Stroke) {
        val canvas = pageCanvas ?: return
        renderer.render(canvas, stroke, max(0, committedIndex))
        committedIndex = stroke.size - 1
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

        const val NOTHING_COMMITTED = -1

        /**
         * 脏矩形的回溯点数。要盖住渲染器为对齐接缝往回重画的那几段
         * （[StrokeRenderer.SEAM_LOOKBACK]），留了富余。
         */
        const val DIRTY_LOOKBACK = 3

        /** 抗锯齿会溢出笔宽一点，多留几像素 */
        const val DIRTY_PADDING_PX = 4f

        /** 抬笔后多久切回正常波形。连着写下一笔时别来回切，切一次就闪一次 */
        const val FAST_REFRESH_LINGER_MS = 1200L
    }
}
