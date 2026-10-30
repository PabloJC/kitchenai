package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.domain.model.RecipeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Photographs that arrived after a list was built: the detail screen fetches one, and the card
 * behind it shows it on the way back instead of waiting for the next launch to read it from disk.
 */
class RecipePictures {
    private val known = MutableStateFlow<Map<RecipeId, String>>(emptyMap())
    val all: StateFlow<Map<RecipeId, String>> = known.asStateFlow()

    fun put(
        id: RecipeId,
        url: String,
    ) = known.update { it + (id to url) }
}
