package com.kitchenai.admin.ingredients

import com.kitchenai.admin.catalogue.CatalogueWriter
import com.kitchenai.admin.catalogue.PictureStore
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.data.remote.dto.IngredientDto
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.port.IngredientRepositoryContract
import com.kitchenai.shared.domain.port.TaxonomyRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.w3c.files.Blob

class FakeIngredients : IngredientRepositoryContract {
    val catalogue = MutableStateFlow<List<Ingredient>>(emptyList())
    val errors = MutableSharedFlow<AppError>()

    override fun observeIngredients(): Flow<List<Ingredient>> = catalogue

    override fun ingredientErrors(): Flow<AppError> = errors

    override suspend fun getIngredient(id: IngredientId): AppResult<Ingredient> =
        catalogue.value.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.NotFound("ingredient"))
}

class FakeTaxonomies : TaxonomyRepositoryContract {
    val taxonomies = MutableStateFlow<List<Taxonomy>>(emptyList())

    override fun observeTaxonomy(id: TaxonomyId): Flow<List<Term>> = MutableStateFlow(emptyList())

    override fun observeTaxonomies(): Flow<List<Taxonomy>> = taxonomies

    override fun taxonomyErrors(id: TaxonomyId): Flow<AppError> = emptyFlow()

    override fun taxonomiesErrors(): Flow<AppError> = emptyFlow()

    override suspend fun getTaxonomies(): AppResult<List<Taxonomy>> = AppResult.Success(taxonomies.value)

    override suspend fun getTerms(id: TaxonomyId): AppResult<List<Term>> = AppResult.Success(emptyList())
}

class FakeWriter : CatalogueWriter {
    val written = mutableListOf<Pair<String, IngredientDto>>()
    val deleted = mutableListOf<String>()
    var refusal: String? = null
    var references: List<String> = emptyList()

    override suspend fun writeIngredient(
        id: String,
        document: IngredientDto,
    ): Result<Unit> =
        refusal?.let { Result.failure(IllegalArgumentException(it)) }
            ?: Result.success(Unit).also { written += id to document }

    override suspend fun deleteIngredient(id: String): Result<Unit> = Result.success(Unit).also { deleted += id }

    override suspend fun recipesReferencing(ingredientId: String): Result<List<String>> = Result.success(references)
}

class FakePictures : PictureStore {
    val stored = mutableListOf<String>()
    private var uploads = 0

    override suspend fun uploadIngredientPicture(
        ingredientId: String,
        picture: Blob,
    ): Result<String> {
        uploads += 1
        val url = "https://storage.example/$ingredientId-$uploads.webp"
        stored += url
        return Result.success(url)
    }

    override suspend fun delete(url: String): Result<Unit> = Result.success(Unit).also { stored -= url }
}
