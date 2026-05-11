package com.carbrowser.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
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
import com.carbrowser.download.DownloadDialog;
import com.carbrowser.download.DownloadManager;
import com.carbrowser.util.MemoryOptimizer;
import com.carbrowser.video.VideoDetector;
import com.carbrowser.video.VideoPlayerService;

/**
 * Main Activity for Android TV / Projector version of LazyLines Browser.
 * Single Activity with side navigation bar + D-pad focus management.
 */
public class TVMainActivity extends AppCompatActivity
        implements WebViewContainer.TabProvider, TabManager.TabListener {

    private static final String PREFS_NAME = "carbrowser_prefs";
    private static final String KEY_SEARCH_ENGINE = "search_engine";
    private static final String KEY_OVERSCAN_PERCENT = "overscan_percent";

    // Layout views
    private LinearLayout safeArea;
    private LinearLayout sideNav;
    private LinearLayout contentZone;
    private FrameLayout tabContainer;
    private EditText urlBar;
    private ProgressBar progressBar;
    private FrameLayout panelOverlay;
    private LinearLayout panelContainer;
    private TextView panelTitle;
    private LinearLayout panelContent;

    // Navigation buttons
    private ImageButton btnBack;
    private ImageButton btnForward;
    private ImageButton btnRefresh;
    private ImageButton btnAdblock;
    private ImageButton navHome;
    private ImageButton navSearch;
    private ImageButton navBookmark;
    private ImageButton navVideo;
    private ImageButton navTabs;
    private ImageButton navSettings;

    // Services
    private TabManager tabManager;
    // VideoDetector created per-tab in createTab() to avoid callback override
    // VideoPlayerService removed - VideoActivity creates its own instance
    private DownloadManager downloadManager;
    private MemoryOptimizer memoryOptimizer;
    private BookmarkDao bookmarkDao;
    private HistoryDao historyDao;

    // Focus management
    private TVFocusManager focusManager;

    // Video detection
    private java.util.List<String> detectedVideoUrls = new java.util.ArrayList<>();

    // Download permission
    private String pendingDownloadUrl;
    private String pendingDownloadDisposition;
    private String pendingDownloadMimeType;
    private static final int REQUEST_STORAGE_PERMISSION = 1001;

    // Panel state
    private boolean panelOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Fullscreen
        hideSystemUI();

        setContentView(R.layout.activity_tv_main);

        initViews();
        initFocusManager();
        initServices();
        initFirstTab();
        updateOverscanPadding();
    }

    private void initViews() {
        safeArea = findViewById(R.id.safe_area);
        sideNav = findViewById(R.id.side_nav);
        contentZone = findViewById(R.id.content_zone);
        tabContainer = findViewById(R.id.tab_container);
        urlBar = findViewById(R.id.url_bar);
        progressBar = findViewById(R.id.progress_bar);
        panelOverlay = findViewById(R.id.panel_overlay);
        panelContainer = findViewById(R.id.panel_container);
        panelTitle = findViewById(R.id.panel_title);
        panelContent = findViewById(R.id.panel_content);

        // Address bar buttons
        btnBack = findViewById(R.id.btn_back);
        btnForward = findViewById(R.id.btn_forward);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnAdblock = findViewById(R.id.btn_adblock);

        // Side nav buttons
        navHome = findViewById(R.id.nav_home);
        navSearch = findViewById(R.id.nav_search);
        navBookmark = findViewById(R.id.nav_bookmark);
        navVideo = findViewById(R.id.nav_video);
        navTabs = findViewById(R.id.nav_tabs);
        navSettings = findViewById(R.id.nav_settings);

        // URL bar submit
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                navigateFromUrlBar();
                return true;
            }
            return false;
        });

        urlBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) urlBar.selectAll();
        });

        // Address bar button clicks
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

        btnAdblock.setOnClickListener(v -> toggleAdBlock());

        // Side nav button clicks
        navHome.setOnClickListener(v -> {
            setActiveNav(navHome);
            navigateToHome();
        });

        navSearch.setOnClickListener(v -> {
            setActiveNav(navSearch);
            focusUrlBar();
        });

        navBookmark.setOnClickListener(v -> {
            setActiveNav(navBookmark);
            showBookmarkPanel();
        });

        navVideo.setOnClickListener(v -> {
            setActiveNav(navVideo);
            showVideoToolsPanel();
        });

        navTabs.setOnClickListener(v -> {
            setActiveNav(navTabs);
            showTabPanel();
        });

        navSettings.setOnClickListener(v -> {
            setActiveNav(navSettings);
            showSettingsPanel();
        });

        // Panel overlay: close on background click
        panelOverlay.setOnClickListener(v -> closePanel());
        panelContainer.setOnClickListener(v -> { /* consume click, don't close */ });
    }

    private void initFocusManager() {
        focusManager = new TVFocusManager(sideNav, contentZone, tabContainer);

        // Set scroll step (200dp in pixels)
        float density = getResources().getDisplayMetrics().density;
        focusManager.setScrollStep((int) (200 * density));

        focusManager.setOnZoneSwitchListener((from, to) -> {
            // When switching to NAV zone, update nav button activated states
            // When switching to CONTENT zone, ensure URL bar has focus
            if (to == TVFocusManager.Zone.CONTENT) {
                // Don't force focus - let Android handle it
            }
        });
    }

    private void initServices() {
        tabManager = new TabManager(tabContainer);
        tabManager.setTabListener(this);


        downloadManager = new DownloadManager(this);

        bookmarkDao = new BookmarkDao(this);
        historyDao = new HistoryDao(this);

        memoryOptimizer = new MemoryOptimizer().register(this, tabManager);
    }

    private void initFirstTab() {
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
            startUrl = "file:///android_asset/homepage.html";
        }
        tabManager.newTab(this, startUrl);

        // Apply WebView TV scale fix after tab is created
        applyWebViewScaleFix();

        // Set initial focus to home nav item
        navHome.requestFocus();
    }

    /**
     * Fix WebView resolution on TV devices.
     * TV WebView defaults to 1/4 resolution; setInitialScale compensates.
     */
    private void applyWebViewScaleFix() {
        WebViewContainer tab = tabManager.getActiveTab();
        if (tab != null && tab.getWebView() != null) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int scale = (int) ((dm.densityDpi / 160.0) * 100);
            tab.getWebView().setInitialScale(scale);

            // Projection reading mode: larger text
            android.webkit.WebSettings settings = tab.getWebView().getSettings();
            settings.setTextZoom(130);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
        }
    }

    /**
     * Update overscan padding based on user setting.
     */
    private void updateOverscanPadding() {
        int percent = getOverscanPercent();
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        int padH = width * percent / 100;
        int padV = height * percent / 100;
        safeArea.setPadding(padH, padV, padH, padV);
    }

    private int getOverscanPercent() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getInt(KEY_OVERSCAN_PERCENT, 10);
    }

    // --- Navigation ---

    private void navigateFromUrlBar() {
        String input = urlBar.getText().toString();
        String engine = getSearchEngine();
        String url = UrlBarHandler.processInput(input, engine);

        // Hide keyboard
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
    }

    private void navigateToHome() {
        WebViewContainer tab = tabManager.getActiveTab();
        if (tab != null) {
            tab.loadUrl("file:///android_asset/homepage.html");
        }
    }

    private void focusUrlBar() {
        urlBar.requestFocus();
        urlBar.selectAll();
    }

    private void setActiveNav(ImageButton activeBtn) {
        // Clear all activated states
        navHome.setActivated(false);
        navSearch.setActivated(false);
        navBookmark.setActivated(false);
        navVideo.setActivated(false);
        navTabs.setActivated(false);
        navSettings.setActivated(false);
        // Set active
        activeBtn.setActivated(true);
        activeBtn.requestFocus();
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

            @Override public void onDownloadRequested(String url, String contentDisposition, String mimeType) {
                runOnUiThread(() -> {
                    if (checkStoragePermission()) {
                        DownloadDialog.showConfirmDialog(
                            TVMainActivity.this, url, contentDisposition, mimeType, downloadManager);
                    } else {
                        pendingDownloadUrl = url;
                        pendingDownloadDisposition = contentDisposition;
                        pendingDownloadMimeType = mimeType;
                        if (Build.VERSION.SDK_INT >= 23) {
                            requestPermissions(
                                new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                REQUEST_STORAGE_PERMISSION);
                        }
                    }
                });
            }
        });

        // Inject video detector (per-tab instance to avoid callback override)
        VideoDetector detector = new VideoDetector();
        container.getWebView().addJavascriptInterface(detector, "VideoDetector");

        // Inject CarBridge for homepage interaction
        com.carbrowser.home.CarBridge carBridge = new com.carbrowser.home.CarBridge(this);
        carBridge.setCallback(new com.carbrowser.home.CarBridge.BridgeCallback() {
            @Override public void openUrl(String url) {
                runOnUiThread(() -> {
                    WebViewContainer tab = tabManager.getActiveTab();
                    if (tab != null) tab.loadUrl(url);
                });
            }
            @Override public String getQuickLinks() { return carBridge.getQuickLinks(); }
            @Override public void saveQuickLinks(String json) { }
            @Override public String getSearchEngine() {
                return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(KEY_SEARCH_ENGINE, "baidu");
            }
            @Override public boolean isAdBlockEnabled() {
                App app = App.getInstance();
                return app != null && app.getAdBlocker() != null && app.getAdBlocker().isEnabled();
            }
        });
        container.getWebView().addJavascriptInterface(carBridge, "CarBridge");


        detector.setCallback(new VideoDetector.VideoCallback() {
            @Override public void onVideoFound(String[] videoUrls) {
                runOnUiThread(() -> {
                    detectedVideoUrls.clear();
                    for (String url : videoUrls) {
                        if (!detectedVideoUrls.contains(url)) {
                            detectedVideoUrls.add(url);
                        }
                    }
                    // Don't auto-popup on TV; just update the list
                    Toast.makeText(TVMainActivity.this,
                        "检测到 " + videoUrls.length + " 个视频，按 ▶ 查看",
                        Toast.LENGTH_SHORT).show();
                });
            }
            @Override public void onVideoError(String msg) {
                runOnUiThread(() -> Toast.makeText(TVMainActivity.this,
                    "视频检测失败: " + msg, Toast.LENGTH_SHORT).show());
            }
        });

        return container;
    }

    // --- TabManager.TabListener ---

    @Override
    public void onTabChanged(int index, String title, String url) {
        runOnUiThread(() -> urlBar.setText(url));
    }

    @Override
    public void onTabAdded(int index) { }

    @Override
    public void onTabRemoved(int index) { }

    // --- Panels ---


    private void showTabPanel() {
        panelTitle.setText("📑 标签页 (" + tabManager.getTabCount() + "/" + TabManager.MAX_TABS + ")");
        panelContent.removeAllViews();

        String[] titles = tabManager.getTabTitles();
        for (int i = 0; i < titles.length; i++) {
            final int tabIndex = i;
            String title = titles[i];
            if (title == null || title.isEmpty()) title = "标签 " + (i + 1);
            addPanelItem((i == tabManager.getActiveTabIndex() ? "● " : "○ ") + title, v -> {
                closePanel();
                tabManager.switchToTab(tabIndex);
            });
        }
        addPanelItem("+ 新建标签页", v -> {
            closePanel();
            int idx = tabManager.newTab(TVMainActivity.this,
                "file:///android_asset/homepage.html");
            if (idx == -1) {
                Toast.makeText(TVMainActivity.this, "最多 " + TabManager.MAX_TABS + " 个标签页",
                    Toast.LENGTH_SHORT).show();
            }
        });
        addPanelItem("✕ 关闭当前标签", v -> {
            closePanel();
            tabManager.closeTab(tabManager.getActiveTabIndex());
        });
        openPanel();
    }

    private void showBookmarkPanel() {
        panelTitle.setText("⭐ 书签");
        panelContent.removeAllViews();

        java.util.List<BookmarkDao.Bookmark> bookmarks = bookmarkDao.getAllBookmarks();
        if (bookmarks.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无书签");
            empty.setTextColor(0xFFA0A0A0);
            empty.setTextSize(16);
            empty.setPadding(16, 24, 16, 24);
            panelContent.addView(empty);
        } else {
            for (BookmarkDao.Bookmark bm : bookmarks) {
                TextView item = new TextView(this);
                item.setText(bm.title + "\n" + bm.url);
                item.setTextColor(0xFFF0F0F0);
                item.setTextSize(16);
                item.setPadding(16, 12, 16, 12);
                item.setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.tv_panel_item_bg));
                item.setFocusable(true);
                item.setFocusableInTouchMode(true);
                item.setOnClickListener(v -> {
                    closePanel();
                    WebViewContainer tab = tabManager.getActiveTab();
                    if (tab != null) tab.loadUrl(bm.url);
                });
                panelContent.addView(item);
            }
        }
        openPanel();
    }

    private void showVideoToolsPanel() {
        panelTitle.setText("▶ 视频工具");
        panelContent.removeAllViews();

        // Detect button
        addPanelItem("🔍 检测当前页面视频", v -> {
            closePanel();
            detectVideosOnCurrentPage();
        });

        // Manual URL
        addPanelItem("✏️ 输入视频URL播放", v -> {
            closePanel();
            showManualVideoUrlDialog();
        });

        // Detected videos
        for (int i = 0; i < detectedVideoUrls.size(); i++) {
            String url = detectedVideoUrls.get(i);
            String label = url.contains("/") ?
                url.substring(url.lastIndexOf("/") + 1) : url;
            if (label.length() > 30) label = label.substring(0, 27) + "...";
            final String videoUrl = url;
            addPanelItem("▶ " + label, v -> {
                closePanel();
                com.carbrowser.video.VideoPlayerService.startVideoActivity(TVMainActivity.this, videoUrl, true);
            });
        }
        openPanel();
    }

    private void showSettingsPanel() {
        panelTitle.setText("⚙ 设置");
        panelContent.removeAllViews();

        // Ad block toggle
        String adBlockLabel = "广告拦截: " + (isAdBlockEnabled() ? "已开启" : "已关闭");
        addPanelItem(adBlockLabel, v -> {
            toggleAdBlock();
            showSettingsPanel(); // Refresh
        });

        // Search engine
        String engine = getSearchEngine();
        String[] engineNames = {"百度", "Google", "Bing", "搜狗"};
        String[] engineKeys = {"baidu", "google", "bing", "sogou"};
        String currentEngine = engineNames[0];
        for (int i = 0; i < engineKeys.length; i++) {
            if (engineKeys[i].equals(engine)) { currentEngine = engineNames[i]; break; }
        }
        addPanelItem("搜索引擎: " + currentEngine, v -> {
            showSearchEngineDialog(engineNames, engineKeys);
        });

        // Overscan adjustment
        addPanelItem("安全区: " + getOverscanPercent() + "% (5/10/15)", v -> {
            cycleOverscan();
        });

        openPanel();
    }

    private void addPanelItem(String text, View.OnClickListener clickListener) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setTextColor(0xFFF0F0F0);
        item.setTextSize(18);
        item.setPadding(16, 14, 16, 14);
        item.setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.tv_panel_item_bg));
        item.setFocusable(true);
        item.setFocusableInTouchMode(true);
        item.setOnClickListener(clickListener);
        panelContent.addView(item);
    }

    private void openPanel() {
        panelOpen = true;
        panelOverlay.setVisibility(View.VISIBLE);
        // Focus first item in panel
        if (panelContent.getChildCount() > 0) {
            panelContent.getChildAt(0).requestFocus();
        }
    }

    private void closePanel() {
        panelOpen = false;
        panelOverlay.setVisibility(View.GONE);
        panelContent.removeAllViews();
    }

    // --- Video Detection ---

    private void detectVideosOnCurrentPage() {
        WebViewContainer tab = tabManager.getActiveTab();
        if (tab == null) return;
        detectedVideoUrls.clear();
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
            "  findVideos();" +
            "  if(!window._videoObserver){" +
            "    window._videoObserver = new MutationObserver(function(){ findVideos(); });" +
            "    window._videoObserver.observe(document.documentElement, {childList:true, subtree:true});" +
            "  }" +
            "})();",
            null
        );
        Toast.makeText(this, "正在检测视频...", Toast.LENGTH_SHORT).show();
    }

    private void showManualVideoUrlDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("输入视频URL");
        input.setSingleLine(true);
        input.setTextColor(0xFFF0F0F0);
        input.setHintTextColor(0xFF666666);

        WebViewContainer tab = tabManager.getActiveTab();
        if (tab != null) {
            input.setText(tab.getCurrentUrl());
            input.selectAll();
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("输入视频URL")
            .setView(input)
            .setPositiveButton("播放", (dialog, which) -> {
                String url = input.getText().toString().trim();
                if (!url.isEmpty()) {
                    com.carbrowser.video.VideoPlayerService.startVideoActivity(TVMainActivity.this, url, true);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // --- Settings Helpers ---

    private void toggleAdBlock() {
        App app = App.getInstance();
        if (app != null && app.getAdBlocker() != null) {
            boolean newState = !app.getAdBlocker().isEnabled();
            app.getAdBlocker().setEnabled(newState);
            Toast.makeText(this, "广告拦截: " + (newState ? "已开启" : "已关闭"),
                Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isAdBlockEnabled() {
        App app = App.getInstance();
        return app != null && app.getAdBlocker() != null && app.getAdBlocker().isEnabled();
    }

    private String getSearchEngine() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_SEARCH_ENGINE, "baidu");
    }

    private void showSearchEngineDialog(String[] names, String[] keys) {
        String current = getSearchEngine();
        int checked = 0;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(current)) { checked = i; break; }
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("搜索引擎")
            .setSingleChoiceItems(names, checked, (dialog, which) -> {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().putString(KEY_SEARCH_ENGINE, keys[which]).apply();
                Toast.makeText(this, "搜索引擎: " + names[which], Toast.LENGTH_SHORT).show();
                closePanel();
                dialog.dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void cycleOverscan() {
        int current = getOverscanPercent();
        int next;
        if (current <= 5) next = 10;
        else if (current <= 10) next = 15;
        else next = 5;

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putInt(KEY_OVERSCAN_PERCENT, next).apply();
        updateOverscanPadding();
        Toast.makeText(this, "安全区: " + next + "%", Toast.LENGTH_SHORT).show();
        showSettingsPanel(); // Refresh
    }

    // --- Key Handling ---

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // If panel is open, BACK closes it
        if (panelOpen && event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            closePanel();
            return true;
        }

        // Let FocusManager handle D-pad zone switching
        if (focusManager.handleKeyEvent(event)) {
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (panelOpen) {
            closePanel();
            return;
        }
        WebViewContainer tab = tabManager.getActiveTab();
        if (tab != null && tab.canGoBack()) {
            tab.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // --- Lifecycle ---

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        String url = intent.getStringExtra("url");
        if (url == null && intent.getData() != null) {
            url = intent.getData().toString();
        }
        if (url != null && !url.isEmpty()) {
            WebViewContainer tab = tabManager.getActiveTab();
            if (tab != null) tab.loadUrl(url);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (WebViewContainer tab : tabManager.getAllTabs()) {
            tab.getWebView().onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        if (downloadManager != null) {
            downloadManager.shutdown();
        }
    }

    // --- Storage Permission ---

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) return true;
        if (Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingDownloadUrl != null) {
                    DownloadDialog.showConfirmDialog(this,
                        pendingDownloadUrl, pendingDownloadDisposition,
                        pendingDownloadMimeType, downloadManager);
                    pendingDownloadUrl = null;
                }
            } else {
                Toast.makeText(this, "需要存储权限才能下载文件", Toast.LENGTH_LONG).show();
            }
        }
    }

    // --- Fullscreen ---

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
