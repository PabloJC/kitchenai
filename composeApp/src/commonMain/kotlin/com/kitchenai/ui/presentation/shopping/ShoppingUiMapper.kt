package com.kitchenai.ui.presentation.shopping

import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.ui.designsystem.format.formatQuantity
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.common.wordFor

internal fun ShoppingItem.toUi(
    labels: LabelResolver,
    context: ShoppingLineContext = ShoppingLineContext(),
): ShoppingItemUi =
    ShoppingItemUi(
        id = id,
        label = label(labels),
        quantity = quantity?.render(labels),
        amount = quantity?.amount,
        unit = quantity?.unit,
        fromCatalogue = ingredient != null,
        checked = checked,
        section = ingredient?.let(context.sectionOf::get),
        forRecipe = sourceRecipe?.let(context.recipeTitles::get),
    )

/**
 * The unchecked lines in the shop's order: one group per section in [sections]' order, then the
 * lines no section claims (free text, untagged) last. Within a group, lines keep their own order.
 */
internal fun List<ShoppingItemUi>.inSections(sections: List<Pair<TermRef, String>>): List<ShoppingSectionUi> {
    val known = sections.map { (ref, _) -> ref }.toSet()
    val bySection = groupBy { line -> line.section?.takeIf { it in known } }
    val named =
        sections.mapNotNull { (ref, title) -> bySection[ref]?.let { lines -> ShoppingSectionUi(ref, title, lines) } }
    val other = bySection[null]?.let { lines -> ShoppingSectionUi(null, null, lines) }
    return named + listOfNotNull(other)
}

/** A catalogue miss renders the identifier: ugly and honest beats a placeholder that hides it. */
internal fun ShoppingItem.label(labels: LabelResolver): String =
    freeText ?: ingredient?.let { id -> labels.label(id) ?: id.value }.orEmpty()

private fun Quantity.render(labels: LabelResolver): String =
    formatQuantity(amount, unit?.let { ref -> labels.wordFor(ref, amount) }, labels.decimalSeparator)
