// ─── Particle System ──────────────────────────────────────────────────────────

class ParticleSystem {
  constructor(scene) {
    this.scene = scene;
    this.pools = {};
    this._tireMarks = [];
    this._tireIdx   = 0;
    this._initPools();
    this._initTireMarks();
  }

  _initPools() {
    // Exhaust smoke (dark grey)
    this._pool('exhaust', 300, { size:0.9, color:0x555577, life:1.0, spread:0.4, up:1.8, blend:false });
    // Nitro jet (bright cyan-blue)
    this._pool('nitro',   150, { size:0.6, color:0x00f5ff, life:0.3, spread:0.1, up:0.4, blend:true  });
    // Drift smoke (magenta tinted)
    this._pool('drift',   220, { size:1.3, color:0xaa44bb, life:1.6, spread:0.7, up:1.0, blend:false });
    // Sparks
    this._pool('spark',    80, { size:0.2, color:0xffaa00, life:0.5, spread:1.2, up:3.0, blend:true  });
    // Boost flash rings
    this._pool('boostRing',40, { size:1.2, color:0x00f5ff, life:0.4, spread:2.0, up:0.5, blend:true  });
    // Dust
    this._pool('dust',    100, { size:0.8, color:0x886644, life:0.9, spread:0.5, up:0.6, blend:false });
  }

  _pool(name, count, cfg) {
    const geo = new THREE.BufferGeometry();
    const pos = new Float32Array(count * 3).fill(99999);
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    const mat = new THREE.PointsMaterial({
      size: cfg.size, color: cfg.color,
      transparent: true, opacity: 0.7, depthWrite: false,
      blending: cfg.blend ? THREE.AdditiveBlending : THREE.NormalBlending,
      sizeAttenuation: true,
    });
    const pts = new THREE.Points(geo, mat);
    pts.frustumCulled = false;
    this.scene.add(pts);
    this.pools[name] = {
      pts, geo, mat, cfg, count,
      p: Array.from({length: count}, () => ({active:false,x:0,y:0,z:0,vx:0,vy:0,vz:0,life:0,maxLife:1})),
      idx: 0,
    };
  }

  _initTireMarks() {
    const mat = new THREE.MeshBasicMaterial({
      color:0x220033, transparent:true, opacity:0.7,
      depthWrite:false, side:THREE.DoubleSide,
    });
    for (let i = 0; i < 180; i++) {
      const m = new THREE.Mesh(new THREE.PlaneGeometry(0.55, 1.4), mat.clone());
      m.rotation.x = -Math.PI/2; m.position.y = 0.006; m.visible = false;
      this.scene.add(m);
      this._tireMarks.push(m);
    }
  }

  emit(name, x, y, z, count = 3) {
    const pool = this.pools[name]; if (!pool) return;
    const {cfg, p} = pool;
    for (let i = 0; i < count; i++) {
      const pt = p[pool.idx++ % pool.count];
      pt.active = true;
      pt.x = x + (Math.random()-.5)*.5;
      pt.y = y;
      pt.z = z + (Math.random()-.5)*.5;
      pt.vx = (Math.random()-.5)*cfg.spread;
      pt.vy = Math.random()*cfg.up + 0.3;
      pt.vz = (Math.random()-.5)*cfg.spread;
      pt.life = cfg.life; pt.maxLife = cfg.life;
    }
  }

  addTireMark(x, z, angle) {
    const m = this._tireMarks[this._tireIdx++ % this._tireMarks.length];
    m.position.x = x; m.position.z = z; m.rotation.z = angle;
    m.material.opacity = 0.65; m.visible = true;
  }

  update(dt) {
    for (const name in this.pools) {
      const {geo, cfg, p, count} = this.pools[name];
      const pos = geo.attributes.position.array;
      for (let i = 0; i < count; i++) {
        const pt = p[i];
        if (!pt.active) { pos[i*3]=pos[i*3+1]=pos[i*3+2]=99999; continue; }
        pt.life -= dt;
        if (pt.life <= 0) { pt.active = false; pos[i*3]=99999; continue; }
        pt.x += pt.vx*dt; pt.y += pt.vy*dt; pt.z += pt.vz*dt;
        pt.vy -= 3*dt;
        pos[i*3]=pt.x; pos[i*3+1]=pt.y; pos[i*3+2]=pt.z;
      }
      geo.attributes.position.needsUpdate = true;
    }
    // Fade tire marks
    for (const m of this._tireMarks) {
      if (m.visible) { m.material.opacity -= dt*0.03; if (m.material.opacity<=0) m.visible=false; }
    }
  }

  emitCarFX(car, dt) {
    const rearX = car.pos.x - Math.sin(car.angle)*2.3;
    const rearZ = car.pos.z - Math.cos(car.angle)*2.3;
    const spd   = car.getSpeedKmh();

    // Exhaust smoke
    if (spd > 5 && Math.random() < dt * 22) {
      this.emit('exhaust', rearX-.5, .38, rearZ, 1);
      this.emit('exhaust', rearX+.5, .38, rearZ, 1);
    }

    // Nitro jet (cyan fire out of exhausts)
    if (car.nitroOn) {
      this.emit('nitro', rearX-.5, .36, rearZ, 3);
      this.emit('nitro', rearX+.5, .36, rearZ, 3);
      this.emit('boostRing', rearX, .3, rearZ, 1);
      if (spd > 60) this.emit('spark', rearX, .25, rearZ, 2);
    }

    // Drift smoke + tire marks
    if (car.drifting) {
      const wL = [car.pos.x - Math.sin(car.angle+Math.PI/2)*1.25,
                   car.pos.z - Math.cos(car.angle+Math.PI/2)*1.25];
      const wR = [car.pos.x + Math.sin(car.angle+Math.PI/2)*1.25,
                   car.pos.z + Math.cos(car.angle+Math.PI/2)*1.25];
      this.emit('drift', wL[0], .16, wL[1], 2);
      this.emit('drift', wR[0], .16, wR[1], 2);
      if (Math.random() < dt * 16) {
        this.addTireMark(wL[0], wL[1], car.angle);
        this.addTireMark(wR[0], wR[1], car.angle);
      }
    }

    // High-speed sparks
    if (spd > 120 && Math.random() < dt * 8) {
      this.emit('spark', car.pos.x, .25, car.pos.z, 3);
    }
  }
}
