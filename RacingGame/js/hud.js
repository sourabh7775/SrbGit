// ─── HUD Renderer ─────────────────────────────────────────────────────────────

const HUD = (() => {
  const CAMERAS = ['CHASE CAM', 'LOW CAM', 'COCKPIT', 'TV BROADCAST', 'HELICOPTER'];

  let speedCtx, minimapCtx;
  let bestLapMs = Infinity;
  let lapTimesArr = [];

  function init() {
    speedCtx   = document.getElementById('speedometer').getContext('2d');
    minimapCtx = document.getElementById('minimap').getContext('2d');
  }

  /* ── Speedometer ─────────────────────────────────────── */
  function drawSpeedometer(speedKmh, maxSpeed = 200) {
    const ctx = speedCtx;
    const cx = 80, cy = 80, r = 68;
    ctx.clearRect(0, 0, 160, 160);

    // Background circle
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, Math.PI*2);
    ctx.fillStyle = 'rgba(0,8,30,0.85)';
    ctx.fill();
    ctx.strokeStyle = 'rgba(0,212,255,0.3)';
    ctx.lineWidth = 2;
    ctx.stroke();

    // Tick marks
    const startAngle = Math.PI * 0.75;
    const sweep      = Math.PI * 1.5;
    for (let i = 0; i <= 20; i++) {
      const a = startAngle + (i / 20) * sweep;
      const isMajor = i % 4 === 0;
      const inner   = isMajor ? r - 14 : r - 8;
      ctx.beginPath();
      ctx.moveTo(cx + Math.cos(a)*inner, cy + Math.sin(a)*inner);
      ctx.lineTo(cx + Math.cos(a)*(r-3), cy + Math.sin(a)*(r-3));
      ctx.strokeStyle = isMajor ? 'rgba(255,255,255,0.7)' : 'rgba(255,255,255,0.3)';
      ctx.lineWidth = isMajor ? 2 : 1;
      ctx.stroke();

      if (isMajor) {
        const label = Math.round((i/20)*maxSpeed);
        const lx = cx + Math.cos(a) * (r-22);
        const ly = cy + Math.sin(a) * (r-22);
        ctx.fillStyle = 'rgba(255,255,255,0.5)';
        ctx.font = '9px monospace';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(label, lx, ly);
      }
    }

    // Arc fill (speed zone colors)
    const fraction = Math.min(1, speedKmh / maxSpeed);
    const endAngle = startAngle + fraction * sweep;

    // Gradient arc
    const grad = ctx.createLinearGradient(0, 0, 160, 160);
    grad.addColorStop(0,   '#00d4ff');
    grad.addColorStop(0.6, '#39ff14');
    grad.addColorStop(1.0, '#ff2d78');
    ctx.beginPath();
    ctx.arc(cx, cy, r - 6, startAngle, endAngle);
    ctx.strokeStyle = grad;
    ctx.lineWidth = 8;
    ctx.lineCap = 'round';
    ctx.stroke();

    // Needle
    ctx.save();
    ctx.translate(cx, cy);
    ctx.rotate(startAngle + fraction * sweep);
    ctx.beginPath();
    ctx.moveTo(-4, 0); ctx.lineTo(r-16, 0);
    ctx.strokeStyle = '#ff2d78';
    ctx.lineWidth = 2.5;
    ctx.lineCap = 'round';
    ctx.stroke();
    ctx.restore();

    // Center dot
    ctx.beginPath();
    ctx.arc(cx, cy, 5, 0, Math.PI*2);
    ctx.fillStyle = '#ff2d78';
    ctx.fill();

    // Speed text
    ctx.fillStyle = 'white';
    ctx.font = 'bold 24px monospace';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(Math.round(speedKmh), cx, cy + 20);
    ctx.font = '9px monospace';
    ctx.fillStyle = 'rgba(255,255,255,0.4)';
    ctx.fillText('km/h', cx, cy + 34);
  }

  /* ── Minimap ─────────────────────────────────────────── */
  function drawMinimap(spline, playerPos, aiCars) {
    const ctx = minimapCtx;
    const W = 140, H = 140;
    ctx.clearRect(0, 0, W, H);

    // Clip to circle
    ctx.save();
    ctx.beginPath();
    ctx.arc(70, 70, 68, 0, Math.PI*2);
    ctx.clip();

    ctx.fillStyle = 'rgba(0,8,30,0.85)';
    ctx.fillRect(0, 0, W, H);

    if (!spline || spline.length === 0) { ctx.restore(); return; }

    // Compute bounds
    let minX=Infinity,maxX=-Infinity,minZ=Infinity,maxZ=-Infinity;
    for (const p of spline) {
      minX=Math.min(minX,p[0]); maxX=Math.max(maxX,p[0]);
      minZ=Math.min(minZ,p[1]); maxZ=Math.max(maxZ,p[1]);
    }
    const pad = 15;
    const sx = (W - pad*2) / (maxX - minX);
    const sz = (H - pad*2) / (maxZ - minZ);
    const scale = Math.min(sx, sz) * 0.85;
    const offX = W/2 - (minX+maxX)/2 * scale;
    const offZ = H/2 - (minZ+maxZ)/2 * scale;

    const toScreen = (x, z) => [x * scale + offX, z * scale + offZ];

    // Draw track outline
    ctx.beginPath();
    for (let i = 0; i < spline.length; i++) {
      const [sx2, sy2] = toScreen(spline[i][0], spline[i][1]);
      i === 0 ? ctx.moveTo(sx2, sy2) : ctx.lineTo(sx2, sy2);
    }
    ctx.closePath();
    ctx.strokeStyle = 'rgba(255,255,255,0.15)';
    ctx.lineWidth = 7;
    ctx.stroke();
    ctx.strokeStyle = 'rgba(0,212,255,0.6)';
    ctx.lineWidth = 3;
    ctx.stroke();

    // AI cars
    if (aiCars) {
      const aiColors = ['#39ff14','#ffe600','#ff8800'];
      for (let i = 0; i < aiCars.length; i++) {
        const [ax, az] = toScreen(aiCars[i].pos.x, aiCars[i].pos.z);
        ctx.beginPath();
        ctx.arc(ax, az, 4, 0, Math.PI*2);
        ctx.fillStyle = aiColors[i % aiColors.length];
        ctx.fill();
      }
    }

    // Player
    if (playerPos) {
      const [px, pz] = toScreen(playerPos.x, playerPos.z);
      ctx.beginPath();
      ctx.arc(px, pz, 6, 0, Math.PI*2);
      ctx.fillStyle = '#ff2d78';
      ctx.fill();
      ctx.strokeStyle = 'white';
      ctx.lineWidth = 1.5;
      ctx.stroke();
    }

    ctx.restore();

    // Outer ring
    ctx.beginPath();
    ctx.arc(70, 70, 68, 0, Math.PI*2);
    ctx.strokeStyle = 'rgba(0,212,255,0.5)';
    ctx.lineWidth = 2;
    ctx.stroke();
  }

  /* ── Race timer ──────────────────────────────────────── */
  function formatTime(ms) {
    if (!ms || ms === Infinity) return '--:--.---';
    const m   = Math.floor(ms / 60000);
    const s   = Math.floor((ms % 60000) / 1000);
    const ms3 = Math.floor(ms % 1000);
    return `${m}:${String(s).padStart(2,'0')}.${String(ms3).padStart(3,'0')}`;
  }

  function updateTimer(raceStartMs) {
    const el = document.getElementById('hud-timer');
    if (!raceStartMs) return;
    el.textContent = formatTime(performance.now() - raceStartMs);
  }

  /* ── Lap times panel ─────────────────────────────────── */
  function updateLapTimes(lapTimes, bestLap) {
    const panel = document.getElementById('lap-times-panel');
    panel.innerHTML = '';
    for (let i = 0; i < lapTimes.length; i++) {
      const div = document.createElement('div');
      div.className = 'lap-time-entry' + (lapTimes[i] === bestLap ? ' lap-time-best' : '');
      div.textContent = `LAP ${i+1}  ${formatTime(lapTimes[i])}`;
      panel.appendChild(div);
    }
  }

  /* ── Camera label ─────────────────────────────────────── */
  function setCameraLabel(idx) {
    document.getElementById('cam-mode-label').textContent = CAMERAS[idx % CAMERAS.length];
  }

  /* ── Nitro bar ────────────────────────────────────────── */
  function updateNitro(fraction) {
    document.getElementById('nitro-bar').style.width = (fraction * 100) + '%';
  }

  /* ── Position & lap ───────────────────────────────────── */
  function updatePosition(pos) {
    document.getElementById('pos-num').textContent = pos;
  }

  function updateLap(lap, total = 3) {
    document.getElementById('lap-num').textContent = lap;
  }

  function updateBestLap(ms) {
    const el = document.getElementById('best-lap-display');
    el.textContent = ms === Infinity ? '--:--' : formatTime(ms);
    document.getElementById('menu-best-lap').textContent = ms === Infinity ? '--:--' : formatTime(ms);
  }

  function updateGear(g) {
    document.getElementById('gear-display').textContent = g;
  }

  /* ── Finish screen ────────────────────────────────────── */
  function showFinish(position, totalTime, bestLap, lapTimes) {
    const ov = document.getElementById('finish-overlay');
    ov.classList.remove('hidden');
    document.getElementById('finish-position').textContent = `POSITION: P${position}`;
    document.getElementById('finish-time').innerHTML  = `TOTAL TIME <span>${formatTime(totalTime)}</span>`;
    document.getElementById('finish-best').innerHTML  = `BEST LAP   <span>${formatTime(bestLap)}</span>`;
    const lapsHtml = lapTimes.map((t,i) => `LAP ${i+1}: ${formatTime(t)}`).join(' | ');
    document.getElementById('finish-laps').textContent = lapsHtml;
  }

  /* ── Countdown ────────────────────────────────────────── */
  function showCountdown(text, color) {
    const el = document.getElementById('countdown-text');
    el.textContent = text;
    el.style.color = color;
    el.style.animation = 'none';
    void el.offsetWidth; // reflow
    el.style.animation = 'countdown-pop 1s ease forwards';
  }

  function hideCountdown() {
    document.getElementById('countdown-text').textContent = '';
  }

  return {
    init, drawSpeedometer, drawMinimap,
    updateTimer, updateLapTimes, updateNitro,
    updatePosition, updateLap, updateBestLap, updateGear,
    showFinish, showCountdown, hideCountdown, setCameraLabel,
    formatTime,
  };
})();
