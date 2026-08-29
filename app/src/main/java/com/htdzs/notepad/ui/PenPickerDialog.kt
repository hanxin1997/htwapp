package com.htdzs.notepad.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.htdzs.notepad.R
import com.htdzs.notepad.model.PenConfig
import com.htdzs.notepad.model.PenType
import com.htdzs.notepad.model.WidthLevel

/**
 * 选笔型和粗细。点任意一个立刻生效并关窗 —— 给小孩用，不要确定/取消这一套。
 *
 * 按钮按枚举动态生成，加一种笔只改 [PenType] 就行，不会和 XML 走偏。
 */
object PenPickerDialog {

    fun show(context: Context, current: PenConfig, onPick: (PenConfig) -> Unit) {
        val content = LayoutInflater.from(context).inflate(R.layout.dialog_pen, null)
        val dialog = AlertDialog.Builder(context).setView(content).create()
        dialog.window?.setWindowAnimations(0)

        fillOptions(
            container = content.findViewById(R.id.pen_type_container),
            options = PenType.values(),
            selected = current.type,
            labelOf = { it.labelRes },
        ) { picked ->
            dialog.dismiss()
            onPick(current.copy(type = picked))
        }

        fillOptions(
            container = content.findViewById(R.id.pen_width_container),
            options = WidthLevel.values(),
            selected = current.level,
            labelOf = { it.labelRes },
        ) { picked ->
            dialog.dismiss()
            onPick(current.copy(level = picked))
        }

        dialog.show()
    }

    private fun <T> fillOptions(
        container: LinearLayout,
        options: Array<T>,
        selected: T,
        labelOf: (T) -> Int,
        onPick: (T) -> Unit,
    ) {
        val context = container.context
        val vertical = container.orientation == LinearLayout.VERTICAL
        for ((index, option) in options.withIndex()) {
            val button = Button(context, null, 0, R.style.Widget_Notepad_OptionButton)
            button.text = context.getString(labelOf(option))
            button.isSelected = option == selected
            button.layoutParams = optionParams(context, vertical, isLast = index == options.size - 1)
            button.setOnClickListener { onPick(option) }
            container.addView(button)
        }
    }

    private fun optionParams(
        context: Context,
        vertical: Boolean,
        isLast: Boolean,
    ): LinearLayout.LayoutParams {
        val height = context.resources.getDimensionPixelSize(R.dimen.option_height)
        val gap = if (isLast) 0 else context.resources.getDimensionPixelSize(R.dimen.option_gap)
        return if (vertical) {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
                .apply { bottomMargin = gap }
        } else {
            LinearLayout.LayoutParams(0, height, 1f).apply { marginEnd = gap }
        }
    }
}
