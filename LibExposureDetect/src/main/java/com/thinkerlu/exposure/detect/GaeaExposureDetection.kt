package com.thinkerlu.exposure.detect

import android.graphics.Rect
import android.view.View
import com.thinkerlu.exposure.detect.GaeaExposureRectListener.Companion.addGaeaExposureListener
import com.thinkerlu.exposure.detect.GaeaExposureRectListener.Companion.calculateExposureRatio

interface GaeaExposureRectListener {
    fun onExposureRectChanged(view: View, rect: Rect)

    companion object {
        @JvmStatic
        fun View.addGaeaExposureListener(listener: GaeaExposureRectListener?) {
            listener ?: return
            getOrCreateGaeaExposureNode().addExposureListener(listener)
        }

        @JvmStatic
        fun View.removeGaeaExposureListener(listener: GaeaExposureRectListener?) {
            listener ?: return
            gaeaExposureNode?.removeExposureListener(listener)
        }

        @JvmStatic
        fun View.calculateExposureRatio(rect: Rect?) =
            if (rect == null || rect.isEmpty || width == 0 || height == 0)
                0f
            else
                rect.width().toFloat() * rect.height() / (width * height)
    }
}

typealias GaeaExposureJudgmentStrategy = (view: View, rect: Rect, lastExpose: Boolean?) -> Boolean

abstract class GaeaExposureListener(
    val strategy: GaeaExposureJudgmentStrategy
) :
    GaeaExposureRectListener {
    private var exposed: Boolean? = null
    override fun onExposureRectChanged(view: View, rect: Rect) {
        val expose = strategy(view, rect, exposed)
        if (expose == exposed) {
            return
        }
        exposed = expose
        if (STATISTICS_MOD) {
            if (expose) {
                sStatisticsData.exposureVisibleCount++
            } else {
                sStatisticsData.exposureGoneCount++
            }
            //统计模式不再触发动作,以免计入回调时间
            return
        }
        onChange(expose)
    }

    abstract fun onChange(exposed: Boolean)

    companion object {
        @JvmStatic
        fun View.addGaeaExposureThresholdListener(
            threshold: Float = 0f,
            onChange: ((exposed: Boolean) -> Unit)
        ): GaeaExposureListener {
            val strategy: GaeaExposureJudgmentStrategy = when (threshold) {
                0f -> sOnePixelStrategy
                1f -> sFullPixelStrategy
                else -> {
                    { view, rect, _ ->
                        view.calculateExposureRatio(rect) >= threshold
                    }
                }
            }
            val listener = object : GaeaExposureListener(strategy) {
                override fun onChange(exposed: Boolean) {
                    onChange.invoke(exposed)
                }
            }
            addGaeaExposureListener(listener)
            return listener
        }

        private val sOnePixelStrategy: GaeaExposureJudgmentStrategy = { _, rect, _ ->
            rect.width() > 0 && rect.height() > 0
        }

        private val sFullPixelStrategy: GaeaExposureJudgmentStrategy = { v, rect, _ ->
            val vWith = v.width
            val vHeight = v.height
            vWith > 0 && vHeight > 0 && rect.width() == vWith && rect.height() == vHeight
        }
    }
}

data class GaeaExposureRootConfig(
    val detectionInterval: Long = 200L,
) {
    companion object {
        @JvmStatic
        fun View.asGaeaExposureDetectRoot(config: GaeaExposureRootConfig = sDefaultExposureRootConfig) {
            gaeaExposureNode?.let {
                throw Error("必须在设置GaeaExposureRectListener前设定ExposureDetectRoot,并且只能设置一次")
            }
            gaeaExposureNode = GaeaExposureRoot(this, config)
        }
    }
}

val sDefaultExposureRootConfig = GaeaExposureRootConfig()