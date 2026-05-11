package com.lazylines.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.webkit.*
import com.lazylines.adblock.AdBlocker

/**
 * LazyLines WebView for Android TV / Projector
 * - Fixed 100% initial scale (TV WebView 1/4 resolution fix)
 * - Locked font scaling (textZoom = 100)
 * - Ad blocking integration
 * - Video detection injection
 * - D-pad focus injection
 */
class LazyWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var onPageLoaded: ((String) -> Unit)? = null
    var onTitleChanged: ((String) -> Unit)? = null
    var onVideoFound: ((String) -> Unit)? = null
    var onProgressChanged: ((Int) -> Unit)? = null
    var onCustomViewRequested: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsBridge = LazyJsBridge(object : LazyJsBridge.JsBridgeListener {
        override fun onVideoFound(videos: String) {
            mainHandler.post { onVideoFound?.invoke(videos) }
        }
        override fun onVideoRemoved(index: Int) {}
    })

    init {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

            cacheMode = WebSettings.LOAD_DEFAULT

            loadWithOverviewMode = false
            useWideViewPort = true

            // Fixed 100% scale for TV (density-based scaling causes 1/4 resolution on TV WebView)
            setInitialScale(100)

            // Lock system font scaling
            textZoom = 100

            minimumFontSize = 14

            setLayerType(LAYER_TYPE_HARDWARE, null)

            mediaPlaybackRequiresUserGesture = false

            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            userAgentString = userAgentString + " LazyLines/1.0"
        }

        addJavascriptInterface(jsBridge, "LazyLinesBridge")

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return if (AdBlocker.isAd(request)) {
                    AdBlocker.block()
                } else null
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                evaluateJavascript(LazyJsBridge.VIDEO_DETECTOR_JS, null)
                evaluateJavascript(LazyJsBridge.AD_HIDE_JS, null)
                evaluateJavascript(LazyJsBridge.FOCUS_INJECT_JS, null)
                onPageLoaded?.invoke(url)
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.proceed()
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                }
                return true
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                onProgressChanged?.invoke(newProgress)
            }

            override fun onReceivedTitle(view: WebView, title: String) {
                onTitleChanged?.invoke(title)
            }

            private var customView: View? = null
            private var customViewCallback: CustomViewCallback? = null

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                customView = view
                customViewCallback = callback
                // Notify host to launch VideoActivity instead of in-app fullscreen
                val currentUrl = this@LazyWebView.url ?: ""
                onCustomViewRequested?.invoke(currentUrl)
                callback.onCustomViewHidden()
            }

            override fun onHideCustomView() {
                customViewCallback?.onCustomViewHidden()
                customView = null
            }
        }
    }

    fun optimizeMemory() {
        stopLoading()
        clearCache(true)
        clearHistory()
        // freeMemory() removed in API 33+, use GC hint instead
        @Suppress("DEPRECATION")
        try { freeMemory() } catch (_: NoSuchMethodError) { System.gc() }
    }

    override fun onDetachedFromWindow() {
        destroy()
        super.onDetachedFromWindow()
    }
}
