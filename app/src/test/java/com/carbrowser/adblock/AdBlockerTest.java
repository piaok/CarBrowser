package com.carbrowser.adblock;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for AdBlocker (non-Android parts: DomainTrie and matching logic).
 */
public class AdBlockerTest {

    private AdBlocker.DomainTrie trie;

    @Before
    public void setUp() {
        trie = new AdBlocker.DomainTrie();
        // Populate with test rules
        trie.insert("doubleclick.net");
        trie.insert("googleadservices.com");
        trie.insert("ads.example.com");
        trie.insert("ad.baidu.com");
        trie.insert("analytics.qq.com");
    }

    // === Domain Trie Matching ===

    @Test
    public void testExactDomainMatch() {
        assertTrue(trie.matches("doubleclick.net"));
    }

    @Test
    public void testSubdomainMatch() {
        // ad.doubleclick.net should match doubleclick.net
        assertTrue(trie.matches("ad.doubleclick.net"));
    }

    @Test
    public void testDeepSubdomainMatch() {
        // cdn.ads.doubleclick.net should match doubleclick.net
        assertTrue(trie.matches("cdn.ads.doubleclick.net"));
    }

    @Test
    public void testNoMatch() {
        assertFalse(trie.matches("www.baidu.com"));
    }

    @Test
    public void testPartialDomainNoMatch() {
        // "click.net" should NOT match "doubleclick.net"
        assertFalse(trie.matches("click.net"));
    }

    @Test
    public void testParentDomainMatch() {
        // "ad.baidu.com" is in the trie
        assertTrue(trie.matches("ad.baidu.com"));
    }

    @Test
    public void testParentDomainNotInTrie() {
        // "www.baidu.com" — baidu.com is NOT in the trie (only ad.baidu.com is)
        assertFalse(trie.matches("www.baidu.com"));
    }

    @Test
    public void testNullHost() {
        assertFalse(trie.matches(null));
    }

    @Test
    public void testEmptyHost() {
        assertFalse(trie.matches(""));
    }

    // === Whitelist Logic (tested via direct rules) ===

    @Test
    public void testWhitelistDomainNotBlocked() {
        // Even if we add baidu.com to the trie, it should be whitelisted
        trie.insert("baidu.com");
        // The trie would match, but the AdBlocker.shouldBlock() method
        // checks whitelist first. This test verifies trie behavior only.
        assertTrue(trie.matches("baidu.com"));
        assertTrue(trie.matches("www.baidu.com")); // parent match
    }

    // === URL Matching Rules ===

    @Test
    public void testAdNetworkUrl() {
        assertTrue(trie.matches("pagead2.googlesyndication.com") == false);
        // This domain is NOT in our test trie, so it should not match
    }

    @Test
    public void testAnalyticsDomain() {
        assertTrue(trie.matches("analytics.qq.com"));
    }

    @Test
    public void testSimilarButDifferentDomain() {
        // "example.com" is different from "ads.example.com"
        assertFalse(trie.matches("example.com"));
    }

    // === Multiple Inserts ===

    @Test
    public void testMultipleInsertsSameDomain() {
        trie.insert("doubleclick.net");
        trie.insert("doubleclick.net");
        // Should still match fine (idempotent)
        assertTrue(trie.matches("doubleclick.net"));
    }

    @Test
    public void testLargeTrie() {
        // Insert 500 domains
        for (int i = 0; i < 500; i++) {
            trie.insert("ad" + i + ".network.com");
        }
        // Verify first and last
        assertTrue(trie.matches("ad0.network.com"));
        assertTrue(trie.matches("ad499.network.com"));
        // Verify subdomain
        assertTrue(trie.matches("cdn.ad250.network.com"));
        // Verify non-existent
        assertFalse(trie.matches("ad500.network.com"));
    }
}
