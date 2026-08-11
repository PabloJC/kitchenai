import FirebaseCore
import FirebaseAppCheck

/// Fábrica de App Attest para builds de release.
///
/// El SDK trae `DeviceCheckProviderFactory` hecha, pero no una equivalente para
/// App Attest: `AppAttestProvider` hay que envolverlo a mano.
///
/// `init?` devuelve `nil` en dispositivos sin soporte de App Attest —el
/// simulador, sin ir más lejos—. Cuando eso pasa, Firebase se queda sin
/// proveedor y las peticiones salen sin atestiguar: es la razón por la que el
/// simulador usa el proveedor de debug y por la que activar enforcement con
/// métricas sucias tira la app. Ver `docs/infra.md`.
final class AppAttestProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        AppAttestProvider(app: app)
    }
}
