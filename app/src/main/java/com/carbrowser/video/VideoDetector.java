package com.carbrowser.video;

import android.webkit.JavascriptInterface;

import org.json.JSONArray;

/**
 * JS Bridge for video detection. Injected into WebView as window.VideoDetector.
 * JS calls onVideoFound() when <video> elements are detected on the page.
 * 
 * NOTE: @JavascriptInterface methods cannot receive String[] from JS.
 * JS arrays are passed as a single String (JSON format) and parsed in Java.
 */
public class VideoDetector {

    public interface VideoCallback {
        void onVideoFound(String[] videoUrls);
        void onVideoError(String message);
    }

    private VideoCallback callback;

    public void setCallback(VideoCallback callback) {
        this.callback = callback;
    }

    @JavascriptInterface
    public void onVideoFound(String urlsJson) {
        if (callback == null || urlsJson == null || urlsJson.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(urlsJson);
            String[] urls = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                urls[i] = arr.getString(i);
            }
            if (urls.length > 0) {
                callback.onVideoFound(urls);
            }
        } catch (Exception e) {
            // Fallback: try comma-separated
            String[] urls = urlsJson.split(",");
            if (urls.length > 0) {
                callback.onVideoFound(urls);
            }
        }
    }

    @JavascriptInterface
    public void onVideoError(String msg) {
        if (callback != null) {
            callback.onVideoError(msg);
        }
    }
}
