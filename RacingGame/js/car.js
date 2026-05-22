// ─── Player Car ──────────────────────────────────────────────────────────────

class Car {
  constructor(scene, color = 0xff2d78, isPlayer = true) {
    this.scene    = scene;
    this.color    = color;
    this.isPlayer = isPlayer;

    // Physics state
    this.pos   = new THREE.Vector3(0, 0.45, 8);
    this.vel   = new THREE.Vector3();
    this.angle = 0;           // radians — car heading (XZ plane)
    this.speed = 0;           // signed m/s
    this.steer = 0;
    this.driftAngle = 0;

    // Nitro
    this.nitro    = 1.0;      // 0..1
    this.nitroOn  = false;
    this.nitroRecharge = false;

    // Lap tracking
    this.lap           = 1;
    this.waypointIdx   = 0;
    this.lapStartTime  = 0;
    this.lapTimes      = [];
    this.bestLap       = Infinity;
    this.raceFinished  = false;
    this.lastCheckpoint = -1;

    this._buildMesh();
    this._buildWheels();
    this._buildLights();
    this._buildShadowBlob();

    // Wheel rotation tracking
    this._wheelRot = 0;
  }

  /* ── Mesh ──────────────────────────────────────────────── */
  _buildMesh() {
    this.group = new THREE.Group();
    this.scene.add(this.group);

    // Body
    const bodyGeo = new THREE.BoxGeometry(2.2, 0.7, 4.5);
    const bodyMat = new THREE.MeshLambertMaterial({ color: this.color });
    this.body = new THREE.Mesh(bodyGeo, bodyMat);
    this.body.position.y = 0.55;
    this.body.castShadow = true;
    this.group.add(this.body);

    // Cockpit / cabin
    const cabinGeo = new THREE.BoxGeometry(1.6, 0.55, 2.0);
    const cabinMat = new THREE.MeshLambertMaterial({ color: 0x111122 });
    const cabin = new THREE.Mesh(cabinGeo, cabinMat);
    cabin.position.set(0, 1.05, -0.2);
    cabin.castShadow = true;
    this.group.add(cabin);

    // Windshield tint
    const windshieldGeo = new THREE.BoxGeometry(1.55, 0.45, 0.08);
    const windshieldMat = new THREE.MeshLambertMaterial({ color: 0x112244, transparent: true, opacity: 0.7 });
    const windshield = new THREE.Mesh(windshieldGeo, windshieldMat);
    windshield.position.set(0, 1.08, 0.72);
    this.group.add(windshield);

    // Spoiler
    const spoilerGeo = new THREE.BoxGeometry(2.4, 0.12, 0.5);
    const spoilerMat = new THREE.MeshLambertMaterial({ color: 0x111111 });
    const spoiler = new THREE.Mesh(spoilerGeo, spoilerMat);
    spoiler.position.set(0, 1.2, -2.3);
    spoiler.castShadow = true;
    this.group.add(spoiler);

    // Spoiler pillars
    for (const x of [-0.85, 0.85]) {
      const pillar = new THREE.Mesh(new THREE.BoxGeometry(0.1, 0.5, 0.1), spoilerMat);
      pillar.position.set(x, 0.95, -2.3);
      this.group.add(pillar);
    }

    // Front diffuser
    const diffGeo = new THREE.BoxGeometry(2.0, 0.18, 0.6);
    const diffMat = new THREE.MeshLambertMaterial({ color: 0x222222 });
    const diffuser = new THREE.Mesh(diffGeo, diffMat);
    diffuser.position.set(0, 0.3, 2.35);
    this.group.add(diffuser);

    // Side skirts
    for (const x of [-1.18, 1.18]) {
      const skirt = new THREE.Mesh(new THREE.BoxGeometry(0.12, 0.25, 3.8), diffMat);
      skirt.position.set(x, 0.3, 0);
      this.group.add(skirt);
    }

    // Exhaust pipes
    this.exhaustL = new THREE.Group();
    this.exhaustR = new THREE.Group();
    for (const [g, x] of [[this.exhaustL, -0.55], [this.exhaustR, 0.55]]) {
      const pipe = new THREE.Mesh(new THREE.CylinderGeometry(0.08, 0.1, 0.4, 8), new THREE.MeshLambertMaterial({ color: 0x888888 }));
      pipe.rotation.x = Math.PI/2;
      pipe.position.set(x, 0.4, -2.35);
      g.add(pipe);
      this.group.add(g);
    }
  }

  _buildWheels() {
    this.wheels = [];
    const wheelGeo  = new THREE.CylinderGeometry(0.4, 0.4, 0.35, 14);
    const tireMat   = new THREE.MeshLambertMaterial({ color: 0x111111 });
    const rimMat    = new THREE.MeshLambertMaterial({ color: 0xcccccc });
    const rimGeo    = new THREE.CylinderGeometry(0.22, 0.22, 0.36, 8);

    const positions = [[-1.25, 0.4, 1.5], [1.25, 0.4, 1.5], [-1.25, 0.4, -1.6], [1.25, 0.4, -1.6]];
    for (const [x, y, z] of positions) {
      const wg = new THREE.Group();
      const tire = new THREE.Mesh(wheelGeo, tireMat);
      tire.rotation.z = Math.PI/2;
      tire.castShadow = true;
      const rim = new THREE.Mesh(rimGeo, rimMat);
      rim.rotation.z = Math.PI/2;
      wg.add(tire); wg.add(rim);
      wg.position.set(x, y, z);
      this.wheels.push(wg);
      this.group.add(wg);
    }
  }

  _buildLights() {
    // Headlights
    const hlMat = new THREE.MeshBasicMaterial({ color: 0xffffff });
    for (const x of [-0.65, 0.65]) {
      const hl = new THREE.Mesh(new THREE.BoxGeometry(0.4, 0.18, 0.08), hlMat);
      hl.position.set(x, 0.62, 2.27);
      this.group.add(hl);
    }
    // Taillights
    const tlMat = new THREE.MeshBasicMaterial({ color: 0xff2200 });
    for (const x of [-0.7, 0.7]) {
      const tl = new THREE.Mesh(new THREE.BoxGeometry(0.35, 0.15, 0.08), tlMat);
      tl.position.set(x, 0.62, -2.27);
      this.group.add(tl);
    }

    // Point light for headlights (player only)
    if (this.isPlayer) {
      this.headLight = new THREE.SpotLight(0xffffff, 1.5, 80, Math.PI/6, 0.5);
      this.headLight.position.set(0, 2, 3);
      this.headLight.target.position.set(0, 0, 20);
      this.group.add(this.headLight);
      this.group.add(this.headLight.target);
    }
  }

  _buildShadowBlob() {
    const geo = new THREE.PlaneGeometry(3.5, 5.5);
    const mat = new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.35 });
    this.shadowBlob = new THREE.Mesh(geo, mat);
    this.shadowBlob.rotation.x = -Math.PI/2;
    this.shadowBlob.position.y = 0.01;
    this.group.add(this.shadowBlob);
  }

  /* ── Update ────────────────────────────────────────────── */
  update(dt, input, waypoints) {
    const MAX_SPEED    = 42;
    const ACCEL        = 28;
    const BRAKE_FORCE  = 40;
    const FRICTION     = 12;
    const STEER_SPEED  = 2.8;
    const NITRO_BOOST  = 18;

    // Nitro
    if (input.nitro && this.nitro > 0 && !this.nitroRecharge) {
      this.nitroOn = true;
      this.nitro = Math.max(0, this.nitro - dt * 0.55);
      if (this.nitro === 0) this.nitroRecharge = true;
    } else {
      this.nitroOn = false;
    }
    if (this.nitroRecharge) {
      this.nitro = Math.min(1, this.nitro + dt * 0.18);
      if (this.nitro >= 1) this.nitroRecharge = false;
    } else if (!input.nitro) {
      this.nitro = Math.min(1, this.nitro + dt * 0.12);
    }

    const topSpeed = MAX_SPEED + (this.nitroOn ? NITRO_BOOST : 0);

    // Acceleration / braking
    if (input.throttle) {
      this.speed += ACCEL * dt;
    } else if (input.brake) {
      if (this.speed > 0) this.speed -= BRAKE_FORCE * dt;
      else                 this.speed -= ACCEL * 0.5 * dt;
    } else {
      if (this.speed > 0) this.speed -= FRICTION * dt;
      else if (this.speed < 0) this.speed += FRICTION * dt;
    }

    this.speed = Math.max(-12, Math.min(topSpeed, this.speed));
    if (Math.abs(this.speed) < 0.05) this.speed = 0;

    // Boost strip check
    if (waypoints) this._checkBoostStrips(waypoints);

    // Steering
    if (Math.abs(this.speed) > 0.5) {
      const steerFactor = 1 - Math.abs(this.speed) / (topSpeed * 1.6);
      const targetSteer = (input.steerLeft ? -1 : input.steerRight ? 1 : 0);
      this.steer += (targetSteer - this.steer) * Math.min(1, dt * 8);
      this.angle += this.steer * STEER_SPEED * steerFactor * dt * Math.sign(this.speed);
    } else {
      this.steer *= 0.9;
    }

    // Drift
    if (Math.abs(this.speed) > 15 && Math.abs(this.steer) > 0.5) {
      this.driftAngle += (this.steer * 0.15 - this.driftAngle) * dt * 4;
    } else {
      this.driftAngle *= (1 - dt * 5);
    }

    // Move
    const heading = this.angle + this.driftAngle * 0.3;
    this.pos.x += Math.sin(heading) * this.speed * dt;
    this.pos.z += Math.cos(heading) * this.speed * dt;

    // Apply to group
    this.group.position.copy(this.pos);
    this.group.rotation.y = this.angle;

    // Wheel rotation
    this._wheelRot += this.speed * dt * 1.5;
    for (let i = 0; i < 4; i++) {
      this.wheels[i].children[0].rotation.x = this._wheelRot;
      this.wheels[i].children[1].rotation.x = this._wheelRot;
    }
    // Front wheel steering
    this.wheels[0].rotation.y = this.steer * 0.45;
    this.wheels[1].rotation.y = this.steer * 0.45;

    // Chassis tilt
    this.body.rotation.x = -this.speed * 0.006;
    this.body.rotation.z = -this.steer * this.speed * 0.008;

    // Waypoint progression
    if (waypoints) this._progressWaypoints(waypoints);
  }

  _checkBoostStrips(trackData) {
    if (!trackData.boostStrips) return;
    for (const s of trackData.boostStrips) {
      const dx = this.pos.x - s.x, dz = this.pos.z - s.z;
      if (Math.sqrt(dx*dx+dz*dz) < 6) {
        this.speed = Math.min(this.speed + 8, 55);
        break;
      }
    }
  }

  _progressWaypoints(trackData) {
    const wps = trackData.waypoints;
    const next = wps[(this.waypointIdx + 1) % wps.length];
    const dx = this.pos.x - next.x, dz = this.pos.z - next.z;
    if (Math.sqrt(dx*dx+dz*dz) < 14) {
      this.waypointIdx = (this.waypointIdx + 1) % wps.length;
      if (this.waypointIdx === 0) {
        this._completeLap();
      }
    }
  }

  _completeLap() {
    const now = performance.now();
    if (this.lapStartTime > 0) {
      const lapTime = now - this.lapStartTime;
      this.lapTimes.push(lapTime);
      if (lapTime < this.bestLap) this.bestLap = lapTime;
    }
    this.lapStartTime = now;
    if (this.lap < 3) this.lap++;
    else this.raceFinished = true;
  }

  getSpeedKmh() { return Math.abs(this.speed) * 3.6; }

  getGear() {
    const s = Math.abs(this.speed);
    if (s < 8)  return 1;
    if (s < 16) return 2;
    if (s < 24) return 3;
    if (s < 32) return 4;
    if (s < 40) return 5;
    return 6;
  }

  reset(pos, angle) {
    this.pos.set(pos.x, 0.45, pos.z);
    this.angle    = angle || 0;
    this.speed    = 0;
    this.steer    = 0;
    this.driftAngle = 0;
    this.group.position.copy(this.pos);
    this.group.rotation.y = this.angle;
  }

  dispose() {
    this.scene.remove(this.group);
  }
}
