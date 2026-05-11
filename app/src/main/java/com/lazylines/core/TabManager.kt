package com.lazylines.core

import android.content.Context
import android.widget.FrameLayout

/**
 * Tab manager for LazyLines browser
 * - Up to MAX_TABS (3) tabs
 * - FrameLayout container-based visibility toggling
 * - Each tab is a LazyWebView instance
 */
class TabManager(
    private val context: Context,
    private val container: FrameLayout
) {

    companion object {
        const val MAX_TABS = 3
    }

    private val tabs = mutableListOf<LazyWebView>()
    private var currentTabIndex = -1

    fun addTab(): LazyWebView {
        if (tabs.size >= MAX_TABS) {
            throw IllegalStateException("Maximum tab limit ($MAX_TABS) reached")
        }

        val webView = LazyWebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        tabs.add(webView)
        container.addView(webView)
        switchTab(tabs.size - 1)
        return webView
    }

    fun switchTab(index: Int) {
        if (index < 0 || index >= tabs.size) return

        tabs.forEachIndexed { i, webView ->
            webView.visibility = if (i == index) FrameLayout.VISIBLE else FrameLayout.GONE
        }

        currentTabIndex = index
    }

    fun closeTab(index: Int) {
        if (index < 0 || index >= tabs.size) return

        val webView = tabs.removeAt(index)
        container.removeView(webView)
        webView.destroy()

        if (tabs.isEmpty()) {
            currentTabIndex = -1
        } else if (currentTabIndex >= tabs.size) {
            switchTab(tabs.size - 1)
        } else if (currentTabIndex == index) {
            val newIndex = (index - 1).coerceAtLeast(0)
            switchTab(newIndex)
        }
    }

    fun getCurrentTabIndex(): Int = currentTabIndex

    fun getTabCount(): Int = tabs.size

    fun getWebViewAt(index: Int): LazyWebView? {
        return if (index in tabs.indices) tabs[index] else null
    }

    fun getActiveWebView(): LazyWebView? {
        return if (currentTabIndex in tabs.indices) tabs[currentTabIndex] else null
    }

    fun destroyAll() {
        tabs.forEach { webView ->
            container.removeView(webView)
            webView.destroy()
        }
        tabs.clear()
        currentTabIndex = -1
    }
}
