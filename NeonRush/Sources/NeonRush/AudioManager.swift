import AVFoundation

// Synthesised engine + boost + lap sounds — no external files needed.
final class AudioManager {
    static let shared = AudioManager()

    private var engine    = AVAudioEngine()
    private var engOsc    = AVAudioUnitVarispeed()  // re-pitched white noise
    private var engNode: AVAudioPlayerNode!
    private var engBuffer: AVAudioPCMBuffer!
    private var isRunning = false
    private var enginePitch: Float = 0.5  // 0..1

    // Oscillator via AVAudioSourceNode for engine rumble
    private var sourceNode: AVAudioSourceNode?
    private var phase: Float = 0
    private var targetFreq: Float = 80
    private var currentFreq: Float = 80
    private var targetGain: Float = 0
    private var currentGain: Float = 0

    private init() {}

    func start() {
        guard !isRunning else { return }
        isRunning = true

        let outputFormat = engine.outputNode.inputFormat(forBus: 0)
        let sr = Float(outputFormat.sampleRate)

        sourceNode = AVAudioSourceNode { [weak self] _, _, frameCount, audioBufferList -> OSStatus in
            guard let self else { return noErr }
            let ablPointer = UnsafeMutableAudioBufferListPointer(audioBufferList)
            let incFreq = self.targetFreq
            let incGain = self.targetGain

            // Smooth
            self.currentFreq += (incFreq - self.currentFreq) * 0.05
            self.currentGain += (incGain - self.currentGain) * 0.08

            for frame in 0..<Int(frameCount) {
                // Sawtooth + harmonic mix for engine character
                self.phase += self.currentFreq / sr
                if self.phase >= 1 { self.phase -= 1 }
                let saw  = 2.0 * self.phase - 1.0
                let saw2 = 2.0 * fmodf(self.phase * 2, 1) - 1.0
                let sample = (saw * 0.6 + saw2 * 0.3) * self.currentGain * 0.12

                for buffer in ablPointer {
                    let buf = UnsafeMutableBufferPointer<Float>(buffer)
                    if frame < buf.count { buf[frame] = sample }
                }
            }
            return noErr
        }

        engine.attach(sourceNode!)
        engine.connect(sourceNode!, to: engine.mainMixerNode,
                       format: AVAudioFormat(standardFormatWithSampleRate: Double(sr), channels: 1))

        try? engine.start()
    }

    func update(speedKmh: Float, nitroOn: Bool) {
        // Pitch: 80 Hz idle → 340 Hz at top speed
        targetFreq = 80 + speedKmh * 1.5
        targetGain = speedKmh > 2 ? min(1.0, 0.2 + speedKmh / 200.0) : 0
        if nitroOn { targetFreq *= 1.4 }
    }

    func playBoostHit() {
        _playTone(frequencies: [440, 660, 880], durations: [0.06, 0.06, 0.1], gain: 0.18)
    }

    func playLapDing() {
        _playTone(frequencies: [880, 1100, 1320, 1760], durations: [0.15, 0.15, 0.15, 0.25], gain: 0.12)
    }

    func playCountdownBeep(isGo: Bool) {
        _playTone(frequencies: isGo ? [880, 1760] : [440], durations: isGo ? [0.1, 0.2] : [0.12], gain: 0.15)
    }

    private func _playTone(frequencies: [Float], durations: [Float], gain: Float) {
        guard isRunning else { return }
        let sr = Float(engine.outputNode.inputFormat(forBus: 0).sampleRate)
        DispatchQueue.global().async { [weak self] in
            guard let self else { return }
            for (i, freq) in frequencies.enumerated() {
                let dur = i < durations.count ? durations[i] : 0.1
                let count = Int(sr * dur)
                let fmt = AVAudioFormat(standardFormatWithSampleRate: Double(sr), channels: 1)!
                guard let buf = AVAudioPCMBuffer(pcmFormat: fmt, frameCapacity: AVAudioFrameCount(count)) else { continue }
                buf.frameLength = AVAudioFrameCount(count)
                let ch = buf.floatChannelData![0]
                for j in 0..<count {
                    let env: Float = j < count/10 ? Float(j)/(Float(count)/10) :
                                     j > count*8/10 ? Float(count-j)/(Float(count)/5) : 1.0
                    ch[j] = sinf(2 * .pi * freq * Float(j) / sr) * gain * env
                }
                let player = AVAudioPlayerNode()
                self.engine.attach(player)
                self.engine.connect(player, to: self.engine.mainMixerNode, format: fmt)
                player.play()
                player.scheduleBuffer(buf) {
                    self.engine.detach(player)
                }
                Thread.sleep(forTimeInterval: Double(dur))
            }
        }
    }
}
