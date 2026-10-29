package com.kitchenai.shared.domain.model

/**
 * What the app does with a vocabulary — app structure, not vocabulary itself, which is the same
 * distinction that lets [ConstraintStrength] be an enum while the terms it applies to may not.
 *
 * Only the uses something reads structurally are named here. A taxonomy with no purpose is one
 * the app merely shows: the preferences screen lists every vocabulary as a section without
 * knowing what any of them mean, and adding an entry to this enum is an argument to be had,
 * not a formality. [isPreference] marks the one use that is read structurally and is still a taste.
 */
enum class TaxonomyPurpose(
    val isPreference: Boolean = false,
) {
    /** Where a [Quantity] is measured; also what an [Ingredient] points at by default. */
    UNITS,

    /** Where a [PantryItem] is kept, which is the only way that picker can be offered at all. */
    STORAGE_LOCATIONS,

    /** Describes a recipe (how it is tagged and pictured), not the cook: never a preference. */
    RECIPE_CLASSIFICATION,

    /** Groups the shopping list in the shop's order. Also a taste: "no dairy" is a fair choice. */
    SHOPPING_SECTIONS(isPreference = true),
    ;

    /** Read by the app and never a choice: offered to nobody, sent to no agent. */
    val isStructural: Boolean get() = !isPreference
}
