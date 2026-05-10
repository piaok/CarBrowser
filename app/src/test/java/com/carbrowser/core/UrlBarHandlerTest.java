package com.carbrowser.core;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for UrlBarHandler.
 * Tests URL vs search query detection and search engine routing.
 */
public class UrlBarHandlerTest {

    // === URL Detection ===

    @Test
    public void testFullHttpUrl() {
        String result = UrlBarHandler.processInput("http://www.baidu.com", "baidu");
        assertEquals("http://www.baidu.com", result);
    }

    @Test
    public void testFullHttpsUrl() {
        String result = UrlBarHandler.processInput("https://www.bilibili.com", "baidu");
        assertEquals("https://www.bilibili.com", result);
    }

    @Test
    public void testFileUrl() {
        String result = UrlBarHandler.processInput("file:///android_asset/homepage.html", "baidu");
        assertEquals("file:///android_asset/homepage.html", result);
    }

    @Test
    public void testBareDomain() {
        String result = UrlBarHandler.processInput("www.baidu.com", "baidu");
        assertEquals("http://www.baidu.com", result);
    }

    @Test
    public void testBareDomainWithPath() {
        String result = UrlBarHandler.processInput("bilibili.com/video/BV123", "baidu");
        assertEquals("http://bilibili.com/video/BV123", result);
    }

    @Test
    public void testDomainWithPort() {
        String result = UrlBarHandler.processInput("192.168.1.1:8080", "baidu");
        assertEquals("http://192.168.1.1:8080", result);
    }

    // === Search Query Detection ===

    @Test
    public void testChineseSearchQuery() {
        String result = UrlBarHandler.processInput("天气预报", "baidu");
        assertTrue(result.startsWith("https://www.baidu.com/s?wd="));
        assertTrue(result.contains("天气预报"));
    }

    @Test
    public void testEnglishSearchQuery() {
        String result = UrlBarHandler.processInput("how to cook rice", "baidu");
        assertTrue(result.startsWith("https://www.baidu.com/s?wd="));
    }

    @Test
    public void testQueryStartingWithQuestionWord() {
        String result = UrlBarHandler.processInput("what is AI", "baidu");
        assertTrue(result.startsWith("https://www.baidu.com/s?wd="));
    }

    @Test
    public void testQueryWithWhy() {
        String result = UrlBarHandler.processInput("why sky is blue", "baidu");
        assertTrue(result.startsWith("https://www.baidu.com/s?wd="));
    }

    // === Search Engine Routing ===

    @Test
    public void testBaiduSearchEngine() {
        String result = UrlBarHandler.processInput("test query", "baidu");
        assertTrue(result.contains("baidu.com"));
    }

    @Test
    public void testBingSearchEngine() {
        String result = UrlBarHandler.processInput("test query", "bing");
        assertTrue(result.contains("bing.com"));
    }

    @Test
    public void testGoogleSearchEngine() {
        String result = UrlBarHandler.processInput("test query", "google");
        assertTrue(result.contains("google.com"));
    }

    @Test
    public void testSogouSearchEngine() {
        String result = UrlBarHandler.processInput("test query", "sogou");
        assertTrue(result.contains("sogou.com"));
    }

    @Test
    public void testDefaultSearchEngine() {
        String result = UrlBarHandler.processInput("test query", null);
        assertTrue(result.contains("baidu.com"));
    }

    // === Edge Cases ===

    @Test
    public void testEmptyInput() {
        String result = UrlBarHandler.processInput("", "baidu");
        assertEquals("", result);
    }

    @Test
    public void testNullInput() {
        String result = UrlBarHandler.processInput(null, "baidu");
        assertEquals("", result);
    }

    @Test
    public void testWhitespaceOnly() {
        String result = UrlBarHandler.processInput("   ", "baidu");
        assertEquals("", result);
    }

    @Test
    public void testInputWithLeadingTrailingSpaces() {
        String result = UrlBarHandler.processInput("  www.baidu.com  ", "baidu");
        assertEquals("http://www.baidu.com", result);
    }

    @Test
    public void testDotButNoTld() {
        // "hello." should be treated as search, not URL
        String result = UrlBarHandler.processInput("hello.", "baidu");
        assertTrue(result.contains("baidu.com"));
    }

    @Test
    public void testIpAddress() {
        String result = UrlBarHandler.processInput("192.168.1.1", "baidu");
        assertEquals("http://192.168.1.1", result);
    }
}
