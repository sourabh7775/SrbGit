import AppKit

final class AppDelegate: NSObject, NSApplicationDelegate {
    private var window: NSWindow!

    func applicationDidFinishLaunching(_ notification: Notification) {
        // Minimal menu bar
        let menu = NSMenu()
        let item = NSMenuItem()
        menu.addItem(item)
        let sub = NSMenu()
        item.submenu = sub
        sub.addItem(NSMenuItem(title: "Quit Neon Rush",
                               action: #selector(NSApplication.terminate(_:)),
                               keyEquivalent: "q"))
        NSApp.mainMenu = menu

        window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 1440, height: 900),
            styleMask: [.titled, .closable, .miniaturizable, .resizable,
                        .fullSizeContentView],
            backing: .buffered,
            defer: false
        )
        window.title = "NEON RUSH"
        window.titlebarAppearsTransparent = true
        window.titleVisibility = .hidden
        window.backgroundColor = .black
        window.center()
        window.minSize = NSSize(width: 800, height: 500)

        let vc = GameViewController()
        window.contentViewController = vc
        window.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }
}
