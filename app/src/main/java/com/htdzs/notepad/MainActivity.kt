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
        findViewById<Button>(R.id.btn_clear).setOnClickListener { drawingView.clear() }
        findViewById<Button>(R.id.btn_undo).setOnClickListener { drawingView.undo() }

        applyPen(PenConfig.DEFAULT)
    }

    /** 进后台要把电纸书刷新波形切回去，别把机器留在快刷模式上 */
    override fun onPause() {
        super.onPause()
        drawingView.releaseFastRefresh()
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
