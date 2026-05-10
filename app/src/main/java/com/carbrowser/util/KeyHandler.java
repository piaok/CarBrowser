package com.carbrowser.util;

import android.view.KeyEvent;

import com.carbrowser.core.TabManager;
import com.carbrowser.core.WebViewContainer;

/**
 * Handles physical key events from car head unit.
 * BACK: web back > tab close > exit
 * MENU: open tab switcher
 */
public class KeyHandler {

    public static final int RESULT_NOT_HANDLED = 0;
    public static final int RESULT_GO_BACK = 1;
    public static final int RESULT_OPEN_MENU = 2;

    public static int handleKey(int keyCode, TabManager tabManager) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                WebViewContainer tab = tabManager.getActiveTab();
                if (tab != null && tab.canGoBack()) {
                    tab.goBack();
                    return RESULT_GO_BACK;
                }
                return RESULT_NOT_HANDLED; // Let Activity handle (close tab or exit)

            case KeyEvent.KEYCODE_MENU:
                return RESULT_OPEN_MENU;

            // Car-specific physical buttons (common on Chinese head units)
            case KeyEvent.KEYCODE_NAVIGATE_PREVIOUS:
                if (tabManager.getActiveTab() != null) {
                    tabManager.getActiveTab().goBack();
                    return RESULT_GO_BACK;
                }
                break;

            case KeyEvent.KEYCODE_NAVIGATE_NEXT:
                if (tabManager.getActiveTab() != null) {
                    tabManager.getActiveTab().goForward();
                    return RESULT_GO_BACK;
                }
                break;
        }

        return RESULT_NOT_HANDLED;
    }
}
