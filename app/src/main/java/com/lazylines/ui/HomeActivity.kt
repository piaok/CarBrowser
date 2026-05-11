package com.lazylines.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.lazylines.home.ProjectorBridge
import com.lazylines.leanback.SafeAreaFrameLayout

/**
 * Home activity for the LazyLines projector browser.
 * Full-screen WebView loading the 10-foot homepage UI.
 * D-pad BACK shows confirm-exit dialog.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bridge: ProjectorBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen + keep screen on
        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        // Build layout programmatically with SafeAreaFrameLayout root
        val root = SafeAreaFrameLayout(this)
        webView = WebView(this)
        webView.id = View.generateViewId()
        val lp = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.addView(webView, lp)
        setContentView(root)

        // Setup bridge
        bridge = ProjectorBridge(this)
        bridge.onOpenUrl = { url ->
            // Launch BrowserActivity with URL
            val intent = android.content.Intent(this, BrowserActivity::class.java)
            intent.putExtra("url", url)
            startActivity(intent)
        }

        setupWebView()
        loadHomepage()
    }

    private fun setupWebView() {
        webView.apply {
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                // Scale for 1920x1080 projector
                useWideViewPort = true
                loadWithOverviewMode = true
            }

            // Register JS bridge
            addJavascriptInterface(bridge, "ProjectorBridge")

            // Enable D-pad focus
            isFocusable = true
            isFocusableInTouchMode = true
        }
    }

    private fun loadHomepage() {
        webView.loadUrl("file:///android_asset/homepage.html")
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        // D-pad BACK → confirm exit dialog
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            showExitConfirmDialog()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showExitConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("退出浏览器")
            .setMessage("确定要退出 LazyLines 浏览器吗？")
            .setPositiveButton("退出") { _, _ ->
                finish()
            }
            .setNegativeButton("取消", null)
            .setCancelable(true)
            .show()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }
}
