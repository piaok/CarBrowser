package com.carbrowser.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.carbrowser.App;
import com.carbrowser.adblock.AdBlocker;

import java.io.ByteArrayInputStream;

/**
 * WebView container that manages a single WebView instance.
 * Handles settings, navigation, and ad-block interception.
 */
public class WebViewContainer {

    private final WebView webView;
    private final Callback callback;
    private String currentUrl = "";
    private String currentTitle = "";
    private boolean isLoading = false;

    public interface Callback {
        void onPageStarted(String url);
        void onPageFinished(String url, String title);
        void onProgressChanged(int progress);
        void onReceivedTitle(String title);
    }

    public interface TabProvider {
        WebViewContainer createTab(Callback callback);
    }

    public WebViewContainer(Context context, Callback callback) {
        this.callback = callback;
        webView = new WebView(context);
        configureSettings();
        setupWebViewClient();
        setupWebChromeClient();
    }

    private void configureSettings() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false); // Hide zoom buttons on car screen
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setTextZoom(100); // Fixed text zoom for car display
        settings.setMediaPlaybackRequiresUserGesture(false); // Auto-play allowed

        // Custom User-Agent: append car marker so sites can adapt
        String ua = settings.getUserAgentString();
        settings.setUserAgentString(ua + " CarBrowser/1.0");

        // Enable hardware acceleration for video
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
    }

    private void setupWebViewClient() {
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Block non-http(s) schemes for safety
                String scheme = request.getUrl().getScheme();
                if (scheme != null && !scheme.equals("http") && !scheme.equals("https")) {
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                isLoading = true;
                currentUrl = url;
                callback.onPageStarted(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isLoading = false;
                currentUrl = url;
                currentTitle = view.getTitle() != null ? view.getTitle() : url;
                callback.onPageFinished(url, currentTitle);

                // Inject video detection script
                injectVideoDetector(view);

                // Inject ad-hiding CSS
                AdBlocker blocker = App.getInstance().getAdBlocker();
                if (blocker.isEnabled()) {
                    blocker.injectHideRules(view);
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String pageHost = view.getUrl() != null ? view.getUrl() : "";

                // Ad blocking
                AdBlocker blocker = App.getInstance().getAdBlocker();
                if (blocker.isEnabled() && blocker.shouldBlock(url, pageHost)) {
                    return new WebResourceResponse(
                        "text/plain", "utf-8",
                        new ByteArrayInputStream("".getBytes())
                    );
                }

                return super.shouldInterceptRequest(view, request);
            }
        });
    }

    private void setupWebChromeClient() {
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                callback.onProgressChanged(newProgress);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (title != null && !title.isEmpty()) {
                    currentTitle = title;
                    callback.onReceivedTitle(title);
                }
            }
        });
    }

    /**
     * Inject JavaScript to detect <video> elements on the page.
     */
    private void injectVideoDetector(WebView view) {
        view.evaluateJavascript(
            "(function(){" +
            "  var videos = document.querySelectorAll('video');" +
            "  var urls = [];" +
            "  videos.forEach(function(v){" +
            "    if(v.src && v.src.length > 0) urls.push(v.src);" +
            "    if(v.currentSrc && v.currentSrc.length > 0) urls.push(v.currentSrc);" +
            "    v.querySelectorAll('source').forEach(function(s){" +
            "      if(s.src && s.src.length > 0) urls.push(s.src);" +
            "    });" +
            "  });" +
            "  if(urls.length > 0 && window.VideoDetector){" +
            "    window.VideoDetector.onVideoFound(JSON.stringify(urls));" +
            "  }" +
            "})();",
            null
        );
    }

    // --- Public API ---

    public void loadUrl(String url) {
        if (url != null && !url.isEmpty()) {
            webView.loadUrl(url);
        }
    }

    public void loadHomePage() {
        webView.loadUrl("file:///android_asset/homepage.html");
    }

    public void goBack() {
        if (webView.canGoBack()) {
            webView.goBack();
        }
    }

    public void goForward() {
        if (webView.canGoForward()) {
            webView.goForward();
        }
    }

    public void reload() {
        webView.reload();
    }

    public void stopLoading() {
        webView.stopLoading();
    }

    public WebView getWebView() {
        return webView;
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public String getCurrentTitle() {
        return currentTitle;
    }

    public boolean isLoading() {
        return isLoading;
    }

    public boolean canGoBack() {
        return webView.canGoBack();
    }

    /**
     * Destroy WebView to free memory. Called when tab is closed
     * or onTrimMemory triggers cleanup.
     */
    public void destroy() {
        webView.stopLoading();
        webView.setWebViewClient(null);
        webView.setWebChromeClient(null);
        webView.destroy();
    }
}
