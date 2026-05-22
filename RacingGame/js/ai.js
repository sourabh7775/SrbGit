// ─── AI Opponents ─────────────────────────────────────────────────────────────

class AICar extends Car {
  constructor(scene, color, offset = 0) {
    super(scene, color, false);
    this.offset    = offset;   // waypoint start offset
    this.aiSpeed   = 22 + Math.random() * 10;
    this.skillJitter = (Math.random() - 0.5) * 0.3;
    this.targetWP  = offset;
    this.waypointIdx = offset;
    this.lap = 1;
  }

  updateAI(dt, trackData) {
    if (!trackData) return;
    const wps  = trackData.waypoints;
    const N    = wps.length;
    const next = wps[this.targetWP % N];

    const dx = next.x - this.pos.x;
    const dz = next.z - this.pos.z;
    const dist = Math.sqrt(dx*dx+dz*dz);

    // Desired angle
    const desired = Math.atan2(dx, dz);
    let diff = desired - this.angle;
    while (diff >  Math.PI) diff -= 2*Math.PI;
    while (diff < -Math.PI) diff += 2*Math.PI;
    this.angle += diff * Math.min(1, dt * 3.5);

    // Advance waypoint
    if (dist < 12) {
      this.targetWP = (this.targetWP + 1) % N;
      this.waypointIdx = this.targetWP;
      if (this.targetWP === 0 || this.targetWP === this.offset % N) {
        if (this.lap < 3) this.lap++;
        else this.raceFinished = true;
      }
    }

    // Speed control
    const cornerFactor = Math.abs(diff) > 0.4 ? 0.7 : 1.0;
    const target = this.aiSpeed * cornerFactor;
    this.speed += (target - this.speed) * dt * 2;

    // Drift angle (cosmetic)
    this.driftAngle = diff * 0.08 + this.skillJitter;

    const heading = this.angle;
    this.pos.x += Math.sin(heading) * this.speed * dt;
    this.pos.z += Math.cos(heading) * this.speed * dt;
    this.pos.y = 0.45;

    this.group.position.copy(this.pos);
    this.group.rotation.y = this.angle;

    this._wheelRot += this.speed * dt * 1.5;
    for (let i = 0; i < 4; i++) {
      this.wheels[i].children[0].rotation.x = this._wheelRot;
      this.wheels[i].children[1].rotation.x = this._wheelRot;
    }

    this.body.rotation.z = -diff * this.speed * 0.006;
    this.body.rotation.x = -this.speed * 0.005;
  }

  getSpeedKmh() { return Math.abs(this.speed) * 3.6; }
}

// Build 3 AI opponents with staggered start positions
function createAIOpponents(scene) {
  const colors  = [0x39ff14, 0xffe600, 0xff8800];
  const offsets = [8, 16, 24];
  return colors.map((c, i) => new AICar(scene, c, offsets[i]));
}
