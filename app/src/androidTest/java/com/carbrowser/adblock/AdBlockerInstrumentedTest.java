package com.carbrowser.adblock;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Instrumented tests for AdBlocker.
 * Tests rule loading from assets and shouldBlock behavior.
 */
@RunWith(AndroidJUnit4.class)
public class AdBlockerInstrumentedTest {

    private AdBlocker adBlocker;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        adBlocker = new AdBlocker(context);
        adBlocker.loadRules();
    }

    @Test
    public void testBlockAdDomain() {
        assertTrue(adBlocker.shouldBlock(
            "https://ad.doubleclick.net/adj/test",
            "https://www.example.com"));
    }

    @Test
    public void testBlockGoogleAds() {
        assertTrue(adBlocker.shouldBlock(
            "https://googleads.g.doubleclick.net/pagead/adview",
            "https://www.example.com"));
    }

    @Test
    public void testAllowNormalSite() {
        assertFalse(adBlocker.shouldBlock(
            "https://www.baidu.com/img/logo.png",
            "https://www.example.com"));
    }

    @Test
    public void testWhitelistBaidu() {
        // Baidu is whitelisted — even if we try to block from it, it should pass
        assertFalse(adBlocker.shouldBlock(
            "https://some-ad.com/ad.js",
            "https://www.baidu.com"));
    }

    @Test
    public void testWhitelistBilibili() {
        assertFalse(adBlocker.shouldBlock(
            "https://some-ad.com/ad.js",
            "https://www.bilibili.com"));
    }

    @Test
    public void testWhitelistZhihu() {
        assertFalse(adBlocker.shouldBlock(
            "https://some-ad.com/ad.js",
            "https://www.zhihu.com"));
    }

    @Test
    public void testDisabledAdBlock() {
        adBlocker.setEnabled(false);
        assertFalse(adBlocker.shouldBlock(
            "https://ad.doubleclick.net/adj/test",
            "https://www.example.com"));
        adBlocker.setEnabled(true); // Restore
    }

    @Test
    public void testEnableDisableToggle() {
        adBlocker.setEnabled(false);
        assertFalse(adBlocker.isEnabled());
        adBlocker.setEnabled(true);
        assertTrue(adBlocker.isEnabled());
    }

    @Test
    public void testNullUrl() {
        assertFalse(adBlocker.shouldBlock(null, "https://www.example.com"));
    }

    @Test
    public void testInvalidUrl() {
        assertFalse(adBlocker.shouldBlock("not-a-url", "https://www.example.com"));
    }
}
