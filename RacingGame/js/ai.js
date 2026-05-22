// ─── AI Opponents ─────────────────────────────────────────────────────────────

class AICar extends Car {
  constructor(scene, color, startOffset, difficultySpeed) {
    super(scene, color, false);
    this.waypointIdx = startOffset;
    this.targetWP    = startOffset;
    this.aiTopSpeed  = difficultySpeed;
    this._jitter     = (Math.random() - 0.5) * 0.25;
  }

  updateAI(dt, trackData) {
    const wps = trackData.waypoints;
    const N   = wps.length;
    // Look 3 waypoints ahead for smoother path
    const lookAhead = wps[(this.targetWP + 3) % N];

    const dx = lookAhead.x - this.pos.x;
    const dz = lookAhead.z - this.pos.z;
    const dist = Math.sqrt(dx*dx + dz*dz);

    const desired = Math.atan2(dx, dz);
    let diff = desired - this.angle;
    while (diff >  Math.PI) diff -= 2*Math.PI;
    while (diff < -Math.PI) diff += 2*Math.PI;
    this.angle += diff * Math.min(1, dt * 4);

    if (dist < 16) {
      this.targetWP = (this.targetWP + 1) % N;
      this.waypointIdx = this.targetWP;
      if (this.targetWP === 0) {
        if (this.lap < 3) this.lap++;
        else this.raceFinished = true;
      }
    }

    // Speed — slow in tight corners
    const cornerSlow = Math.abs(diff) > 0.5 ? 0.72 : 1.0;
    const target = this.aiTopSpeed * cornerSlow + this._jitter * 3;
    this.speed += (target - this.speed) * dt * 2.5;

    this.drifting = Math.abs(diff) > 0.4 && this.speed > 15;
    this.driftAngle = diff * 0.07;

    this.pos.x += Math.sin(this.angle) * this.speed * dt;
    this.pos.z += Math.cos(this.angle) * this.speed * dt;
    this.pos.y  = 0.5;

    if (trackData) this._wallCollision(trackData);

    this.group.position.copy(this.pos);
    this.group.rotation.y = this.angle;

    this._wheelRot += this.speed * dt * 1.8;
    for (let i = 0; i < 4; i++) this.wheels[i].rotation.x = this._wheelRot;
    this.group.children[0].rotation.z = -diff * this.speed * 0.006;
    this.group.children[0].rotation.x = -this.speed * 0.004;
  }
}

function createAIOpponents(scene) {
  return [
    new AICar(scene, 0x39ff14, 8,  28),
    new AICar(scene, 0xffe600, 16, 25),
    new AICar(scene, 0xff6600, 24, 22),
  ];
}
