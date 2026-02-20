(() => {
  const HB_URL = '/api/heartbeat';
  const STATS_URL = '/api/stats';
  const KEY_LAST_SEEN = 'cp:lastSeen';
  const KEY_LAST_STATS = 'cp:lastStats';

  let failStreak = 0;
  let offline = false;

  const timeoutFetch = async (url, timeoutMs) => {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const response = await fetch(url, { cache: 'no-store', signal: controller.signal });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return await response.json();
    } finally {
      clearTimeout(timer);
    }
  };

  const getPowerState = (stats) => {
    if (!stats) return 'н/д';
    const crit = stats.undervoltageNow || stats.freqCappedNow || stats.throttledNow;
    const was = stats.undervoltageOccurred || stats.freqCappedOccurred || stats.throttledOccurred;
    if (crit) return 'КРИТИЧНО';
    if (was) return 'БУЛО';
    return 'OK';
  };

  const formatSeen = (value) => {
    if (!value) return '—';
    const ts = Number(value);
    if (Number.isNaN(ts) || ts <= 0) return '—';
    return new Date(ts).toLocaleString('uk-UA');
  };

  const formatPercent = (num) => {
    if (typeof num !== 'number' || Number.isNaN(num)) return 'н/д';
    return `${Math.round(num * 100)}%`;
  };

  const formatTemp = (value) => {
    if (typeof value !== 'number' || Number.isNaN(value)) return 'н/д';
    return `${value.toFixed(1)}°C`;
  };

  const formatUptime = (seconds) => {
    if (typeof seconds !== 'number' || Number.isNaN(seconds)) return 'н/д';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    return `${h}h ${m}m`;
  };

  const makeSnapshot = (stats) => {
    if (!stats) return 'немає збережених метрик';
    const ram = stats.ramTotalBytes > 0 ? formatPercent((stats.ramTotalBytes - stats.ramAvailableBytes) / stats.ramTotalBytes) : 'н/д';
    const disk = stats.diskTotalBytes > 0 ? formatPercent((stats.diskTotalBytes - stats.diskUsableBytes) / stats.diskTotalBytes) : 'н/д';
    return `CPU ${formatPercent(stats.cpuLoad01)} • RAM ${ram} • DISK ${disk} • TEMP ${formatTemp(stats.cpuTempC)} • POWER ${getPowerState(stats)}`;
  };

  const parseStats = () => {
    try {
      const raw = localStorage.getItem(KEY_LAST_STATS);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  };

  const refreshOverlay = () => {
    const seenEl = document.getElementById('cp-last-seen');
    const snapEl = document.getElementById('cp-offline-snapshot');
    if (seenEl) seenEl.textContent = formatSeen(localStorage.getItem(KEY_LAST_SEEN));
    if (snapEl) snapEl.textContent = makeSnapshot(parseStats());
  };

  const setOffline = (value) => {
    offline = value;
    document.body.classList.toggle('is-offline', offline);
    const overlay = document.querySelector('.cp-offline');
    if (overlay) overlay.classList.toggle('is-visible', offline);
    if (offline) refreshOverlay();
  };

  const tick = async () => {
    try {
      await timeoutFetch(HB_URL, 1200);
      const stats = await timeoutFetch(STATS_URL, 1500);
      localStorage.setItem(KEY_LAST_STATS, JSON.stringify(stats));
      localStorage.setItem(KEY_LAST_SEEN, String(Date.now()));
      failStreak = 0;
      if (offline) setOffline(false);
    } catch {
      failStreak += 1;
      if (failStreak >= 2) setOffline(true);
    }
  };

  refreshOverlay();
  window.addEventListener('storage', refreshOverlay);
  tick();
  setInterval(tick, 2000);
})();
