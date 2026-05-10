package com.carbrowser.core;

import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages multiple browser tabs (max 5 for car memory constraints).
 * Uses a FrameLayout container with visibility toggling.
 */
public class TabManager {

    public interface TabListener {
        void onTabChanged(int index, String title, String url);
        void onTabAdded(int index);
        void onTabRemoved(int index);
    }

    public static final int MAX_TABS = 5;

    private final FrameLayout container;
    private final List<WebViewContainer> tabs = new ArrayList<>();
    private int activeTabIndex = -1;
    private TabListener listener;

    public TabManager(FrameLayout container) {
        this.container = container;
    }

    public void setTabListener(TabListener listener) {
        this.listener = listener;
    }

    /**
     * Create a new tab and load the given URL.
     * @return the index of the new tab, or -1 if max tabs reached.
     */
    public int newTab(WebViewContainer.TabProvider provider, String url) {
        if (tabs.size() >= MAX_TABS) {
            return -1;
        }

        final WebViewContainer[] tabHolder = new WebViewContainer[1];
        WebViewContainer tab = provider.createTab(new WebViewContainer.Callback() {
            @Override
            public void onPageStarted(String url1) {}

            @Override
            public void onPageFinished(String url1, String title) {
                if (listener != null && tabHolder[0] != null) {
                    int idx = tabs.indexOf(tabHolder[0]);
                    if (idx == activeTabIndex) {
                        listener.onTabChanged(idx, title, url1);
                    }
                }
            }

            @Override
            public void onProgressChanged(int progress) {}

            @Override
            public void onReceivedTitle(String title) {
                if (listener != null && tabHolder[0] != null) {
                    int idx = tabs.indexOf(tabHolder[0]);
                    if (idx == activeTabIndex) {
                        listener.onTabChanged(idx, title, tabHolder[0].getCurrentUrl());
                    }
                }
            }
        });
        tabHolder[0] = tab;

        tabs.add(tab);
        int index = tabs.size() - 1;

        // Add WebView to container but hide it
        container.addView(tab.getWebView());
        tab.getWebView().setVisibility(View.GONE);

        // Switch to the new tab
        switchToTab(index);

        // Load URL
        if (url != null && !url.isEmpty()) {
            tab.loadUrl(url);
        }

        if (listener != null) {
            listener.onTabAdded(index);
        }

        return index;
    }

    public void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;

        // Hide current tab
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            tabs.get(activeTabIndex).getWebView().setVisibility(View.GONE);
        }

        // Show new tab
        activeTabIndex = index;
        tabs.get(activeTabIndex).getWebView().setVisibility(View.VISIBLE);
        tabs.get(activeTabIndex).getWebView().requestFocus();

        if (listener != null) {
            WebViewContainer tab = tabs.get(activeTabIndex);
            listener.onTabChanged(activeTabIndex, tab.getCurrentTitle(), tab.getCurrentUrl());
        }
    }

    public void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        if (tabs.size() <= 1) return; // Keep at least 1 tab

        WebViewContainer tab = tabs.remove(index);
        container.removeView(tab.getWebView());
        tab.destroy();

        if (listener != null) {
            listener.onTabRemoved(index);
        }

        // Adjust active tab index
        if (activeTabIndex >= tabs.size()) {
            activeTabIndex = tabs.size() - 1;
        } else if (activeTabIndex > index) {
            activeTabIndex--;
        } else if (activeTabIndex == index) {
            activeTabIndex = Math.min(index, tabs.size() - 1);
        }

        // Show the active tab
        if (activeTabIndex >= 0) {
            tabs.get(activeTabIndex).getWebView().setVisibility(View.VISIBLE);
            tabs.get(activeTabIndex).getWebView().requestFocus();

            if (listener != null) {
                WebViewContainer active = tabs.get(activeTabIndex);
                listener.onTabChanged(activeTabIndex, active.getCurrentTitle(), active.getCurrentUrl());
            }
        }
    }

    public WebViewContainer getActiveTab() {
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            return tabs.get(activeTabIndex);
        }
        return null;
    }

    public int getActiveTabIndex() {
        return activeTabIndex;
    }

    public int getTabCount() {
        return tabs.size();
    }

    public List<WebViewContainer> getAllTabs() {
        return new ArrayList<>(tabs);
    }

    public String[] getTabTitles() {
        String[] titles = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            String t = tabs.get(i).getCurrentTitle();
            titles[i] = (t != null && !t.isEmpty()) ? t : "New Tab";
        }
        return titles;
    }

    /**
     * Destroy all tabs to free memory (called on app exit or onTrimMemory).
     */
    public void destroyAll() {
        for (WebViewContainer tab : tabs) {
            container.removeView(tab.getWebView());
            tab.destroy();
        }
        tabs.clear();
        activeTabIndex = -1;
    }
}
