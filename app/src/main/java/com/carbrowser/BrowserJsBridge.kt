package com.carbrowser

import android.webkit.JavascriptInterface
import org.json.JSONArray

/**
 * JavaScript 桥接接口
 * - 视频检测回调
 * - 广告元素隐藏
 */
class BrowserJsBridge(private val listener: JsBridgeListener) {

    interface JsBridgeListener {
        fun onVideoFound(videos: String)  // JSON array of video URLs
        fun onVideoRemoved(index: Int)
    }

    /**
     * JS 检测到视频时回调
     */
    @JavascriptInterface
    fun onVideoDetected(videoJson: String) {
        listener.onVideoFound(videoJson)
    }

    /**
     * JS 检测到视频移除
     */
    @JavascriptInterface
    fun onVideoRemoved(index: Int) {
        listener.onVideoRemoved(index)
    }

    companion object {
        /**
         * 注入到页面的视频检测 JS
         * 自动监控 DOM 变化，发现 video 元素即上报
         */
        const val VIDEO_DETECTOR_JS = """
(function() {
    if (window.__carBrowserVideoDetector) return;
    window.__carBrowserVideoDetector = true;
    
    var bridge = window.CarBrowserBridge;
    if (!bridge) return;
    
    function getVideoInfo(video) {
        var src = video.src || video.currentSrc || '';
        // 尝试从 source 子元素获取
        if (!src) {
            var sources = video.querySelectorAll('source');
            for (var i = 0; i < sources.length; i++) {
                if (sources[i].src) {
                    src = sources[i].src;
                    break;
                }
            }
        }
        return {
            src: src,
            poster: video.poster || '',
            width: video.videoWidth || video.clientWidth,
            height: video.videoHeight || video.clientHeight,
            duration: video.duration || 0,
            index: Array.from(document.querySelectorAll('video')).indexOf(video)
        };
    }
    
    function scanVideos() {
        var videos = document.querySelectorAll('video');
        var result = [];
        for (var i = 0; i < videos.length; i++) {
            var info = getVideoInfo(videos[i]);
            if (info.src || info.poster) {
                result.push(info);
            }
        }
        if (result.length > 0) {
            try {
                bridge.onVideoDetected(JSON.stringify(result));
            } catch(e) {}
        }
    }
    
    // 初始扫描
    setTimeout(scanVideos, 500);
    setTimeout(scanVideos, 1500);
    setTimeout(scanVideos, 3000);
    
    // 监控 DOM 变化
    var observer = new MutationObserver(function(mutations) {
        var found = false;
        for (var i = 0; i < mutations.length; i++) {
            var added = mutations[i].addedNodes;
            for (var j = 0; j < added.length; j++) {
                if (added[j].nodeName === 'VIDEO' || 
                    (added[j].querySelector && added[j].querySelector('video'))) {
                    found = true;
                    break;
                }
            }
            if (found) break;
        }
        if (found) {
            setTimeout(scanVideos, 200);
        }
    });
    
    observer.observe(document.documentElement || document.body, {
        childList: true,
        subtree: true
    });
})();
"""

        /**
         * 广告元素隐藏 CSS + JS
         */
        const val AD_HIDE_JS = """
(function() {
    if (window.__carBrowserAdHider) return;
    window.__carBrowserAdHider = true;
    
    // 常见广告选择器
    var adSelectors = [
        '[class*="ad-"]', '[class*="ad_"]', '[class*="_ad"]', '[class*="-ad-"]',
        '[id*="ad-"]', '[id*="ad_"]', '[id*="_ad"]', '[id*="-ad-"]',
        '[class*="banner"]', '[class*="popup"]', '[class*="modal-ad"]',
        '[class*="sponsor"]', '[class*="promoted"]',
        'ins.adsbygoogle', 'div[id^="google_ads"]',
        'iframe[src*="doubleclick"]', 'iframe[src*="googlesyndication"]',
        'iframe[src*="ad."]', 'iframe[src*="/ad/"]',
        '[class*="feed-ad"]', '[class*="video-ad"]',
        '[class*="interstitial"]', '[class*="pre-roll"]',
    ];
    
    var style = document.createElement('style');
    style.textContent = adSelectors.join(', ') + ' { display: none !important; height: 0 !important; overflow: hidden !important; }';
    document.head.appendChild(style);
    
    // 移除广告 iframe
    var iframes = document.querySelectorAll('iframe');
    for (var i = 0; i < iframes.length; i++) {
        var src = iframes[i].src || '';
        if (src.indexOf('doubleclick') > -1 || 
            src.indexOf('googlesyndication') > -1 ||
            src.indexOf('/ad/') > -1 ||
            src.indexOf('ad.') > -1) {
            iframes[i].remove();
        }
    }
})();
"""
    }
}
