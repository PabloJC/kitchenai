import SwiftUI
import FirebaseCore
import ComposeApp   // el framework exporta también los tipos de :shared

@main
struct iOSApp: App {
    init() {
        // Orden importante: Firebase antes de tocar nada de Kotlin.
        FirebaseApp.configure()
        SharedModuleKt.doInitKoin(appDeclaration: { _ in })
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}
