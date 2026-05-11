package com.lazylines.core

/**
 * URL bar input processor for LazyLines browser
 * - Detects URLs vs search queries
 * - Supports multiple search engines
 */
object UrlBarHandler {

    private val SEARCH_ENGINES = mapOf(
        "bing" to "https://www.bing.com/search?q=",
        "google" to "https://www.google.com/search?q=",
        "baidu" to "https://www.baidu.com/s?wd=",
        "duckduckgo" to "https://duckduckgo.com/?q="
    )

    /**
     * Process user input from the URL bar.
     * - If starts with http:// or https:// → return as-is
     * - If contains "." and no space → prepend https://
     * - Otherwise → construct search URL
     *
     * @param text User input text
     * @param searchEngine Search engine key (default: bing)
     * @return Processed URL string
     */
    fun processInput(text: String, searchEngine: String = "bing"): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        // Already a full URL
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }

        // Looks like a domain (contains dot, no spaces)
        if (trimmed.contains(".") && !trimmed.contains(" ")) {
            return "https://$trimmed"
        }

        // Search query
        val engineUrl = SEARCH_ENGINES[searchEngine] ?: SEARCH_ENGINES["bing"]!!
        return engineUrl + java.net.URLEncoder.encode(trimmed, "UTF-8")
    }
}
