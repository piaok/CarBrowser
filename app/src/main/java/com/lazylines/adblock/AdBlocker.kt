package com.lazylines.adblock

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight ad blocker for LazyLines
 * - URL keyword and domain blacklist based
 * - Zero external dependencies, pure in-memory matching
 * - Optimized for common ad networks
 */
object AdBlocker {

    private val EMPTY_RESPONSE = WebResourceResponse(
        "text/plain", "utf-8", ByteArrayInputStream("".toByteArray())
    )

    private val AD_DOMAINS = setOf(
        "ad.doubleclick.net", "pagead2.googlesyndication.com",
        "googleads.g.doubleclick.net", "ads.google.com",
        "advertising.com", "adnxs.com", "adsrvr.org",
        "adcolony.com", "admob.com", "adskeeper.co.uk",
        "pos.baidu.com", "cbjs.baidu.com", "wn.pos.baidu.com",
        "dsp-impr2.youdao.com", "a.0.0.0.0.cn",
        "ad.sina.com.cn", "sax.sina.com.cn", "d0.sina.com.cn",
        "ad.qq.com", "tajs.qq.com", "btrace.qq.com",
        "mi.gdt.qq.com", "pgdt.ugdtimg.com",
        "ad.toutiao.com", "pdsp.wtatx.com",
        "ad.mopub.com", "ads.tiktok.com",
        "pgdt.bf.3g.qq.com", "adsmind.gdt.qq.com",
        "static.ads-twitter.com", "analytics.twitter.com",
        "ads.yahoo.com", "advertising.yahoo.com",
        "umeng.com", "cnzz.com", "umengcloud.com",
        "ad.iqiyi.com", "ad.qq.com",
        "sdk.e.qq.com", "lives.l.qq.com",
        "ade.googlesyndication.com",
        "push.js", "pop.js", "ads.js",
        "tracking.miui.com", "a.stat.mi.com",
        "misc.in.app.mi.com",
        "adview.cn", "tanx.com", "mmstat.com",
        "atm.youku.com", "i.alicdn.com/af/",
        "count.tb.cn", "log.mmstat.com",
        "cdn.mxpnl.com", "cdn.mparticle.com",
    )

    private val AD_KEYWORDS = listOf(
        "/ad/", "/ads/", "/adv/", "/adver/",
        "/banner/", "/popup/", "/popunder/",
        "doubleclick", "googlesyndication", "googleadservices",
        "google-analytics", "googletagmanager",
        "/tracking", "/tracker", "/pixel.",
        "adservice", "adservice.google",
        "pagead", "show_ad", "showad",
        "ad.js", "ads.js", "adv.js", "ad.js",
        "prebid", "rubicon", "criteo",
        "popunder", "popunder", "clicktracker",
        "/stat/", "/count.", "/beacon/",
        "um.js", "cnzz.js", "analytics.js",
        "/track.js", "/monitor.",
    )

    private val WHITE_LIST = setOf(
        "www.baidu.com", "m.baidu.com", "baidu.com",
        "www.bilibili.com", "bilibili.com",
        "www.iqiyi.com", "iqiyi.com",
        "v.qq.com", "www.youku.com",
        "www.youtube.com", "youtube.com",
        "www.zhihu.com", "zhihu.com",
    )

    private val domainCache = ConcurrentHashMap<String, Boolean>()

    fun init(context: Context) {
        AD_DOMAINS.forEach { domainCache[it] = true }
    }

    fun isAd(request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        val host = request.url.host ?: return false

        if (isWhitelisted(host)) return false
        if (isAdDomain(host)) return true
        if (containsAdKeyword(url)) return true

        return false
    }

    fun block(): WebResourceResponse = EMPTY_RESPONSE

    private fun isWhitelisted(host: String): Boolean {
        return WHITE_LIST.any { host == it || host.endsWith(".$it") }
    }

    private fun isAdDomain(host: String): Boolean {
        return domainCache.getOrPut(host) {
            AD_DOMAINS.any { adDomain ->
                host == adDomain || host.endsWith(".$adDomain")
            }
        }
    }

    private fun containsAdKeyword(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return AD_KEYWORDS.any { lowerUrl.contains(it) }
    }
}
