package com.lazylines.core

import android.webkit.JavascriptInterface

/**
 * JavaScript bridge for LazyLines
 * - Video detection callbacks
 * - Ad element hiding
 * - D-pad spatial navigation injection
 */
class LazyJsBridge(private val listener: JsBridgeListener) {

    interface JsBridgeListener {
        fun onVideoFound(videos: String)
        fun onVideoRemoved(index: Int)
    }

    @JavascriptInterface
    fun onVideoDetected(videoJson: String) {
        listener.onVideoFound(videoJson)
    }

    @JavascriptInterface
    fun onVideoRemoved(index: Int) {
        listener.onVideoRemoved(index)
    }

    companion object {
        const val VIDEO_DETECTOR_JS = """
(function() {
    if (window.__lazylinesVideoDetector) return;
    window.__lazylinesVideoDetector = true;
    
    var bridge = window.LazyLinesBridge;
    if (!bridge) return;
    
    function getVideoInfo(video) {
        var src = video.src || video.currentSrc || '';
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
    
    setTimeout(scanVideos, 500);
    setTimeout(scanVideos, 1500);
    setTimeout(scanVideos, 3000);
    
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

        const val AD_HIDE_JS = """
(function() {
    if (window.__lazylinesAdHider) return;
    window.__lazylinesAdHider = true;
    
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

        const val FOCUS_INJECT_JS = """
(function(){
  if(window.__lazylinesFocusInject) return;
  window.__lazylinesFocusInject=true;
  window.focusNext=function(dir){
    var cur=document.activeElement;
    if(!cur||cur===document.body) cur=getFocusableElements()[0];
    if(!cur) return;
    var cr=cur.getBoundingClientRect();
    var cands=getFocusableElements().filter(function(e){return e!==cur;});
    var best=null,bestDist=Infinity;
    cands.forEach(function(c){
      var r=c.getBoundingClientRect();
      var inDir=false,main=0,cross=0;
      if(dir==='up'&&r.bottom<=cr.top){inDir=true;main=cr.top-r.bottom;cross=Math.abs((r.left+r.width/2)-(cr.left+cr.width/2));}
      else if(dir==='down'&&r.top>=cr.bottom){inDir=true;main=r.top-cr.bottom;cross=Math.abs((r.left+r.width/2)-(cr.left+cr.width/2));}
      else if(dir==='left'&&r.right<=cr.left){inDir=true;main=cr.left-r.right;cross=Math.abs((r.top+r.height/2)-(cr.top+cr.height/2));}
      else if(dir==='right'&&r.left>=cr.right){inDir=true;main=r.left-cr.right;cross=Math.abs((r.top+r.height/2)-(cr.top+cr.height/2));}
      if(inDir){var d=main+0.3*cross;if(d<bestDist){bestDist=d;best=c;}}
    });
    if(best) best.focus();
  };
  window.focusFirst=function(){
    var els=getFocusableElements();
    if(els.length>0) els[0].focus();
  };
  window.blurCurrent=function(){
    if(document.activeElement&&document.activeElement!==document.body) document.activeElement.blur();
  };
  function getFocusableElements(){
    return Array.from(document.querySelectorAll('a,button,input,select,textarea,[tabindex],[role="button"],[role="link"],[onclick],[contenteditable="true"]')).filter(function(e){return e.offsetHeight>0&&e.offsetWidth>0;});
  }
  var s=document.createElement('style');
  s.textContent=':focus{outline:3px solid #00E5FF!important;outline-offset:2px;}:focus-visible{box-shadow:0 0 0 4px rgba(0,229,255,0.5)!important;}';
  document.head.appendChild(s);
})();
"""
    }
}
