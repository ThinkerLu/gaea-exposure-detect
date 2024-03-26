package com.thinkerlu.exposure.detect

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.thinkerlu.exposure.detect.PageVisibleObserver.Companion.getPageVisibleObserver

private const val INVALID_INT = Int.MIN_VALUE
private val INVALID_RECT = Rect(INVALID_INT, INVALID_INT, INVALID_INT, INVALID_INT)
private val GONE_RECT = Rect(0, 0, 0, 0)
private val tmpRect = Rect()
private const val STATISTICS_LOG_TAG = "GaeaExposureStatistics"
internal const val STATISTICS_MOD: Boolean = false

internal data class GaeaExposureStatisticsData(
    var startTraverseTime: Long = 0L,
    var traverseNodeCount: Int = 0,
    var fastGoneCount: Int = 0,
    var notifyRectChangeCount: Int = 0,
    var exposureVisibleCount: Int = 0,
    var exposureGoneCount: Int = 0,
    var calculateClippingRectCount: Int = 0,
) {
    fun reset() {
        traverseNodeCount = 0
        fastGoneCount = 0
        notifyRectChangeCount = 0
        exposureVisibleCount = 0
        exposureGoneCount = 0
        calculateClippingRectCount = 0
    }
}

internal val sStatisticsData = GaeaExposureStatisticsData()

internal var View.gaeaExposureNode: GaeaExposureNode?
    get() = (getTag(R.id.tag_gaea_exposure_detection_node) as? GaeaExposureNode)
    set(value) {
        setTag(R.id.tag_gaea_exposure_detection_node, value)
    }

private val View.isDecorView
    get() = isAttachedToWindow && parent !is View

internal fun View.getOrCreateGaeaExposureNode(): GaeaExposureNode {
    gaeaExposureNode?.let {
        return it
    }
    val node = if (this.isDecorView) {
        GaeaExposureRoot(this)
    } else {
        GaeaExposureNode(this)
    }
    gaeaExposureNode = node
    return node
}

private var sOnAttachStateChangeListener = object : View.OnAttachStateChangeListener {
    override fun onViewAttachedToWindow(v: View?) {
        v?.addPathMark()
    }

    override fun onViewDetachedFromWindow(v: View?) {
        v?.gaeaExposureNode?.notifyRectChange(GONE_RECT)
        v?.reducePathMark()
    }
}

internal open class GaeaExposureNode(
    val refView: View
) {
    var markCount: Int = 0
    val clipRect: Rect = Rect(INVALID_RECT)
    var exposureListeners: MutableList<GaeaExposureRectListener>? = null

    fun addExposureListener(listener: GaeaExposureRectListener?) {
        listener ?: return
        (exposureListeners ?: mutableListOf<GaeaExposureRectListener>().apply {
            exposureListeners = this
        }).let {
            it.add(listener)
            if (exposureListeners?.size == 1) {
                enableExposureDetection()
            }
            if (clipRect.isValid) {
                listener.onExposureRectChanged(refView, clipRect)
            }
        }
    }

    fun removeExposureListener(listener: GaeaExposureRectListener?) {
        listener ?: return
        exposureListeners?.let {
            it.remove(listener)
            if (it.isEmpty()) {
                disableExposureDetection()
            }
        }
    }

    private fun enableExposureDetection() {
        refView.addOnAttachStateChangeListener(sOnAttachStateChangeListener)
        if (refView.isAttachedToWindow) {
            sOnAttachStateChangeListener.onViewAttachedToWindow(refView)
            refView.invalidate()//防止view在开启曝光检测后再无绘制而无法触发首次检测
        }
    }

    private fun disableExposureDetection() {
        refView.removeOnAttachStateChangeListener(sOnAttachStateChangeListener)
    }
}

internal class GaeaExposureRoot(
    view: View,
    val config: GaeaExposureRootConfig = sDefaultExposureRootConfig
) : GaeaExposureNode(view), View.OnAttachStateChangeListener, ViewTreeObserver.OnDrawListener,
    PageVisibleLister {
    override fun onViewAttachedToWindow(v: View?) {
        registerListener()
    }

    override fun onViewDetachedFromWindow(v: View?) {
        unregisterListener()
    }

    private fun registerListener() {
        refView.getPageVisibleObserver()?.addListener(this)
        refView.viewTreeObserver?.addOnDrawListener(this)
    }

    private fun unregisterListener() {
        refView.getPageVisibleObserver()?.removeListener(this)
        refView.viewTreeObserver?.addOnDrawListener(this)
    }

    private var pageVisible = false
    override fun onPageVisibleChange(visible: Boolean) {
        pageVisible = visible
        traverseRunnable.run()
    }

    private var lastDrawTime = 0L
    private val traverseRunnable = Runnable {
        traverse(pageVisible)
    }

    override fun onDraw() {
        if (markCount <= 0 || !pageVisible) {
            return
        }
        val curTime = System.currentTimeMillis()
        val interval = config.detectionInterval
        if (curTime - lastDrawTime < interval) {
            return
        }
        refView.postDelayed(traverseRunnable, interval)
        lastDrawTime = curTime
    }

    init {
        view.addOnAttachStateChangeListener(this)
        if (refView.isAttachedToWindow) {
            registerListener()
        }
    }
}

private interface PageVisibleLister {
    fun onPageVisibleChange(visible: Boolean)
}

private class PageVisibleObserver(
    context: Context
) : View(context) {
    private val listeners = mutableListOf<PageVisibleLister>()

    fun addListener(listener: PageVisibleLister) {
        listeners.add(listener)
        (parent as? View)?.let {
            listener.onPageVisibleChange(it.visibility == VISIBLE)
        }
    }

    fun removeListener(listener: PageVisibleLister) {
        listeners.remove(listener)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView != parent) {
            return
        }
        listeners.forEach {
            it.onPageVisibleChange(visibility == VISIBLE)
        }
    }

//    虽然它可监听Dialog可见性变化，但仅在SDK24及以上版本可用,故不使用
//    override fun onVisibilityAggregated(isVisible: Boolean){}

    companion object {
        private val sLayoutParams = ViewGroup.LayoutParams(0, 0)
        fun View.getPageVisibleObserver(): PageVisibleObserver? {
            val decorView = (this.getActivity()?.window?.decorView as? ViewGroup) ?: return null
            for (i in 0 until decorView.childCount) {
                val child = decorView.getChildAt(i)
                if (child is PageVisibleObserver) {
                    return child
                }
            }
            val observer = PageVisibleObserver(context)
            decorView.addView(observer, sLayoutParams)
            return observer
        }
    }
}

private fun Rect.toInvalid() {
    left = INVALID_INT
}

private val Rect.isValid: Boolean
    get() = left != INVALID_INT

private fun GaeaExposureNode.traverse(pageVisible: Boolean) {
    if (STATISTICS_MOD) {
        sStatisticsData.reset()
        sStatisticsData.startTraverseTime = System.nanoTime()
    }

    if (pageVisible) {
        updateGaeaClipRect(null)
    } else {
        fastNotifyGone()
    }
    if (STATISTICS_MOD) {
        sStatisticsData.apply {
            val traverseTimeConsuming = System.nanoTime() - startTraverseTime
            val msg = "TraverseEnd\t" +
                    "traverseNodeCount: $traverseNodeCount\t" +
                    "traverseRootMarkCount: $markCount\t" +
                    "fastGoneCount: $fastGoneCount\t" +
                    "notifyCount: $notifyRectChangeCount\t" +
                    "exposureVisibleCount: $exposureVisibleCount\t" +
                    "exposureGoneCount: $exposureGoneCount\t" +
                    "traverseTimeConsuming: ${traverseTimeConsuming / 1000000f}ms\t\t" +
                    "traverseRootNode: $refView"
            Log.d(STATISTICS_LOG_TAG, msg)
        }
    }
}

private fun GaeaExposureNode.updateGaeaClipRect(parentClipRect: Rect?) {
    if (markCount <= 0) {
        return
    }
    if (refView.visibility != View.VISIBLE) {
        fastNotifyGone()
        return
    }
    val newRect = tmpRect
    if (parentClipRect == null) {
        refView.getDrawingRect(newRect)
    } else {
        calculateClippingRect(parentClipRect, refView, newRect)
    }
    if (newRect.isEmpty) {
        fastNotifyGone()
        return
    }
    if (STATISTICS_MOD) {
        sStatisticsData.traverseNodeCount++
    }
    notifyRectChange(newRect)

    if (refView is ViewGroup) {
        for (i in 0 until refView.childCount) {
            refView.getChildAt(i)?.gaeaExposureNode?.updateGaeaClipRect(clipRect)
        }
    }
}

private fun GaeaExposureNode.fastNotifyGone() {
    if (markCount <= 0) {
        return
    }
    if (STATISTICS_MOD) {
        sStatisticsData.traverseNodeCount++
        sStatisticsData.fastGoneCount++
    }
    if (!notifyRectChange(GONE_RECT)) {
        return
    }
    if (refView is ViewGroup) {
        for (i in 0 until refView.childCount) {
            refView.getChildAt(i)?.gaeaExposureNode?.fastNotifyGone()
        }
    }
}

private fun GaeaExposureNode.notifyRectChange(newRect: Rect): Boolean {
    if (clipRect == newRect) {
        return false
    }
    if (STATISTICS_MOD) {
        sStatisticsData.notifyRectChangeCount++
    }
    clipRect.set(newRect)
    exposureListeners?.forEach {
        it.onExposureRectChanged(refView, clipRect)
    }
    return true
}

private fun calculateClippingRect(parentClippingRect: Rect, view: View, outRect: Rect) {
    if (STATISTICS_MOD) {
        sStatisticsData.calculateClippingRectCount++
    }
    outRect.set(parentClippingRect)
    val translationX = view.translationX.toInt()
    val translationY = view.translationY.toInt()
    if (outRect.intersect(
            view.left + translationX,
            view.top + translationY,
            view.right + translationX,
            view.bottom + translationY
        )
    ) {
        outRect.offset(-view.left, -view.top)
        outRect.offset(-translationX, -translationY)
        outRect.offset(view.scrollX, view.scrollY)
    } else {
        outRect.setEmpty()
    }
}

private fun View.addPathMark() {
    val gaeaExposureNode = getOrCreateGaeaExposureNode()
    if (gaeaExposureNode.markCount > 0) {
        return
    }
    var view: Any? = this
    while (view is View) {
        val node = view.getOrCreateGaeaExposureNode()
        node.markCount += 1
        if (node is GaeaExposureRoot) {
            break
        }
        view = view.parent
    }
}

private fun View.reducePathMark() {
    val gaeaExposureNode = gaeaExposureNode ?: return
    if (gaeaExposureNode.markCount <= 0) {
        return
    }

    var view: Any? = this
    while (view is View) {
        val node = view.gaeaExposureNode ?: break
        node.markCount -= 1
        node.clipRect.toInvalid()
        view = view.parent
    }
}