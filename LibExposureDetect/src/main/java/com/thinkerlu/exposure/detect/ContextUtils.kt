package com.thinkerlu.exposure.detect

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup

object ContextUtils {
    fun getActivity(context: Context?): Activity? {
        context ?: return null
        var contextWrapper = context
        while (contextWrapper is ContextWrapper) {
            if (contextWrapper is Activity) {
                return contextWrapper
            }
            contextWrapper = contextWrapper.baseContext
        }
        return null
    }
}

/**
 * decorView的context无法找到activity
 */
fun Context?.getActivity() = ContextUtils.getActivity(this)

/**
 * 通过此方法也能找到decorView的activity
 */
fun View?.getActivity(): Activity? {
    this?.context?.getActivity()?.let {
        return it
    }
    this ?: return null
    if (this.parent !is View && this is ViewGroup && this.childCount > 0) {
        //decorView的子view才能找到activity
        return getChildAt(0).context?.getActivity()
    }
    return null
}