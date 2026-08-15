import SwiftUI
import FirebaseCore
import FirebaseAppCheck
import ComposeApp   // the framework also exports the :shared types

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

        // Read rather than defaulted: the region comes from Config.xcconfig through Info.plist,
        // and a build that lost it must stop here instead of calling a region nobody chose.
        guard let region = Bundle.main.object(forInfoDictionaryKey: "FunctionsRegion") as? String,
              !region.isEmpty else {
            fatalError("FunctionsRegion is missing from Info.plist. See iosApp/Configuration/Config.xcconfig.")
        }

        // PresentationModuleKt, not SharedModuleKt: `initKoin` lives in :shared, which
        // cannot see :composeApp.
        PresentationModuleKt.doInitKoinUi(functionsRegion: region, appDeclaration: { _ in })
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}
