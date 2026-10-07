import Foundation
import os

/// Launch-time check that Google Sign-In's two Info.plist inputs agree.
///
/// The Google sheet returns to the app through the reversed client id registered as a URL scheme;
/// without it sign-in opens and never comes back, with no error anywhere.
enum GoogleSignInConfiguration {

    private static let log = Logger(subsystem: "com.kitchenai.app", category: "google-signin")

    /// Blank client id is allowed (anonymous-only builds); a client id without its scheme is not.
    static func verify() {
        let clientId = (Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String) ?? ""
        guard !clientId.isEmpty else {
            log.error("GIDClientID is blank: Google sign-in will fail. See iosApp/Configuration/Config.xcconfig.")
            return
        }

        let expected = clientId.split(separator: ".").reversed().joined(separator: ".")
        let registered = (Bundle.main.object(forInfoDictionaryKey: "CFBundleURLTypes") as? [[String: Any]] ?? [])
            .flatMap { $0["CFBundleURLSchemes"] as? [String] ?? [] }

        guard registered.contains(expected) else {
            fatalError(
                "GIDClientID is set but its reversed form is not a registered URL scheme, so Google " +
                    "sign-in cannot return to the app. Set GOOGLE_IOS_REVERSED_CLIENT_ID in " +
                    "iosApp/Configuration/Config.xcconfig."
            )
        }
    }
}
