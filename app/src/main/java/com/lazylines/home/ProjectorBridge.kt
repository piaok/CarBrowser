package com.lazylines.home

import android.content.Context
import android.webkit.JavascriptInterface
import com.lazylines.core.UrlBarHandler

/**
 * JavaScript bridge for the projector homepage.
 * Exposes URL handling, quick links, and preferences to the WebView.
 */
class ProjectorBridge(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "lazylines_prefs"
        private const val KEY_QUICK_LINKS = "quick_links"
        private const val KEY_SEARCH_ENGINE = "search_engine"
        private const val KEY_ADBLOCK_ENABLED = "adblock_enabled"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Listener for URL open requests from the homepage. */
    var onOpenUrl: ((String) -> Unit)? = null

    /**
     * Process user input (URL or search query) and navigate.
     * Called from JS: ProjectorBridge.openUrl(input)
     */
    @JavascriptInterface
    fun openUrl(input: String) {
        val engine = getSearchEngine()
        val url = UrlBarHandler.processInput(input, engine)
        if (url.isNotEmpty()) {
            onOpenUrl?.invoke(url)
        }
    }

    /**
     * Return saved quick links as JSON array string.
     * Called from JS: ProjectorBridge.getQuickLinks()
     */
    @JavascriptInterface
    fun getQuickLinks(): String {
        return prefs.getString(KEY_QUICK_LINKS, "[]") ?: "[]"
    }

    /**
     * Save quick links JSON array string to SharedPreferences.
     * Called from JS: ProjectorBridge.saveQuickLinks(json)
     */
    @JavascriptInterface
    fun saveQuickLinks(json: String) {
        prefs.edit().putString(KEY_QUICK_LINKS, json).apply()
    }

    /**
     * Return the current search engine name.
     * Called from JS: ProjectorBridge.getSearchEngine()
     */
    @JavascriptInterface
    fun getSearchEngine(): String {
        return prefs.getString(KEY_SEARCH_ENGINE, "bing") ?: "bing"
    }

    /**
     * Return whether ad blocking is enabled.
     */
    @JavascriptInterface
    fun isAdBlockEnabled(): Boolean {
        return prefs.getBoolean(KEY_ADBLOCK_ENABLED, true)
    }
}
