package com.kitchenai.admin.ingredients

import com.kitchenai.admin.catalogue.CatalogueWriter
import com.kitchenai.admin.catalogue.PictureStore
import com.kitchenai.admin.core.catchingFailures
import com.kitchenai.admin.core.messageFor
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.port.IngredientRepositoryContract
import com.kitchenai.shared.domain.port.TaxonomyRepositoryContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.w3c.files.Blob

/** The ingredients section: the catalogue as it is, and at most one ingredient being edited. */
class IngredientsModel(
    private val ingredients: IngredientRepositoryContract,
    private val taxonomies: TaxonomyRepositoryContract,
    private val writer: CatalogueWriter,
    private val pictures: PictureStore,
    /** Shrinks and re-encodes a picked file; the browser's canvas in production. */
    private val resize: suspend (Blob) -> Blob,
) {
    private val _state = MutableStateFlow(IngredientsUiState())
    val state: StateFlow<IngredientsUiState> = _state.asStateFlow()

    // The picture the stored document points at, and the ones uploaded since: what a save or a discard cleans up.
    private var savedPicture: String? = null
    private val uploaded = mutableListOf<String>()

    fun start(scope: CoroutineScope) {
        scope.launch {
            ingredients.observeIngredients().collect { loaded ->
                _state.update { it.copy(ingredients = loaded, isLoading = false) }
            }
        }
        scope.launch {
            ingredients.ingredientErrors().collect { error ->
                _state.update { it.copy(isLoading = false, error = error.messageFor("the ingredients")) }
            }
        }
        scope.launch { watchTaxonomies() }
        scope.launch {
            taxonomies.taxonomiesErrors().collect { error ->
                _state.update { it.copy(error = error.messageFor("the taxonomies")) }
            }
        }
    }

    fun onQueryChange(query: IngredientQuery) = _state.update { it.copy(query = query) }

    fun create() = open(IngredientDraft())

    fun edit(ingredient: Ingredient) = open(ingredient.toDraft())

    /** A change to the latest draft, not a whole draft: two events before a recomposition must not undo each other. */
    fun updateDraft(change: (IngredientDraft) -> IngredientDraft) =
        updateEditor { it.copy(draft = change(it.draft), message = null) }

    fun onDeleteConfirmationChange(text: String) = updateEditor { it.copy(deleteConfirmation = text) }

    fun removePicture() = updateEditor { it.copy(draft = it.draft.copy(imageUrl = null)) }

    /** Discards the edit, and with it every picture uploaded for it. */
    suspend fun close() {
        val keep = savedPicture
        _state.update { it.copy(editor = null) }
        cleanUp(keep = keep)
    }

    /** Uploads at once, so the preview is the stored file; the document changes only on save. */
    suspend fun attachPicture(picture: Blob) {
        val draft = busy() ?: return
        ingredientIdProblem(draft.id)?.let { problem -> return fail("Set the id before the picture: $problem") }
        val upload =
            catchingFailures { resize(picture) }.fold(
                onSuccess = { resized -> pictures.uploadIngredientPicture(draft.id, resized) },
                onFailure = { failure -> Result.failure(failure) },
            )
        upload.fold(
            onSuccess = { url ->
                uploaded += url
                updateEditor { it.copy(isBusy = false, draft = it.draft.copy(imageUrl = url)) }
            },
            onFailure = { failure -> fail("Could not upload the picture: ${failure.message}") },
        )
    }

    suspend fun save() {
        val draft = busy() ?: return
        draft.problem()?.let { problem -> return fail(problem) }
        writer.writeIngredient(draft.id, draft.toDto()).fold(
            onSuccess = {
                _state.update { it.copy(editor = null) }
                cleanUp(keep = draft.imageUrl)
            },
            onFailure = { failure -> fail(failure.message ?: "Could not save") },
        )
    }

    /** Archiving is a save of the stored ingredient with the flag flipped, whatever the draft holds. */
    suspend fun setArchived(
        ingredient: Ingredient,
        archived: Boolean,
    ) {
        val draft = ingredient.toDraft().copy(archived = archived)
        writer.writeIngredient(draft.id, draft.toDto()).onFailure { failure ->
            _state.update { it.copy(error = "Could not ${if (archived) "archive" else "restore"}: ${failure.message}") }
        }
    }

    suspend fun checkReferences() {
        val draft = busy() ?: return
        writer.recipesReferencing(draft.id).fold(
            onSuccess = { ids -> updateEditor { it.copy(isBusy = false, references = ids) } },
            onFailure = { failure -> fail("Could not check the recipes: ${failure.message}") },
        )
    }

    suspend fun delete() {
        val editor = _state.value.editor?.takeIf { it.canDelete } ?: return
        busy() ?: return
        writer.deleteIngredient(editor.draft.id).fold(
            onSuccess = {
                _state.update { it.copy(editor = null) }
                cleanUp(keep = null)
            },
            onFailure = { failure -> fail("Could not delete: ${failure.message}") },
        )
    }

    private fun open(draft: IngredientDraft) {
        savedPicture = draft.imageUrl
        uploaded.clear()
        _state.update { it.copy(editor = IngredientEditorState(draft)) }
    }

    /** Every picture of this edit but [keep]; a file that will not go is reported, never forgotten. */
    private suspend fun cleanUp(keep: String?) {
        val stale = (uploaded + listOfNotNull(savedPicture)).distinct().filter { url -> url != keep }
        savedPicture = null
        uploaded.clear()
        val failed = stale.filter { url -> pictures.delete(url).isFailure }
        if (failed.isNotEmpty()) {
            _state.update {
                it.copy(
                    error = "Saved, but ${failed.size} old picture(s) could not be removed from Storage",
                )
            }
        }
    }

    private fun busy(): IngredientDraft? {
        val editor = _state.value.editor?.takeUnless { it.isBusy } ?: return null
        updateEditor { it.copy(isBusy = true, message = null) }
        return editor.draft
    }

    private fun fail(message: String) = updateEditor { it.copy(isBusy = false, message = message) }

    private fun updateEditor(change: (IngredientEditorState) -> IngredientEditorState) =
        _state.update { current -> current.copy(editor = current.editor?.let(change)) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun watchTaxonomies() {
        taxonomies
            .observeTaxonomies()
            .flatMapLatest { loaded -> termsOf(loaded).map { terms -> loaded to terms } }
            .collect { (loaded, terms) -> _state.update { it.copy(taxonomies = loaded, terms = terms) } }
    }

    private fun termsOf(loaded: List<Taxonomy>) =
        if (loaded.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(
                loaded.map {
                        taxonomy ->
                    taxonomies.observeTaxonomy(taxonomy.id).map { taxonomy.id to it }
                },
            ) { pairs ->
                pairs.toMap()
            }
        }
}
