import SceneKit
import simd

// ─── Data types ──────────────────────────────────────────────────────────────

struct Waypoint {
    var x: Float, z: Float   // centre
    var nx: Float, nz: Float  // outward normal (perpendicular to track)
}

struct BoostStrip {
    var x: Float, z: Float
}

struct TrackData {
    var waypoints:   [Waypoint]
    var spline:      [(Float, Float)]
    var boostStrips: [BoostStrip]
    let trackWidth:  Float = 20.0
}

// ─── Builder ─────────────────────────────────────────────────────────────────

enum TrackBuilder {

    private static let controlPoints: [(Float, Float)] = [
        (  0,   0), (100, -30), (200,  20), (240, 110),
        (220, 210), (160, 270), ( 60, 310), (-60, 310),
        (-160, 265), (-220, 180), (-230, 70), (-180, -10), (-90, -30)
    ]
    private static let stepsPerSegment = 28
    private static let TW: Float = 20.0

    // ── Public entry point ───────────────────────────────
    static func build(into scene: SCNScene) -> TrackData {
        let spline  = buildSpline()
        let N       = spline.count
        var waypoints  = [Waypoint]()
        var boostStrips = [BoostStrip]()
        var leftEdge  = [(Float, Float)]()
        var rightEdge = [(Float, Float)]()

        // Compute normals + edges
        for i in 0..<N {
            let c = spline[i]
            let n = spline[(i+1) % N]
            let dx = n.0 - c.0, dz = n.1 - c.1
            let l = hypotf(dx, dz)
            let px = -dz / l, pz = dx / l
            waypoints.append(Waypoint(x:c.0, z:c.1, nx:px, nz:pz))
            leftEdge.append((c.0 + px*TW, c.1 + pz*TW))
            rightEdge.append((c.0 - px*TW, c.1 - pz*TW))
        }

        addTrackSurface(scene, spline:spline, N:N)
        addNeonEdges(scene, left:leftEdge, right:rightEdge, N:N)
        addKerbs(scene, left:leftEdge, right:rightEdge, N:N)
        addLaneMarkings(scene, spline:spline, N:N)
        addBoostPads(scene, spline:spline, waypoints:waypoints, N:N, strips:&boostStrips)
        addGantry(scene, wp:waypoints[0])
        addEnvironment(scene)
        addLights(scene)

        return TrackData(waypoints: waypoints, spline: spline, boostStrips: boostStrips)
    }

    // ── Catmull-Rom spline ───────────────────────────────
    private static func buildSpline() -> [(Float, Float)] {
        let pts = controlPoints
        let n   = pts.count
        var out = [(Float, Float)]()
        for i in 0..<n {
            let p0 = pts[(i-1+n)%n], p1 = pts[i],
                p2 = pts[(i+1)%n],   p3 = pts[(i+2)%n]
            for s in 0..<stepsPerSegment {
                let t  = Float(s) / Float(stepsPerSegment)
                let t2 = t*t, t3 = t2*t
                let x  = 0.5*((2*p1.0)+(-p0.0+p2.0)*t+(2*p0.0-5*p1.0+4*p2.0-p3.0)*t2+(-p0.0+3*p1.0-3*p2.0+p3.0)*t3)
                let z  = 0.5*((2*p1.1)+(-p0.1+p2.1)*t+(2*p0.1-5*p1.1+4*p2.1-p3.1)*t2+(-p0.1+3*p1.1-3*p2.1+p3.1)*t3)
                out.append((x, z))
            }
        }
        return out
    }

    // ── Asphalt surface ──────────────────────────────────
    private static func addTrackSurface(_ scene: SCNScene, spline: [(Float, Float)], N: Int) {
        var verts  = [SCNVector3]()
        var idxs   = [Int32]()

        for i in 0..<N {
            let c = spline[i], n = spline[(i+1) % N]
            let dx = n.0-c.0, dz = n.1-c.1
            let l = hypotf(dx,dz); let px = -dz/l, pz = dx/l
            verts.append(SCNVector3(c.0+px*TW, 0, c.1+pz*TW))
            verts.append(SCNVector3(c.0-px*TW, 0, c.1-pz*TW))
            let b = Int32(i*2)
            let nb = Int32(((i+1)%N)*2)
            idxs.append(contentsOf: [b, b+1, nb+1, b, nb+1, nb])
        }

        let src = SCNGeometrySource(vertices: verts)
        let el  = SCNGeometryElement(indices: idxs, primitiveType: .triangles)
        let geo = SCNGeometry(sources: [src], elements: [el])
        geo.firstMaterial = material(diffuse: NSColor(red:0.05, green:0.05, blue:0.13, alpha:1))
        let node = SCNNode(geometry: geo)
        node.castsShadow = false
        scene.rootNode.addChildNode(node)
    }

    // ── Neon edge tubes (cyan left, pink right) ──────────
    private static func addNeonEdges(_ scene: SCNScene, left: [(Float,Float)], right: [(Float,Float)], N: Int) {
        for (edge, col) in [(left, NSColor.cyan), (right, NSColor(red:1,green:0.18,blue:0.6,alpha:1))] {
            for i in 0..<N {
                let a = edge[i], b = edge[(i+1)%N]
                addGlowSegment(scene, ax:a.0,az:a.1, bx:b.0,bz:b.1, y:0.22, radius:0.12, color:col)
            }
        }
    }

    private static func addGlowSegment(_ scene: SCNScene,
                                        ax:Float,az:Float, bx:Float,bz:Float,
                                        y:Float, radius:CGFloat, color:NSColor) {
        let dx = bx-ax, dz = bz-az
        let len = hypotf(dx,dz)
        guard len > 0.01 else { return }

        let cyl = SCNCylinder(radius: radius, height: CGFloat(len))
        let mat = SCNMaterial()
        mat.diffuse.contents   = color
        mat.emission.contents  = color
        mat.lightingModel      = .constant
        cyl.firstMaterial      = mat

        // Halo (additive)
        let halo = SCNCylinder(radius: radius*3.5, height: CGFloat(len))
        let hm   = SCNMaterial()
        hm.diffuse.contents        = color
        hm.emission.contents       = color
        hm.lightingModel           = .constant
        hm.transparency            = 0.82
        hm.blendMode               = .add
        halo.firstMaterial         = hm

        let mid = SCNVector3((ax+bx)*0.5, y, (az+bz)*0.5)
        let quat = quaternionAlignY(dx: dx, dz: dz)

        for g in [cyl, halo] {
            let n = SCNNode(geometry: g)
            n.simdPosition    = simd_float3(mid.x, mid.y, mid.z)
            n.simdOrientation = quat
            scene.rootNode.addChildNode(n)
        }
    }

    // ── Kerbs ───────────────────────────────────────────
    private static func addKerbs(_ scene: SCNScene, left:[(Float,Float)], right:[(Float,Float)], N:Int) {
        let red   = NSColor(red:0.8, green:0.1, blue:0.1, alpha:1)
        let white = NSColor(red:0.9, green:0.9, blue:0.9, alpha:1)
        for side in [left, right] {
            for i in 0..<N {
                let a = side[i], b = side[(i+1)%N]
                let col = (i/3) % 2 == 0 ? red : white
                let dx = b.0-a.0, dz = b.1-a.1
                let len = hypotf(dx,dz)
                let box = SCNBox(width: CGFloat(len)+0.1, height: 0.18, length: 1.2, chamferRadius: 0)
                box.firstMaterial = material(diffuse: col)
                let n = SCNNode(geometry: box)
                n.position = SCNVector3((a.0+b.0)*0.5, 0.09, (a.1+b.1)*0.5)
                n.eulerAngles = SCNVector3(0, atan2f(dx,dz), 0)
                scene.rootNode.addChildNode(n)
            }
        }
    }

    // ── Lane markings ────────────────────────────────────
    private static func addLaneMarkings(_ scene: SCNScene, spline:[(Float,Float)], N:Int) {
        let mat = SCNMaterial()
        mat.diffuse.contents  = NSColor(white: 1, alpha: 0.22)
        mat.lightingModel     = .constant
        for i in stride(from:0, to:N, by:5) {
            let c = spline[i], n = spline[(i+1)%N]
            let dx = n.0-c.0, dz = n.1-c.1
            let len = hypotf(dx,dz)
            let plane = SCNPlane(width: 0.25, height: CGFloat(len*3.5))
            plane.firstMaterial = mat
            let node = SCNNode(geometry: plane)
            node.eulerAngles = SCNVector3(-Float.pi/2, atan2f(dx,dz), 0)
            node.position    = SCNVector3((c.0+n.0)*0.5, 0.005, (c.1+n.1)*0.5)
            scene.rootNode.addChildNode(node)
        }
    }

    // ── Boost pads ───────────────────────────────────────
    private static func addBoostPads(_ scene: SCNScene, spline:[(Float,Float)],
                                     waypoints:[Waypoint], N:Int, strips: inout [BoostStrip]) {
        let step = N / 5
        var i = step + 10
        while i < N {
            let c = spline[i], n = spline[(i+1)%N]
            let dx = n.0-c.0, dz = n.1-c.1

            // Cyan glowing pad
            let pad = SCNPlane(width: CGFloat(TW*0.75), height: 4.0)
            let pm  = SCNMaterial()
            pm.diffuse.contents  = NSColor.cyan
            pm.emission.contents = NSColor.cyan
            pm.lightingModel     = .constant
            pm.transparency      = 0.55
            pm.blendMode         = .add
            pad.firstMaterial    = pm
            let pn = SCNNode(geometry: pad)
            pn.eulerAngles = SCNVector3(-Float.pi/2, atan2f(dx,dz), 0)
            pn.position    = SCNVector3(c.0, 0.01, c.1)
            scene.rootNode.addChildNode(pn)

            strips.append(BoostStrip(x: c.0, z: c.1))
            i += step
        }
    }

    // ── Start/Finish gantry ──────────────────────────────
    private static func addGantry(_ scene: SCNScene, wp: Waypoint) {
        let hw = TW + 3
        let purp = NSColor(red:0.6, green:0, blue:1, alpha:1)
        let pillarMat = material(diffuse:purp, emissive:NSColor(red:0.4,green:0,blue:0.7,alpha:1))

        // Pillars
        for s: Float in [-1, 1] {
            let box = SCNBox(width:0.9, height:12, length:0.9, chamferRadius:0.1)
            box.firstMaterial = pillarMat
            let n = SCNNode(geometry: box)
            n.position = SCNVector3(wp.x + wp.nx*s*hw, 6, wp.z + wp.nz*s*hw)
            scene.rootNode.addChildNode(n)
        }
        // Cross bar
        let bar = SCNBox(width:CGFloat(hw*2+6), height:1.2, length:0.9, chamferRadius:0.1)
        bar.firstMaterial = pillarMat
        let bn = SCNNode(geometry: bar)
        bn.position    = SCNVector3(wp.x, 12.6, wp.z)
        bn.eulerAngles = SCNVector3(0, atan2f(wp.nx, wp.nz), 0)
        scene.rootNode.addChildNode(bn)

        // Neon strip on top of bar
        let strip = SCNBox(width:CGFloat(hw*2+4), height:0.15, length:0.15, chamferRadius:0)
        let sm = SCNMaterial(); sm.emission.contents = NSColor.cyan; sm.lightingModel = .constant
        strip.firstMaterial = sm
        let sn = SCNNode(geometry: strip)
        sn.position    = SCNVector3(wp.x, 11.9, wp.z)
        sn.eulerAngles = SCNVector3(0, atan2f(wp.nx, wp.nz), 0)
        scene.rootNode.addChildNode(sn)

        // Checkered tiles on the start/finish line
        let white = SCNMaterial(); white.diffuse.contents = NSColor.white
        let black = SCNMaterial(); black.diffuse.contents = NSColor.black
        for col in 0..<8 {
            for row in 0..<3 {
                let tile = SCNBox(width:2.5, height:0.02, length:1.4, chamferRadius:0)
                tile.firstMaterial = (col+row)%2==0 ? white : black
                let tn = SCNNode(geometry: tile)
                let off = Float(col-3)*2.5 + 1.25
                tn.position    = SCNVector3(wp.x + wp.nx*off, 0.01, wp.z + wp.nz*off)
                tn.eulerAngles = SCNVector3(0, atan2f(wp.nx,wp.nz), 0)
                scene.rootNode.addChildNode(tn)
            }
        }
    }

    // ── Environment ──────────────────────────────────────
    private static func addEnvironment(_ scene: SCNScene) {
        scene.background.contents = NSColor(red:0.015, green:0, blue:0.06, alpha:1)
        scene.fogColor             = NSColor(red:0.015, green:0, blue:0.06, alpha:1)
        scene.fogStartDistance     = 250
        scene.fogEndDistance       = 600

        // Grid ground
        let gridNode = buildGrid(size: 2000, divisions: 120,
                                  color: NSColor(red:0.3, green:0, blue:0.6, alpha:0.5))
        gridNode.position = SCNVector3(0, -0.06, 0)
        scene.rootNode.addChildNode(gridNode)

        // Solid ground beneath
        let ground = SCNFloor()
        ground.reflectivity     = 0.08
        ground.firstMaterial    = material(diffuse: NSColor(red:0.02, green:0, blue:0.04, alpha:1))
        let gn = SCNNode(geometry: ground)
        gn.position = SCNVector3(0,-0.08,0)
        scene.rootNode.addChildNode(gn)

        addRetroCun(scene)
        addMountains(scene)
        addCityline(scene)
        addFloatingRings(scene)
    }

    private static func buildGrid(size: Float, divisions: Int, color: NSColor) -> SCNNode {
        let parent = SCNNode()
        let mat    = SCNMaterial()
        mat.diffuse.contents  = color
        mat.emission.contents = color
        mat.lightingModel     = .constant

        let step = size / Float(divisions)
        for i in 0...divisions {
            let pos = -size/2 + Float(i)*step
            for axis in 0..<2 {
                let box = SCNBox(width: axis==0 ? CGFloat(size) : 0.05,
                                 height: 0.02,
                                 length: axis==0 ? 0.05 : CGFloat(size),
                                 chamferRadius: 0)
                box.firstMaterial = mat
                let n = SCNNode(geometry: box)
                n.position = axis==0 ? SCNVector3(0,0,pos) : SCNVector3(pos,0,0)
                parent.addChildNode(n)
            }
        }
        return parent
    }

    private static func addRetroCun(_ scene: SCNScene) {
        // Retro horizon sun — semicircle made of stacked planes
        let sunMat = SCNMaterial()
        sunMat.diffuse.contents  = NSColor(red:1, green:0.38, blue:0.56, alpha:0.9)
        sunMat.emission.contents = NSColor(red:1, green:0.38, blue:0.56, alpha:0.9)
        sunMat.lightingModel     = .constant
        sunMat.isDoubleSided     = true

        // Main disc (half)
        let disc = SCNBox(width:160, height:80, length:0.1, chamferRadius:0)
        disc.firstMaterial = sunMat
        let dn = SCNNode(geometry: disc)
        dn.position = SCNVector3(0, 42, -480)
        scene.rootNode.addChildNode(dn)

        // Dark stripes across it
        let stripeMat = SCNMaterial()
        stripeMat.diffuse.contents  = NSColor(red:0.015,green:0,blue:0.06,alpha:1)
        stripeMat.lightingModel     = .constant
        stripeMat.isDoubleSided     = true
        for i in 0..<8 {
            let h: Float = 2 + Float(i)*1.4
            let stripe = SCNBox(width:162, height:CGFloat(h), length:0.12, chamferRadius:0)
            stripe.firstMaterial = stripeMat
            let sn = SCNNode(geometry: stripe)
            sn.position = SCNVector3(0, Float(5 - i*9), -479.9)
            scene.rootNode.addChildNode(sn)
        }

        // Outer glow disc
        let glowMat = SCNMaterial()
        glowMat.diffuse.contents  = NSColor(red:1,green:0.2,blue:0.5,alpha:0.15)
        glowMat.emission.contents = NSColor(red:1,green:0.2,blue:0.5,alpha:0.15)
        glowMat.lightingModel     = .constant
        glowMat.transparency      = 0.85
        glowMat.blendMode         = .add
        let gd = SCNBox(width:200, height:100, length:0.1, chamferRadius:0)
        gd.firstMaterial = glowMat
        let gn = SCNNode(geometry: gd)
        gn.position = SCNVector3(0, 42, -481)
        scene.rootNode.addChildNode(gn)
    }

    private static func addMountains(_ scene: SCNScene) {
        var rng = SeededRNG(seed: 7)
        let darkMat = material(diffuse: NSColor(red:0.1,green:0,blue:0.2,alpha:1),
                               emissive: NSColor(red:0.05,green:0,blue:0.1,alpha:1))
        let edgeMat = SCNMaterial()
        edgeMat.diffuse.contents  = NSColor(red:0.6,green:0,blue:1,alpha:0.5)
        edgeMat.emission.contents = NSColor(red:0.6,green:0,blue:1,alpha:0.5)
        edgeMat.lightingModel     = .constant

        for _ in 0..<22 {
            let h = Float(40 + rng.next()*80)
            let w = Float(50 + rng.next()*100)
            let x = Float((rng.next()-0.5)*800)
            let z = Float(-280 - rng.next()*250)
            let sides = Int(4 + rng.next()*3)

            let cone = SCNCone(topRadius: 0, bottomRadius: CGFloat(w), height: CGFloat(h))
            cone.firstMaterial = darkMat
            let cn = SCNNode(geometry: cone)
            cn.position = SCNVector3(x, h*0.5-1, z)
            scene.rootNode.addChildNode(cn)

            // Neon outline
            let outline = SCNCone(topRadius: 0.5, bottomRadius: CGFloat(w+0.5), height: CGFloat(h+0.5))
            outline.firstMaterial = edgeMat
            let on = SCNNode(geometry: outline)
            on.position = cn.position
            scene.rootNode.addChildNode(on)
        }
    }

    private static func addCityline(_ scene: SCNScene) {
        var rng = SeededRNG(seed: 99)
        let buildMat = material(diffuse: NSColor(red:0.04,green:0,blue:0.1,alpha:1))
        let winMat   = SCNMaterial()
        winMat.diffuse.contents  = NSColor(red:1,green:0.9,blue:0.5,alpha:0.4)
        winMat.emission.contents = NSColor(red:1,green:0.9,blue:0.5,alpha:0.4)
        winMat.lightingModel     = .constant
        winMat.blendMode         = .add

        for _ in 0..<60 {
            let h = Float(20 + rng.next()*80)
            let w = Float(8  + rng.next()*20)
            let x = Float((rng.next()-0.5)*1000)
            let z = Float(-330 - rng.next()*200)
            let box = SCNBox(width:CGFloat(w), height:CGFloat(h), length:CGFloat(w*0.7), chamferRadius:0)
            box.firstMaterial = buildMat
            let bn = SCNNode(geometry: box)
            bn.position = SCNVector3(x, h*0.5, z)
            scene.rootNode.addChildNode(bn)
            // Window glow face
            if rng.next() > 0.4 {
                let wf = SCNBox(width:CGFloat(w-1), height:CGFloat(h-2), length:0.1, chamferRadius:0)
                wf.firstMaterial = winMat
                let wn = SCNNode(geometry: wf)
                wn.position = SCNVector3(x, h*0.5, z + w*0.35 + 0.1)
                scene.rootNode.addChildNode(wn)
            }
        }
    }

    private static func addFloatingRings(_ scene: SCNScene) {
        var rng = SeededRNG(seed: 13)
        let mat = SCNMaterial()
        mat.diffuse.contents  = NSColor(red:0.6,green:0,blue:1,alpha:0.2)
        mat.emission.contents = NSColor(red:0.6,green:0,blue:1,alpha:0.2)
        mat.lightingModel     = .constant
        mat.blendMode         = .add

        for _ in 0..<10 {
            let r  = Float(30 + rng.next()*50)
            let tube = SCNTorus(ringRadius: CGFloat(r), pipeRadius: 0.4)
            tube.firstMaterial = mat
            let tn = SCNNode(geometry: tube)
            tn.position    = SCNVector3(Float((rng.next()-0.5)*500),
                                        Float(20+rng.next()*50),
                                        Float(-100-rng.next()*350))
            tn.eulerAngles = SCNVector3(Float(rng.next()*Float.pi),
                                        Float(rng.next()*Float.pi), 0)
            scene.rootNode.addChildNode(tn)
            // Gentle spin animation
            let spin = SCNAction.repeatForever(.rotateBy(x:0, y:CGFloat(rng.next()*0.5+0.1), z:0, duration:10))
            tn.runAction(spin)
        }
    }

    // ── Lighting ─────────────────────────────────────────
    private static func addLights(_ scene: SCNScene) {
        // Ambient
        let amb   = SCNLight(); amb.type = .ambient
        amb.color = NSColor(red:0.15, green:0.1, blue:0.3, alpha:1)
        let an = SCNNode(); an.light = amb; scene.rootNode.addChildNode(an)

        // Key directional
        let dir   = SCNLight(); dir.type = .directional
        dir.color = NSColor(red:0.8, green:0.6, blue:1.0, alpha:1)
        dir.intensity = 800
        dir.castsShadow = true
        dir.shadowMode  = .deferred
        dir.shadowMapSize   = CGSize(width:2048, height:2048)
        dir.shadowSampleCount = 16
        dir.shadowRadius = 3
        let dn = SCNNode(); dn.light = dir
        dn.eulerAngles = SCNVector3(-Float.pi/4, Float.pi/6, 0)
        scene.rootNode.addChildNode(dn)

        // Pink fill from below
        let fill   = SCNLight(); fill.type = .ambient
        fill.color = NSColor(red:0.15, green:0, blue:0.08, alpha:1)
        let fn = SCNNode(); fn.light = fill; scene.rootNode.addChildNode(fn)

        // Track-side point lights
        for i in 0..<8 {
            let angle = Float(i) / 8.0 * 2 * Float.pi
            let r: Float = 150
            let pl = SCNLight(); pl.type = .omni
            pl.color = i%2==0 ? NSColor.cyan : NSColor(red:1,green:0.18,blue:0.6,alpha:1)
            pl.intensity  = 300
            pl.attenuationStartDistance = 0
            pl.attenuationEndDistance   = 200
            let pln = SCNNode(); pln.light = pl
            pln.position = SCNVector3(cosf(angle)*r, 20, sinf(angle)*r*1.4 + 150)
            scene.rootNode.addChildNode(pln)
        }
    }

    // ── Helpers ───────────────────────────────────────────
    private static func material(diffuse: NSColor, emissive: NSColor? = nil) -> SCNMaterial {
        let m = SCNMaterial()
        m.diffuse.contents  = diffuse
        if let e = emissive { m.emission.contents = e }
        return m
    }

    // Rotate cylinder's Y-axis to align with horizontal direction (dx, dz)
    static func quaternionAlignY(dx: Float, dz: Float) -> simd_quatf {
        let dir = simd_normalize(simd_float3(dx, 0, dz))
        let yAxis = simd_float3(0, 1, 0)
        let rotAxis = simd_cross(yAxis, dir)
        let la = simd_length(rotAxis)
        if la < 0.0001 {
            // Parallel — no rotation needed (or 180°)
            return simd_dot(yAxis, dir) > 0
                ? simd_quaternion(0, simd_float3(1,0,0))
                : simd_quaternion(Float.pi, simd_float3(1,0,0))
        }
        return simd_quaternion(Float.pi/2, simd_normalize(rotAxis))
    }
}

// ─── Seeded RNG ───────────────────────────────────────────────────────────────
struct SeededRNG {
    private var seed: UInt32
    init(seed: UInt32) { self.seed = seed }
    mutating func next() -> Float {
        seed = seed &+ 0x6D2B79F5
        var z = seed ^ (seed >> 15)
        z = (z &* (1 | seed))
        z = z ^ z >> 7
        z = z ^ (z &* (61 | z))
        return Float(z ^ z >> 14) / Float(UInt32.max)
    }
}
