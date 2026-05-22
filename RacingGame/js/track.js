// ─── Track builder ───────────────────────────────────────────────────────────
// Generates 3-D track mesh, barriers, decorations and returns waypoints.

const TrackBuilder = (() => {

  // Control points for a smooth closed circuit (XZ plane, Y=0)
  const CTRL_PTS = [
    [  0,   0],
    [ 80, -20],
    [160,  40],
    [200, 120],
    [180, 220],
    [100, 280],
    [  0, 300],
    [-90, 270],
    [-160, 190],
    [-170,  90],
    [-120,  10],
    [-60,  -10],
  ];

  const TRACK_WIDTH   = 22;
  const BARRIER_H     = 1.4;
  const BARRIER_W     = 0.8;
  const TOTAL_LAPS    = 3;

  /* ── Catmull-Rom spline ─────────────────────────────────── */
  function catmullRom(p0, p1, p2, p3, t) {
    const t2 = t * t, t3 = t2 * t;
    return [
      0.5 * ((2*p1[0]) + (-p0[0]+p2[0])*t + (2*p0[0]-5*p1[0]+4*p2[0]-p3[0])*t2 + (-p0[0]+3*p1[1]-3*p2[0]+p3[0])*t3),
      0.5 * ((2*p1[1]) + (-p0[1]+p2[1])*t + (2*p0[1]-5*p1[1]+4*p2[1]-p3[1])*t2 + (-p0[1]+3*p1[1]-3*p2[1]+p3[1])*t3),
    ];
  }

  function buildSpline(pts, steps = 20) {
    const n = pts.length, out = [];
    for (let i = 0; i < n; i++) {
      const p0 = pts[(i - 1 + n) % n];
      const p1 = pts[i];
      const p2 = pts[(i + 1) % n];
      const p3 = pts[(i + 2) % n];
      for (let s = 0; s < steps; s++) {
        out.push(catmullRom(p0, p1, p2, p3, s / steps));
      }
    }
    return out;
  }

  /* ── Geometry helpers ───────────────────────────────────── */
  function addQuad(positions, normals, uvs, indices, a, b, c, d) {
    const base = positions.length / 3;
    for (const v of [a, b, c, d]) {
      positions.push(...v);
      normals.push(0, 1, 0);
    }
    const [u0, u1] = [uvs.length / 2 % 2, (uvs.length / 2 + 2) % 2];
    uvs.push(0, 0, 1, 0, 1, 1, 0, 1);
    indices.push(base, base+1, base+2, base, base+2, base+3);
  }

  /* ── Build everything ───────────────────────────────────── */
  function build(scene) {
    const spline = buildSpline(CTRL_PTS, 24);
    const N = spline.length;
    const waypoints = [];

    // Flat track surface
    const pos = [], nor = [], uv = [], idx = [];
    // Left / right barrier positions
    const barrierL = [], barrierR = [];
    // Boost strip positions (every ~100 pts for 20 pts span)
    const boostStrips = [];

    for (let i = 0; i < N; i++) {
      const cur  = spline[i];
      const next = spline[(i + 1) % N];
      const dx = next[0] - cur[0], dz = next[1] - cur[1];
      const len = Math.sqrt(dx*dx + dz*dz) || 1;
      const nx = -dz / len, nz = dx / len; // perpendicular

      const hw = TRACK_WIDTH / 2;
      const lx = cur[0] + nx * hw, lz = cur[1] + nz * hw;
      const rx = cur[0] - nx * hw, rz = cur[1] - nz * hw;

      const nlx = next[0] + nx * hw, nlz = next[1] + nz * hw;
      const nrx = next[0] - nx * hw, nrz = next[1] - nz * hw;

      waypoints.push({ x: cur[0], z: cur[1], nx, nz });
      barrierL.push([lx, lz]);
      barrierR.push([rx, rz]);

      const uLen = i / N * 40;
      const base = pos.length / 3;
      pos.push(lx,0,lz, rx,0,rz, nrx,0,nrz, nlx,0,nlz);
      nor.push(0,1,0, 0,1,0, 0,1,0, 0,1,0);
      uv.push(0,uLen, 1,uLen, 1,uLen+0.2, 0,uLen+0.2);
      idx.push(base,base+1,base+2, base,base+2,base+3);

      if (i % 80 === 40) {
        boostStrips.push({ x: cur[0], z: cur[1], nx, nz });
      }
    }

    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    geo.setAttribute('normal',   new THREE.Float32BufferAttribute(nor, 3));
    geo.setAttribute('uv',       new THREE.Float32BufferAttribute(uv,  2));
    geo.setIndex(idx);

    // Track material — dark asphalt with grid lines
    const trackMat = new THREE.MeshLambertMaterial({ color: 0x1a1a2e });
    const trackMesh = new THREE.Mesh(geo, trackMat);
    trackMesh.receiveShadow = true;
    scene.add(trackMesh);

    // Center line dashes
    _buildCenterLine(scene, spline);

    // Barriers
    _buildBarriers(scene, barrierL, barrierR, N);

    // Ground plane (grass)
    const ground = new THREE.Mesh(
      new THREE.PlaneGeometry(1200, 1200),
      new THREE.MeshLambertMaterial({ color: 0x1a3a1a })
    );
    ground.rotation.x = -Math.PI / 2;
    ground.receiveShadow = true;
    scene.add(ground);

    // Decorations
    _addTrees(scene, spline, TRACK_WIDTH);
    _addStands(scene, spline, TRACK_WIDTH);
    _addBoostStrips(scene, boostStrips, TRACK_WIDTH);
    _addStartLine(scene, spline[0], spline[1]);
    _addSkyGradient(scene);
    _addLights(scene);

    return { waypoints, spline, boostStrips, totalLaps: TOTAL_LAPS };
  }

  function _buildCenterLine(scene, spline) {
    const N = spline.length;
    const mat = new THREE.MeshBasicMaterial({ color: 0xffffff, opacity: 0.4, transparent: true });
    for (let i = 0; i < N; i += 4) {
      const c = spline[i], n = spline[(i+1)%N];
      const dx = n[0]-c[0], dz = n[1]-c[1];
      const len = Math.sqrt(dx*dx+dz*dz);
      const mesh = new THREE.Mesh(new THREE.PlaneGeometry(0.35, len), mat);
      mesh.rotation.x = -Math.PI/2;
      mesh.position.set((c[0]+n[0])/2, 0.01, (c[1]+n[1])/2);
      mesh.rotation.z = -Math.atan2(dx, dz);
      scene.add(mesh);
    }
  }

  function _buildBarriers(scene, L, R, N) {
    const matR = new THREE.MeshLambertMaterial({ color: 0xcc2222 });
    const matW = new THREE.MeshLambertMaterial({ color: 0xeeeeee });
    for (let i = 0; i < N; i++) {
      const j = (i+1) % N;
      for (const [pts, isLeft] of [[L, true],[R, false]]) {
        const a = pts[i], b = pts[j];
        const dx = b[0]-a[0], dz = b[1]-a[1];
        const len = Math.sqrt(dx*dx+dz*dz);
        const mat = (Math.floor(i/3) % 2 === 0) ? matR : matW;
        const bar = new THREE.Mesh(new THREE.BoxGeometry(len+0.1, BARRIER_H, BARRIER_W), mat);
        bar.position.set((a[0]+b[0])/2, BARRIER_H/2, (a[1]+b[1])/2);
        bar.rotation.y = -Math.atan2(dx, dz);
        bar.castShadow = true;
        scene.add(bar);
      }
    }
  }

  function _addTrees(scene, spline, tw) {
    const N = spline.length;
    const rng = mulberry32(42);
    const trunkMat = new THREE.MeshLambertMaterial({ color: 0x4d2600 });
    const leafMat1 = new THREE.MeshLambertMaterial({ color: 0x1a6b1a });
    const leafMat2 = new THREE.MeshLambertMaterial({ color: 0x267326 });

    for (let i = 0; i < N; i += 3) {
      const c = spline[i];
      const next = spline[(i+1)%N];
      const dx = next[0]-c[0], dz = next[1]-c[1];
      const l = Math.sqrt(dx*dx+dz*dz)||1;
      const nx = -dz/l, nz = dx/l;

      const sides = rng() > 0.5 ? [1,-1] : (rng() > 0.5 ? [1] : [-1]);
      for (const side of sides) {
        const dist = tw * 0.7 + rng() * 30;
        const tx = c[0] + nx * side * dist + (rng()-0.5)*8;
        const tz = c[1] + nz * side * dist + (rng()-0.5)*8;
        const h  = 4 + rng() * 8;
        const trunk = new THREE.Mesh(new THREE.CylinderGeometry(0.25, 0.4, h*0.5, 6), trunkMat);
        trunk.position.set(tx, h*0.25, tz);
        trunk.castShadow = true;
        scene.add(trunk);

        const leafGeo = new THREE.ConeGeometry(1.8 + rng()*1.5, h*0.7, 7);
        const leaf = new THREE.Mesh(leafGeo, rng()>0.5?leafMat1:leafMat2);
        leaf.position.set(tx, h*0.5+h*0.35, tz);
        leaf.castShadow = true;
        scene.add(leaf);
      }
    }
  }

  function _addStands(scene, spline, tw) {
    const standPositions = [0, 60, 120, 180, 240];
    const N = spline.length;
    const mat = new THREE.MeshLambertMaterial({ color: 0x2a2a5a });
    const seatMat = new THREE.MeshLambertMaterial({ color: 0x8822cc });
    for (const si of standPositions) {
      if (si >= N) continue;
      const c = spline[si];
      const next = spline[(si+1)%N];
      const dx = next[0]-c[0], dz = next[1]-c[1];
      const l = Math.sqrt(dx*dx+dz*dz)||1;
      const nx = -dz/l, nz = dx/l;

      for (const side of [1,-1]) {
        const dist = tw + 8;
        const sx = c[0] + nx*side*dist;
        const sz = c[1] + nz*side*dist;
        const stand = new THREE.Mesh(new THREE.BoxGeometry(18, 6, 8), mat);
        stand.position.set(sx, 3, sz);
        stand.rotation.y = -Math.atan2(dx, dz);
        stand.castShadow = true;
        scene.add(stand);

        // Seats
        const seats = new THREE.Mesh(new THREE.BoxGeometry(18, 4, 6), seatMat);
        seats.position.set(sx, 5, sz);
        seats.rotation.y = -Math.atan2(dx, dz);
        scene.add(seats);
      }
    }
  }

  function _addBoostStrips(scene, strips, tw) {
    const mat = new THREE.MeshBasicMaterial({
      color: 0x00d4ff, transparent: true, opacity: 0.55,
      side: THREE.DoubleSide
    });
    for (const s of strips) {
      const mesh = new THREE.Mesh(new THREE.PlaneGeometry(tw * 0.55, 4), mat);
      mesh.rotation.x = -Math.PI/2;
      mesh.position.set(s.x, 0.02, s.z);
      mesh.rotation.z = -Math.atan2(s.nx, s.nz);
      scene.add(mesh);
    }
  }

  function _addStartLine(scene, p0, p1) {
    const mat = new THREE.MeshBasicMaterial({ color: 0xffffff });
    const dx = p1[0]-p0[0], dz = p1[1]-p0[1];
    const len = Math.sqrt(dx*dx+dz*dz)||1;
    const nx = -dz/len, nz = dx/len;
    for (let i = -2; i <= 2; i++) {
      const sq = new THREE.Mesh(new THREE.PlaneGeometry(2.8, TRACK_WIDTH), mat);
      sq.rotation.x = -Math.PI/2;
      sq.position.set(p0[0]+nx*i*3, 0.03, p0[1]+nz*i*3);
      sq.rotation.z = -Math.atan2(dx, dz);
      scene.add(sq);
    }
    // Start arch
    const archMat = new THREE.MeshLambertMaterial({ color: 0xffcc00 });
    const archL = new THREE.Mesh(new THREE.BoxGeometry(1, 10, 1), archMat);
    archL.position.set(p0[0] + nx * (TRACK_WIDTH/2), 5, p0[1] + nz * (TRACK_WIDTH/2));
    archL.castShadow = true;
    scene.add(archL);
    const archR = new THREE.Mesh(new THREE.BoxGeometry(1, 10, 1), archMat);
    archR.position.set(p0[0] - nx * (TRACK_WIDTH/2), 5, p0[1] - nz * (TRACK_WIDTH/2));
    archR.castShadow = true;
    scene.add(archR);
    const archTop = new THREE.Mesh(new THREE.BoxGeometry(TRACK_WIDTH+4, 1, 1), archMat);
    archTop.position.set(p0[0], 10.5, p0[1]);
    archTop.rotation.y = -Math.atan2(dx, dz);
    archTop.castShadow = true;
    scene.add(archTop);
  }

  function _addSkyGradient(scene) {
    scene.background = new THREE.Color(0x0a0a20);
    scene.fog = new THREE.Fog(0x0a0a20, 200, 700);
  }

  function _addLights(scene) {
    const ambient = new THREE.AmbientLight(0x334466, 0.8);
    scene.add(ambient);

    const sun = new THREE.DirectionalLight(0xffeedd, 1.2);
    sun.position.set(80, 120, 60);
    sun.castShadow = true;
    sun.shadow.mapSize.width  = 2048;
    sun.shadow.mapSize.height = 2048;
    sun.shadow.camera.near = 1;
    sun.shadow.camera.far  = 800;
    sun.shadow.camera.left = -300;
    sun.shadow.camera.right = 300;
    sun.shadow.camera.top = 300;
    sun.shadow.camera.bottom = -300;
    scene.add(sun);

    // Track lights (lamp posts)
    for (let angle = 0; angle < 360; angle += 60) {
      const r = 130, a = angle * Math.PI/180;
      const lamp = new THREE.PointLight(0x6688ff, 0.5, 120);
      lamp.position.set(Math.cos(a)*r, 25, Math.sin(a)*r*1.5);
      scene.add(lamp);
    }

    // Neon glow on track (blue fill)
    const fillLight = new THREE.PointLight(0x0033ff, 0.3, 300);
    fillLight.position.set(0, 30, 150);
    scene.add(fillLight);
  }

  // Simple seeded RNG
  function mulberry32(seed) {
    return function() {
      seed |= 0; seed = seed + 0x6D2B79F5 | 0;
      let z = Math.imul(seed ^ seed >>> 15, 1 | seed);
      z = z + Math.imul(z ^ z >>> 7, 61 | z) ^ z;
      return ((z ^ z >>> 14) >>> 0) / 4294967296;
    };
  }

  return { build };
})();
