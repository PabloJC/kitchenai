package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.mapper.toDomain
import com.kitchenai.shared.data.remote.dto.TaxonomyDto
import com.kitchenai.shared.data.remote.dto.TermDto
import com.kitchenai.shared.data.remote.firebase.FirestorePaths
import com.kitchenai.shared.data.remote.firebase.firestoreCall
import com.kitchenai.shared.data.remote.firebase.reportingErrorsTo
import com.kitchenai.shared.data.remote.firebase.toAppError
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.port.TaxonomyRepositoryContract
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

/**
 * [TaxonomyRepositoryContract] over the read-only `taxonomies` collection. Reads are snapshot listeners rather
 * than one-shot gets so the catalogue keeps rendering from the offline cache; [getTaxonomies] is
 * the exception, because validating a profile cannot wait on a listener that may have failed.
 */
class FirestoreTaxonomyRepository(
    private val paths: FirestorePaths,
    private val dispatchers: DispatcherProvider,
) : TaxonomyRepositoryContract {
    private val termErrors = KeyedErrorSinks<TaxonomyId>()

    // The catalogue listener takes no argument, so it owns a single sink.
    private val catalogueErrors = errorSink()

    override fun observeTaxonomy(id: TaxonomyId): Flow<List<Term>> =
        paths
            .terms(id)
            .snapshots
            .map { snapshot -> snapshot.toTerms(id) }
            .reportingErrorsTo(termErrors.of(id))

    override fun observeTaxonomies(): Flow<List<Taxonomy>> =
        paths
            .taxonomies()
            .snapshots
            .map { snapshot -> snapshot.toTaxonomies() }
            .reportingErrorsTo(catalogueErrors)

    override fun taxonomyErrors(id: TaxonomyId): Flow<AppError> = termErrors.of(id).asSharedFlow()

    override fun taxonomiesErrors(): Flow<AppError> = catalogueErrors.asSharedFlow()

    override suspend fun getTaxonomies(): AppResult<List<Taxonomy>> =
        firestoreCall(dispatchers) { paths.taxonomies().get().toTaxonomies() }

    private fun QuerySnapshot.toTaxonomies(): List<Taxonomy> = documents.mapNotNull { it.toTaxonomy().orNull() }

    private fun QuerySnapshot.toTerms(taxonomy: TaxonomyId): List<Term> =
        documents.mapNotNull { it.toTerm(taxonomy).orNull() }

    private fun DocumentSnapshot.toTaxonomy(): AppResult<Taxonomy> =
        runCatching { data(TaxonomyDto.serializer()) }.fold(
            onSuccess = { dto -> dto.toDomain(id) },
            onFailure = { failure -> AppResult.Failure(failure.toAppError()) },
        )

    private fun DocumentSnapshot.toTerm(taxonomy: TaxonomyId): AppResult<Term> =
        runCatching { data(TermDto.serializer()) }.fold(
            onSuccess = { dto -> dto.toDomain(taxonomy, id) },
            onFailure = { failure -> AppResult.Failure(failure.toAppError()) },
        )
}

/**
 * A document that will not decode is dropped rather than failing the whole read: one bad row must
 * not leave the profile screen with nothing to render.
 */
private fun <T> AppResult<T>.orNull(): T? = (this as? AppResult.Success)?.data
