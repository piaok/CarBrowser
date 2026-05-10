package com.carbrowser.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.carbrowser.App;
import com.carbrowser.R;
import com.carbrowser.home.CarBridge;

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
                return App.getInstance().getAdBlocker().isEnabled();
            }
        });

        homeWebView.addJavascriptInterface(carBridge, "CarBridge");
        homeWebView.loadUrl("file:///android_asset/homepage.html");
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
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (homeWebView != null) {
            homeWebView.destroy();
        }
        super.onDestroy();
    }
}
