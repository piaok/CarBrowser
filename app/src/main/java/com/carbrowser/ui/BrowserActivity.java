package com.carbrowser.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.carbrowser.App;
import com.carbrowser.R;
import com.carbrowser.adblock.AdBlocker;
import com.carbrowser.core.TabManager;
import com.carbrowser.core.UrlBarHandler;
import com.carbrowser.core.WebViewContainer;
import com.carbrowser.data.BookmarkDao;
import com.carbrowser.data.HistoryDao;
import com.carbrowser.util.KeyHandler;
import com.carbrowser.util.MemoryOptimizer;
import com.carbrowser.video.VideoDetector;
import com.carbrowser.video.VideoPlayerService;

/**
 * Main browser activity. Contains the address bar, tab container,
 * and all navigation controls.
 */
public class BrowserActivity extends AppCompatActivity
        implements WebViewContainer.TabProvider, TabManager.TabListener {

    private static final String PREFS_NAME = "carbrowser_prefs";
    private static final String KEY_SEARCH_ENGINE = "search_engine";

    private FrameLayout tabContainer;
    private EditText urlBar;
    private ProgressBar progressBar;
    private TabManager tabManager;
    private VideoDetector videoDetector;
    private VideoPlayerService videoPlayerService;
    private MemoryOptimizer memoryOptimizer;
    private BookmarkDao bookmarkDao;
    private HistoryDao historyDao;

    // Navigation buttons
    private ImageButton btnBack;
    private ImageButton btnForward;
    private ImageButton btnRefresh;
    private ImageButton btnHome;
    private ImageButton btnBookmark;
    private ImageButton btnTabs;
    private ImageButton btnSettings;
    private ImageButton btnVideo;
    private TextView tabCountBadge;

    // Detected video URLs from current page
    private java.util.List<String> detectedVideoUrls = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on in browser
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Fullscreen for car display
        hideSystemUI();

        setContentView(R.layout.activity_browser);

        initViews();
        initServices();
        initFirstTab();

        // Note: handleIntent is NOT called here because initFirstTab already
        // processes the incoming URL. For subsequent intents (singleTask),
        // onNewIntent -> handleIntent handles them.
    }

    private void initViews() {
        urlBar = findViewById(R.id.url_bar);
        tabContainer = findViewById(R.id.tab_container);
        progressBar = findViewById(R.id.progress_bar);
        btnBack = findViewById(R.id.btn_back);
        btnForward = findViewById(R.id.btn_forward);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnHome = findViewById(R.id.btn_home);
        btnBookmark = findViewById(R.id.btn_bookmark);
        btnTabs = findViewById(R.id.btn_tabs);
        btnSettings = findViewById(R.id.btn_settings);
        btnVideo = findViewById(R.id.btn_video);
        tabCountBadge = findViewById(R.id.tab_count);

        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                navigateFromUrlBar();
                return true;
            }
            return false;
        });

        urlBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                urlBar.selectAll();
            }
        });

        btnBack.setOnClickListener(v -> {
            WebViewContainer tab = tabManager.getActiveTab();
            if (tab != null) tab.goBack();
        });

        btnForward.setOnClickListener(v -> {
            WebViewContainer tab = tabManager.getActiveTab();
            if (tab != null) tab.goForward();
        });

        btnRefresh.setOnClickListener(v -> {
            WebViewContainer tab = tabManager.getActiveTab();
            if (tab != null) tab.reload();
        });

        btnHome.setOnClickListener(v -> navigateToHome());

        btnBookmark.setOnClickListener(v -> toggleBookmark());

        btnTabs.setOnClickListener(v -> showTabSwitcher());

        btnSettings.setOnClickListener(v -> showSettingsDialog());

        btnVideo.setOnClickListener(v -> showVideoToolsDialog());
    }

    private void initServices() {
        tabManager = new TabManager(tabContainer);
        tabManager.setTabListener(this);

        videoDetector = new VideoDetector();
        videoPlayerService = new VideoPlayerService(this);

        bookmarkDao = new BookmarkDao(this);
        historyDao = new HistoryDao(this);

        // Register memory optimizer (save reference for unregister)
        memoryOptimizer = new MemoryOptimizer().register(this, tabManager);
    }

    private void initFirstTab() {
        // Check if we have an incoming URL from HomeActivity
        String incomingUrl = null;
        Intent intent = getIntent();
        if (intent != null) {
            incomingUrl = intent.getStringExtra("url");
            if (incomingUrl == null && intent.getData() != null) {
                incomingUrl = intent.getData().toString();
            }
        }

        String startUrl;
        if (incomingUrl != null && !incomingUrl.isEmpty()) {
            startUrl = incomingUrl;
        } else {
            startUrl = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString("home_url", "");
            if (startUrl.isEmpty()) {
                startUrl = "file:///android_asset/homepage.html";
            }
        }
        tabManager.newTab(this, startUrl);
    }

    // --- WebViewContainer.TabProvider ---

    @Override
    public WebViewContainer createTab(WebViewContainer.Callback callback) {
        WebViewContainer container = new WebViewContainer(this, new WebViewContainer.Callback() {
            @Override public void onPageStarted(String url) {
                callback.onPageStarted(url);
                runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));
            }

            @Override public void onPageFinished(String url, String title) {
                callback.onPageFinished(url, title);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    urlBar.setText(url);
                    // Save to history
                    historyDao.addHistory(title, url);
                });
            }

            @Override public void onProgressChanged(int progress) {
                callback.onProgressChanged(progress);
                runOnUiThread(() -> progressBar.setProgress(progress));
            }

            @Override public void onReceivedTitle(String title) {
                callback.onReceivedTitle(title);
            }
        });

        // Inject video detector JS bridge
        container.getWebView().addJavascriptInterface(videoDetector, "VideoDetector");

        videoDetector.setCallback(new VideoDetector.VideoCallback() {
            @Override public void onVideoFound(String[] videoUrls) {
                runOnUiThread(() -> {
                    // Save detected video URLs
                    detectedVideoUrls.clear();
                    for (String url : videoUrls) {
                        if (!detectedVideoUrls.contains(url)) {
                            detectedVideoUrls.add(url);
                        }
                    }
                    showVideoOptions(videoUrls);
                });
            }
            @Override public void onVideoError(String msg) {
                runOnUiThread(() -> Toast.makeText(BrowserActivity.this,
                    "视频检测失败: " + msg, Toast.LENGTH_SHORT).show());
            }
        });

        return container;
    }

    // --- TabManager.TabListener ---

    @Override
    public void onTabChanged(int index, String title, String url) {
        runOnUiThread(() -> {
            urlBar.setText(url);
            updateTabBadge();
        });
    }

    @Override
    public void onTabAdded(int index) {
        updateTabBadge();
    }

    @Override
    public void onTabRemoved(int index) {
        updateTabBadge();
    }

    // --- Navigation ---

    private void navigateFromUrlBar() {
        String input = urlBar.getText().toString();
        String engine = getSearchEngine();
        String url = UrlBarHandler.processInput(input, engine);

        // Hide soft keyboard first
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
        }

        urlBar.clearFocus();

        WebViewContainer tab = tabManager.getActiveTab();
        if (tab != null) {
            tab.loadUrl(url);
        }

        hideSystemUI();
    }

    private void navigateToHome() {
        WebViewContainer tab = tabManager.getActiveTab();
        if (tab != null) {
            tab.loadUrl("file:///android_asset/homepage.html");
        }
    }

    private void toggleBookmark() {
        WebViewContainer tab = tabManager.getActiveTab();
        if (tab == null) return;
        String url = tab.getCurrentUrl();
        String title = tab.getCurrentTitle();

        if (bookmarkDao.isBookmarked(url)) {
            bookmarkDao.deleteBookmarkByUrl(url);
            Toast.makeText(this, "已移除书签", Toast.LENGTH_SHORT).show();
        } else {
            bookmarkDao.addBookmark(title, url);
            Toast.makeText(this, "已添加书签", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show video tools dialog — manual entry, re-detect, or play detected videos.
     */
    private void showVideoToolsDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("视频工具");

        // Build items: always show "detect" and "manual URL", then add detected videos
        java.util.List<String> items = new java.util.ArrayList<>();
        items.add("🔍 检测当前页面视频");
        items.add("✏️ 输入视频URL播放");

        final java.util.List<String> videoUrls = new java.util.ArrayList<>(detectedVideoUrls);
        for (int i = 0; i < videoUrls.size(); i++) {
            String url = videoUrls.get(i);
            // Show short filename or domain
            String label;
            if (url.contains("/")) {
                String file = url.substring(url.lastIndexOf("/") + 1);
                label = file.length() > 30 ? file.substring(0, 27) + "..." : file;
            } else {
                label = url;
            }
            items.add("▶ " + label);
        }

        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
            if (which == 0) {
                // Re-detect videos on current page
                detectVideosOnCurrentPage();
            } else if (which == 1) {
                // Manual URL input
                showManualVideoUrlDialog();
            } else {
                // Play detected video
                int videoIndex = which - 2;
                if (videoIndex < videoUrls.size()) {
                    showVideoOptionsMenu(videoUrls.get(videoIndex));
                }
            }
        });

        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    /**
     * Re-run video detection JS on the current page.
     */
    private void detectVideosOnCurrentPage() {
        WebViewContainer tab = tabManager.getActiveTab();
        if (tab == null) {
            Toast.makeText(this, "没有活动标签页", Toast.LENGTH_SHORT).show();
            return;
        }
        detectedVideoUrls.clear();
        // Inject a more thorough detection script that also checks for
        // dynamic/iframed videos and uses MutationObserver for lazy-loaded content
        tab.getWebView().evaluateJavascript(
            "(function(){" +
            "  function findVideos(){" +
            "    var urls = [];" +
            "    function collectFromDoc(doc){" +
            "      try {" +
            "        doc.querySelectorAll('video').forEach(function(v){" +
            "          if(v.src && v.src.length>0 && urls.indexOf(v.src)===-1) urls.push(v.src);" +
            "          if(v.currentSrc && v.currentSrc.length>0 && urls.indexOf(v.currentSrc)===-1) urls.push(v.currentSrc);" +
            "          v.querySelectorAll('source').forEach(function(s){" +
            "            if(s.src && s.src.length>0 && urls.indexOf(s.src)===-1) urls.push(s.src);" +
            "          });" +
            "        });" +
            "        doc.querySelectorAll('iframe').forEach(function(f){" +
            "          try { collectFromDoc(f.contentDocument); } catch(e) {}" +
            "        });" +
            "      } catch(e) {}" +
            "    }" +
            "    collectFromDoc(document);" +
            "    if(urls.length>0 && window.VideoDetector){" +
            "      window.VideoDetector.onVideoFound(JSON.stringify(urls));" +
            "    }" +
            "    return urls.length;" +
            "  }" +
            "  var count = findVideos();" +
            "  if(count===0){" +
            "    // Set up MutationObserver to watch for dynamically added videos" +
            "    if(!window._videoObserver){" +
            "      window._videoObserver = new MutationObserver(function(mutations){" +
            "        findVideos();" +
            "      });" +
            "      window._videoObserver.observe(document.documentElement, {childList:true, subtree:true});" +
            "    }" +
            "    window.VideoDetector.onVideoFound('[]');" +
            "  }" +
            "})();",
            null
        );
        Toast.makeText(this, "正在检测视频...", Toast.LENGTH_SHORT).show();
    }

    /**
     * Show dialog for manually entering a video URL.
     */
    private void showManualVideoUrlDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("输入视频URL（mp4/m3u8/...）");
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        // Pre-fill with current page URL as hint
        WebViewContainer tab = tabManager.getActiveTab();
        if (tab != null) {
            input.setText(tab.getCurrentUrl());
            input.selectAll();
        }

        new android.app.AlertDialog.Builder(this)
            .setTitle("输入视频URL")
            .setView(input)
            .setPositiveButton("播放", (dialog, which) -> {
                String url = input.getText().toString().trim();
                if (!url.isEmpty()) {
                    showVideoOptionsMenu(url);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * Show playback mode selection for a specific video URL.
     */
    private void showVideoOptionsMenu(String url) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("播放视频")
            .setItems(new CharSequence[]{"📺 ExoPlayer 播放", "🔲 浮窗播放", "🖥️ 全屏播放"}, (dialog, which) -> {
                switch (which) {
                    case 0:
                        videoPlayerService.playInActivity(url, false);
                        break;
                    case 1:
                        if (videoPlayerService.canDrawOverlays()) {
                            videoPlayerService.showFloatWindow(url);
                        } else {
                            Intent permIntent = videoPlayerService.getOverlayPermissionIntent();
                            if (permIntent != null) startActivity(permIntent);
                            Toast.makeText(this, "请授权浮窗权限后重试", Toast.LENGTH_LONG).show();
                        }
                        break;
                    case 2:
                        videoPlayerService.playInActivity(url, true);
                        break;
                }
            })
            .show();
    }

    private void showVideoOptions(String[] videoUrls) {
        // Auto-detected video: show notification + first video options
        if (videoUrls != null && videoUrls.length > 0) {
            Toast.makeText(this, "检测到 " + videoUrls.length + " 个视频，点击 ▶ 按钮查看", Toast.LENGTH_SHORT).show();
            // Auto-show playback options for first video
            showVideoOptionsMenu(videoUrls[0]);
        } else {
            Toast.makeText(this, "未检测到视频，可手动输入URL", Toast.LENGTH_SHORT).show();
        }
    }

    private void showTabSwitcher() {
        String[] titles = tabManager.getTabTitles();
        new android.app.AlertDialog.Builder(this)
            .setTitle("标签页 (" + tabManager.getTabCount() + "/" + TabManager.MAX_TABS + ")")
            .setItems(titles, (dialog, which) -> tabManager.switchToTab(which))
            .setPositiveButton("新建标签", (dialog, which) -> {
                int idx = tabManager.newTab(this,
                    "file:///android_asset/homepage.html");
                if (idx == -1) {
                    Toast.makeText(this, "最多 5 个标签页", Toast.LENGTH_SHORT).show();
                }
            })
            .setNeutralButton("关闭当前", (dialog, which) -> {
                tabManager.closeTab(tabManager.getActiveTabIndex());
            })
            .show();
    }

    private void showSettingsDialog() {
        String currentEngine = getSearchEngine();
        String[] engineNames = {"百度", "Google", "Bing", "搜狗"};
        String[] engineKeys = {"baidu", "google", "bing", "sogou"};
        int checkedItem = 0;
        for (int i = 0; i < engineKeys.length; i++) {
            if (engineKeys[i].equals(currentEngine)) { checkedItem = i; break; }
        }

        // Build settings items
        String[] items = new String[3];
        items[0] = "广告拦截: " + (isAdBlockEnabled() ? "已开启" : "已关闭");
        items[1] = "搜索引擎: " + engineNames[checkedItem];
        items[2] = "查看崩溃日志";

        new android.app.AlertDialog.Builder(this)
            .setTitle("⚙️ 设置")
            .setItems(items, (dialog, which) -> {
                switch (which) {
                    case 0:
                        toggleAdBlock();
                        break;
                    case 1:
                        showSearchEngineDialog(engineNames, engineKeys);
                        break;
                    case 2:
                        showCrashLogFromBrowser();
                        break;
                }
            })
            .setNegativeButton("关闭", null)
            .show();
    }

    private boolean isAdBlockEnabled() {
        App app = App.getInstance();
        return app != null && app.getAdBlocker() != null && app.getAdBlocker().isEnabled();
    }

    private void toggleAdBlock() {
        App app = App.getInstance();
        if (app != null && app.getAdBlocker() != null) {
            boolean newState = !app.getAdBlocker().isEnabled();
            app.getAdBlocker().setEnabled(newState);
            Toast.makeText(this, "广告拦截: " + (newState ? "已开启" : "已关闭"),
                Toast.LENGTH_SHORT).show();
        }
    }

    private void showSearchEngineDialog(String[] names, String[] keys) {
        String current = getSearchEngine();
        int checked = 0;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(current)) { checked = i; break; }
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("搜索引擎")
            .setSingleChoiceItems(names, checked, (dialog, which) -> {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().putString(KEY_SEARCH_ENGINE, keys[which]).apply();
                Toast.makeText(this, "搜索引擎: " + names[which],
                    Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showCrashLogFromBrowser() {
        java.io.File logFile = App.getCrashLogFile(this);
        if (!logFile.exists() || logFile.length() == 0) {
            Toast.makeText(this, "没有崩溃日志", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(logFile));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();

            android.widget.TextView tv = new android.widget.TextView(this);
            tv.setText(sb.toString());
            tv.setTextSize(12);
            tv.setPadding(24, 24, 24, 24);
            tv.setTextIsSelectable(true);

            android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
            scrollView.addView(tv);

            new android.app.AlertDialog.Builder(this)
                .setTitle("崩溃日志")
                .setView(scrollView)
                .setPositiveButton("关闭", null)
                .setNeutralButton("清除", (d, w) -> logFile.delete())
                .show();
        } catch (Exception e) {
            Toast.makeText(this, "读取日志失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateTabBadge() {
        runOnUiThread(() -> tabCountBadge.setText(String.valueOf(tabManager.getTabCount())));
    }

    private String getSearchEngine() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_SEARCH_ENGINE, "baidu");
    }

    // --- Physical Key Handling (M06) ---

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (KeyHandler.handleKey(keyCode, tabManager)) {
            case KeyHandler.RESULT_GO_BACK:
                return true;
            case KeyHandler.RESULT_OPEN_MENU:
                showTabSwitcher();
                return true;
            case KeyHandler.RESULT_NOT_HANDLED:
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public void onBackPressed() {
        WebViewContainer tab = tabManager.getActiveTab();
        if (tab != null && tab.canGoBack()) {
            tab.goBack();
        } else if (tabManager.getTabCount() > 1) {
            tabManager.closeTab(tabManager.getActiveTabIndex());
        } else {
            super.onBackPressed();
        }
    }

    // --- Lifecycle ---

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        // Priority 1: explicit "url" extra (from HomeActivity quick links)
        String url = intent.getStringExtra("url");

        // Priority 2: intent data URI
        if (url == null && intent.getData() != null) {
            url = intent.getData().toString();
        }

        if (url != null && !url.isEmpty()) {
            WebViewContainer tab = tabManager.getActiveTab();
            if (tab != null) {
                tab.loadUrl(url);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pause all WebViews to save CPU
        for (WebViewContainer tab : tabManager.getAllTabs()) {
            tab.getWebView().onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume active WebView
        WebViewContainer tab = tabManager.getActiveTab();
        if (tab != null) tab.getWebView().onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (memoryOptimizer != null) {
            memoryOptimizer.unregister(this);
        }
        tabManager.destroyAll();
        videoPlayerService.releasePlayer();
    }

    // --- Fullscreen for car display ---

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }
}
