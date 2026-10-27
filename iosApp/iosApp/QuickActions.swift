import UIKit
import ComposeApp

/// Only here to hand SwiftUI a scene delegate: quick actions reach the app through the scene.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = QuickActionSceneDelegate.self
        return configuration
    }
}

/// Forwards a quick action's type, one of the app's own kitchenai:// links, to Kotlin. No logic here.
final class QuickActionSceneDelegate: NSObject, UIWindowSceneDelegate {
    // A cold launch from a quick action delivers it here rather than to performActionFor.
    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        if let item = connectionOptions.shortcutItem {
            LaunchLinkBridgeKt.openLaunchLink(link: item.type)
        }
    }

    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        LaunchLinkBridgeKt.openLaunchLink(link: shortcutItem.type)
        completionHandler(true)
    }
}
