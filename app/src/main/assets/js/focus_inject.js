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
