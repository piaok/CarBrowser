package com.carbrowser.core;

import android.content.SharedPreferences;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

/**
 * Handles address bar input: determines if text is a URL or search query,
 * and routes to the appropriate action.
 */
public class UrlBarHandler {

    public interface SearchEngine {
        String BAIDU = "https://www.baidu.com/s?wd=%s";
        String BING = "https://www.bing.com/search?q=%s";
        String GOOGLE = "https://www.google.com/search?q=%s";
        String SOGOU = "https://www.sogou.com/web?query=%s";
    }

    /**
     * Process user input from address bar.
     * @param input raw text from address bar
     * @param engineKey search engine preference key
     * @return final URL to load
     */
    public static String processInput(String input, String engineKey) {
        if (input == null || input.trim().isEmpty()) return "";

        input = input.trim();

        // Already a full URL
        if (input.startsWith("http://") || input.startsWith("https://") ||
            input.startsWith("file://")) {
            return input;
        }

        // Looks like a URL (contains dot, no spaces)
        if (looksLikeUrl(input)) {
            return "http://" + input;
        }

        // Search query
        return formatSearchUrl(input, engineKey);
    }

    /**
     * Heuristic: if input contains a dot, has no spaces, and doesn't look
     * like a sentence, treat it as a URL.
     */
    private static boolean looksLikeUrl(String input) {
        // Must contain a dot
        if (!input.contains(".")) return false;
        // Must not contain spaces
        if (input.contains(" ")) return false;
        // Must not start with common query words
        String lower = input.toLowerCase(Locale.US);
        if (lower.startsWith("what") || lower.startsWith("how") ||
            lower.startsWith("why") || lower.startsWith("when") ||
            lower.startsWith("where") || lower.startsWith("who")) {
            return false;
        }
        // Must have something after the last dot (TLD)
        int lastDot = input.lastIndexOf('.');
        if (lastDot >= input.length() - 1) return false;

        // Try to parse as host
        try {
            String host = input.contains("/") ? input.substring(0, input.indexOf('/')) : input;
            // Valid host: contains only alphanumerics, dots, hyphens
            return host.matches("^[a-zA-Z0-9][a-zA-Z0-9.\\-]*[a-zA-Z0-9]$");
        } catch (Exception e) {
            return false;
        }
    }

    private static String formatSearchUrl(String query, String engineKey) {
        String template;
        switch (engineKey != null ? engineKey : "baidu") {
            case "bing":
                template = SearchEngine.BING;
                break;
            case "google":
                template = SearchEngine.GOOGLE;
                break;
            case "sogou":
                template = SearchEngine.SOGOU;
                break;
            case "baidu":
            default:
                template = SearchEngine.BAIDU;
                break;
        }
        return String.format(Locale.US, template, query);
    }
}
