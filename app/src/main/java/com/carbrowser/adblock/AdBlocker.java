package com.carbrowser.adblock;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ad blocker using shouldInterceptRequest URL matching + DOM-level CSS/JS injection.
 * Uses built-in EasyList Lite rules (Top 500 domains) stored in assets.
 */
public class AdBlocker {

    private static final String PREFS_NAME = "carbrowser_adblock";
    private static final String KEY_ENABLED = "adblock_enabled";

    // Trie-based domain matcher for fast URL blocking
    private final DomainTrie blockTrie = new DomainTrie();

    // Whitelist: main sites that should never be blocked
    private final Set<String> whitelist = new HashSet<>();

    // DOM hide rules: CSS selectors for ad containers
    private final Set<String> hideSelectors = new HashSet<>();

    private final Context context;
    private volatile boolean enabled = true;

    public AdBlocker(Context context) {
        this.context = context.getApplicationContext();
        this.enabled = getEnabledFromPrefs();

        // Default whitelist
        String[] defaultWhitelist = {
            "baidu.com", "www.baidu.com",
            "bilibili.com", "www.bilibili.com",
            "zhihu.com", "www.zhihu.com",
            "taobao.com", "www.taobao.com",
            "jd.com", "www.jd.com",
            "weibo.com", "www.weibo.com",
            "youku.com", "www.youku.com",
            "kugou.com", "www.kugou.com"
        };
        for (String domain : defaultWhitelist) {
            whitelist.add(domain);
        }

        // Default hide selectors
        String[] defaultSelectors = {
            "[class*='ad-']", "[class*='ads-']", "[class*='advert']",
            "[id*='ad-']", "[id*='ads-']", "[id*='advert']",
            "[class*='sponsor']", "[id*='sponsor']",
            "ins.adsbygoogle", "iframe[src*='ad']",
            "div[class*='banner']", "div[id*='popup']"
        };
        for (String sel : defaultSelectors) {
            hideSelectors.add(sel);
        }
    }

    /**
     * Load ad-block rules from assets/adblock/easylist_lite.txt.
     * Called once at app startup.
     */
    public void loadRules() {
        try {
            InputStream is = context.getAssets().open("adblock/easylist_lite.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue;

                // Parse EasyList format: ||domain^ or ||domain/path
                if (line.startsWith("||") && line.endsWith("^")) {
                    String domain = line.substring(2, line.length() - 1);
                    blockTrie.insert(domain);
                    count++;
                } else if (line.startsWith("||")) {
                    String domain = line.substring(2);
                    int slashIdx = domain.indexOf('/');
                    if (slashIdx > 0) {
                        domain = domain.substring(0, slashIdx);
                    }
                    blockTrie.insert(domain);
                    count++;
                }
            }
            reader.close();
            android.util.Log.d("AdBlocker", "Loaded " + count + " block rules");
        } catch (Exception e) {
            android.util.Log.e("AdBlocker", "Failed to load rules", e);
        }
    }

    /**
     * Check if a URL should be blocked.
     * @param url the request URL
     * @param pageHost the host of the page making the request (for whitelist)
     * @return true if the URL should be blocked
     */
    public boolean shouldBlock(String url, String pageHost) {
        if (!enabled) return false;

        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            if (host == null) return false;

            // Whitelist check: never block requests from whitelisted page hosts
            if (isWhitelisted(pageHost)) return false;

            // Block trie check
            return blockTrie.matches(host);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Inject CSS + JS into WebView to hide ad elements in the DOM.
     */
    public void injectHideRules(WebView webView) {
        if (!enabled) return;

        StringBuilder css = new StringBuilder();
        for (String selector : hideSelectors) {
            if (css.length() > 0) css.append(",");
            css.append(selector);
        }

        String js = "(function(){" +
            "var style = document.createElement('style');" +
            "style.textContent = '" + css.toString() + "{display:none!important;}';" +
            "document.head.appendChild(style);" +
            "})();";

        webView.evaluateJavascript(js, null);
    }

    private boolean isWhitelisted(String host) {
        if (host == null) return false;
        // Exact match or parent domain match
        if (whitelist.contains(host)) return true;
        // Check parent domain: ads.baidu.com → baidu.com
        int dot = host.indexOf('.');
        if (dot > 0) {
            String parent = host.substring(dot + 1);
            return whitelist.contains(parent);
        }
        return false;
    }

    // --- Settings ---

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    private boolean getEnabledFromPrefs() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ENABLED, true);
    }

    /**
     * Simple Trie for domain matching.
     * Supports ||domain^ style rules by matching suffix.
     */
    static class DomainTrie {
        private final ConcurrentHashMap<String, Boolean> domains = new ConcurrentHashMap<>();

        void insert(String domain) {
            domains.put(domain, true);
        }

        boolean matches(String host) {
            if (host == null) return false;
            // Exact match
            if (domains.containsKey(host)) return true;
            // Suffix match: ad.doubleclick.net → doubleclick.net
            String[] parts = host.split("\\.");
            StringBuilder sb = new StringBuilder();
            for (int i = parts.length - 1; i >= 1; i--) {
                if (sb.length() > 0) sb.insert(0, ".");
                sb.insert(0, parts[i]);
                if (domains.containsKey(sb.toString())) return true;
            }
            return false;
        }
    }
}
