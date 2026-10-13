import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        DemoRuntimeKt.doInitDemoRouter()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
