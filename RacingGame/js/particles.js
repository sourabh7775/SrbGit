// ─── Particle Systems ─────────────────────────────────────────────────────────

class ParticleSystem {
  constructor(scene) {
    this.scene    = scene;
    this.pools    = {};
    this.active   = [];

    this._initPools();
    this._buildTireMarkSystem();
  }

  _initPools() {
    // Exhaust smoke
    this._makePool('exhaust', 200, {
      size: 0.6, color: 0x888888, opacity: 0.4,
      life: 0.8, spread: 0.3, upward: 1.5,
    });

    // Nitro fire
    this._makePool('nitro', 120, {
      size: 0.5, color: 0x00aaff, opacity: 0.7,
      life: 0.35, spread: 0.15, upward: 0.5,
    });

    // Tire smoke
    this._makePool('tire', 160, {
      size: 0.9, color: 0xaaaaaa, opacity: 0.5,
      life: 1.2, spread: 0.5, upward: 0.8,
    });

    // Spark
    this._makePool('spark', 60, {
      size: 0.15, color: 0xffaa00, opacity: 1.0,
      life: 0.4, spread: 1.0, upward: 2.0,
    });

    // Boost flash
    this._makePool('boost', 80, {
      size: 0.7, color: 0x00d4ff, opacity: 0.8,
      life: 0.5, spread: 0.8, upward: 1.0,
    });
  }

  _makePool(name, count, cfg) {
    const geo = new THREE.BufferGeometry();
    const pos = new Float32Array(count * 3);
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));

    const mat = new THREE.PointsMaterial({
      size: cfg.size, color: cfg.color, transparent: true,
      opacity: cfg.opacity, depthWrite: false, sizeAttenuation: true,
    });

    const pts = new THREE.Points(geo, mat);
    pts.frustumCulled = false;
    this.scene.add(pts);

    this.pools[name] = {
      pts, geo, mat, cfg, count,
      particles: Array.from({ length: count }, () => ({
        active: false, x:0, y:0, z:0, vx:0, vy:0, vz:0, life:0, maxLife:1
      })),
      idx: 0,
    };
  }

  _buildTireMarkSystem() {
    // Simple decal planes for tire marks (limited pool)
    this.tireMarks = [];
    this.tireMarkIdx = 0;
    const mat = new THREE.MeshBasicMaterial({ color: 0x111111, transparent: true, opacity: 0.6, depthWrite: false });
    for (let i = 0; i < 120; i++) {
      const m = new THREE.Mesh(new THREE.PlaneGeometry(0.5, 1.2), mat.clone());
      m.rotation.x = -Math.PI/2;
      m.position.y = 0.005;
      m.visible = false;
      this.scene.add(m);
      this.tireMarks.push(m);
    }
  }

  /* ── Emit ──────────────────────────────────────────────── */
  emit(name, x, y, z, count = 3) {
    const pool = this.pools[name];
    if (!pool) return;
    const cfg = pool.cfg;
    for (let i = 0; i < count; i++) {
      const p = pool.particles[pool.idx % pool.count];
      pool.idx++;
      p.active  = true;
      p.x = x + (Math.random()-0.5)*0.3;
      p.y = y + (Math.random()-0.5)*0.2;
      p.z = z + (Math.random()-0.5)*0.3;
      p.vx = (Math.random()-0.5) * cfg.spread;
      p.vy = Math.random() * cfg.upward;
      p.vz = (Math.random()-0.5) * cfg.spread;
      p.life    = cfg.life;
      p.maxLife = cfg.life;
    }
  }

  addTireMark(x, z, angle) {
    const m = this.tireMarks[this.tireMarkIdx % this.tireMarks.length];
    this.tireMarkIdx++;
    m.position.x = x;
    m.position.z = z;
    m.rotation.z = angle;
    m.material.opacity = 0.55;
    m.visible = true;
  }

  /* ── Update ────────────────────────────────────────────── */
  update(dt) {
    for (const name in this.pools) {
      const pool = this.pools[name];
      const positions = pool.geo.attributes.position.array;
      let visible = 0;

      for (let i = 0; i < pool.count; i++) {
        const p = pool.particles[i];
        if (!p.active) {
          positions[i*3]   = 99999;
          positions[i*3+1] = 99999;
          positions[i*3+2] = 99999;
          continue;
        }
        p.life -= dt;
        if (p.life <= 0) {
          p.active = false;
          positions[i*3] = 99999;
          continue;
        }
        p.x += p.vx * dt;
        p.y += p.vy * dt;
        p.z += p.vz * dt;
        p.vy -= 2 * dt; // slight gravity
        positions[i*3]   = p.x;
        positions[i*3+1] = p.y;
        positions[i*3+2] = p.z;
        visible++;
      }
      pool.geo.attributes.position.needsUpdate = true;
      // Fade opacity
      pool.mat.opacity = pool.cfg.opacity * (visible / Math.max(1, pool.count) * 0.3 + 0.7);
    }

    // Fade tire marks
    for (const m of this.tireMarks) {
      if (m.visible && m.material.opacity > 0.01) {
        m.material.opacity -= dt * 0.04;
      } else if (m.material.opacity <= 0.01) {
        m.visible = false;
      }
    }
  }

  /* ── Per-car emission helpers ──────────────────────────── */
  emitCarEffects(car, dt) {
    const ex = [car.pos.x - Math.sin(car.angle)*2.2, car.pos.z - Math.cos(car.angle)*2.2];

    // Exhaust puffs
    if (car.speed > 2) {
      if (Math.random() < dt * 18) {
        this.emit('exhaust', ex[0] - 0.55, 0.38, ex[1], 1);
        this.emit('exhaust', ex[0] + 0.55, 0.38, ex[1], 1);
      }
    }

    // Nitro fire
    if (car.nitroOn) {
      this.emit('nitro', ex[0] - 0.55, 0.38, ex[1], 2);
      this.emit('nitro', ex[0] + 0.55, 0.38, ex[1], 2);
      this.emit('boost', car.pos.x, 0.3, car.pos.z, 1);
    }

    // Tire smoke on drift or hard braking
    const drifting = Math.abs(car.driftAngle) > 0.1 || (car.steer !== undefined && Math.abs(car.steer) > 0.7 && car.speed > 12);
    if (drifting) {
      const wheelL = [car.pos.x - Math.sin(car.angle+Math.PI/2)*1.25, car.pos.z - Math.cos(car.angle+Math.PI/2)*1.25];
      const wheelR = [car.pos.x + Math.sin(car.angle+Math.PI/2)*1.25, car.pos.z + Math.cos(car.angle+Math.PI/2)*1.25];
      this.emit('tire', wheelL[0], 0.15, wheelL[1], 2);
      this.emit('tire', wheelR[0], 0.15, wheelR[1], 2);
      if (Math.random() < dt * 12) {
        this.addTireMark(car.pos.x, car.pos.z, car.angle);
      }
    }

    // Sparks on high speed
    if (car.speed > 38 && Math.random() < dt * 5) {
      this.emit('spark', ex[0], 0.25, ex[1], 3);
    }
  }
}
