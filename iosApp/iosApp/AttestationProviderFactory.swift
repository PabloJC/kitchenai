import FirebaseCore
import FirebaseAppCheck
import os

/// Fábrica de App Check para builds de release: App Attest, y DeviceCheck
/// cuando el dispositivo no soporta el primero.
///
/// No se llama `AppAttestProviderFactory` porque ese nombre ya es el de una
/// clase del SDK (`FIRAppAttestProviderFactory`, con ese `NS_SWIFT_NAME`). Una
/// declaración local eclipsa a la importada sin aviso, así que borrar este
/// fichero no daría un error de compilación: cambiaría el proveedor en
/// silencio. El nombre distinto hace imposible esa confusión.
///
/// `AppAttestProvider` es `init?` y devuelve `nil` en **cualquier** dispositivo
/// sin soporte de App Attest, no sólo en el simulador. Sin fallback, Firebase
/// se queda sin proveedor y esas peticiones salen sin atestiguar para siempre;
/// con enforcement activo empiezan a fallar con un error de permisos que no
/// menciona App Check. DeviceCheck atestigua el dispositivo, no la integridad
/// del build —es más débil—, pero la comparación no es «App Attest o
/// DeviceCheck»: es «DeviceCheck o nada». Ver `docs/infra.md`.
final class AttestationProviderFactory: NSObject, AppCheckProviderFactory {

    private static let log = Logger(subsystem: "com.kitchenai.app", category: "appcheck")

    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        if let appAttest = AppAttestProvider(app: app) {
            return appAttest
        }

        // El motivo no lo da el SDK: `init?` sólo dice que no. Lo que sí es
        // seguro es que en este dispositivo no se va a poder atestiguar el
        // build, ni ahora ni más tarde.
        Self.log.warning("App Attest no disponible en este dispositivo. Se atestigua con DeviceCheck.")

        guard let deviceCheck = DeviceCheckProvider(app: app) else {
            Self.log.error("Sin App Attest ni DeviceCheck: las peticiones saldrán sin atestiguar.")
            return nil
        }

        return deviceCheck
    }
}
