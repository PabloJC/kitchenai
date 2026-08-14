package com.kitchenai.ui.presentation.suggestions

import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The last generation, held so the detail screen can open a dish that exists nowhere else.
 *
 * A generated recipe has no home: its id is minted on this device and the server never saw it,
 * so asking a repository for it is a guaranteed miss. Writing every suggestion to Firestore
 * instead would bill for dishes nobody opens.
 *
 * It holds one generation. That is the promise the app makes about a suggestion's lifetime:
 * open it now, or ask again — which is cheap. Nothing here survives process death, and the
 * detail screen is expected to say so rather than pretend the recipe was deleted.
 */
class SuggestionCache {
    private val recipes = MutableStateFlow<Map<RecipeId, Recipe>>(emptyMap())

    /** Replaces rather than accumulates: a new generation makes the previous one unreachable anyway. */
    fun put(generated: List<Recipe>) = recipes.update { generated.associateBy { recipe -> recipe.id } }

    operator fun get(id: RecipeId): Recipe? = recipes.value[id]
}
