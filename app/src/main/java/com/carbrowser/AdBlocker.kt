package com.carbrowser

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 轻量级广告拦截器
 * - 基于 URL 关键字和域名黑名单
 * - 零外部依赖，纯内存匹配
 * - 针对国内常见广告优化
 */
object AdBlocker {

    private val EMPTY_RESPONSE = WebResourceResponse(
        "text/plain", "utf-8", ByteArrayInputStream("".toByteArray())
    )

    // 广告域名黑名单
    private val AD_DOMAINS = setOf(
        // 通用广告平台
        "ad.doubleclick.net", "pagead2.googlesyndication.com",
        "googleads.g.doubleclick.net", "ads.google.com",
        "advertising.com", "adnxs.com", "adsrvr.org",
        "adcolony.com", "admob.com", "adskeeper.co.uk",
        // 国内广告
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
        // 短视频/视频广告
        "ad.iqiyi.com", "ad.qq.com",
        "sdk.e.qq.com", "lives.l.qq.com",
        "ade.googlesyndication.com",
        // 弹窗/推广
        "push.js", "pop.js", "ads.js",
        "tracking.miui.com", "a.stat.mi.com",
        "misc.in.app.mi.com",
        // 更多常见广告
        "adview.cn", "tanx.com", "mmstat.com",
        "atm.youku.com", "i.alicdn.com/af/",
        "count.tb.cn", "log.mmstat.com",
        "cdn.mxpnl.com", "cdn.mparticle.com",
    )

    // URL 中的广告关键字
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

    // 允许的域名（防误杀白名单）
    private val WHITE_LIST = setOf(
        "www.baidu.com", "m.baidu.com", "baidu.com",
        "www.bilibili.com", "bilibili.com",
        "www.iqiyi.com", "iqiyi.com",
        "v.qq.com", "www.youku.com",
        "www.youtube.com", "youtube.com",
        "www.zhihu.com", "zhihu.com",
    )

    // 缓存已判断的域名结果
    private val domainCache = ConcurrentHashMap<String, Boolean>()

    fun init(context: Context) {
        // 预热缓存
        AD_DOMAINS.forEach { domainCache[it] = true }
    }

    /**
     * 判断请求是否为广告
     */
    fun isAd(request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        val host = request.url.host ?: return false

        // 白名单域名直接放行
        if (isWhitelisted(host)) return false

        // 检查域名黑名单
        if (isAdDomain(host)) return true

        // 检查 URL 关键字
        if (containsAdKeyword(url)) return true

        return false
    }

    /**
     * 拦截广告请求，返回空响应
     */
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
