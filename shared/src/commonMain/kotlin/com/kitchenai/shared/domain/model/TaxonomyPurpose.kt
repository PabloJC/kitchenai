package com.kitchenai.shared.domain.model

/**
 * What the app does with a vocabulary — app structure, not vocabulary itself, which is the same
 * distinction that lets [ConstraintStrength] be an enum while the terms it applies to may not.
 *
 * Only the uses something reads structurally are named here. A taxonomy with no purpose is one
 * the app merely shows: the preferences screen lists every vocabulary as a section without
 * knowing what any of them mean, and adding an entry to this enum is an argument to be had,
 * not a formality.
 */
enum class TaxonomyPurpose {
    /** Where a [Quantity] is measured; also what an [Ingredient] points at by default. */
    UNITS,

    /** Where a [PantryItem] is kept, which is the only way that picker can be offered at all. */
    STORAGE_LOCATIONS,
}
