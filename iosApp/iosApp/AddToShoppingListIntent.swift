import AppIntents
import ComposeApp

/// "Add to my KitchenAI list" for Siri and Shortcuts. The adding is Kotlin's; this only asks and answers.
struct AddToShoppingListIntent: AppIntent {
    static let title: LocalizedStringResource = "intent.add.title"
    static let description = IntentDescription("intent.add.description")
    // Runs in the background: the point is not having to open the app.
    static let openAppWhenRun = false

    @Parameter(title: "intent.add.item", requestValueDialog: IntentDialog("intent.add.ask"))
    var item: String

    // Kotlin suspend functions must be entered from the main thread.
    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let outcome = try await ShoppingListBridgeKt.addToShoppingList(text: item)
        // Kotlin enums reach Swift as classes, so they compare rather than switch.
        if outcome == QuickAddOutcome.added {
            return .result(dialog: IntentDialog("intent.add.done \(item)"))
        } else if outcome == QuickAddOutcome.nothingToAdd {
            return .result(dialog: IntentDialog("intent.add.nothing"))
        } else if outcome == QuickAddOutcome.noList {
            return .result(dialog: IntentDialog("intent.add.no_list"))
        } else {
            return .result(dialog: IntentDialog("intent.add.failed"))
        }
    }
}

/// Registers the phrases with Siri on install; no setup in the Shortcuts app needed.
struct KitchenAIShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: AddToShoppingListIntent(),
            phrases: [
                "Add to my \(.applicationName) list",
                "Add to \(.applicationName)",
            ],
            shortTitle: "intent.add.short",
            systemImageName: "cart.badge.plus"
        )
    }
}
