// ─── Player Car ───────────────────────────────────────────────────────────────

class Car {
  constructor(scene, color = 0xff2d9e, isPlayer = true) {
    this.scene    = scene;
    this.color    = color;
    this.isPlayer = isPlayer;

    this.pos   = new THREE.Vector3(0, 0.5, 0);
    this.vel   = new THREE.Vector3();
    this.angle = 0;
    this.speed = 0;
    this.steer = 0;
    this.driftAngle = 0;
    this.drifting = false;

    this.nitro    = 1.0;
    this.nitroOn  = false;
    this.nitroLocked = false;

    this.lap          = 1;
    this.waypointIdx  = 0;
    this.lapStartTime = 0;
    this.lapTimes     = [];
    this.bestLap      = Infinity;
    this.raceFinished = false;

    this._wheelRot = 0;
    this._buildMesh();
  }

  /* ── Mesh: proper low-poly sports car ─────────────── */
  _buildMesh() {
    this.group = new THREE.Group();
    this.scene.add(this.group);

    const mat = (c, em=0, ei=0) => new THREE.MeshLambertMaterial({
      color:c, emissive:em, emissiveIntensity:ei,
    });

    // ── Lower body (wide, flat) ──
    const lbGeo = new THREE.BufferGeometry();
    // 8 vertices for a wedge: wide at rear, slightly narrower at front
    const lbV = new Float32Array([
      // bottom
      -1.1,0,-2.2,  1.1,0,-2.2,  1.2,0,1.8,  -1.2,0,1.8,
      // top
      -1.0,.5,-2.1,  1.0,.5,-2.1,  1.15,.45,1.7,  -1.15,.45,1.7,
    ]);
    const lbI = [
      0,1,5, 0,5,4,   // front
      2,3,7, 2,7,6,   // rear
      0,3,7, 0,7,4,   // left
      1,2,6, 1,6,5,   // right
      4,5,6, 4,6,7,   // top
      0,1,2, 0,2,3,   // bottom
    ];
    lbGeo.setAttribute('position',new THREE.Float32BufferAttribute(lbV,3));
    lbGeo.setIndex(lbI); lbGeo.computeVertexNormals();
    const lb = new THREE.Mesh(lbGeo, mat(this.color));
    lb.castShadow = true; this.group.add(lb);

    // ── Sidepods / wide body kit ──
    for(const x of [-1.32, 1.32]){
      const pod = new THREE.Mesh(new THREE.BoxGeometry(.3,.3,2.8), mat(0x111111));
      pod.position.set(x,.25,-.2);
      this.group.add(pod);
    }

    // ── Cockpit / cabin (tapered roof) ──
    const cabinGeo = new THREE.BufferGeometry();
    const cabV = new Float32Array([
      -0.75,.5,-.8,  0.75,.5,-.8,  0.75,.5,.8,  -0.75,.5,.8,  // bottom
      -0.45,1.1,-.5,  0.45,1.1,-.5,  0.45,1.1,.5,  -0.45,1.1,.5, // top
    ]);
    cabinGeo.setAttribute('position',new THREE.Float32BufferAttribute(cabV,3));
    cabinGeo.setIndex([0,1,5,0,5,4, 2,3,7,2,7,6, 0,3,7,0,7,4, 1,2,6,1,6,5, 4,5,6,4,6,7, 0,1,2,0,2,3]);
    cabinGeo.computeVertexNormals();
    const cabin = new THREE.Mesh(cabinGeo, mat(0x080814));
    cabin.castShadow=true; this.group.add(cabin);

    // Windshield
    const ws = new THREE.Mesh(new THREE.PlaneGeometry(1.3,.7),
      new THREE.MeshLambertMaterial({color:0x1133aa,transparent:true,opacity:.55,side:THREE.DoubleSide}));
    ws.position.set(0,.82,.81); ws.rotation.x=-.35;
    this.group.add(ws);

    // ── Front splitter ──
    const splitter = new THREE.Mesh(new THREE.BoxGeometry(2.4,.08,1.0), mat(0x111111));
    splitter.position.set(0,.06,1.9); this.group.add(splitter);

    // ── Rear wing ──
    const wing = new THREE.Mesh(new THREE.BoxGeometry(2.5,.1,.7), mat(0x111111));
    wing.position.set(0,1.0,-2.1); this.group.add(wing);
    for(const x of [-.9,.9]){
      const pylon = new THREE.Mesh(new THREE.BoxGeometry(.12,.6,.1), mat(0x111111));
      pylon.position.set(x,.7,-2.1); this.group.add(pylon);
    }

    // ── Underglow (neon emissive strip) ──
    const glow = new THREE.Mesh(new THREE.BoxGeometry(2.1,.05,4.2),
      mat(this.color, this.color, 1.2));
    glow.position.set(0,.02,0);
    this.group.add(glow);

    // ── Wheels ──
    this.wheels = [];
    this.steerGroups = [];
    const tireMat  = mat(0x111111);
    const rimMat   = mat(0xbbbbbb, this.color, 0.4);
    const brakeMat = mat(this.color, this.color, 0.8);
    const wPositions = [[-1.25,.38,1.5],[1.25,.38,1.5],[-1.25,.38,-1.65],[1.25,.38,-1.65]];
    for(let i=0;i<4;i++){
      const [x,y,z] = wPositions[i];
      const sg = new THREE.Group();
      const wg = new THREE.Group();
      const tire = new THREE.Mesh(new THREE.CylinderGeometry(.38,.38,.32,16), tireMat);
      tire.rotation.z = Math.PI/2; tire.castShadow=true;
      // Rim
      const rim = new THREE.Mesh(new THREE.CylinderGeometry(.22,.22,.33,8), rimMat);
      rim.rotation.z = Math.PI/2;
      // Brake disc
      const brake = new THREE.Mesh(new THREE.CylinderGeometry(.18,.18,.05,8), brakeMat);
      brake.rotation.z = Math.PI/2; brake.position.x = x>0 ? -.18 : .18;
      wg.add(tire); wg.add(rim); wg.add(brake);
      sg.add(wg);
      sg.position.set(x,y,z);
      this.wheels.push(wg);
      this.steerGroups.push(sg);
      this.group.add(sg);
    }

    // ── Headlights ──
    const hlMat = new THREE.MeshBasicMaterial({color:0xffffff});
    const tlMat = new THREE.MeshBasicMaterial({color:0xff1100, blending:THREE.AdditiveBlending});
    for(const x of [-.6,.6]){
      const hl=new THREE.Mesh(new THREE.BoxGeometry(.35,.14,.05),hlMat);
      hl.position.set(x,.45,2.2); this.group.add(hl);
      const tl=new THREE.Mesh(new THREE.BoxGeometry(.3,.12,.05),tlMat);
      tl.position.set(x,.5,-2.22); this.group.add(tl);
    }

    // ── Exhaust glow ──
    this.exhaustL = new THREE.Mesh(new THREE.CylinderGeometry(.08,.1,.3,8),
      mat(0x888888)); this.exhaustL.rotation.x=Math.PI/2;
    this.exhaustL.position.set(-.5,.35,-2.22); this.group.add(this.exhaustL);
    this.exhaustR = this.exhaustL.clone();
    this.exhaustR.position.set(.5,.35,-2.22); this.group.add(this.exhaustR);
  }

  /* ── Physics update ───────────────────────────────── */
  update(dt, input, trackData) {
    const ACCEL     = 38;
    const BRAKE     = 50;
    const FRICTION  = 14;
    const MAX_SPEED = 52;
    const NITRO_ADD = 22;
    const STEER_MAX = 3.0;

    // Nitro
    if (input.nitro && this.nitro > 0 && !this.nitroLocked) {
      this.nitroOn = true;
      this.nitro = Math.max(0, this.nitro - dt * 0.5);
      if (this.nitro === 0) this.nitroLocked = true;
    } else {
      this.nitroOn = false;
    }
    if (this.nitroLocked) {
      this.nitro = Math.min(1, this.nitro + dt * 0.15);
      if (this.nitro >= 1) this.nitroLocked = false;
    } else if (!this.nitroOn) {
      this.nitro = Math.min(1, this.nitro + dt * 0.08);
    }

    const topSpeed = MAX_SPEED + (this.nitroOn ? NITRO_ADD : 0);

    // Throttle / brake
    if (input.throttle)      this.speed += ACCEL * dt;
    else if (input.brake) {
      if (this.speed > 1)    this.speed -= BRAKE * dt;
      else                   this.speed -= ACCEL * 0.4 * dt;
    } else {
      this.speed -= Math.sign(this.speed) * FRICTION * dt;
      if (Math.abs(this.speed) < 0.1) this.speed = 0;
    }
    this.speed = Math.max(-14, Math.min(topSpeed, this.speed));

    // Steering — tighter at high speed
    const rawSteer = input.steerLeft ? -1 : input.steerRight ? 1 : 0;
    const steerRate = 5.5 / (1 + Math.abs(this.speed) * 0.04);
    this.steer += (rawSteer - this.steer) * Math.min(1, dt * steerRate);

    if (Math.abs(this.speed) > 0.5) {
      const steerEffect = STEER_MAX * (1 - Math.abs(this.speed) / (topSpeed * 2.2));
      this.angle += this.steer * steerEffect * dt * Math.sign(this.speed);
    }

    // Drift
    const driftThreshold = 0.55;
    this.drifting = Math.abs(this.steer) > driftThreshold && Math.abs(this.speed) > 18;
    if (this.drifting) {
      this.driftAngle += (this.steer * 0.18 - this.driftAngle) * dt * 5;
      // Nitro recharge while drifting
      if (!this.nitroLocked) this.nitro = Math.min(1, this.nitro + dt * 0.25);
    } else {
      this.driftAngle *= (1 - dt * 8);
    }

    // Position
    const heading = this.angle + this.driftAngle * 0.28;
    this.pos.x += Math.sin(heading) * this.speed * dt;
    this.pos.z += Math.cos(heading) * this.speed * dt;
    this.pos.y = 0.5;

    // Boost pads
    if (trackData) this._checkBoostStrips(trackData);

    // Wall collision
    if (trackData) this._wallCollision(trackData);

    // Apply to group
    this.group.position.copy(this.pos);
    this.group.rotation.y = this.angle;

    // Wheel animation
    this._wheelRot += this.speed * dt * 1.8;
    for (let i = 0; i < 4; i++) {
      this.wheels[i].rotation.x = this._wheelRot;
      if (i < 2) this.steerGroups[i].rotation.y = this.steer * 0.4;
    }

    // Body tilt
    this.group.children[0].rotation.z = -this.steer * this.speed * 0.007;
    this.group.children[0].rotation.x = -this.speed * 0.005;

    // Waypoint progress
    if (trackData) this._progressWaypoints(trackData);
  }

  _checkBoostStrips(td) {
    for (const s of td.boostStrips) {
      const dx = this.pos.x - s.x, dz = this.pos.z - s.z;
      if (dx*dx + dz*dz < 64) {
        if (this.speed < 60) { this.speed = Math.min(60, this.speed + 12); AudioEngine.playBoostHit(); }
        break;
      }
    }
  }

  _wallCollision(td) {
    const wps = td.waypoints;
    const N   = wps.length;
    // Find closest waypoint
    let minD = Infinity, minI = this.waypointIdx;
    for (let off = -4; off <= 4; off++) {
      const i = (this.waypointIdx + off + N) % N;
      const dx = this.pos.x - wps[i].x, dz = this.pos.z - wps[i].z;
      const d = dx*dx + dz*dz;
      if (d < minD) { minD = d; minI = i; }
    }
    const wp = wps[minI];
    // Lateral offset from track center
    const dx = this.pos.x - wp.x, dz = this.pos.z - wp.z;
    const lateral = dx * wp.nx + dz * wp.nz;
    if (Math.abs(lateral) > td.TW - 1.2) {
      // Push car back inside + damp speed
      const push = (Math.abs(lateral) - (td.TW - 1.2)) * Math.sign(lateral);
      this.pos.x -= wp.nx * push;
      this.pos.z -= wp.nz * push;
      this.speed *= 0.55;
      this.steer = 0;
    }
  }

  _progressWaypoints(td) {
    const wps = td.waypoints;
    const N   = wps.length;
    const next = wps[(this.waypointIdx + 1) % N];
    const dx = this.pos.x - next.x, dz = this.pos.z - next.z;
    if (dx*dx + dz*dz < 180) {
      this.waypointIdx = (this.waypointIdx + 1) % N;
      if (this.waypointIdx === 0) this._finishLap();
    }
  }

  _finishLap() {
    const now = performance.now();
    if (this.lapStartTime > 0) {
      const t = now - this.lapStartTime;
      this.lapTimes.push(t);
      if (t < this.bestLap) this.bestLap = t;
      if (this.isPlayer) AudioEngine.playLapDing();
    }
    this.lapStartTime = now;
    if (this.lap < 3) this.lap++;
    else this.raceFinished = true;
  }

  getSpeedKmh() { return Math.abs(this.speed) * 3.6; }

  getGear() {
    const s = Math.abs(this.speed);
    return s<8?1:s<17?2:s<26?3:s<35?4:s<44?5:6;
  }

  reset(wp, wpNext) {
    const ang = Math.atan2(wpNext.x - wp.x, wpNext.z - wp.z);
    this.pos.set(wp.x, 0.5, wp.z);
    this.angle = ang; this.speed = 0; this.steer = 0; this.driftAngle = 0;
    this.group.position.copy(this.pos);
    this.group.rotation.y = ang;
  }

  dispose() { this.scene.remove(this.group); }
}
