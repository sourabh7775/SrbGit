// ─── Web Audio Engine ────────────────────────────────────────────────────────
// Synthesised engine + nitro + tyre sounds — no external files needed.

const AudioEngine = (() => {
  let ctx, engineOsc, engineGain, nitroOsc, nitroGain, tyreOsc, tyreGain;
  let ready = false;

  function _boot() {
    if (ready) return;
    ctx = new (window.AudioContext || window.webkitAudioContext)();

    // ── engine oscillator (sawtooth, pitch tracks speed) ──
    engineOsc  = ctx.createOscillator();
    engineGain = ctx.createGain();
    const dist = ctx.createWaveShaper();
    dist.curve = _makeDistCurve(60);
    dist.oversample = '4x';
    const lpf = ctx.createBiquadFilter();
    lpf.type = 'lowpass'; lpf.frequency.value = 400;
    engineOsc.type = 'sawtooth';
    engineOsc.frequency.value = 60;
    engineGain.gain.value = 0;
    engineOsc.connect(dist); dist.connect(lpf); lpf.connect(engineGain); engineGain.connect(ctx.destination);
    engineOsc.start();

    // ── nitro hiss ──
    nitroOsc  = ctx.createOscillator();
    nitroGain = ctx.createGain();
    const nLpf = ctx.createBiquadFilter();
    nLpf.type = 'highpass'; nLpf.frequency.value = 1200;
    nitroOsc.type = 'sawtooth'; nitroOsc.frequency.value = 1800;
    nitroGain.gain.value = 0;
    nitroOsc.connect(nLpf); nLpf.connect(nitroGain); nitroGain.connect(ctx.destination);
    nitroOsc.start();

    // ── tyre squeal ──
    tyreOsc  = ctx.createOscillator();
    tyreGain = ctx.createGain();
    tyreOsc.type = 'sine'; tyreOsc.frequency.value = 340;
    tyreGain.gain.value = 0;
    tyreOsc.connect(tyreGain); tyreGain.connect(ctx.destination);
    tyreOsc.start();

    ready = true;
  }

  function _makeDistCurve(amount) {
    const n = 256, curve = new Float32Array(n);
    for (let i = 0; i < n; i++) {
      const x = (i * 2) / n - 1;
      curve[i] = ((Math.PI + amount) * x) / (Math.PI + amount * Math.abs(x));
    }
    return curve;
  }

  function update(speedKmh, nitroOn, drifting) {
    if (!ready) return;
    const t = ctx.currentTime;
    // Engine pitch & volume
    const targetFreq   = 55 + speedKmh * 1.6;
    const targetVolume = Math.min(0.12, 0.02 + speedKmh * 0.0015);
    engineOsc.frequency.setTargetAtTime(targetFreq,   t, 0.08);
    engineGain.gain.setTargetAtTime(targetVolume, t, 0.1);

    // Nitro hiss
    const nVol = nitroOn ? 0.06 : 0;
    nitroGain.gain.setTargetAtTime(nVol, t, 0.05);

    // Tyre squeal
    const tVol = (drifting && speedKmh > 30) ? 0.05 : 0;
    tyreGain.gain.setTargetAtTime(tVol, t, 0.08);
    tyreOsc.frequency.setTargetAtTime(280 + speedKmh * 1.2, t, 0.1);
  }

  function playBoostHit() {
    if (!ready) return;
    const o = ctx.createOscillator();
    const g = ctx.createGain();
    o.type = 'square'; o.frequency.value = 440;
    g.gain.setValueAtTime(0.15, ctx.currentTime);
    g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.3);
    o.connect(g); g.connect(ctx.destination);
    o.start(); o.stop(ctx.currentTime + 0.3);
  }

  function playLapDing() {
    if (!ready) return;
    for (const [f, t] of [[880, 0], [1100, 0.12], [1320, 0.24]]) {
      const o = ctx.createOscillator();
      const g = ctx.createGain();
      o.type = 'sine'; o.frequency.value = f;
      g.gain.setValueAtTime(0.1, ctx.currentTime + t);
      g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + t + 0.25);
      o.connect(g); g.connect(ctx.destination);
      o.start(ctx.currentTime + t);
      o.stop(ctx.currentTime + t + 0.28);
    }
  }

  // Must be called from a user gesture
  function enable() { _boot(); if (ctx.state === 'suspended') ctx.resume(); }

  return { update, enable, playBoostHit, playLapDing };
})();
