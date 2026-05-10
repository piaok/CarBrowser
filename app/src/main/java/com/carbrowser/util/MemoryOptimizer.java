package com.carbrowser.util;

import android.app.Activity;
import android.content.ComponentCallbacks2;
import android.util.Log;

import com.carbrowser.core.TabManager;
import com.carbrowser.core.WebViewContainer;

import java.util.List;

/**
 * Memory optimization for low-memory car head units (1-2GB RAM).
 * Destroys inactive tabs when memory pressure is detected.
 */
public class MemoryOptimizer {

    private static final String TAG = "MemoryOptimizer";
    private ComponentCallbacks2 callback;

    /**
     * Register memory trim callback on the Activity.
     * Returns this instance so caller can call unregister() in onDestroy.
     */
    public MemoryOptimizer register(Activity activity, TabManager tabManager) {
        callback = new ComponentCallbacks2() {
            @Override
            public void onTrimMemory(int level) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
                    Log.w(TAG, "Memory pressure detected (level=" + level + "), releasing inactive tabs");
                    destroyInactiveTabs(tabManager);
                }
                if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
                    Log.w(TAG, "Critical memory pressure, forcing GC");
                    System.gc();
                }
            }

            @Override
            public void onConfigurationChanged(android.content.res.Configuration newConfig) {}

            @Override
            public void onLowMemory() {
                Log.w(TAG, "Low memory callback, releasing all inactive tabs");
                destroyInactiveTabs(tabManager);
            }
        };
        activity.registerComponentCallbacks(callback);
        return this;
    }

    /**
     * Unregister callback to prevent memory leak. Call in Activity.onDestroy().
     */
    public void unregister(Activity activity) {
        if (callback != null) {
            activity.unregisterComponentCallbacks(callback);
            callback = null;
        }
    }

    /**
     * Destroy all tabs except the active one.
     */
    private void destroyInactiveTabs(TabManager tabManager) {
        int activeIndex = tabManager.getActiveTabIndex();
        List<WebViewContainer> allTabs = tabManager.getAllTabs();

        // Destroy from end to start to avoid index shifting
        for (int i = allTabs.size() - 1; i >= 0; i--) {
            if (i != activeIndex && tabManager.getTabCount() > 1) {
                tabManager.closeTab(i);
            }
        }
    }
}
