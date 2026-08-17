package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.domain.agent.SuggestionOptions
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.model.RecipeSource
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.ui.presentation.common.LabelResolver

internal fun SuggestionOptionsUi.toDomain(): SuggestionOptions =
    SuggestionOptions(maxResults = maxResults, maxMinutes = maxMinutes, useOnlyPantry = useOnlyPantry)

internal fun RecipeSuggestion.toUi(resolver: LabelResolver): SuggestionUi {
    val held = match.covered.count { !it.ingredient.optional }
    val short = match.missing.count { !it.ingredient.optional }
    return SuggestionUi(
        id = recipe.id,
        title = recipe.title,
        summary = recipe.summary,
        totalMinutes = recipe.totalMinutes,
        coverage = match.coverage,
        heldCount = held,
        totalCount = held + short,
        missing = match.missing.map { it.ingredient.name(resolver) },
        unverifiable = match.unverifiable.map { line -> line.name(resolver) },
        // Both ids travel as the response reported them; neither is named anywhere in this module.
        provenance = (source as? RecipeSource.Agent)?.let { ProvenanceUi(it.agentId.value, it.modelId) },
    )
}

/** Free text is already its own name; a catalogue line falls back to its identifier. */
internal fun RecipeIngredient.name(resolver: LabelResolver): String {
    freeText?.let { return it }
    val id = ingredient ?: return ""
    return resolver.label(id) ?: id.value
}
