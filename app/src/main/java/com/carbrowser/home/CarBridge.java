package com.carbrowser.home;

import android.content.Context;
import android.webkit.JavascriptInterface;

import com.carbrowser.data.DatabaseHelper;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * JS Bridge for the homepage (assets/homepage.html).
 * Provides methods for quick links and search engine queries.
 */
public class CarBridge {

    public interface BridgeCallback {
        void openUrl(String url);
        String getQuickLinks();
        void saveQuickLinks(String json);
        String getSearchEngine();
        boolean isAdBlockEnabled();
    }

    private final Context context;
    private BridgeCallback callback;

    public CarBridge(Context context) {
        this.context = context;
    }

    public void setCallback(BridgeCallback callback) {
        this.callback = callback;
    }

    @JavascriptInterface
    public void openUrl(String url) {
        if (callback != null) {
            callback.openUrl(url);
        }
    }

    @JavascriptInterface
    public String getQuickLinks() {
        if (callback != null) {
            return callback.getQuickLinks();
        }
        return getDefaultQuickLinks();
    }

    @JavascriptInterface
    public void saveQuickLinks(String json) {
        if (callback != null) {
            callback.saveQuickLinks(json);
        }
    }

    @JavascriptInterface
    public String getSearchEngine() {
        if (callback != null) {
            return callback.getSearchEngine();
        }
        return "baidu";
    }

    @JavascriptInterface
    public boolean isAdBlockEnabled() {
        if (callback != null) {
            return callback.isAdBlockEnabled();
        }
        return true;
    }

    private String getDefaultQuickLinks() {
        try {
            JSONArray arr = new JSONArray();
            String[][] defaults = {
                {"百度", "https://www.baidu.com"},
                {"B站", "https://www.bilibili.com"},
                {"知乎", "https://www.zhihu.com"},
                {"淘宝", "https://www.taobao.com"},
                {"微博", "https://www.weibo.com"},
                {"京东", "https://www.jd.com"},
                {"优酷", "https://www.youku.com"},
                {"酷狗", "https://www.kugou.com"}
            };
            for (String[] item : defaults) {
                JSONObject obj = new JSONObject();
                obj.put("title", item[0]);
                obj.put("url", item[1]);
                arr.put(obj);
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }
}
