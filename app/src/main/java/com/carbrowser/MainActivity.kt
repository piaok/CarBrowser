package com.carbrowser

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

/**
 * 主界面 - 车机浏览器
 * 适配 1024x600 分辨率
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: CarWebView
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnHome: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnVideo: ImageButton   // 视频浮窗按钮
    private lateinit var btnRefresh: ImageButton
    private lateinit var videoPanel: LinearLayout // 视频检测提示面板
    private lateinit var videoTip: TextView

    private val detectedVideos = mutableListOf<VideoInfo>()
    private var videoPanelVisible = false

    // 视频信息
    data class VideoInfo(
        val src: String,
        val poster: String,
        val width: Int,
        val height: Int,
        val duration: Double,
        val index: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏 + 保持常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        initViews()
        setupWebView()
        loadHome()
    }

    private fun initViews() {
        urlBar = findViewById(R.id.url_bar)
        progressBar = findViewById(R.id.progress_bar)
        btnHome = findViewById(R.id.btn_home)
        btnBack = findViewById(R.id.btn_back)
        btnForward = findViewById(R.id.btn_forward)
        btnVideo = findViewById(R.id.btn_video)
        btnRefresh = findViewById(R.id.btn_refresh)
        videoPanel = findViewById(R.id.video_panel)
        videoTip = findViewById(R.id.video_tip)
        webView = findViewById(R.id.web_view)

        // URL 栏回车导航
        urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigateToUrl(urlBar.text.toString())
                true
            } else false
        }

        // 工具栏按钮
        btnHome.setOnClickListener { loadHome() }
        btnBack.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        btnForward.setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        btnRefresh.setOnClickListener {
            webView.reload()
        }
        btnVideo.setOnClickListener {
            toggleVideoPanel()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.onPageLoaded = { url ->
            urlBar.setText(url)
            // 保存历史
            saveHistory(url, webView.title ?: "")
        }

        webView.onTitleChanged = { title ->
            // 可用于显示标题
        }

        webView.onVideoFound = { videoJson ->
            handleVideoDetection(videoJson)
        }

        webView.onProgressChanged = { progress ->
            progressBar.progress = progress
            progressBar.visibility = if (progress < 100) View.VISIBLE else View.GONE
        }
    }

    /**
     * 加载主页
     */
    private fun loadHome() {
        webView.loadUrl("file:///android_asset/homepage.html")
        urlBar.setText("")
    }

    /**
     * URL 导航
     */
    private fun navigateToUrl(input: String) {
        var url = input.trim()
        if (url.isEmpty()) return

        // 自动补全
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            // 如果是域名格式
            if (url.contains(".") && !url.contains(" ")) {
                url = "https://$url"
            } else {
                // 搜索
                url = "https://www.bing.com/search?q=${Uri.encode(url)}"
            }
        }

        webView.loadUrl(url)
        urlBar.setText(url)
        // 收起键盘
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(urlBar.windowToken, 0)
    }

    /**
     * 处理视频检测
     */
    private fun handleVideoDetection(videoJson: String) {
        try {
            val arr = JSONArray(videoJson)
            detectedVideos.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                detectedVideos.add(
                    VideoInfo(
                        src = obj.optString("src", ""),
                        poster = obj.optString("poster", ""),
                        width = obj.optInt("width", 0),
                        height = obj.optInt("height", 0),
                        duration = obj.optDouble("duration", 0.0),
                        index = obj.optInt("index", 0)
                    )
                )
            }
            if (detectedVideos.isNotEmpty()) {
                runOnUiThread {
                    btnVideo.visibility = View.VISIBLE
                    videoTip.text = "发现 ${detectedVideos.size} 个视频"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 切换视频面板
     */
    private fun toggleVideoPanel() {
        if (detectedVideos.isEmpty()) {
            Toast.makeText(this, "未检测到视频", Toast.LENGTH_SHORT).show()
            return
        }

        videoPanelVisible = !videoPanelVisible
        videoPanel.visibility = if (videoPanelVisible) View.VISIBLE else View.GONE

        if (videoPanelVisible) {
            updateVideoPanel()
        }
    }

    private fun updateVideoPanel() {
        videoPanel.removeAllViews()
        detectedVideos.forEachIndexed { i, video ->
            val btn = Button(this).apply {
                text = "视频 ${i + 1}${if (video.duration > 0) " (${formatDuration(video.duration)})" else ""}"
                textSize = 14f
                setOnClickListener { openFloatingVideo(video.src) }
            }
            videoPanel.addView(btn)
        }
    }

    /**
     * 打开悬浮窗视频播放
     */
    private fun openFloatingVideo(videoUrl: String) {
        if (videoUrl.isEmpty()) {
            Toast.makeText(this, "无法获取视频地址", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        // 启动悬浮窗服务
        val intent = Intent(this, FloatingVideoService::class.java).apply {
            putExtra("video_url", videoUrl)
        }
        startService(intent)

        // 隐藏视频面板
        videoPanelVisible = false
        videoPanel.visibility = View.GONE
    }

    /**
     * 保存浏览历史
     */
    private fun saveHistory(url: String, title: String) {
        val prefs = getSharedPreferences("browser_history", MODE_PRIVATE)
        val history = prefs.getStringSet("history", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        history.add("$title|$url")
        // 只保留最近100条
        if (history.size > 100) {
            val sorted = history.toSortedSet(compareByDescending { it })
            val trimmed = sorted.take(100).toMutableSet()
            prefs.edit().putStringSet("history", trimmed).apply()
        } else {
            prefs.edit().putStringSet("history", history).apply()
        }
    }

    private fun formatDuration(seconds: Double): String {
        val s = seconds.toInt()
        return "${s / 60}:${String.format("%02d", s % 60)}"
    }

    // 车机物理按键支持
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (webView.canGoBack()) {
                    webView.goBack()
                    return true
                }
            }
            KeyEvent.KEYCODE_MENU -> {
                toggleVideoPanel()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        webView.optimizeMemory()
        super.onDestroy()
    }

    // 处理外部链接
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { url ->
            webView.loadUrl(url)
        }
    }
}
