package com.kitchenai.shared.domain.model

/**
 * One holding a cook changes: [before] as read, [after] as it will be written. An [after] at
 * zero is removed rather than written, and undoing it restores [before] whole.
 */
data class PantryDeduction(
    val before: PantryItem,
    val after: PantryItem,
) {
    /** In the holding's own unit, which the subtraction keeps whatever unit the recipe used. */
    val taken: Quantity get() = before.quantity.copy(amount = before.quantity.amount - after.quantity.amount)
}
