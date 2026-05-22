import SceneKit
import simd

final class AICarNode: CarNode {
    private let topAISpeed: Float
    private let startOffset: Int
    private var targetWP: Int

    init(color: NSColor, startOffset: Int, topSpeed: Float) {
        self.topAISpeed  = topSpeed
        self.startOffset = startOffset
        self.targetWP    = startOffset
        super.init(color: color)
        waypointIndex = startOffset
    }

    required init?(coder: NSCoder) { fatalError() }

    func updateAI(dt: Float, trackData: TrackData) {
        let wps = trackData.waypoints; let N = wps.count
        // Look 4 WPs ahead for smoother path
        let look = wps[(targetWP + 4) % N]
        let dx   = look.x - carPosition.x
        let dz   = look.z - carPosition.z
        let dist = hypotf(dx, dz)

        let desired  = atan2f(dx, dz)
        var diff     = desired - carAngle
        while diff >  Float.pi { diff -= 2 * Float.pi }
        while diff < -Float.pi { diff += 2 * Float.pi }
        carAngle += diff * min(1, dt * 3.8)

        if dist < 16 {
            targetWP = (targetWP + 1) % N
            waypointIndex = targetWP
            if targetWP == startOffset % N {
                if lap < 3 { lap += 1 } else { raceFinished = true }
            }
        }

        let cornerSlow: Float = abs(diff) > 0.5 ? 0.70 : 1.0
        let target = topAISpeed * cornerSlow
        speed += (target - speed) * dt * 2.5

        isDrifting = abs(diff) > 0.4 && speed > 15
        driftAngle = diff * 0.07

        carPosition.x += sinf(carAngle) * speed * dt
        carPosition.z += cosf(carAngle) * speed * dt
        carPosition.y  = 0.5

        wallCollision(trackData)

        simdPosition    = carPosition
        simdEulerAngles = simd_float3(0, carAngle, 0)

        // Wheel animation
        let wr = speed * dt * 1.9
        for w in wheelSpinNodes { w.eulerAngles.x += wr }
        bodyNode.eulerAngles = SCNVector3(-speed * 0.004, 0, -diff * speed * 0.006)
    }

    // Expose internals needed by updateAI
    var wheelSpinNodes: [SCNNode] {
        // Collect spin nodes by traversal — they're direct children of the pivot nodes
        childNodes.flatMap { $0.childNodes }.filter { !$0.childNodes.isEmpty }
    }
    var bodyNode: SCNNode {
        // First SCNBox child — the main body
        childNodes.first ?? SCNNode()
    }

    private func wallCollision(_ td: TrackData) {
        let wps = td.waypoints; let N = wps.count
        var minD: Float = .infinity; var minI = waypointIndex
        for off in -3...3 {
            let i = (waypointIndex + off + N) % N
            let dx = carPosition.x - wps[i].x, dz = carPosition.z - wps[i].z
            let d  = dx*dx + dz*dz
            if d < minD { minD = d; minI = i }
        }
        let wp  = wps[minI]
        let lat = (carPosition.x - wp.x)*wp.nx + (carPosition.z - wp.z)*wp.nz
        let lim = td.trackWidth - 1.8
        if abs(lat) > lim {
            let push = (abs(lat) - lim) * (lat > 0 ? 1 : -1)
            carPosition.x -= wp.nx * push
            carPosition.z -= wp.nz * push
            speed *= 0.6
        }
    }
}

func makeAIOpponents(scene: SCNScene) -> [AICarNode] {
    let cfg: [(NSColor, Int, Float)] = [
        (NSColor(red:0.22,green:1,blue:0.08,alpha:1),  8, 28),
        (NSColor(red:1,   green:0.9,blue:0,   alpha:1), 16, 25),
        (NSColor(red:1,   green:0.4,blue:0.1, alpha:1), 24, 22),
    ]
    return cfg.map { (color, offset, speed) in
        let ai = AICarNode(color: color, startOffset: offset, topSpeed: speed)
        scene.rootNode.addChildNode(ai)
        return ai
    }
}
