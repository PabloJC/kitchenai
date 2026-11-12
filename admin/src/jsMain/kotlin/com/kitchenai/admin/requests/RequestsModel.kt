package com.kitchenai.admin.requests

import com.kitchenai.admin.catalogue.CatalogueWriter
import com.kitchenai.admin.ingredients.IngredientsModel
import com.kitchenai.admin.ingredients.LabelRow
import com.kitchenai.admin.ingredients.problem
import com.kitchenai.admin.ingredients.toDraft
import com.kitchenai.admin.ingredients.toDto
import com.kitchenai.shared.domain.model.Ingredient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The free-text queue. Promoting borrows [ingredients]' editor, so a new ingredient saves as in its own section. */
class RequestsModel(
    private val store: IngredientRequestStore,
    private val writer: CatalogueWriter,
    private val converter: ItemConverter,
    val ingredients: IngredientsModel,
) {
    private val _state = MutableStateFlow(RequestsUiState())
    val state: StateFlow<RequestsUiState> = _state.asStateFlow()

    private val catalogue: List<Ingredient> get() = ingredients.state.value.ingredients

    fun start(scope: CoroutineScope) {
        ingredients.start(scope)
        scope.launch {
            store.observeRequests().collect { result ->
                result.fold(
                    onSuccess = { loaded -> _state.update { it.copy(requests = loaded, isLoading = false) } },
                    onFailure = { failure ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = "Could not read the queue: ${failure.message}",
                            )
                        }
                    },
                )
            }
        }
    }

    fun onQueryChange(query: RequestQuery) = _state.update { it.copy(query = query) }

    fun onTypedLanguageChange(language: String) = _state.update { it.copy(typedLanguage = language.trim()) }

    /** The request closes only once the ingredient is written: a refused save leaves it open. */
    fun promote(request: IngredientRequest) {
        _state.update { it.copy(alias = null, conversion = null, error = null, notice = null) }
        ingredients.create(request.toIngredientDraft(_state.value.typedLanguage)) { id ->
            close(request, RequestStatus.PROMOTED, id, done = "Created $id")
        }
    }

    fun startAlias(request: IngredientRequest) =
        _state.update {
            it.copy(
                alias = AliasState(request, language = it.typedLanguage),
                conversion = null,
                error = null,
                notice = null,
            )
        }

    fun updateAlias(change: (AliasState) -> AliasState) =
        _state.update { current -> current.copy(alias = current.alias?.let { change(it).copy(message = null) }) }

    fun cancelAlias() = _state.update { it.copy(alias = null) }

    /** A new name is written first; if `writeCatalogue` refuses it, the request stays open. */
    suspend fun alias() {
        val alias = _state.value.alias?.takeUnless { it.isBusy } ?: return
        alias.problem(catalogue)?.let { problem -> return failAlias(problem) }
        val target = alias.target(catalogue) ?: return
        updateAlias { it.copy(isBusy = true) }
        if (alias.addLabel && alias.isNewSpelling(target)) {
            addName(target, alias)?.let { problem -> return failAlias(problem) }
        }
        _state.update { it.copy(alias = null) }
        close(alias.request, RequestStatus.ALIASED, target.id.value, done = "Added ${target.id.value}'s new name")
    }

    /** A dry run first: the admin confirms against the number of items it will change. */
    suspend fun previewConversion(request: IngredientRequest) {
        if (_state.value.working != null) return
        _state.update { it.copy(working = request.id, error = null, notice = null, alias = null) }
        val preview = converter.convert(request.id, dryRun = true)
        _state.update { current ->
            preview.fold(
                onSuccess = { counts ->
                    if (counts.total == 0) {
                        current.copy(working = null, notice = "No item still holds \"${request.text}\".")
                    } else {
                        current.copy(working = null, conversion = ConversionState(request, counts))
                    }
                },
                onFailure = { failure ->
                    current.copy(working = null, error = "Could not count the items: ${failure.message}")
                },
            )
        }
    }

    suspend fun convert() {
        val conversion = _state.value.conversion?.takeUnless { it.isBusy } ?: return
        updateConversion { it.copy(isBusy = true, message = null) }
        converter.convert(conversion.request.id, dryRun = false).fold(
            onSuccess = { counts ->
                _state.update {
                    it.copy(
                        conversion = null,
                        notice = "Converted ${counts.pantryItems} pantry and ${counts.shoppingItems} shopping items.",
                    )
                }
            },
            onFailure = { failure ->
                updateConversion { it.copy(isBusy = false, message = "Could not convert: ${failure.message}") }
            },
        )
    }

    fun cancelConversion() = _state.update { it.copy(conversion = null) }

    suspend fun dismiss(request: IngredientRequest) = setStatus(request, RequestStatus.DISMISSED, ingredientId = null)

    suspend fun reopen(request: IngredientRequest) = setStatus(request, RequestStatus.OPEN, ingredientId = null)

    /** Null once the name is stored, or why it was not. */
    private suspend fun addName(
        target: Ingredient,
        alias: AliasState,
    ): String? {
        val named = target.withName(alias.language, alias.request.text)
        return named.problem()
            ?: writer.writeIngredient(named.id, named.toDto()).exceptionOrNull()?.let { failure ->
                "Could not add the name: ${failure.message}"
            }
    }

    private suspend fun close(
        request: IngredientRequest,
        status: RequestStatus,
        ingredientId: String,
        done: String,
    ) {
        store.setStatus(request.id, status, ingredientId).onFailure { failure ->
            // Only after a catalogue write: say what landed, so the admin aliases rather than creates again.
            _state.update {
                it.copy(error = "$done, but \"${request.text}\" is still open: ${failure.message}")
            }
        }
    }

    private suspend fun setStatus(
        request: IngredientRequest,
        status: RequestStatus,
        ingredientId: String?,
    ) {
        if (_state.value.working != null) return
        _state.update { it.copy(working = request.id, error = null, notice = null) }
        val result = store.setStatus(request.id, status, ingredientId)
        _state.update {
            it.copy(
                working = null,
                error =
                    result.exceptionOrNull()?.let {
                            failure ->
                        "Could not update \"${request.text}\": ${failure.message}"
                    },
            )
        }
    }

    private fun updateConversion(change: (ConversionState) -> ConversionState) =
        _state.update { current -> current.copy(conversion = current.conversion?.let(change)) }

    private fun failAlias(message: String) =
        _state.update { current -> current.copy(alias = current.alias?.copy(isBusy = false, message = message)) }
}

/** The ingredient as stored, plus [text] in [language]. */
private fun Ingredient.withName(
    language: String,
    text: String,
) = toDraft().let { draft ->
    draft.copy(labels = draft.labels.filterNot { row -> row.language == language } + LabelRow(language, text))
}
