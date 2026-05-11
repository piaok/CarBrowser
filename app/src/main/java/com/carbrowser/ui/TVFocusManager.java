package com.carbrowser.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.carbrowser.R;

/**
 * Manages D-pad focus navigation between two zones: NAV (side bar) and CONTENT (address bar + webview).
 * 
 * Zone switching: DPAD_LEFT/RIGHT toggles between zones.
 * Within NAV zone: DPAD_UP/DOWN moves between nav items.
 * Within CONTENT zone: DPAD_UP/DOWN scrolls WebView or moves between address bar items.
 * 
 * Focus indicator is handled by drawable state (tv_focusable_bg, tv_nav_item_bg).
 */
public class TVFocusManager {

    public enum Zone {
        NAV, CONTENT
    }

    public interface OnZoneSwitchListener {
        void onZoneSwitched(Zone from, Zone to);
    }

    private final LinearLayout sideNav;
    private final LinearLayout contentZone;
    private final FrameLayout tabContainer;
    private Zone currentZone = Zone.NAV;
    private OnZoneSwitchListener zoneSwitchListener;
    private int scrollStepPx;

    public TVFocusManager(LinearLayout sideNav, LinearLayout contentZone, FrameLayout tabContainer) {
        this.sideNav = sideNav;
        this.contentZone = contentZone;
        this.tabContainer = tabContainer;
    }

    public void setScrollStep(int pixels) {
        this.scrollStepPx = pixels;
    }

    public void setOnZoneSwitchListener(OnZoneSwitchListener listener) {
        this.zoneSwitchListener = listener;
    }

    public Zone getCurrentZone() {
        return currentZone;
    }

    /**
     * Handle a key event. Returns true if consumed.
     */
    public boolean handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (currentZone == Zone.CONTENT) {
                    switchZone(Zone.NAV);
                    return true;
                }
                return false;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (currentZone == Zone.NAV) {
                    switchZone(Zone.CONTENT);
                    return true;
                }
                return false;

            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (currentZone == Zone.CONTENT) {
                    // If focus is on WebView area (tab_container), scroll instead of moving focus
                    View focused = sideNav.getFocusedChild() != null ? null : 
                                   contentZone.getFocusedChild();
                    if (focused == null || isInWebViewArea(focused)) {
                        scrollWebView(event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP);
                        return true;
                    }
                }
                return false;

            case KeyEvent.KEYCODE_BACK:
                // BACK doesn't switch zones, let the activity handle it
                return false;
        }
        return false;
    }

    /**
     * Switch focus to the specified zone.
     */
    public void switchZone(Zone targetZone) {
        if (currentZone == targetZone) return;
        Zone from = currentZone;
        currentZone = targetZone;

        if (targetZone == Zone.NAV) {
            // Focus the first focusable child in side nav
            focusFirstChild(sideNav);
        } else {
            // Focus the address bar (url_bar is always the first focusable in content zone)
            focusFirstChild(contentZone);
        }

        if (zoneSwitchListener != null) {
            zoneSwitchListener.onZoneSwitched(from, targetZone);
        }
    }

    /**
     * Focus the first focusable child in a ViewGroup.
     */
    private void focusFirstChild(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.isFocusable()) {
                child.requestFocus();
                return;
            }
            if (child instanceof ViewGroup) {
                focusFirstChild((ViewGroup) child);
            }
        }
    }

    /**
     * Check if the currently focused view is in the WebView/tab container area.
     */
    private boolean isInWebViewArea(View focused) {
        // Check if the focused view is inside tabContainer
        View parent = (View) focused.getParent();
        while (parent != null) {
            if (parent == tabContainer) return true;
            if (parent instanceof View) {
                parent = (View) parent.getParent();
            } else {
                break;
            }
        }
        return false;
    }

    /**
     * Scroll the WebView content up or down by scrollStepPx.
     */
    private void scrollWebView(boolean up) {
        if (tabContainer.getChildCount() == 0) return;
        View tab = tabContainer.getChildAt(0);
        android.webkit.WebView webView = null;
        // The tab could be a WebView directly or a ViewGroup containing one
        if (tab instanceof android.webkit.WebView) {
            webView = (android.webkit.WebView) tab;
        } else if (tab instanceof ViewGroup) {
            webView = findWebView((ViewGroup) tab);
        }
        if (webView != null) {
            int scroll = up ? -scrollStepPx : scrollStepPx;
            webView.scrollBy(0, scroll);
        }
    }

    /**
     * Recursively find a WebView in a ViewGroup.
     */
    private android.webkit.WebView findWebView(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof android.webkit.WebView) {
                return (android.webkit.WebView) child;
            }
            if (child instanceof ViewGroup) {
                android.webkit.WebView found = findWebView((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Set initial focus to the first nav item.
     */
    public void setInitialFocus() {
        switchZone(Zone.NAV);
    }
}
