package com.carbrowser.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.carbrowser.App;
import com.carbrowser.R;
import com.carbrowser.home.CarBridge;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Home activity that displays the offline homepage.
 * This is the launcher activity.
 */
public class HomeActivity extends AppCompatActivity {

    private WebView homeWebView;
    private CarBridge carBridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide system UI for car display
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );

        setContentView(R.layout.activity_home);

        homeWebView = findViewById(R.id.home_webview);
        configureWebView();

        carBridge = new CarBridge(this);
        carBridge.setCallback(new CarBridge.BridgeCallback() {
            @Override
            public void openUrl(String url) {
                Intent intent = new Intent(HomeActivity.this, BrowserActivity.class);
                intent.putExtra("url", url);
                startActivity(intent);
            }

            @Override
            public String getQuickLinks() {
                return carBridge.getQuickLinks(); // Uses defaults from CarBridge
            }

            @Override
            public void saveQuickLinks(String json) {
                // TODO: Persist to SharedPreferences
            }

            @Override
            public String getSearchEngine() {
                return getSharedPreferences("carbrowser_prefs", MODE_PRIVATE)
                    .getString("search_engine", "baidu");
            }

            @Override
            public boolean isAdBlockEnabled() {
                App app = App.getInstance();
                return app != null && app.getAdBlocker() != null && app.getAdBlocker().isEnabled();
            }
        });

        homeWebView.addJavascriptInterface(carBridge, "CarBridge");
        homeWebView.loadUrl("file:///android_asset/homepage.html");

        // Show crash log on resume if exists
        showCrashLogIfAny();
    }

    private void configureWebView() {
        WebSettings settings = homeWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);

        homeWebView.setWebViewClient(new WebViewClient());
        homeWebView.setWebChromeClient(new WebChromeClient());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // MENU key opens browser
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(new Intent(this, BrowserActivity.class));
            return true;
        }
        // BACK key shows crash log (if any) for debugging
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            showCrashLogIfAny();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * If crash.log exists, show it in a dialog so user can report it.
     */
    private void showCrashLogIfAny() {
        File logFile = App.getCrashLogFile(this);
        if (!logFile.exists() || logFile.length() == 0) return;

        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();

            String logContent = sb.toString();
            if (logContent.trim().isEmpty()) return;

            // Show in scrollable dialog
            TextView tv = new TextView(this);
            tv.setText(logContent);
            tv.setTextSize(12);
            tv.setPadding(24, 24, 24, 24);
            tv.setTextIsSelectable(true);

            ScrollView scrollView = new ScrollView(this);
            scrollView.addView(tv);

            new AlertDialog.Builder(this)
                .setTitle("⚠️ 上次崩溃日志")
                .setView(scrollView)
                .setPositiveButton("关闭", null)
                .setNeutralButton("清除日志", (dialog, which) -> {
                    logFile.delete();
                })
                .show();
        } catch (Exception e) {
            // Ignore
        }
    }

    @Override
    protected void onDestroy() {
        if (homeWebView != null) {
            homeWebView.destroy();
        }
        super.onDestroy();
    }
}
