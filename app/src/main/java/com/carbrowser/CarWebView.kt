package com.carbrowser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.webkit.*

/**
 * 优化的车机 WebView
 * - 硬件加速渲染
 * - 内存优化
 * - 广告拦截集成
 * - 视频检测注入
 */
class CarWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var onPageLoaded: ((String) -> Unit)? = null
    var onTitleChanged: ((String) -> Unit)? = null
    var onVideoFound: ((String) -> Unit)? = null
    var onProgressChanged: ((Int) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsBridge = BrowserJsBridge(object : BrowserJsBridge.JsBridgeListener {
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
            // 核心设置 - 流畅优先
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

            // 性能优化
            cacheMode = WebSettings.LOAD_DEFAULT
            // AppCache removed in API 34 — no longer needed

            // 渲染优化
            loadWithOverviewMode = true
            useWideViewPort = true

            // 车机适配 - 固定1024x600
            val dm = context.resources.displayMetrics
            val scale = dm.densityDpi / 160f
            setInitialScale((scale * 100).toInt())

            // 文字大小优化
            minimumFontSize = 14

            // 硬件加速
            setLayerType(LAYER_TYPE_HARDWARE, null)

            // 媒体自动播放（视频需要）
            mediaPlaybackRequiresUserGesture = false

            // 混合内容
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            // UA - 标识为车机浏览器
            userAgentString = userAgentString + " CarBrowser/1.0"
        }

        // JS 桥接
        addJavascriptInterface(jsBridge, "CarBrowserBridge")

        // WebViewClient
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                // 广告拦截
                return if (AdBlocker.isAd(request)) {
                    AdBlocker.block()
                } else null
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // 注入视频检测脚本
                evaluateJavascript(BrowserJsBridge.VIDEO_DETECTOR_JS, null)
                // 注入广告隐藏脚本
                evaluateJavascript(BrowserJsBridge.AD_HIDE_JS, null)
                onPageLoaded?.invoke(url)
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                // 车机环境下允许继续加载（很多HTTP站点）
                handler.proceed()
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                // 只处理 http/https
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                }
                return true
            }
        }

        // WebChromeClient - 进度和视频
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                onProgressChanged?.invoke(newProgress)
            }

            override fun onReceivedTitle(view: WebView, title: String) {
                onTitleChanged?.invoke(title)
            }

            // 视频全屏支持
            private var customView: View? = null
            private var customViewCallback: CustomViewCallback? = null

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                // 不使用原生全屏，由悬浮窗接管
                callback.onCustomViewHidden()
            }

            override fun onHideCustomView() {
                customViewCallback?.onCustomViewHidden()
                customView = null
            }
        }
    }

    /**
     * 优化内存
     */
    fun optimizeMemory() {
        stopLoading()
        clearCache(true)
        clearHistory()
        freeMemory()
    }

    override fun onDetachedFromWindow() {
        destroy()
        super.onDetachedFromWindow()
    }
}
