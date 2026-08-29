package com.htdzs.notepad.ink

import android.view.MotionEvent

/**
 * 防误触。没有厂商 SDK 可用，只能靠一条朴素规则：最近 [FINGER_LOCKOUT_MS]
 * 内出现过手写笔事件，就不接受手指起笔。
 *
 * 手写笔本身永远接受。已经开始的笔画不会被中途否决 —— 写一半断掉比误触更难受。
 */
class PalmRejector {

    private var lastStylusAt = 0L

    /** 每个事件都要调一次，不管收不收，用来更新「笔刚才在写」的时间戳。 */
    fun observe(event: MotionEvent) {
        val anyStylus = (0 until event.pointerCount).any { isStylus(event.getToolType(it)) }
        if (anyStylus) lastStylusAt = event.eventTime
    }

    /** 这个刚按下的指针能不能起笔？ */
    fun acceptDown(event: MotionEvent, pointerIndex: Int): Boolean {
        if (isStylus(event.getToolType(pointerIndex))) return true
        return event.eventTime - lastStylusAt > FINGER_LOCKOUT_MS
    }

    companion object {
        private const val FINGER_LOCKOUT_MS = 2000L

        fun isStylus(toolType: Int): Boolean =
            toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
    }
}
