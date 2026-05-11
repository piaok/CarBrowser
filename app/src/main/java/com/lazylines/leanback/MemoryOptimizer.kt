package com.lazylines.leanback

import android.webkit.WebView
import androidx.annotation.IntDef

/**
 * Responds to OS memory-pressure callbacks by destroying the least-recently-used
 * tab [WebView] instances, while guaranteeing that at least one tab stays alive.
 *
 * Usage from [android.app.Application.onTrimMemory] or
 * [android.content.ComponentCallbacks2.onTrimMemory]:
 *
 * ```kotlin
 * override fun onTrimMemory(level: Int) {
 *     super.onTrimMemory(level)
 *     MemoryOptimizer.onTrimMemory(level, activeTabs)
 * }
 * ```
 */
object MemoryOptimizer {

    /**
     * Trim-level classification accepted by [onTrimMemory].
     * Mirrors [android.content.ComponentCallbacks2] constants.
     */
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        value = [
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_UI_HIDDEN,
            TRIM_MEMORY_BACKGROUND,
            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_COMPLETE
        ]
    )
    annotation class TrimLevel

    // ── Trim-level constants (same values as ComponentCallbacks2) ──
    private const val TRIM_MEMORY_RUNNING_LOW      = 10
    private const val TRIM_MEMORY_RUNNING_CRITICAL  = 15
    private const val TRIM_MEMORY_UI_HIDDEN         = 20
    private const val TRIM_MEMORY_BACKGROUND        = 40
    private const val TRIM_MEMORY_MODERATE          = 60
    private const val TRIM_MEMORY_COMPLETE          = 80

    /**
     * Called when the OS reports memory pressure.
     *
     * - **Low pressure** (RUNNING_LOW): destroy the single oldest background tab
     *   if we exceed [maxTabs].
     * - **Critical pressure** (RUNNING_CRITICAL / UI_HIDDEN / BACKGROUND+):
     *   destroy background tabs until we are within [maxTabs], keeping at least 1.
     *
     * @param level   The trim level from [ComponentCallbacks2.onTrimMemory].
     * @param tabs    The current list of tab WebViews. The list is ordered
     *                with the most-recently-used tab at the end.
     * @param maxTabs Maximum number of tabs to keep alive under pressure.
     */
    fun onTrimMemory(level: Int, tabs: List<WebView>, maxTabs: Int = 3) {
        if (tabs.isEmpty()) return

        when (level) {
            TRIM_MEMORY_RUNNING_LOW -> {
                // Drop one oldest tab if we exceed the limit
                if (tabs.size > maxTabs) {
                    destroyOldestTab(tabs, keepAlive = 1)
                }
            }
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_UI_HIDDEN,
            TRIM_MEMORY_BACKGROUND,
            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_COMPLETE -> {
                // Aggressive: reduce down to maxTabs, keep at least 1
                destroyOldestTab(tabs, keepAlive = maxTabs.coerceAtMost(1))
            }
        }
    }

    /**
     * Destroy the oldest (least-recently-used) background tabs until
     * only [keepAlive] tabs remain.
     *
     * @param tabs       Ordered list: index 0 = oldest / LRU, last = newest / MRU.
     * @param keepAlive  Minimum number of tabs to keep alive.
     */
    private fun destroyOldestTab(tabs: List<WebView>, keepAlive: Int) {
        val toDestroy = tabs.size - keepAlive
        if (toDestroy <= 0) return

        var destroyed = 0
        val iterator = tabs.iterator()
        while (iterator.hasNext() && destroyed < toDestroy) {
            val webView = iterator.next()
            try {
                webView.destroy()
            } catch (_: Exception) {
                // WebView may already be destroyed or attached to a window
            }
            destroyed++
        }
    }
}
