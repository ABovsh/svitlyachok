(function () {
  const downloads = document.querySelectorAll('[data-apk]');
  if (!downloads.length || !window.fetch) return;

  function showRelease(release) {
    if (!release || release.draft || release.prerelease || !Array.isArray(release.assets)) return;
    const version = /^v(\d+\.\d+\.\d+)$/.exec(release.tag_name);
    if (!version) return;
    const apk = release.assets.find(asset => asset.name === 'Svitlyachok-' + version[1] + '.apk');
    if (!apk) return;

    downloads.forEach(link => { link.href = apk.browser_download_url; });
    const size = (apk.size / 1048576).toFixed(1).replace('.', ',');
    const labels = document.querySelectorAll('[data-ver]');
    labels.forEach(label => { label.textContent = 'Версія ' + version[1] + ' · ' + size + ' МБ'; });

    const report = /^\[VirusTotal · 0\/([1-9]\d*) · (\d{4}-\d{2}-\d{2})\]\(https:\/\/www\.virustotal\.com\/gui\/file\/([0-9a-f]{64})\)$/m.exec(release.body || '');
    if (!report || apk.digest !== 'sha256:' + report[3]) return;
    labels.forEach(label => {
      const link = document.createElement('a');
      link.href = 'https://www.virustotal.com/gui/file/' + report[3];
      link.textContent = 'Перевірка APK у VirusTotal · 0/' + report[1] + ' · ' + report[2];
      label.parentElement.append(document.createElement('br'), link);
    });
  }

  fetch('https://api.github.com/repos/ABovsh/svitlyachok/releases/latest', {
    headers: { Accept: 'application/vnd.github+json' }
  }).then(response => response.ok ? response.json() : null).then(showRelease).catch(() => {});
})();
