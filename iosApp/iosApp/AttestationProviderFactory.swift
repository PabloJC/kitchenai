import FirebaseCore
import FirebaseAppCheck
import os

/// App Check provider for release builds: App Attest, falling back to DeviceCheck.
///
/// Not named `AppAttestProviderFactory` because the SDK already exports that Swift name, and
/// a local declaration would shadow it silently.
///
/// `AppAttestProvider` returns `nil` on any device without App Attest support, not only the
/// simulator. DeviceCheck attests the device rather than the build — weaker, but the choice
/// is DeviceCheck or nothing. See `docs/infra.md`.
final class AttestationProviderFactory: NSObject, AppCheckProviderFactory {

    private static let log = Logger(subsystem: "com.kitchenai.app", category: "appcheck")

    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        if let appAttest = AppAttestProvider(app: app) {
            return appAttest
        }

        // `init?` does not say why; what is certain is that this device will never attest
        // the build.
        Self.log.warning("App Attest unavailable on this device. Falling back to DeviceCheck.")

        guard let deviceCheck = DeviceCheckProvider(app: app) else {
            Self.log.error("Neither App Attest nor DeviceCheck: requests will go out unattested.")
            return nil
        }

        return deviceCheck
    }
}
