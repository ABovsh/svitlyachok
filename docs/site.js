(function(){
  var a=document.querySelectorAll('[data-apk]'),m=document.querySelectorAll('[data-ver]');
  if(!a.length||!window.fetch)return;
  fetch('https://api.github.com/repos/ABovsh/svitlyachok/releases/latest',{headers:{Accept:'application/vnd.github+json'}})
  .then(function(r){return r.ok?r.json():null}).then(function(d){
    if(!d||!d.assets)return;
    var f=d.assets.filter(function(x){return /\.apk$/.test(x.name)})[0];if(!f)return;
    a.forEach(function(e){e.href=f.browser_download_url});
    var t='Версія '+String(d.tag_name).replace(/^v/,'')+' · '+(f.size/1048576).toFixed(1).replace('.',',')+' МБ';
    m.forEach(function(e){e.textContent=t});
  }).catch(function(){});
})();
