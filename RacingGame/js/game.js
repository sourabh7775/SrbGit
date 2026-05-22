// ─── Main Game Engine ─────────────────────────────────────────────────────────

const Game = (() => {

  /* ── State ────────────────────────────────────────────── */
  let renderer, scene, camera;
  let player, aiCars = [];
  let trackData;
  let particles;
  let input = {};
  let paused = false;
  let gameRunning = false;
  let raceStartTime = 0;
  let countdownActive = false;
  let camMode = 0;           // 0-4
  const CAM_MODES = 5;
  let cameraTarget = new THREE.Vector3();
  let cameraPos    = new THREE.Vector3();
  let prevTime     = 0;
  let bestLapGlobal = Infinity;
  let topSpeedGlobal = 0;

  // Menu background
  let menuRenderer, menuScene, menuCamera, menuCars = [];

  /* ── Public API ───────────────────────────────────────── */
  function startRace() {
    showScreen('game-screen');
    if (!renderer) _initRenderer();
    _buildScene();
    _startCountdown();
  }

  function resume() {
    paused = false;
    document.getElementById('pause-menu').classList.add('hidden');
    prevTime = performance.now();
    requestAnimationFrame(_loop);
  }

  function restart() {
    _tearDown();
    startRace();
  }

  function exitToMenu() {
    _tearDown();
    document.getElementById('finish-overlay').classList.add('hidden');
    document.getElementById('pause-menu').classList.add('hidden');
    showScreen('menu-screen');
    _startMenuAnimation();
  }

  /* ── Renderer ─────────────────────────────────────────── */
  function _initRenderer() {
    const canvas = document.getElementById('game-canvas');
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type    = THREE.PCFSoftShadowMap;
    renderer.outputEncoding    = THREE.sRGBEncoding;

    camera = new THREE.PerspectiveCamera(65, window.innerWidth / window.innerHeight, 0.5, 800);

    window.addEventListener('resize', () => {
      renderer.setSize(window.innerWidth, window.innerHeight);
      camera.aspect = window.innerWidth / window.innerHeight;
      camera.updateProjectionMatrix();
    });
  }

  /* ── Scene ────────────────────────────────────────────── */
  function _buildScene() {
    scene = new THREE.Scene();

    // Build track
    trackData = TrackBuilder.build(scene);

    // Player car — spawned at first waypoint
    const startPos = trackData.waypoints[0];
    player = new Car(scene, 0xff2d78, true);
    player.pos.set(startPos.x, 0.45, startPos.z + 5);
    player.angle = Math.atan2(
      trackData.waypoints[1].x - startPos.x,
      trackData.waypoints[1].z - startPos.z
    );

    // AI cars
    aiCars = createAIOpponents(scene);
    for (let i = 0; i < aiCars.length; i++) {
      const wpOff = aiCars[i].offset;
      const wp = trackData.waypoints[wpOff % trackData.waypoints.length];
      const wpN = trackData.waypoints[(wpOff+1) % trackData.waypoints.length];
      aiCars[i].pos.set(wp.x + (i+1)*3, 0.45, wp.z);
      aiCars[i].angle = Math.atan2(wpN.x - wp.x, wpN.z - wp.z);
      aiCars[i].group.position.copy(aiCars[i].pos);
      aiCars[i].group.rotation.y = aiCars[i].angle;
    }

    // Particles
    particles = new ParticleSystem(scene);

    // Stars
    _addStars();

    HUD.init();
    _bindInput();
  }

  function _addStars() {
    const geo = new THREE.BufferGeometry();
    const positions = new Float32Array(2000 * 3);
    for (let i = 0; i < 2000; i++) {
      const theta = Math.random() * Math.PI*2;
      const phi   = Math.acos(2*Math.random()-1);
      const r = 500 + Math.random()*100;
      positions[i*3]   = r * Math.sin(phi) * Math.cos(theta);
      positions[i*3+1] = r * Math.cos(phi);
      positions[i*3+2] = r * Math.sin(phi) * Math.sin(theta);
    }
    geo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
    const mat = new THREE.PointsMaterial({ color: 0xffffff, size: 1.2, sizeAttenuation: true });
    scene.add(new THREE.Points(geo, mat));
  }

  /* ── Countdown ─────────────────────────────────────────── */
  function _startCountdown() {
    countdownActive = true;
    gameRunning     = false;
    const steps = [
      { text: '3', color: '#ff2d78', delay: 0 },
      { text: '2', color: '#ffe600', delay: 1000 },
      { text: '1', color: '#00d4ff', delay: 2000 },
      { text: 'GO!', color: '#39ff14', delay: 3000 },
    ];
    for (const s of steps) {
      setTimeout(() => HUD.showCountdown(s.text, s.color), s.delay);
    }
    setTimeout(() => {
      HUD.hideCountdown();
      countdownActive = false;
      gameRunning     = true;
      raceStartTime   = performance.now();
      player.lapStartTime = raceStartTime;
      prevTime = performance.now();
      requestAnimationFrame(_loop);
    }, 4000);

    // Render static frame while counting down
    prevTime = performance.now();
    requestAnimationFrame(_countdownLoop);
  }

  function _countdownLoop(ts) {
    if (gameRunning || paused) return;
    _render(0);
    if (countdownActive) requestAnimationFrame(_countdownLoop);
  }

  /* ── Main Loop ─────────────────────────────────────────── */
  function _loop(ts) {
    if (!gameRunning || paused) return;
    const dt = Math.min((ts - prevTime) / 1000, 0.05);
    prevTime = ts;

    _update(dt);
    _render(dt);
    requestAnimationFrame(_loop);
  }

  function _update(dt) {
    if (player.raceFinished && !document.getElementById('finish-overlay').classList.contains('hidden') === false) return;

    // Player
    player.update(dt, input, trackData);
    particles.emitCarEffects(player, dt);

    // AI
    for (const ai of aiCars) {
      ai.updateAI(dt, trackData);
      particles.emitCarEffects(ai, dt);
    }

    particles.update(dt);

    // Camera
    _updateCamera(dt);

    // HUD updates
    const speed = player.getSpeedKmh();
    if (speed > topSpeedGlobal) {
      topSpeedGlobal = speed;
      document.getElementById('menu-top-speed').textContent = Math.round(topSpeedGlobal) + ' km/h';
    }
    HUD.drawSpeedometer(speed, 200);
    HUD.drawMinimap(trackData.spline, player.pos, aiCars);
    HUD.updateTimer(raceStartTime);
    HUD.updateNitro(player.nitro);
    HUD.updateLap(player.lap);
    HUD.updateGear(player.getGear());
    if (player.bestLap < bestLapGlobal) {
      bestLapGlobal = player.bestLap;
    }
    HUD.updateBestLap(bestLapGlobal);
    HUD.updateLapTimes(player.lapTimes, player.bestLap);

    // Race position
    const pos = _calcPosition();
    HUD.updatePosition(pos);

    // Nitro flash
    const flash = document.getElementById('nitro-flash');
    if (player.nitroOn) flash.classList.add('active');
    else                flash.classList.remove('active');

    // Speed lines
    let sl = document.getElementById('speed-lines');
    if (!sl) {
      sl = document.createElement('div');
      sl.id = 'speed-lines';
      document.getElementById('game-screen').appendChild(sl);
    }
    if (speed > 100) sl.classList.add('active');
    else             sl.classList.remove('active');

    // Race finish
    if (player.raceFinished) {
      gameRunning = false;
      const totalTime = performance.now() - raceStartTime;
      HUD.showFinish(pos, totalTime, player.bestLap, player.lapTimes);
    }
  }

  /* ── Camera ─────────────────────────────────────────────── */
  function _updateCamera(dt) {
    const p   = player.pos;
    const ang = player.angle;
    const fwd = new THREE.Vector3(Math.sin(ang), 0, Math.cos(ang));
    const right = new THREE.Vector3(Math.cos(ang), 0, -Math.sin(ang));

    let targetPos, lookAt;

    switch (camMode) {
      case 0: // Chase cam
        targetPos = new THREE.Vector3(
          p.x - fwd.x * 14 + right.x * 0,
          p.y + 5.5,
          p.z - fwd.z * 14
        );
        lookAt = new THREE.Vector3(p.x + fwd.x*8, p.y+1, p.z + fwd.z*8);
        break;

      case 1: // Low chase
        targetPos = new THREE.Vector3(
          p.x - fwd.x * 9,
          p.y + 2.2,
          p.z - fwd.z * 9
        );
        lookAt = new THREE.Vector3(p.x + fwd.x*12, p.y+0.5, p.z + fwd.z*12);
        break;

      case 2: // Cockpit
        targetPos = new THREE.Vector3(
          p.x + fwd.x * 1.2,
          p.y + 1.3,
          p.z + fwd.z * 1.2
        );
        lookAt = new THREE.Vector3(p.x + fwd.x*15, p.y+0.8, p.z + fwd.z*15);
        break;

      case 3: // TV broadcast — side angle
        targetPos = new THREE.Vector3(
          p.x - fwd.x*6 + right.x*22,
          p.y + 10,
          p.z - fwd.z*6 + right.z*22
        );
        lookAt = new THREE.Vector3(p.x, p.y, p.z);
        break;

      case 4: // Helicopter — top down orbit
        const t = performance.now() * 0.0003;
        targetPos = new THREE.Vector3(
          p.x + Math.cos(t)*20,
          p.y + 40,
          p.z + Math.sin(t)*20
        );
        lookAt = new THREE.Vector3(p.x, p.y, p.z);
        break;
    }

    // Smooth camera lerp
    cameraPos.lerp(targetPos, dt * 6);
    cameraTarget.lerp(lookAt, dt * 8);
    camera.position.copy(cameraPos);
    camera.lookAt(cameraTarget);

    // Dynamic FOV for nitro
    const targetFOV = player.nitroOn ? 80 : 65;
    camera.fov += (targetFOV - camera.fov) * dt * 4;
    camera.updateProjectionMatrix();
  }

  /* ── Render ────────────────────────────────────────────── */
  function _render(dt) {
    if (!renderer || !scene || !camera) return;
    renderer.render(scene, camera);
  }

  /* ── Input ─────────────────────────────────────────────── */
  function _bindInput() {
    const keyMap = {
      KeyW: 'throttle', ArrowUp: 'throttle',
      KeyS: 'brake',    ArrowDown: 'brake',
      KeyA: 'steerLeft', ArrowLeft: 'steerLeft',
      KeyD: 'steerRight', ArrowRight: 'steerRight',
      Space: 'nitro',
    };

    document.addEventListener('keydown', e => {
      if (keyMap[e.code]) { input[keyMap[e.code]] = true; e.preventDefault(); }
      if (e.code === 'KeyC') {
        camMode = (camMode + 1) % CAM_MODES;
        HUD.setCameraLabel(camMode);
      }
      if (e.code === 'KeyR') {
        const wp = trackData.waypoints[player.waypointIdx];
        const wpN = trackData.waypoints[(player.waypointIdx+1) % trackData.waypoints.length];
        const ang = Math.atan2(wpN.x - wp.x, wpN.z - wp.z);
        player.reset({ x: wp.x, z: wp.z }, ang);
      }
      if (e.code === 'Escape' && gameRunning) {
        paused = !paused;
        document.getElementById('pause-menu').classList.toggle('hidden', !paused);
        if (!paused) { prevTime = performance.now(); requestAnimationFrame(_loop); }
      }
    });
    document.addEventListener('keyup', e => {
      if (keyMap[e.code]) input[keyMap[e.code]] = false;
    });

    // Gamepad (basic)
    window.addEventListener('gamepadconnected', () => {
      _gamepadLoop();
    });
  }

  function _gamepadLoop() {
    const gps = navigator.getGamepads();
    if (!gps[0]) return;
    const gp = gps[0];
    input.throttle  = gp.buttons[7].pressed || gp.buttons[0].pressed;
    input.brake     = gp.buttons[6].pressed || gp.buttons[1].pressed;
    input.steerLeft  = gp.axes[0] < -0.2;
    input.steerRight = gp.axes[0] >  0.2;
    input.nitro      = gp.buttons[5].pressed || gp.buttons[2].pressed;
    requestAnimationFrame(_gamepadLoop);
  }

  /* ── Position calculation ───────────────────────────────── */
  function _calcPosition() {
    const playerProgress = player.lap * 1000 + player.waypointIdx;
    let pos = 1;
    for (const ai of aiCars) {
      const aiProgress = ai.lap * 1000 + ai.waypointIdx;
      if (aiProgress > playerProgress) pos++;
    }
    return pos;
  }

  /* ── Tear down ─────────────────────────────────────────── */
  function _tearDown() {
    gameRunning = false;
    paused      = false;
    input       = {};
    if (player)   { player.dispose(); player = null; }
    for (const ai of aiCars) ai.dispose();
    aiCars = [];
    if (scene) {
      while (scene.children.length) scene.remove(scene.children[0]);
    }
    document.getElementById('finish-overlay').classList.add('hidden');
    document.getElementById('pause-menu').classList.add('hidden');
    document.getElementById('lap-times-panel').innerHTML = '';
  }

  /* ── Menu background animation ─────────────────────────── */
  function _startMenuAnimation() {
    const canvas = document.getElementById('menu-canvas');
    if (!menuRenderer) {
      menuRenderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
      menuRenderer.setSize(window.innerWidth, window.innerHeight);
      menuScene = new THREE.Scene();
      menuCamera = new THREE.PerspectiveCamera(70, window.innerWidth/window.innerHeight, 0.5, 1000);
      menuCamera.position.set(0, 15, 30);
      menuCamera.lookAt(0, 0, 0);
      menuScene.background = new THREE.Color(0x000015);
      menuScene.fog = new THREE.Fog(0x000015, 40, 200);

      // Simple road
      const road = new THREE.Mesh(
        new THREE.PlaneGeometry(20, 500),
        new THREE.MeshLambertMaterial({ color: 0x111122 })
      );
      road.rotation.x = -Math.PI/2;
      menuScene.add(road);

      // Ground
      const gnd = new THREE.Mesh(
        new THREE.PlaneGeometry(400, 500),
        new THREE.MeshLambertMaterial({ color: 0x0a1a0a })
      );
      gnd.rotation.x = -Math.PI/2;
      menuScene.add(gnd);

      // Lights
      menuScene.add(new THREE.AmbientLight(0x334466, 1));
      const sl = new THREE.DirectionalLight(0xffeedd, 1.5);
      sl.position.set(10, 30, 10);
      menuScene.add(sl);

      // Neon lines on road
      for (let z = -200; z < 200; z += 8) {
        const line = new THREE.Mesh(
          new THREE.PlaneGeometry(0.3, 4),
          new THREE.MeshBasicMaterial({ color: 0xffffff, opacity: 0.4, transparent: true })
        );
        line.rotation.x = -Math.PI/2;
        line.position.set(0, 0.01, z);
        menuScene.add(line);
      }

      // Stars
      const sGeo = new THREE.BufferGeometry();
      const sPos = new Float32Array(1000*3);
      for (let i = 0; i < 1000; i++) {
        sPos[i*3]   = (Math.random()-0.5)*800;
        sPos[i*3+1] = Math.random()*200 + 5;
        sPos[i*3+2] = (Math.random()-0.5)*500;
      }
      sGeo.setAttribute('position', new THREE.Float32BufferAttribute(sPos, 3));
      menuScene.add(new THREE.Points(sGeo, new THREE.PointsMaterial({ color:0xffffff, size:1, sizeAttenuation:true })));

      // Menu cars
      for (let i = 0; i < 3; i++) {
        const colors = [0xff2d78, 0x39ff14, 0xffe600];
        const mc = { group: new THREE.Group(), z: -50 - i*30, x: (i-1)*5 };
        const body = new THREE.Mesh(new THREE.BoxGeometry(2.2, 0.7, 4.5), new THREE.MeshLambertMaterial({ color: colors[i] }));
        body.position.y = 0.55;
        mc.group.add(body);
        mc.group.position.set(mc.x, 0, mc.z);
        menuScene.add(mc.group);
        menuCars.push(mc);
      }

      window.addEventListener('resize', () => {
        menuRenderer.setSize(window.innerWidth, window.innerHeight);
        menuCamera.aspect = window.innerWidth / window.innerHeight;
        menuCamera.updateProjectionMatrix();
      });
    }

    _menuLoop();
  }

  function _menuLoop() {
    if (document.getElementById('menu-screen').classList.contains('active')) {
      requestAnimationFrame(_menuLoop);
    } else return;

    const t = performance.now() * 0.001;

    for (const mc of menuCars) {
      mc.z += 0.5;
      if (mc.z > 60) mc.z = -80;
      mc.group.position.z = mc.z;
      mc.group.position.x = mc.x + Math.sin(t + mc.x) * 1;
    }

    menuCamera.position.x = Math.sin(t*0.2) * 5;
    menuCamera.position.y = 15 + Math.sin(t*0.15) * 3;
    menuCamera.lookAt(0, 2, -20);

    menuRenderer.render(menuScene, menuCamera);
  }

  /* ── Init ────────────────────────────────────────────────── */
  function init() {
    showScreen('menu-screen');
    _startMenuAnimation();
  }

  return { startRace, resume, restart, exitToMenu, init };
})();

/* ── Utility ── */
function showScreen(id) {
  document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
  document.getElementById(id).classList.add('active');
}

// Boot
window.addEventListener('DOMContentLoaded', () => Game.init());
