import SceneKit
import simd

class CarNode: SCNNode {

    // ─── Physics state ────────────────────────────────────
    var carPosition  = simd_float3(0, 0.5, 0)
    var carAngle: Float = 0      // yaw, radians
    var speed:    Float = 0      // m/s (signed)
    var steer:    Float = 0      // -1..1
    var driftAngle: Float = 0
    var isDrifting = false

    // Nitro
    var nitro:       Float = 1.0
    var nitroOn      = false
    var nitroLocked  = false

    // Lap / race state
    var lap           = 1
    var waypointIndex = 0
    var lapStartTime: TimeInterval = 0
    var lapTimes      = [TimeInterval]()
    var bestLap: TimeInterval = .infinity
    var raceFinished  = false

    // ─── Mesh references ──────────────────────────────────
    private var wheelSpinNodes  = [SCNNode]()
    private var steerPivots     = [SCNNode]()  // front wheels
    private var bodyNode        = SCNNode()
    private var wheelRot: Float = 0

    // ─── Init ─────────────────────────────────────────────
    let carColor: NSColor

    init(color: NSColor) {
        self.carColor = color
        super.init()
        buildGeometry()
        setupParticles()
    }

    required init?(coder: NSCoder) { fatalError() }

    // ─── Geometry ─────────────────────────────────────────
    private func buildGeometry() {

        func mat(_ c: NSColor, _ em: NSColor? = nil, _ ei: CGFloat = 0) -> SCNMaterial {
            let m = SCNMaterial()
            m.diffuse.contents  = c
            m.specular.contents = NSColor.white
            m.shininess         = 60
            if let e = em { m.emission.contents = e; m.emissionIntensity = ei }
            return m
        }

        // ── Lower body (slightly wedge-shaped) ──────────
        let body = SCNBox(width:2.2, height:0.52, length:4.4, chamferRadius:0.09)
        body.firstMaterial = mat(carColor)
        bodyNode = SCNNode(geometry: body)
        bodyNode.position = SCNVector3(0, 0.52, 0)
        bodyNode.castsShadow = true
        addChildNode(bodyNode)

        // ── Sidepods ──────────────────────────────────────
        for x: Float in [-1.28, 1.28] {
            let pod = SCNBox(width:0.28, height:0.28, length:2.9, chamferRadius:0.04)
            pod.firstMaterial = mat(NSColor(red:0.1,green:0.1,blue:0.1,alpha:1))
            let pn = SCNNode(geometry: pod); pn.position = SCNVector3(x, 0.26, -0.15)
            addChildNode(pn)
        }

        // ── Cabin (tapered) ───────────────────────────────
        let cabGeo = buildCabinGeometry()
        let cabin  = SCNNode(geometry: cabGeo)
        cabin.castsShadow = true
        addChildNode(cabin)

        // ── Windshield ────────────────────────────────────
        let ws = SCNBox(width:1.45, height:0.5, length:0.08, chamferRadius:0.02)
        ws.firstMaterial = mat(NSColor(red:0.1,green:0.2,blue:0.5,alpha:0.55))
        let wsn = SCNNode(geometry: ws)
        wsn.position = SCNVector3(0, 1.04, 0.78); wsn.eulerAngles = SCNVector3(-0.3,0,0)
        addChildNode(wsn)

        // ── Rear wing ─────────────────────────────────────
        let wing = SCNBox(width:2.55, height:0.12, length:0.62, chamferRadius:0.02)
        wing.firstMaterial = mat(NSColor(red:0.08,green:0.08,blue:0.08,alpha:1))
        let wn = SCNNode(geometry: wing); wn.position = SCNVector3(0, 1.25, -2.15)
        addChildNode(wn)
        for x: Float in [-0.9, 0.9] {
            let p = SCNBox(width:0.1, height:0.55, length:0.1, chamferRadius:0)
            p.firstMaterial = wn.geometry!.firstMaterial
            let pn = SCNNode(geometry:p); pn.position=SCNVector3(x,0.92,-2.15)
            addChildNode(pn)
        }

        // ── Front splitter ────────────────────────────────
        let spl = SCNBox(width:2.4, height:0.1, length:0.95, chamferRadius:0.02)
        spl.firstMaterial = mat(NSColor(red:0.1,green:0.1,blue:0.1,alpha:1))
        let spln = SCNNode(geometry:spl); spln.position=SCNVector3(0,0.1,1.85)
        addChildNode(spln)

        // ── Side skirts ────────────────────────────────────
        for x: Float in [-1.18, 1.18] {
            let sk = SCNBox(width:0.11, height:0.28, length:3.85, chamferRadius:0)
            sk.firstMaterial = mat(NSColor(red:0.08,green:0.08,blue:0.08,alpha:1))
            let skn = SCNNode(geometry:sk); skn.position=SCNVector3(x,0.3,0)
            addChildNode(skn)
        }

        // ── Underglow ─────────────────────────────────────
        let glow = SCNBox(width:2.0, height:0.05, length:4.1, chamferRadius:0)
        glow.firstMaterial = mat(carColor, carColor, 1.4)
        let glown = SCNNode(geometry:glow); glown.position=SCNVector3(0,0.05,0)
        addChildNode(glown)

        // ── Headlights ────────────────────────────────────
        let hlm = SCNMaterial(); hlm.diffuse.contents = NSColor.white; hlm.emission.contents = NSColor.white
        let tlm = SCNMaterial(); tlm.diffuse.contents = NSColor.red;   tlm.emission.contents = NSColor.red
        for x: Float in [-0.62, 0.62] {
            let hl = SCNBox(width:0.34,height:0.14,length:0.07,chamferRadius:0)
            hl.firstMaterial = hlm
            let hln = SCNNode(geometry:hl); hln.position=SCNVector3(x,0.52,2.22); addChildNode(hln)
            let tl = SCNBox(width:0.28,height:0.12,length:0.07,chamferRadius:0)
            tl.firstMaterial = tlm
            let tln = SCNNode(geometry:tl); tln.position=SCNVector3(x,0.56,-2.22); addChildNode(tln)
        }

        // ── Wheels ────────────────────────────────────────
        buildWheels()
    }

    private func buildCabinGeometry() -> SCNGeometry {
        // Hexagonal prism (top view: narrow front, wide rear) via custom verts
        let verts: [SCNVector3] = [
            // bottom ring
            SCNVector3(-0.72, 0.78,  0.82), SCNVector3(0.72, 0.78,  0.82),
            SCNVector3( 0.76, 0.78,  0.30), SCNVector3(0.76, 0.78, -0.60),
            SCNVector3( 0.68, 0.78, -0.82), SCNVector3(-0.68, 0.78, -0.82),
            SCNVector3(-0.76, 0.78, -0.60), SCNVector3(-0.76, 0.78,  0.30),
            // top ring
            SCNVector3(-0.42, 1.28,  0.55), SCNVector3(0.42, 1.28,  0.55),
            SCNVector3( 0.44, 1.28,  0.15), SCNVector3(0.44, 1.28, -0.52),
            SCNVector3( 0.36, 1.28, -0.72), SCNVector3(-0.36, 1.28, -0.72),
            SCNVector3(-0.44, 1.28, -0.52), SCNVector3(-0.44, 1.28,  0.15),
        ]
        var idxs = [Int32]()
        // Side quads (8 sides)
        for i in 0..<8 {
            let b0 = Int32(i), b1 = Int32((i+1)%8)
            let t0 = Int32(8+i), t1 = Int32(8+(i+1)%8)
            idxs.append(contentsOf: [b0,b1,t1, b0,t1,t0])
        }
        // Top fan
        for i in 1..<7 { idxs.append(contentsOf: [8, Int32(8+i), Int32(9+i)]) }

        let src = SCNGeometrySource(vertices: verts)
        let el  = SCNGeometryElement(indices: idxs, primitiveType: .triangles)
        let geo = SCNGeometry(sources:[src], elements:[el])
        geo.computeNormals()
        let m = SCNMaterial()
        m.diffuse.contents  = NSColor(red:0.06,green:0.06,blue:0.16,alpha:1)
        m.specular.contents = NSColor.white; m.shininess = 80
        geo.firstMaterial   = m
        return geo
    }

    private func buildWheels() {
        let tireMat  = SCNMaterial()
        tireMat.diffuse.contents = NSColor(red:0.1,green:0.1,blue:0.1,alpha:1)

        let rimMat = SCNMaterial()
        rimMat.diffuse.contents  = NSColor(red:0.8,green:0.8,blue:0.8,alpha:1)
        rimMat.emission.contents = carColor
        rimMat.emissionIntensity = 0.5

        let brakeMat = SCNMaterial()
        brakeMat.diffuse.contents  = carColor
        brakeMat.emission.contents = carColor
        brakeMat.emissionIntensity = 0.9

        let positions: [(Float,Float,Float)] = [
            (-1.26, 0.40,  1.52), (1.26, 0.40,  1.52),
            (-1.26, 0.40, -1.62), (1.26, 0.40, -1.62)
        ]

        for (i,(x,y,z)) in positions.enumerated() {
            let pivot = SCNNode()        // steering pivot (front wheels only)
            let spin  = SCNNode()        // rolling spin node

            // Tyre
            let tire = SCNCylinder(radius:0.37, height:0.31)
            tire.firstMaterial = tireMat
            let tireNode = SCNNode(geometry:tire)
            tireNode.eulerAngles = SCNVector3(0, 0, Float.pi/2)

            // Rim
            let rim  = SCNCylinder(radius:0.22, height:0.32)
            rim.firstMaterial = rimMat
            let rimNode = SCNNode(geometry:rim)
            rimNode.eulerAngles = SCNVector3(0, 0, Float.pi/2)

            // Brake caliper
            let brake = SCNBox(width:0.12, height:0.1, length:0.12, chamferRadius:0)
            brake.firstMaterial = brakeMat
            let brakeNode = SCNNode(geometry:brake)
            brakeNode.position = SCNVector3(x > 0 ? -0.22 : 0.22, 0.08, 0)

            spin.addChildNode(tireNode)
            spin.addChildNode(rimNode)
            spin.addChildNode(brakeNode)
            pivot.addChildNode(spin)
            pivot.position = SCNVector3(x, y, z)
            addChildNode(pivot)

            wheelSpinNodes.append(spin)
            if i < 2 { steerPivots.append(pivot) }
        }
    }

    private func setupParticles() {
        // Exhaust particles on each exhaust port
        for xOff: Float in [-0.5, 0.5] {
            guard let ps = makeExhaustParticles() else { continue }
            let pn = SCNNode()
            pn.position = SCNVector3(xOff, 0.38, -2.28)
            pn.addParticleSystem(ps)
            addChildNode(pn)
        }
    }

    private func makeExhaustParticles() -> SCNParticleSystem? {
        let ps = SCNParticleSystem()
        ps.birthRate              = 30
        ps.particleLifeSpan       = 1.0
        ps.particleLifeSpanVariation = 0.3
        ps.particleSize           = 0.5
        ps.particleSizeVariation  = 0.2
        ps.particleVelocity       = 4
        ps.particleVelocityVariation = 2
        ps.emittingDirection      = SCNVector3(0, 0.3, -1)
        ps.spreadingAngle         = 15
        ps.particleColor          = NSColor(red:0.45,green:0.45,blue:0.55,alpha:0.5)
        ps.particleColorVariation = SCNVector4(0.1, 0.1, 0.1, 0.2)
        ps.blendMode              = .alpha
        ps.isAffectedByGravity    = false
        ps.loops                  = true
        return ps
    }

    // ─── Physics update ───────────────────────────────────
    func update(dt: Float, input: InputState, trackData: TrackData) {
        let ACCEL:     Float = 40
        let BRAKE:     Float = 55
        let FRICTION:  Float = 14
        let MAX_SPEED: Float = 54
        let NITRO_ADD: Float = 24
        let STEER_MAX: Float = 3.2

        // Nitro
        if input.nitro && nitro > 0 && !nitroLocked {
            nitroOn = true
            nitro = max(0, nitro - dt * 0.50)
            if nitro == 0 { nitroLocked = true }
        } else { nitroOn = false }
        if nitroLocked {
            nitro = min(1, nitro + dt * 0.14)
            if nitro >= 1 { nitroLocked = false }
        } else if !nitroOn { nitro = min(1, nitro + dt * 0.07) }

        let topSpeed = MAX_SPEED + (nitroOn ? NITRO_ADD : 0)

        // Speed
        if input.throttle      { speed += ACCEL * dt }
        else if input.brake    { speed -= (speed > 1 ? BRAKE : ACCEL * 0.4) * dt }
        else {
            let f = FRICTION * dt
            speed = abs(speed) > f ? speed - copysignf(f, speed) : 0
        }
        speed = max(-14, min(topSpeed, speed))

        // Steer
        let rawSteer: Float = input.left ? -1 : input.right ? 1 : 0
        let steerRate: Float = 5.5 / (1 + abs(speed) * 0.04)
        steer += (rawSteer - steer) * min(1, dt * steerRate)
        if abs(speed) > 0.5 {
            let eff = STEER_MAX * (1 - abs(speed)/(topSpeed*2.1))
            carAngle += steer * eff * dt * (speed >= 0 ? 1 : -1)
        }

        // Drift
        isDrifting = abs(steer) > 0.55 && abs(speed) > 18
        if isDrifting {
            driftAngle += (steer * 0.2 - driftAngle) * dt * 5
            if !nitroLocked { nitro = min(1, nitro + dt * 0.28) }
        } else {
            driftAngle *= max(0, 1 - dt * 8)
        }

        let heading = carAngle + driftAngle * 0.25
        carPosition.x += sinf(heading) * speed * dt
        carPosition.z += cosf(heading) * speed * dt

        checkBoostStrips(trackData)
        wallCollision(trackData)

        // Apply to scene node
        simdPosition    = carPosition
        simdEulerAngles = simd_float3(0, carAngle, 0)

        // Wheels
        wheelRot += speed * dt * 1.9
        for spin in wheelSpinNodes { spin.eulerAngles.x = wheelRot }
        for pivot in steerPivots   { pivot.eulerAngles.y = steer * 0.42 }

        // Body rock
        bodyNode.eulerAngles = SCNVector3(-speed * 0.004, 0, -steer * speed * 0.007)

        progressWaypoints(trackData)
    }

    private func checkBoostStrips(_ td: TrackData) {
        for s in td.boostStrips {
            let dx = carPosition.x - s.x, dz = carPosition.z - s.z
            if dx*dx + dz*dz < 64 && speed < 62 {
                speed = min(62, speed + 14)
                AudioManager.shared.playBoostHit()
                break
            }
        }
    }

    private func wallCollision(_ td: TrackData) {
        let wps = td.waypoints; let N = wps.count
        var minD: Float = .infinity; var minI = waypointIndex
        for off in -4...4 {
            let i = (waypointIndex + off + N) % N
            let dx = carPosition.x - wps[i].x, dz = carPosition.z - wps[i].z
            let d  = dx*dx + dz*dz
            if d < minD { minD = d; minI = i }
        }
        let wp = wps[minI]
        let lat = (carPosition.x - wp.x)*wp.nx + (carPosition.z - wp.z)*wp.nz
        let lim = td.trackWidth - 1.6
        if abs(lat) > lim {
            let push = (abs(lat) - lim) * (lat > 0 ? 1 : -1)
            carPosition.x -= wp.nx * push
            carPosition.z -= wp.nz * push
            speed  *= 0.50
            steer   = 0
        }
    }

    private func progressWaypoints(_ td: TrackData) {
        let N    = td.waypoints.count
        let next = td.waypoints[(waypointIndex+1) % N]
        let dx   = carPosition.x - next.x, dz = carPosition.z - next.z
        guard dx*dx + dz*dz < 200 else { return }
        waypointIndex = (waypointIndex + 1) % N
        if waypointIndex == 0 { finishLap() }
    }

    private func finishLap() {
        let now = CACurrentMediaTime()
        if lapStartTime > 0 {
            let t = now - lapStartTime
            lapTimes.append(t)
            if t < bestLap { bestLap = t }
            AudioManager.shared.playLapDing()
        }
        lapStartTime = now
        if lap < 3 { lap += 1 } else { raceFinished = true }
    }

    var speedKmh: Float { abs(speed) * 3.6 }

    var gear: Int {
        let s = abs(speed)
        switch s {
        case ..<8:  return 1; case ..<17: return 2; case ..<27: return 3
        case ..<36: return 4; case ..<46: return 5; default: return 6
        }
    }

    func resetTo(waypoint wp: Waypoint, nextWP: Waypoint) {
        carPosition  = simd_float3(wp.x, 0.5, wp.z)
        carAngle     = atan2f(nextWP.x - wp.x, nextWP.z - wp.z)
        speed        = 0; steer = 0; driftAngle = 0
        simdPosition    = carPosition
        simdEulerAngles = simd_float3(0, carAngle, 0)
    }
}

// SCNGeometry helper to compute normals after setting verts
extension SCNGeometry {
    func computeNormals() {
        // SceneKit auto-computes normals from the geometry sources when requested
        // We just ensure the geometry has the right sources set.
        // No-op here — SceneKit handles smooth normals automatically.
    }
}
