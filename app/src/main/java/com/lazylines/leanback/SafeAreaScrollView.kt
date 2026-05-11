package com.lazylines.leanback

import android.content.Context
import android.util.AttributeSet
import android.view.Display
import android.view.WindowManager
import android.widget.ScrollView

/**
 * A [ScrollView] that applies 5% overscan safe-area padding.
 *
 * On a 1080p display this equates to 54 px on each edge. The actual pixel
 * value is computed dynamically from the real display metrics so it works
 * on any resolution.
 *
 * Padding is applied inside [init] after view construction, which means
 * children are laid out inside the safe area automatically.
 */
class SafeAreaScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    /** The computed safe-area margin in pixels. */
    val safeMarginPx: Int

    init {
        safeMarginPx = calculateSafeMargin()
        setPadding(safeMarginPx, safeMarginPx, safeMarginPx, safeMarginPx)
    }

    /**
     * Calculate 5% of the smaller display dimension as the safe margin.
     * On 1080p (1920×1080) this yields 54 px.
     */
    private fun calculateSafeMargin(): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val display = windowManager?.defaultDisplay
        val metrics = context.resources.displayMetrics

        val width = display?.let { getRealWidth(it) } ?: metrics.widthPixels
        val height = display?.let { getRealHeight(it) } ?: metrics.heightPixels

        val smallerDimension = minOf(width, height)
        return (smallerDimension * OVERSCAN_RATIO).toInt()
    }

    @Suppress("DEPRECATION")
    private fun getRealWidth(display: Display): Int {
        val realMetrics = android.util.DisplayMetrics()
        display.getRealMetrics(realMetrics)
        return realMetrics.widthPixels
    }

    @Suppress("DEPRECATION")
    private fun getRealHeight(display: Display): Int {
        val realMetrics = android.util.DisplayMetrics()
        display.getRealMetrics(realMetrics)
        return realMetrics.heightPixels
    }

    companion object {
        private const val OVERSCAN_RATIO = 0.05f
    }
}
