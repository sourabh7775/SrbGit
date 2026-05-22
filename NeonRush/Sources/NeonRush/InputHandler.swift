import AppKit

struct InputState {
    var throttle  = false
    var brake     = false
    var left      = false
    var right     = false
    var nitro     = false
    var cycleCamera = false
    var resetCar    = false
    var pause       = false
}

final class InputHandler {
    private(set) var state = InputState()

    func keyDown(_ event: NSEvent) {
        apply(event, on: true)
    }

    func keyUp(_ event: NSEvent) {
        apply(event, on: false)
        // One-shot flags cleared after consumed
        state.cycleCamera = false
        state.resetCar    = false
        state.pause       = false
    }

    private func apply(_ event: NSEvent, on: Bool) {
        switch event.keyCode {
        case 13, 126: state.throttle     = on  // W, Up
        case 1,  125: state.brake        = on  // S, Down
        case 0,  123: state.left         = on  // A, Left
        case 2,  124: state.right        = on  // D, Right
        case 49:      state.nitro        = on  // Space
        case 8:  if on { state.cycleCamera = true }  // C
        case 15: if on { state.resetCar    = true }  // R
        case 53: if on { state.pause       = true }  // Esc
        default: break
        }
    }

    func clearOneShots() {
        state.cycleCamera = false
        state.resetCar    = false
        state.pause       = false
    }
}
