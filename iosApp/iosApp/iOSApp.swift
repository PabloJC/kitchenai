import SwiftUI
import FirebaseCore
import FirebaseAppCheck
import ComposeApp   // el framework exporta también los tipos de :shared

@main
struct iOSApp: App {
    init() {
        // App Check antes de configure(): la fábrica se consulta durante la
        // configuración, y lo que se envíe antes de instalarla sale sin token.
        //
        // El proveedor de debug no se recorta por build type como en Android
        // —el SDK de iOS es un único producto—, así que la condición es #if DEBUG,
        // evaluada en compilación: en un build de Release la rama de debug no
        // existe en el binario.
        #if DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        #else
        AppCheck.setAppCheckProviderFactory(AppAttestProviderFactory())
        #endif

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
