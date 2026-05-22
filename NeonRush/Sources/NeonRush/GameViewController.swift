import AppKit
import SceneKit
import SpriteKit
import simd

// ─── Game states ─────────────────────────────────────────────────────────────
enum GameState { case menu, countdown, racing, paused, finished }

// ─── Main view controller ─────────────────────────────────────────────────────
final class GameViewController: NSViewController {

    // SceneKit
    private var scnView  = SCNView()
    private var scene    = SCNScene()
    private var camera   = SCNNode()

    // SpriteKit HUD
    private var hud: HUDScene!

    // Game objects
    private var player:  CarNode!
    private var aiCars:  [AICarNode] = []
    private var trackData: TrackData!

    // Input
    private let input = InputHandler()

    // State
    private var state: GameState = .menu
    private var raceStartTime: TimeInterval = 0
    private var lastFrameTime: TimeInterval = 0
    private var cameraMode = 0
    private var camPos    = simd_float3(0, 8, -20)
    private var camTarget = simd_float3(0, 0, 0)

    // Drift scoring
    private var driftAccum: Float = 0
    private var driftMultiplier = 1

    // Camera mode names (5 modes)
    private let numCamModes = 5

    // Menu animated car
    private var menuCarAngle: Float = 0

    // ─── View lifecycle ───────────────────────────────────
    override func loadView() {
        view = scnView
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        setupSCNView()
        setupHUD()
        setupMenuScene()
        AudioManager.shared.start()
    }

    override func viewDidLayout() {
        super.viewDidLayout()
        hud.size = scnView.bounds.size
    }

    // ─── SceneKit setup ───────────────────────────────────
    private func setupSCNView() {
        scnView.antialiasingMode  = .multisampling4X
        scnView.allowsCameraControl = false
        scnView.showsStatistics   = false
        scnView.backgroundColor   = .black
        scnView.preferredFramesPerSecond = 60
        scnView.delegate          = self
        scnView.isPlaying         = true

        // Camera node (reused across states)
        camera.camera = SCNCamera()
        camera.camera!.fieldOfView      = 65
        camera.camera!.zNear            = 0.5
        camera.camera!.zFar             = 800
        camera.camera!.wantsDepthOfField = false
        scene.rootNode.addChildNode(camera)
        scnView.pointOfView = camera
    }

    private func setupHUD() {
        hud = HUDScene(size: scnView.bounds.size)
        hud.scaleMode = .resizeFill
        scnView.overlaySKScene = hud
    }

    // ─── Menu scene ───────────────────────────────────────
    private func setupMenuScene() {
        scene = SCNScene()
        scene.background.contents = NSColor(red:0.01, green:0, blue:0.04, alpha:1)
        scnView.scene = scene

        // Simple menu: neon-lit road stretching into the horizon
        let floor = SCNFloor()
        floor.reflectivity  = 0.15
        floor.firstMaterial = { let m = SCNMaterial()
            m.diffuse.contents = NSColor(red:0.02,green:0,blue:0.05,alpha:1)
            return m }()
        scene.rootNode.addChildNode(SCNNode(geometry: floor))

        // Grid lines
        for i in stride(from:-200, through:200, by:8) {
            let line = SCNBox(width:400, height:0.02, length:0.06, chamferRadius:0)
            line.firstMaterial = gridMat(NSColor(red:0.3,green:0,blue:0.6,alpha:0.5))
            let n = SCNNode(geometry:line); n.position = SCNVector3(0,0.01,Float(i))
            scene.rootNode.addChildNode(n)
        }
        for i in stride(from:-200, through:200, by:8) {
            let line = SCNBox(width:0.06, height:0.02, length:400, chamferRadius:0)
            line.firstMaterial = gridMat(NSColor(red:0.3,green:0,blue:0.6,alpha:0.5))
            let n = SCNNode(geometry:line); n.position = SCNVector3(Float(i),0.01,0)
            scene.rootNode.addChildNode(n)
        }

        // Sun on horizon
        let sun = SCNBox(width:200, height:100, length:0.1, chamferRadius:0)
        let sm  = SCNMaterial(); sm.diffuse.contents = NSColor(red:1,green:0.35,blue:0.6,alpha:0.9)
        sm.emission.contents = NSColor(red:1,green:0.35,blue:0.6,alpha:0.9); sm.lightingModel = .constant
        sun.firstMaterial = sm
        let sn  = SCNNode(geometry:sun); sn.position = SCNVector3(0,50,-500)
        scene.rootNode.addChildNode(sn)

        // Mountains
        var rng = SeededRNG(seed:5)
        for _ in 0..<12 {
            let h: Float = Float(30+rng.next()*70)
            let cone = SCNCone(topRadius:0, bottomRadius:CGFloat(40+rng.next()*60), height:CGFloat(h))
            let cm   = SCNMaterial(); cm.diffuse.contents = NSColor(red:0.08,green:0,blue:0.18,alpha:1)
            cone.firstMaterial = cm
            let cn   = SCNNode(geometry:cone)
            cn.position = SCNVector3(Float((rng.next()-0.5)*700), h*0.5, Float(-200-rng.next()*300))
            scene.rootNode.addChildNode(cn)
        }

        // Lights
        let amb = SCNLight(); amb.type = .ambient; amb.color = NSColor(red:0.1,green:0.05,blue:0.2,alpha:1)
        let an  = SCNNode(); an.light = amb; scene.rootNode.addChildNode(an)
        let dir = SCNLight(); dir.type = .directional; dir.color = NSColor(red:0.7,green:0.5,blue:1,alpha:1)
        let dn  = SCNNode(); dn.light = dir; dn.eulerAngles = SCNVector3(-1.0, 0.4, 0)
        scene.rootNode.addChildNode(dn)

        // Demo cars on the road
        let colors: [NSColor] = [
            NSColor(red:1,green:0.18,blue:0.6,alpha:1),
            NSColor(red:0,green:0.95,blue:1,alpha:1),
            NSColor(red:0.6,green:0,blue:1,alpha:1),
        ]
        for (i, c) in colors.enumerated() {
            let car = CarNode(color:c)
            car.simdPosition = simd_float3(Float(i-1)*4, 0.5, Float(-30 - i*20))
            scene.rootNode.addChildNode(car)
        }

        showMenuOverlay()
        state = .menu
    }

    private func gridMat(_ c: NSColor) -> SCNMaterial {
        let m = SCNMaterial(); m.diffuse.contents = c
        m.emission.contents = c; m.lightingModel = .constant; return m
    }

    // ─── Menu overlay (drawn in HUD) ──────────────────────
    private func showMenuOverlay() {
        guard let hud = hud else { return }
        let W = hud.size.width, H = hud.size.height

        // Dark vignette
        let v = SKShapeNode(rect:CGRect(x:0,y:0,width:W,height:H))
        v.fillColor = NSColor(red:0,green:0,blue:0,alpha:0.45); v.strokeColor = .clear
        v.name = "menuOverlay"; v.zPosition = 30
        hud.addChild(v)

        // Logo
        let logo1 = hudLabel("NEON", size:100, color:NSColor(red:0,green:0.95,blue:1,alpha:1), bold:true)
        logo1.position = CGPoint(x:W/2-78, y:H/2+60)
        logo1.name = "menuOverlay"; logo1.zPosition = 31
        hud.addChild(logo1)

        let logo2 = hudLabel("RUSH", size:100, color:NSColor(red:1,green:0.18,blue:0.6,alpha:1), bold:true)
        logo2.position = CGPoint(x:W/2+78, y:H/2+60)
        logo2.name = "menuOverlay2"; logo2.zPosition = 31
        hud.addChild(logo2)

        let sub = hudLabel("NATIVE macOS SYNTHWAVE RACER", size:13,
                           color:NSColor(white:1,alpha:0.35), bold:false)
        sub.position = CGPoint(x:W/2, y:H/2+14)
        sub.name = "menuOverlay"; sub.zPosition = 31
        hud.addChild(sub)

        // Start button hint
        let hint = hudLabel("PRESS  ENTER  TO  START", size:18,
                            color:NSColor(red:1,green:0.9,blue:0,alpha:1), bold:true)
        hint.position = CGPoint(x:W/2, y:H/2-40)
        hint.name = "menuOverlay"; hint.zPosition = 31
        hud.addChild(hint)
        hint.run(.repeatForever(.sequence([.fadeAlpha(to:0.2, duration:0.7),
                                           .fadeAlpha(to:1.0, duration:0.7)])))

        let ctrl = hudLabel("W/S/A/D — DRIVE    SPACE — NITRO    C — CAMERA    ESC — PAUSE", size:11,
                            color:NSColor(white:1,alpha:0.28), bold:false)
        ctrl.position = CGPoint(x:W/2, y:H/2-80)
        ctrl.name = "menuOverlay"; ctrl.zPosition = 31
        hud.addChild(ctrl)

        // Records
        if let def = UserDefaults.standard.object(forKey:"neonrush.bestlap") as? Double {
            let rec = hudLabel("BEST LAP  \(formatT(def))", size:14,
                               color:NSColor(red:0,green:0.95,blue:1,alpha:0.8), bold:false)
            rec.position = CGPoint(x:W/2, y:H/2-110)
            rec.name = "menuOverlay"; rec.zPosition = 31
            hud.addChild(rec)
        }
    }

    private func clearMenuOverlay() {
        hud.children.filter { $0.name == "menuOverlay" || $0.name == "menuOverlay2" }
               .forEach { $0.removeFromParent() }
    }

    // ─── Start race ───────────────────────────────────────
    func beginRace() {
        clearMenuOverlay()
        state = .countdown
        buildRaceScene()
        runCountdown()
    }

    private func buildRaceScene() {
        // Tear down any previous race scene
        scene = SCNScene()
        scnView.scene = scene
        player  = nil
        aiCars  = []

        // Track
        trackData = TrackBuilder.build(into: scene)

        // Player car
        player = CarNode(color: NSColor(red:1,green:0.18,blue:0.6,alpha:1))
        let wp0 = trackData.waypoints[0]
        let wp1 = trackData.waypoints[1]
        player.resetTo(waypoint: wp0, nextWP: wp1)
        player.carPosition.x += wp0.nx * 2  // slight offset from centre
        scene.rootNode.addChildNode(player)

        // AI cars
        aiCars = makeAIOpponents(scene: scene)
        for (i, ai) in aiCars.enumerated() {
            let wi = ai.waypointIndex < trackData.waypoints.count ? ai.waypointIndex : 0
            let wn = (wi+1) % trackData.waypoints.count
            ai.resetTo(waypoint: trackData.waypoints[wi], nextWP: trackData.waypoints[wn])
            ai.carPosition.x += trackData.waypoints[wi].nx * Float(i+1) * 3.5
        }

        // Camera start position
        camPos    = simd_float3(player.carPosition.x, player.carPosition.y + 6, player.carPosition.z - 14)
        camTarget = simd_float3(player.carPosition.x, player.carPosition.y,     player.carPosition.z)

        // HUD
        hud.setTrackSpline(trackData.spline)
    }

    private func runCountdown() {
        raceStartTime = 0
        driftAccum    = 0
        driftMultiplier = 1

        let steps: [(String, NSColor, Double)] = [
            ("3", NSColor(red:1,green:0.18,blue:0.6,alpha:1), 0),
            ("2", .yellow,                                     1.0),
            ("1", NSColor(red:0,green:0.95,blue:1,alpha:1),   2.0),
            ("GO!", NSColor(red:0.22,green:1,blue:0.08,alpha:1), 3.0),
        ]
        for (text,color,delay) in steps {
            DispatchQueue.main.asyncAfter(deadline:.now()+delay) { [weak self] in
                self?.hud.showCountdown(text, color:color)
                AudioManager.shared.playCountdownBeep(isGo: text == "GO!")
            }
        }
        DispatchQueue.main.asyncAfter(deadline:.now()+3.8) { [weak self] in
            guard let self else { return }
            self.hud.hideCountdown()
            self.state          = .racing
            self.raceStartTime  = CACurrentMediaTime()
            self.player.lapStartTime = self.raceStartTime
        }
    }

    // ─── Main update (called each frame by SCNSceneRendererDelegate) ──────────
    func update(time: TimeInterval) {
        let dt: Float = lastFrameTime > 0 ? Float(min(time - lastFrameTime, 0.05)) : 0.016
        lastFrameTime = time

        switch state {
        case .menu:
            updateMenuScene(dt: dt)
        case .countdown:
            updateCamera(dt: dt)
        case .racing:
            updateRace(dt: dt)
        case .paused, .finished:
            break
        }
    }

    // ─── Menu animation ───────────────────────────────────
    private func updateMenuScene(dt: Float) {
        menuCarAngle += dt * 0.2
        camera.simdPosition = simd_float3(sinf(menuCarAngle)*5, 14, cosf(menuCarAngle)*12 + 20)
        camera.look(at: SCNVector3(0, 2, -18), up: SCNVector3(0,1,0), localFront: SCNVector3(0,0,-1))
    }

    // ─── Race update ──────────────────────────────────────
    private func updateRace(dt: Float) {
        guard let player, let trackData else { return }

        // Handle one-shot inputs
        if input.state.cycleCamera {
            cameraMode = (cameraMode + 1) % numCamModes
            input.clearOneShots()
        }
        if input.state.resetCar {
            let wi  = player.waypointIndex
            let wn  = (wi+1) % trackData.waypoints.count
            player.resetTo(waypoint: trackData.waypoints[wi], nextWP: trackData.waypoints[wn])
            input.clearOneShots()
        }

        // Player physics
        player.update(dt: dt, input: input.state, trackData: trackData)

        // AI
        for ai in aiCars { ai.updateAI(dt: dt, trackData: trackData) }

        // Drift score
        if player.isDrifting {
            driftAccum  += dt * abs(player.steer) * player.speedKmh * 0.4
            driftMultiplier = min(8, 1 + Int(driftAccum / 200))
        } else if driftAccum > 0 {
            driftAccum  = max(0, driftAccum - dt * 80)
        }

        // Camera
        updateCamera(dt: dt)

        // Audio
        AudioManager.shared.update(speedKmh: player.speedKmh, nitroOn: player.nitroOn)

        // HUD
        hud.updateHUD(player:  player, aiCars:  aiCars,
                      raceStartTime: raceStartTime, cameraMode: cameraMode,
                      driftAccum: driftAccum, driftMultiplier: driftMultiplier)

        // Save record
        if player.bestLap < .infinity {
            let prev = UserDefaults.standard.double(forKey:"neonrush.bestlap")
            if prev == 0 || player.bestLap < prev {
                UserDefaults.standard.set(player.bestLap, forKey:"neonrush.bestlap")
            }
        }

        // Finish
        if player.raceFinished { finishRace() }
    }

    private func finishRace() {
        state = .finished
        let pos = calcPosition()
        let total = CACurrentMediaTime() - raceStartTime
        hud.showFinish(position: pos, total: total,
                       best: player.bestLap, lapTimes: player.lapTimes)
    }

    // ─── Camera ───────────────────────────────────────────
    private func updateCamera(dt: Float) {
        guard let player else { return }
        let p   = player.carPosition
        let ang = player.carAngle
        let fwd = simd_float3(sinf(ang), 0, cosf(ang))
        let rgt = simd_float3(cosf(ang), 0,-sinf(ang))

        var target: simd_float3
        var look:   simd_float3

        switch cameraMode {
        case 0: // Chase
            target = p - fwd * 13 + simd_float3(0, 5.5, 0)
            look   = p + fwd * 10 + simd_float3(0, 1, 0)
        case 1: // Low chase
            target = p - fwd * 9  + simd_float3(0, 2.5, 0)
            look   = p + fwd * 14 + simd_float3(0, 0.5, 0)
        case 2: // Cockpit
            target = p + fwd * 1.2 + simd_float3(0, 1.35, 0)
            look   = p + fwd * 20  + simd_float3(0, 0.8, 0)
        case 3: // TV broadcast
            target = p - fwd * 6 + rgt * 22 + simd_float3(0, 10, 0)
            look   = p
        case 4: // Helicopter orbit
            let t  = Float(CACurrentMediaTime()) * 0.3
            target = p + simd_float3(cosf(t)*22, 42, sinf(t)*22)
            look   = p
        default:
            target = p - fwd * 13 + simd_float3(0, 5.5, 0)
            look   = p + fwd * 10
        }

        let lerpRate: Float = dt * 6
        camPos    = camPos    + (target - camPos)    * min(1, lerpRate)
        camTarget = camTarget + (look   - camTarget) * min(1, dt * 9)
        camera.simdPosition = camPos
        camera.look(at: SCNVector3(camTarget.x, camTarget.y, camTarget.z),
                    up: SCNVector3(0,1,0), localFront: SCNVector3(0,0,-1))

        // Dynamic FOV: wider during nitro
        let targetFOV: CGFloat = player.nitroOn ? 82 : 65
        let cam = camera.camera!
        cam.fieldOfView += (targetFOV - cam.fieldOfView) * CGFloat(dt * 4)
    }

    // ─── Position ─────────────────────────────────────────
    private func calcPosition() -> Int {
        guard let player else { return 1 }
        let pp = player.lap * 10000 + player.waypointIndex
        var pos = 1
        for ai in aiCars {
            if ai.lap * 10000 + ai.waypointIndex > pp { pos += 1 }
        }
        return pos
    }

    // ─── Input forwarding ─────────────────────────────────
    override var acceptsFirstResponder: Bool { true }

    override func keyDown(with event: NSEvent) {
        input.keyDown(event)

        // Enter key → start race from menu
        if state == .menu && (event.keyCode == 36 || event.keyCode == 76) {
            beginRace(); return
        }

        // Escape key handling
        if input.state.pause {
            handlePause()
            input.clearOneShots()
        }

        // R key to restart from finish
        if input.state.resetCar && state == .finished {
            restartRace()
            input.clearOneShots()
        }
    }

    override func keyUp(with event: NSEvent) {
        input.keyUp(event)
    }

    override func viewDidAppear() {
        super.viewDidAppear()
        view.window?.makeFirstResponder(self)
    }

    // ─── Pause / restart ──────────────────────────────────
    private func handlePause() {
        switch state {
        case .racing:
            state = .paused
            scnView.isPlaying = false
            hud.showPause()
        case .paused:
            state = .racing
            scnView.isPlaying = true
            lastFrameTime = CACurrentMediaTime()
            hud.hidePause()
        default: break
        }
    }

    func restartRace() {
        hud.children.filter { $0.name == "finishPanel" || $0.name == "pausePanel" }
               .forEach { $0.removeFromParent() }
        state = .countdown
        buildRaceScene()
        runCountdown()
    }

    func returnToMenu() {
        hud.children.filter { $0.name == "finishPanel" || $0.name == "pausePanel" }
               .forEach { $0.removeFromParent() }
        player?.removeFromParentNode()
        aiCars.forEach { $0.removeFromParentNode() }
        player = nil; aiCars = []
        setupMenuScene()
    }

    // ─── Helper ───────────────────────────────────────────
    private func formatT(_ t: TimeInterval) -> String {
        let m  = Int(t/60), s = Int(t)%60, ms = Int((t.truncatingRemainder(dividingBy:1))*1000)
        return String(format:"%d:%02d.%03d", m, s, ms)
    }

    private func hudLabel(_ text: String, size: CGFloat, color: NSColor, bold: Bool) -> SKLabelNode {
        let l = SKLabelNode(text:text)
        l.fontName   = bold ? "CourierNewPS-BoldMT" : "CourierNewPSMT"
        l.fontSize   = size
        l.fontColor  = color
        l.verticalAlignmentMode   = .baseline
        l.horizontalAlignmentMode = .center
        return l
    }
}

// ─── SCNSceneRendererDelegate ─────────────────────────────────────────────────
extension GameViewController: SCNSceneRendererDelegate {
    func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
        DispatchQueue.main.async { [weak self] in
            self?.update(time: time)
        }
    }
}
