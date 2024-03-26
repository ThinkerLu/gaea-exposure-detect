package com.thinkerlu.exposure.test

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.thinkerlu.exposure.detect.GaeaExposureListener.Companion.addGaeaExposureThresholdListener

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<ViewGroup>(R.id.container)?.let { container ->
            container.post {
                val itemHeight = ((container.parent as? View)?.height ?: 0) / 5
                for (i in 0 until 20) {
                    val textView = TextView(this)
                    textView.text = "TextView $i"
                    container.addView(
                        textView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            itemHeight
                        )
                    )
                    textView.addGaeaExposureThresholdListener(0.6f) { visible ->
                        textView.setBackgroundColor(
                            if (visible) visibleColor else willInvisibleColor
                        )
                    }
                }
            }
        }

    }

    companion object {
        private val willInvisibleColor = Color.parseColor("#F8F6E3")
        private val visibleColor = Color.parseColor("#97E7E1")
    }
}