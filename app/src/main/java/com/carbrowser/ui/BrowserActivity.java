package com.carbrowser.ui;

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
    private TextView tabCountBadge;

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
                runOnUiThread(() -> showVideoOptions(videoUrls));
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
        WebViewContainer tab = tabManager.getActiveTab();
        if (tab != null) {
            tab.loadUrl(url);
        }
        urlBar.clearFocus();
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

    private void showVideoOptions(String[] videoUrls) {
        // Simple toast + auto-open in ExoPlayer
        // In production, this would show a dialog with options
        if (videoUrls != null && videoUrls.length > 0) {
            String url = videoUrls[0]; // Play first video
            new android.app.AlertDialog.Builder(this)
                .setTitle("检测到视频")
                .setItems(new CharSequence[]{"ExoPlayer 播放", "浮窗播放", "全屏播放"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            videoPlayerService.playInActivity(url, false);
                            break;
                        case 1:
                            if (videoPlayerService.canDrawOverlays()) {
                                videoPlayerService.showFloatWindow(url);
                            } else {
                                // Request overlay permission
                                Intent permIntent = videoPlayerService.getOverlayPermissionIntent();
                                if (permIntent != null) {
                                    startActivity(permIntent);
                                }
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
