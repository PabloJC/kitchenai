import SwiftUI
import FirebaseCore
import FirebaseAppCheck
import ComposeApp   // el framework exporta también los tipos de :shared

@main
struct iOSApp: App {
    init() {
        // App Check before configure(): the factory is consulted during configuration.
        // #if DEBUG, not a build type as on Android: the iOS SDK is a single product, so the
        // debug branch is compiled out of a Release binary.
        #if DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        #else
        AppCheck.setAppCheckProviderFactory(AttestationProviderFactory())
        #endif

        // Order matters: Firebase before anything Kotlin.
        FirebaseApp.configure()

        // PresentationModuleKt, not SharedModuleKt: `initKoin` lives in :shared, which
        // cannot see :composeApp.
        PresentationModuleKt.doInitKoinUi(appDeclaration: { _ in })
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}
