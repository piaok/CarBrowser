package com.lazylines.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.lazylines.R
import com.lazylines.adblock.AdBlocker
import com.lazylines.core.LazyWebView
import com.lazylines.core.TabManager
import com.lazylines.core.UrlBarHandler
import com.lazylines.download.DownloadDialog
import com.lazylines.leanback.KeyHandler
import com.lazylines.leanback.KeyHandler.Action
import com.lazylines.leanback.LeanbackFocusManager

/**
 * BrowserActivity — LazyLines 投影仪浏览器主界面
 * D-pad遥控器导航 + 10-foot UI
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var tabManager: TabManager
    private lateinit var focusManager: LeanbackFocusManager
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var tabContainerBar: LinearLayout
    private lateinit var webviewContainer: FrameLayout
    private lateinit var btnHome: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnDownload: ImageButton
    private lateinit var btnBookmark: ImageButton
    private lateinit var btnMore: ImageButton

    private var webViewHasFocus = false
    private var currentTabCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_browser)

        initViews()
        initTabManager()
        initFocusManager()
        handleIntent(intent)
    }

    private fun initViews() {
        urlBar = findViewById(R.id.url_bar)
        progressBar = findViewById(R.id.progress_bar)
        tabContainerBar = findViewById(R.id.tab_container_bar)
        webviewContainer = findViewById(R.id.webview_container)
        btnHome = findViewById(R.id.btn_home)
        btnBack = findViewById(R.id.btn_back)
        btnForward = findViewById(R.id.btn_forward)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnDownload = findViewById(R.id.btn_download)
        btnBookmark = findViewById(R.id.btn_bookmark)
        btnMore = findViewById(R.id.btn_more)

        // URL bar: Enter → navigate
        urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigateToUrl(urlBar.text.toString())
                true
            } else false
        }

        // Sidebar buttons
        btnHome.setOnClickListener { goToHome() }
        btnBack.setOnClickListener { tabManager.getActiveWebView()?.let { if (it.canGoBack()) it.goBack() } }
        btnForward.setOnClickListener { tabManager.getActiveWebView()?.let { if (it.canGoForward()) it.goForward() } }
        btnRefresh.setOnClickListener { tabManager.getActiveWebView()?.reload() }
        btnDownload.setOnClickListener { showDownloadDialog() }
        btnBookmark.setOnClickListener { bookmarkCurrentPage() }
        btnMore.setOnClickListener { showMoreMenu() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initTabManager() {
        tabManager = TabManager(this, webviewContainer)
        addNewTab()
    }

    private fun initFocusManager() {
        focusManager = LeanbackFocusManager()
        // Register sidebar focus chain
        focusManager.registerFocusChain(
            listOf(btnHome, btnBack, btnForward, btnRefresh, btnDownload),
            View.FOCUS_DOWN
        )
    }

    private fun addNewTab(url: String? = null) {
        if (tabManager.getTabCount() >= TabManager.MAX_TABS) {
            Toast.makeText(this, "最多打开${TabManager.MAX_TABS}个标签", Toast.LENGTH_SHORT).show()
            return
        }

        val webView = tabManager.addTab() as LazyWebView
        setupWebViewCallbacks(webView)

        if (url != null) {
            webView.loadUrl(url)
        } else {
            webView.loadUrl("file:///android_asset/homepage.html")
        }

        updateTabBar()
    }

    private fun setupWebViewCallbacks(webView: LazyWebView) {
        webView.onPageLoaded = { url ->
            urlBar.setText(url)
            saveHistory(url, webView.title ?: "")
            updateTabBar()
        }

        webView.onProgressChanged = { progress ->
            progressBar.progress = progress
            progressBar.visibility = if (progress < 100) View.VISIBLE else View.GONE
        }

        webView.onVideoFound = { videoJson ->
            // Show video indicator — user can launch VideoActivity from more menu
            runOnUiThread {
                btnBookmark.contentDescription = "检测到视频"
            }
        }

        webView.onCustomViewRequested = { videoUrl ->
            openVideoActivity(videoUrl)
        }
    }

    private fun navigateToUrl(input: String) {
        val url = UrlBarHandler.processInput(input, getSearchEngine())
        tabManager.getActiveWebView()?.loadUrl(url)
        urlBar.setText(url)
        hideKeyboard()
    }

    private fun goToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
    }

    private fun openVideoActivity(videoUrl: String) {
        val intent = Intent(this, VideoActivity::class.java)
        intent.putExtra("video_url", videoUrl)
        startActivity(intent)
    }

    private fun showDownloadDialog() {
        val dialog = DownloadDialog(this)
        dialog.show()
    }

    private fun bookmarkCurrentPage() {
        val webView = tabManager.getActiveWebView() ?: return
        val url = webView.url ?: return
        val title = webView.title ?: url

        val prefs = getSharedPreferences("lazylines_bookmarks", MODE_PRIVATE)
        val bookmarks = prefs.getStringSet("bookmarks", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        bookmarks.add("$title|$url")
        prefs.edit().putStringSet("bookmarks", bookmarks).apply()
        Toast.makeText(this, "已添加书签", Toast.LENGTH_SHORT).show()
    }

    private fun showMoreMenu() {
        val options = arrayOf("书签此页", "查看书签", "分享链接", "设置", "退出")
        AlertDialog.Builder(this, R.style.TvDialog)
            .setTitle("更多")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> bookmarkCurrentPage()
                    1 -> startActivity(Intent(this, BookmarkActivity::class.java))
                    2 -> shareCurrentUrl()
                    3 -> startActivity(Intent(this, SettingsActivity::class.java))
                    4 -> confirmExit()
                }
            }
            .show()
    }

    private fun shareCurrentUrl() {
        val url = tabManager.getActiveWebView()?.url ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(intent, "分享链接"))
    }

    private fun confirmExit() {
        AlertDialog.Builder(this, R.style.TvDialog)
            .setTitle("退出 LazyLines？")
            .setPositiveButton("退出") { _, _ -> finish() }
            .setNegativeButton("取消", null)
            .show()
    }

    // ===== Tab bar UI =====
    private fun updateTabBar() {
        tabContainerBar.removeAllViews()
        for (i in 0 until tabManager.getTabCount()) {
            val webView = tabManager.getWebViewAt(i) as? LazyWebView ?: continue
            val isActive = i == tabManager.getCurrentTabIndex()

            val tabView = TextView(this).apply {
                text = truncateTitle(webView.title ?: "新标签", 8)
                textSize = 13f
                setTextColor(if (isActive) 0xFFFFFFFF.toInt() else 0xFF607D8B.toInt())
                setPadding(20, 0, 20, 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundColor(if (isActive) 0xFF16213E.toInt() else 0xFF1A1A2E.toInt())
                setOnClickListener {
                    tabManager.switchTab(i)
                    updateTabBar()
                }
                setOnLongClickListener {
                    if (tabManager.getTabCount() > 1) {
                        tabManager.closeTab(i)
                        updateTabBar()
                        true
                    } else false
                }
                isFocusable = true
                isFocusableInTouchMode = true
            }
            tabContainerBar.addView(tabView)
        }

        // "+" new tab button
        if (tabManager.getTabCount() < TabManager.MAX_TABS) {
            val newTabBtn = TextView(this).apply {
                text = "+"
                textSize = 16f
                setTextColor(0xFF607D8B.toInt())
                setPadding(20, 0, 20, 0)
                gravity = android.view.Gravity.CENTER
                isFocusable = true
                isFocusableInTouchMode = true
                setOnClickListener { addNewTab() }
            }
            tabContainerBar.addView(newTabBtn)
        }
    }

    private fun truncateTitle(title: String, maxLen: Int): String {
        return if (title.length > maxLen) title.substring(0, maxLen) + "…" else title
    }

    // ===== D-pad key handling =====
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val action = KeyHandler.handleKey(keyCode) ?: return super.onKeyDown(keyCode, event)

        // Check if WebView currently has focus
        val currentFocus = currentFocus
        val inWebView = currentFocus != null && currentFocus is LazyWebView

        when (action) {
            Action.BACK -> {
                val webView = tabManager.getActiveWebView()
                if (webView != null && webView.canGoBack()) {
                    webView.goBack()
                    return true
                } else if (tabManager.getTabCount() > 1) {
                    tabManager.closeTab(tabManager.getCurrentTabIndex())
                    updateTabBar()
                    return true
                } else {
                    confirmExit()
                    return true
                }
            }
            Action.HOME -> {
                goToHome()
                return true
            }
            Action.SELECT -> {
                if (inWebView) {
                    tabManager.getActiveWebView()?.evaluateJavascript(
                        "javascript:document.activeElement.click()", null
                    )
                    return true
                }
            }
            Action.FOCUS_UP, Action.FOCUS_DOWN, Action.FOCUS_LEFT, Action.FOCUS_RIGHT -> {
                if (inWebView) {
                    val dir = when (action) {
                        Action.FOCUS_UP -> "up"
                        Action.FOCUS_DOWN -> "down"
                        Action.FOCUS_LEFT -> "left"
                        Action.FOCUS_RIGHT -> "right"
                        else -> ""
                    }
                    tabManager.getActiveWebView()?.evaluateJavascript(
                        "javascript:window.focusNext('$dir')", null
                    )
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ===== Helpers =====
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlBar.windowToken, 0)
    }

    private fun saveHistory(url: String, title: String) {
        val prefs = getSharedPreferences("lazylines_history", MODE_PRIVATE)
        val history = prefs.getStringSet("history", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        history.add("$title|$url")
        if (history.size > 100) {
            val sorted = history.toSortedSet(compareByDescending { it })
            prefs.edit().putStringSet("history", sorted.take(100).toMutableSet()).apply()
        } else {
            prefs.edit().putStringSet("history", history).apply()
        }
    }

    private fun getSearchEngine(): String {
        return getSharedPreferences("lazylines_settings", MODE_PRIVATE)
            .getString("search_engine", "bing") ?: "bing"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val url = intent?.getStringExtra("url")
            ?: intent?.dataString
            ?: return
        tabManager.getActiveWebView()?.loadUrl(url)
        urlBar.setText(url)
    }

    override fun onResume() {
        super.onResume()
        tabManager.getActiveWebView()?.onResume()
    }

    override fun onPause() {
        super.onPause()
        tabManager.getActiveWebView()?.onPause()
    }

    override fun onDestroy() {
        tabManager.destroyAll()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        com.lazylines.leanback.MemoryOptimizer.onTrimMemory(
            level,
            (0 until tabManager.getTabCount()).mapNotNull { tabManager.getWebViewAt(it) as? android.webkit.WebView }
        )
    }
}
