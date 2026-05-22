import SpriteKit
import AppKit

// ─── HUD drawn as an SKScene overlay on top of the SCNView ───────────────────

final class HUDScene: SKScene {

    // Nodes
    private var timerLabel    = SKLabelNode()
    private var lapLabel      = SKLabelNode()
    private var posLabel      = SKLabelNode()
    private var bestLabel     = SKLabelNode()
    private var camLabel      = SKLabelNode()
    private var gearLabel     = SKLabelNode()
    private var lapTimesNode  = SKNode()
    private var nitroBarFill  = SKShapeNode()
    private var speedoNode    = SKNode()
    private var minimapNode   = SKNode()
    private var minimapTrack  = SKShapeNode()
    private var minimapPlayer = SKShapeNode()
    private var minimapAIs    = [SKShapeNode]()
    private var needleNode    = SKShapeNode()
    private var driftNode     = SKNode()
    private var driftScore    = SKLabelNode()
    private var driftMulti    = SKLabelNode()

    // State
    var trackSpline: [(Float, Float)] = []
    var splineMinX: Float = 0, splineMaxX: Float = 0
    var splineMinZ: Float = 0, splineMaxZ: Float = 0

    // Camera mode names
    private let camModes = ["CHASE", "LOW CAM", "COCKPIT", "TV SHOT", "HELICOPTER"]

    // ─── Setup ────────────────────────────────────────────
    override func didMove(to view: SKView) {
        backgroundColor = .clear
        isUserInteractionEnabled = false
        buildTopBar()
        buildSpeedometer()
        buildNitroBar()
        buildMinimap()
        buildDriftPanel()
        buildLapTimesPanel()
    }

    // ─── Top bar ──────────────────────────────────────────
    private func buildTopBar() {
        let W = size.width, H = size.height

        // gradient background strip
        let bar = SKShapeNode(rect: CGRect(x:0, y:H-70, width:W, height:70))
        bar.fillColor    = NSColor(red:0,green:0,blue:0,alpha:0.65)
        bar.strokeColor  = .clear
        addChild(bar)

        posLabel = makeLabel("P1", size:42, color:.yellow, bold:true)
        posLabel.horizontalAlignmentMode = .left
        posLabel.position = CGPoint(x:20, y:H-54)
        addChild(posLabel)

        lapLabel = makeLabel("LAP 1 / 3", size:13, color:NSColor(white:1,alpha:0.5), bold:false)
        lapLabel.horizontalAlignmentMode = .center
        lapLabel.position = CGPoint(x:W/2, y:H-22)
        addChild(lapLabel)

        timerLabel = makeLabel("0:00.000", size:32, color:.white, bold:true)
        timerLabel.horizontalAlignmentMode = .center
        timerLabel.position = CGPoint(x:W/2, y:H-56)
        addChild(timerLabel)

        bestLabel = makeLabel("BEST  --:--.---", size:12, color:NSColor(white:1,alpha:0.35), bold:false)
        bestLabel.horizontalAlignmentMode = .right
        bestLabel.position = CGPoint(x:W-20, y:H-24)
        addChild(bestLabel)

        let bv = makeLabel("", size:16, color:NSColor(red:0,green:0.95,blue:1,alpha:1), bold:true)
        bv.name = "bestVal"
        bv.horizontalAlignmentMode = .right
        bv.position = CGPoint(x:W-20, y:H-50)
        addChild(bv)

        camLabel = makeLabel("CHASE", size:10, color:NSColor(white:1,alpha:0.3), bold:false)
        camLabel.horizontalAlignmentMode = .right
        camLabel.position = CGPoint(x:W-20, y:H-68)
        addChild(camLabel)
    }

    // ─── Speedometer ──────────────────────────────────────
    private func buildSpeedometer() {
        let W = size.width, H = size.height
        let cx: CGFloat = W - 108, cy: CGFloat = 108
        speedoNode.position = CGPoint(x:cx, y:cy)

        // Outer ring
        let ring = SKShapeNode(circleOfRadius:88)
        ring.fillColor   = NSColor(red:0,green:0,blue:0.1,alpha:0.88)
        ring.strokeColor = NSColor(red:0,green:0.95,blue:1,alpha:0.3)
        ring.lineWidth   = 1.5
        speedoNode.addChild(ring)

        // Tick marks
        for i in 0...20 {
            let a   = CGFloat(0.75*Double.pi + Double(i)/20.0 * 1.5*Double.pi)
            let isMajor = i % 4 == 0
            let r0: CGFloat = isMajor ? 70 : 76, r1: CGFloat = 84
            let l = SKShapeNode()
            let path = CGMutablePath()
            path.move(to:    CGPoint(x:cos(a)*r0, y:sin(a)*r0))
            path.addLine(to: CGPoint(x:cos(a)*r1, y:sin(a)*r1))
            l.path        = path
            l.strokeColor = isMajor ? NSColor(white:1,alpha:0.7) : NSColor(white:1,alpha:0.25)
            l.lineWidth   = isMajor ? 2 : 1
            speedoNode.addChild(l)
            if isMajor {
                let v = i * 10
                let lbl = makeLabel("\(v)", size:9, color:NSColor(white:1,alpha:0.4), bold:false)
                lbl.position = CGPoint(x:cos(a)*58, y:sin(a)*58-4)
                speedoNode.addChild(lbl)
            }
        }

        // Speed arc (redrawn each frame)
        let arcNode = SKShapeNode()
        arcNode.name = "speedArc"
        speedoNode.addChild(arcNode)

        // Needle
        needleNode = SKShapeNode()
        needleNode.name = "needle"
        speedoNode.addChild(needleNode)

        // Center dot
        let dot = SKShapeNode(circleOfRadius:5)
        dot.fillColor   = NSColor(red:1,green:0.18,blue:0.6,alpha:1)
        dot.strokeColor = .clear
        dot.zPosition   = 10
        speedoNode.addChild(dot)

        // Speed value
        let sv = makeLabel("0", size:26, color:.white, bold:true)
        sv.name     = "speedVal"
        sv.position = CGPoint(x:0, y:-22)
        speedoNode.addChild(sv)
        let su = makeLabel("km/h", size:9, color:NSColor(white:1,alpha:0.35), bold:false)
        su.position = CGPoint(x:0, y:-42)
        speedoNode.addChild(su)

        gearLabel = makeLabel("1", size:36, color:.yellow, bold:true)
        gearLabel.position = CGPoint(x:-55, y:-30)
        speedoNode.addChild(gearLabel)

        addChild(speedoNode)
    }

    // ─── Nitro bar ────────────────────────────────────────
    private func buildNitroBar() {
        let W = size.width, cy: CGFloat = 28
        let bg = SKShapeNode(rect: CGRect(x:W/2-110, y:cy-7, width:220, height:14), cornerRadius:7)
        bg.fillColor   = NSColor(white:1,alpha:0.06)
        bg.strokeColor = NSColor(red:1,green:0.18,blue:0.6,alpha:0.35)
        bg.lineWidth   = 1
        addChild(bg)

        let lbl = makeLabel("NITRO", size:9, color:NSColor(red:1,green:0.18,blue:0.6,alpha:0.8), bold:false)
        lbl.position = CGPoint(x:W/2, y:cy+10)
        addChild(lbl)

        nitroBarFill = SKShapeNode(rect: CGRect(x:W/2-109, y:cy-6, width:218, height:12), cornerRadius:6)
        nitroBarFill.fillColor    = NSColor(red:0.6,green:0,blue:1,alpha:1)
        nitroBarFill.strokeColor  = .clear
        nitroBarFill.glowWidth    = 4
        addChild(nitroBarFill)
    }

    // ─── Minimap ──────────────────────────────────────────
    private func buildMinimap() {
        let cx: CGFloat = 90, cy: CGFloat = 90
        minimapNode.position = CGPoint(x:cx, y:cy)

        let bg = SKShapeNode(circleOfRadius:78)
        bg.fillColor   = NSColor(red:0,green:0,blue:0.08,alpha:0.85)
        bg.strokeColor = NSColor(red:0,green:0.95,blue:1,alpha:0.4)
        bg.lineWidth   = 1.5
        minimapNode.addChild(bg)

        minimapTrack = SKShapeNode()
        minimapTrack.strokeColor = NSColor(red:0,green:0.95,blue:1,alpha:0.5)
        minimapTrack.lineWidth   = 3
        minimapNode.addChild(minimapTrack)

        minimapPlayer = SKShapeNode(circleOfRadius:6)
        minimapPlayer.fillColor   = NSColor(red:1,green:0.18,blue:0.6,alpha:1)
        minimapPlayer.strokeColor = .white
        minimapPlayer.lineWidth   = 1.5
        minimapPlayer.zPosition   = 5
        minimapNode.addChild(minimapPlayer)

        let aiColors: [NSColor] = [
            NSColor(red:0.22,green:1,blue:0.08,alpha:1),
            NSColor(red:1,green:0.9,blue:0,alpha:1),
            NSColor(red:1,green:0.4,blue:0.1,alpha:1)
        ]
        for c in aiColors {
            let dot = SKShapeNode(circleOfRadius:4)
            dot.fillColor = c; dot.strokeColor = .clear
            minimapNode.addChild(dot)
            minimapAIs.append(dot)
        }

        let ring = SKShapeNode(circleOfRadius:78)
        ring.fillColor   = .clear
        ring.strokeColor = NSColor(red:0,green:0.95,blue:1,alpha:0.5)
        ring.lineWidth   = 1.5
        minimapNode.addChild(ring)

        let lbl = makeLabel("MAP", size:8, color:NSColor(white:1,alpha:0.25), bold:false)
        lbl.position = CGPoint(x:0, y:-90)
        minimapNode.addChild(lbl)

        addChild(minimapNode)
    }

    // ─── Drift panel ──────────────────────────────────────
    private func buildDriftPanel() {
        driftNode.position = CGPoint(x:size.width/2, y:size.height/2 - 80)
        driftNode.alpha    = 0

        let lbl = makeLabel("DRIFT", size:10, color:NSColor(red:1,green:0.18,blue:0.6,alpha:1), bold:false)
        lbl.position = CGPoint(x:0, y:46)
        driftNode.addChild(lbl)

        driftScore = makeLabel("0", size:44, color:NSColor(red:1,green:0.18,blue:0.6,alpha:1), bold:true)
        driftNode.addChild(driftScore)

        driftMulti = makeLabel("x1", size:16, color:.yellow, bold:true)
        driftMulti.position = CGPoint(x:0, y:-36)
        driftNode.addChild(driftMulti)

        addChild(driftNode)
    }

    // ─── Lap times panel ──────────────────────────────────
    private func buildLapTimesPanel() {
        lapTimesNode.position = CGPoint(x:16, y:size.height - 90)
        addChild(lapTimesNode)
    }

    // ═══ Per-frame update ════════════════════════════════

    func updateHUD(player: CarNode, aiCars: [AICarNode],
                   raceStartTime: TimeInterval, cameraMode: Int,
                   driftAccum: Float, driftMultiplier: Int) {

        let now = CACurrentMediaTime()

        // Timer
        if raceStartTime > 0 {
            let elapsed = now - raceStartTime
            timerLabel.text = formatTime(elapsed)
        }

        // Lap + position
        lapLabel.text = "LAP \(player.lap) / 3"
        let pos = calcPosition(player: player, aiCars: aiCars)
        posLabel.text = "P\(pos)"

        // Best lap
        if player.bestLap < .infinity {
            childNode(withName:"bestVal")?.run(.run { n in
                (n as? SKLabelNode)?.text = self.formatTime(player.bestLap)
            })
        }

        // Camera label
        camLabel.text = camModes[cameraMode % camModes.count]

        // Speedo
        updateSpeedometer(speedKmh: CGFloat(player.speedKmh))
        gearLabel.text = "\(player.gear)"

        // Nitro
        nitroBarFill.xScale = CGFloat(player.nitro)
        nitroBarFill.alpha  = player.nitroOn ? 1.0 : 0.7

        // Minimap
        updateMinimap(player: player, aiCars: aiCars)

        // Drift display
        if player.isDrifting && driftAccum > 0 {
            driftNode.alpha = 1
            driftScore.text = "\(Int(driftAccum))"
            driftMulti.text = "x\(driftMultiplier)"
        } else {
            driftNode.alpha = max(0, driftNode.alpha - 0.05)
        }

        // Lap times sidebar
        updateLapTimesPanel(player: player)
    }

    private func updateSpeedometer(speedKmh: CGFloat) {
        let maxSpeed: CGFloat = 220
        let fraction = min(1, speedKmh / maxSpeed)
        let startAngle: CGFloat = CGFloat(0.75 * Double.pi)
        let sweep: CGFloat      = CGFloat(1.5 * Double.pi)
        let endAngle = startAngle + fraction * sweep

        // Arc
        if let arc = speedoNode.childNode(withName:"speedArc") as? SKShapeNode {
            let p = CGMutablePath()
            p.addArc(center:.zero, radius:80, startAngle:startAngle,
                     endAngle:endAngle, clockwise:false)
            arc.path        = p
            arc.strokeColor = arcColor(fraction:fraction)
            arc.lineWidth   = 8
            arc.lineCap     = .round
            arc.fillColor   = .clear
        }

        // Needle
        let needleAngle = startAngle + fraction * sweep
        let path = CGMutablePath()
        path.move(to:    CGPoint(x:cos(needleAngle)*(-6),  y:sin(needleAngle)*(-6)))
        path.addLine(to: CGPoint(x:cos(needleAngle)*72,    y:sin(needleAngle)*72))
        needleNode.path        = path
        needleNode.strokeColor = NSColor(red:1,green:0.18,blue:0.6,alpha:1)
        needleNode.lineWidth   = 2.5
        needleNode.lineCap     = .round

        if let sv = speedoNode.childNode(withName:"speedVal") as? SKLabelNode {
            sv.text = "\(Int(speedKmh))"
        }
    }

    private func arcColor(fraction: CGFloat) -> NSColor {
        if fraction < 0.5  { return NSColor(red:0,green:0.95,blue:1,alpha:1) }
        if fraction < 0.78 { return NSColor(red:0.22,green:1,blue:0.08,alpha:1) }
        return NSColor(red:1,green:0.18,blue:0.6,alpha:1)
    }

    private func updateMinimap(player: CarNode, aiCars: [AICarNode]) {
        guard !trackSpline.isEmpty else { return }
        let r: CGFloat = 72
        let scaleX = r*2 / CGFloat(splineMaxX - splineMinX + 1)
        let scaleZ = r*2 / CGFloat(splineMaxZ - splineMinZ + 1)
        let scale  = min(scaleX, scaleZ) * 0.85
        let offX   = CGFloat(-(splineMinX + splineMaxX)*0.5)
        let offZ   = CGFloat(-(splineMinZ + splineMaxZ)*0.5)

        func toMap(_ x:Float, _ z:Float) -> CGPoint {
            CGPoint(x: CGFloat(x)*scale + offX*scale,
                    y: CGFloat(z)*scale + offZ*scale)
        }

        // Track outline (rebuild only when needed — do it always for simplicity)
        let tp = CGMutablePath()
        if let first = trackSpline.first {
            tp.move(to: toMap(first.0, first.1))
            for pt in trackSpline.dropFirst() { tp.addLine(to: toMap(pt.0, pt.1)) }
            tp.closeSubpath()
        }
        minimapTrack.path = tp

        minimapPlayer.position = toMap(player.carPosition.x, player.carPosition.z)
        for (i, ai) in aiCars.enumerated() {
            if i < minimapAIs.count {
                minimapAIs[i].position = toMap(ai.carPosition.x, ai.carPosition.z)
            }
        }
    }

    private func updateLapTimesPanel(_ player: CarNode) {
        lapTimesNode.removeAllChildren()
        for (i, t) in player.lapTimes.enumerated() {
            let isBest = t == player.bestLap
            let entry  = makeLabel("LAP \(i+1)  \(formatTime(t))", size:11,
                                   color: isBest ? NSColor(red:0,green:0.95,blue:1,alpha:1)
                                                 : NSColor(white:1,alpha:0.4),
                                   bold: isBest)
            entry.horizontalAlignmentMode = .left
            entry.position = CGPoint(x:0, y:-CGFloat(i)*18)
            let bg = SKShapeNode(rect: CGRect(x:-4,y:-12,width:160,height:16), cornerRadius:2)
            bg.fillColor   = NSColor(red:0,green:0,blue:0,alpha:0.45)
            bg.strokeColor = isBest ? NSColor(red:0,green:0.95,blue:1,alpha:0.4) : .clear
            bg.lineWidth   = 1
            entry.addChild(bg)
            lapTimesNode.addChild(entry)
        }
    }

    // ─── Overlay screens ──────────────────────────────────

    func showCountdown(_ text: String, color: NSColor) {
        removeAction(forKey:"cd")
        childNode(withName:"cdLabel")?.removeFromParent()
        let lbl = makeLabel(text, size:130, color:color, bold:true)
        lbl.name      = "cdLabel"
        lbl.position  = CGPoint(x:size.width/2, y:size.height/2 - 50)
        lbl.zPosition = 20
        addChild(lbl)
        let seq = SKAction.sequence([
            .scale(to:1.3, duration:0),
            .group([.scale(to:1.0, duration:0.15), .fadeIn(withDuration:0.05)]),
            .wait(forDuration:0.65),
            .group([.scale(to:0.7, duration:0.2), .fadeOut(withDuration:0.2)]),
            .removeFromParent()
        ])
        lbl.run(seq, withKey:"cd")
    }

    func hideCountdown() { childNode(withName:"cdLabel")?.removeFromParent() }

    func showPause() {
        guard childNode(withName:"pausePanel") == nil else { return }
        let panel = buildOverlayPanel(title:"PAUSED",
                                       titleColor:NSColor(red:0,green:0.95,blue:1,alpha:1))
        panel.name = "pausePanel"
        addChild(panel)
    }

    func hidePause() { childNode(withName:"pausePanel")?.removeFromParent() }

    func showFinish(position: Int, total: TimeInterval,
                    best: TimeInterval, lapTimes: [TimeInterval]) {
        guard childNode(withName:"finishPanel") == nil else { return }
        let panel = buildFinishPanel(pos:position, total:total, best:best, laps:lapTimes)
        panel.name = "finishPanel"
        addChild(panel)
    }

    private func buildOverlayPanel(title: String, titleColor: NSColor) -> SKNode {
        let node = SKNode()
        node.zPosition = 50
        let bg = SKShapeNode(rect: CGRect(x:-180,y:-100,width:360,height:220), cornerRadius:8)
        bg.fillColor   = NSColor(red:0,green:0,blue:0.08,alpha:0.92)
        bg.strokeColor = NSColor(red:0,green:0.95,blue:1,alpha:0.35)
        bg.lineWidth   = 1.5
        node.addChild(bg)
        let lbl = makeLabel(title, size:30, color:titleColor, bold:true)
        lbl.position = CGPoint(x:0, y:76)
        node.addChild(lbl)
        node.position = CGPoint(x:size.width/2, y:size.height/2)
        return node
    }

    private func buildFinishPanel(pos:Int, total:TimeInterval,
                                   best:TimeInterval, laps:[TimeInterval]) -> SKNode {
        let node = SKNode()
        node.zPosition = 50
        let bg = SKShapeNode(rect:CGRect(x:-220,y:-160,width:440,height:360), cornerRadius:10)
        bg.fillColor   = NSColor(red:0,green:0,blue:0.08,alpha:0.94)
        bg.strokeColor = NSColor(red:1,green:0.9,blue:0,alpha:0.4)
        bg.lineWidth   = 2
        node.addChild(bg)

        let title = makeLabel("🏆  RACE COMPLETE", size:28, color:.yellow, bold:true)
        title.position = CGPoint(x:0, y:138)
        node.addChild(title)

        let p = makeLabel("POSITION  P\(pos)", size:20, color:.white, bold:false)
        p.position = CGPoint(x:0, y:100); node.addChild(p)

        let t = makeLabel("TIME     \(formatTime(total))", size:16,
                          color:NSColor(red:0,green:0.95,blue:1,alpha:1), bold:false)
        t.position = CGPoint(x:0, y:68); node.addChild(t)

        let b = makeLabel("BEST LAP  \(formatTime(best))", size:16,
                          color:NSColor(red:0.22,green:1,blue:0.08,alpha:1), bold:false)
        b.position = CGPoint(x:0, y:40); node.addChild(b)

        for (i, lt) in laps.enumerated() {
            let le = makeLabel("LAP \(i+1)   \(formatTime(lt))", size:13,
                               color: lt==best ? NSColor(red:0,green:0.95,blue:1,alpha:1)
                                               : NSColor(white:1,alpha:0.45), bold:false)
            le.position = CGPoint(x:0, y:CGFloat(8 - i*18))
            node.addChild(le)
        }

        let hint = makeLabel("R = RESTART    ESC = MENU", size:11,
                             color:NSColor(white:1,alpha:0.3), bold:false)
        hint.position = CGPoint(x:0, y:-140); node.addChild(hint)
        node.position = CGPoint(x:size.width/2, y:size.height/2)
        return node
    }

    // ─── Helpers ──────────────────────────────────────────

    func setTrackSpline(_ spline: [(Float,Float)]) {
        trackSpline = spline
        splineMinX = spline.map{$0.0}.min() ?? 0
        splineMaxX = spline.map{$0.0}.max() ?? 1
        splineMinZ = spline.map{$0.1}.min() ?? 0
        splineMaxZ = spline.map{$0.1}.max() ?? 1
    }

    private func formatTime(_ t: TimeInterval) -> String {
        guard t > 0 && t < 99999 else { return "--:--.---" }
        let m  = Int(t / 60)
        let s  = Int(t) % 60
        let ms = Int((t.truncatingRemainder(dividingBy:1)) * 1000)
        return String(format:"%d:%02d.%03d", m, s, ms)
    }

    private func calcPosition(player: CarNode, aiCars: [AICarNode]) -> Int {
        let pp = player.lap * 10000 + player.waypointIndex
        var pos = 1
        for ai in aiCars {
            if ai.lap * 10000 + ai.waypointIndex > pp { pos += 1 }
        }
        return pos
    }

    private func makeLabel(_ text: String, size: CGFloat, color: NSColor, bold: Bool) -> SKLabelNode {
        let l = SKLabelNode(text: text)
        l.fontName      = bold ? "CourierNewPS-BoldMT" : "CourierNewPSMT"
        l.fontSize      = size
        l.fontColor     = color
        l.verticalAlignmentMode   = .baseline
        l.horizontalAlignmentMode = .center
        return l
    }
}
