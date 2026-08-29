package com.htdzs.notepad

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.htdzs.notepad.model.PenConfig
import com.htdzs.notepad.ui.PenPickerDialog
import com.htdzs.notepad.view.DrawingView

class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    private lateinit var penButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)
        penButton = findViewById(R.id.btn_pen)

        penButton.setOnClickListener {
            PenPickerDialog.show(this, drawingView.pen) { applyPen(it) }
        }
        findViewById<Button>(R.id.btn_clear).setOnClickListener { clearAll() }
        findViewById<Button>(R.id.btn_undo).setOnClickListener { drawingView.undo() }

        applyPen(PenConfig.DEFAULT)
    }

    /** 清空 = 擦掉全部笔画 + 笔恢复默认。不弹确认，一按就干净。 */
    private fun clearAll() {
        drawingView.clear()
        applyPen(PenConfig.DEFAULT)
    }

    private fun applyPen(config: PenConfig) {
        drawingView.pen = config
        penButton.text = getString(
            R.string.btn_pen_format,
            getString(config.type.labelRes),
            getString(config.level.labelRes),
        )
    }
}
