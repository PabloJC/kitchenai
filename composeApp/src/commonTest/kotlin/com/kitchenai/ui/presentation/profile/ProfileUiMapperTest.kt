package com.kitchenai.ui.presentation.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.DietaryConstraint
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.ui.presentation.common.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ProfileUiMapperTest {
    private val taxonomyId = TaxonomyId.of("diets").value()
    private val termRef = TermRef(taxonomyId, TermId.of("vegan").value())
    private val userId = UserId.of("user-1").value()
    private val now = Instant.fromEpochSeconds(1_000)

    @Test
    fun `a loading draft renders as loading`() {
        val state = uiState(draft = null, catalogue = CatalogueState(), saving = false, failure = null)

        assertEquals(true, state.isLoading)
    }

    @Test
    fun `a loaded draft is not loading and carries the saving flag`() {
        val state =
            uiState(draft = ProfileDraft(profile()), catalogue = CatalogueState(), saving = true, failure = null)

        assertEquals(false, state.isLoading)
        assertEquals(true, state.isSaving)
    }

    @Test
    fun `sections skip taxonomies with no declared purpose only`() {
        val undeclared = Taxonomy(taxonomyId, mapOf("en" to "Diets"), purpose = null)
        val declared = Taxonomy(TaxonomyId.of("units").value(), emptyMap(), purpose = TaxonomyPurpose.UNITS)
        val catalogue = CatalogueState(taxonomies = listOf(undeclared, declared))

        val result = sections(catalogue, profile())

        assertEquals(listOf(taxonomyId), result.map { it.taxonomy })
    }

    @Test
    fun `a term resolves selected against the profile's own constraints`() {
        val taxonomy = Taxonomy(taxonomyId, mapOf("en" to "Diets"), purpose = null)
        val term = Term(termRef, mapOf("en" to "Vegan"), null, 0)
        val catalogue = CatalogueState(taxonomies = listOf(taxonomy), terms = mapOf(taxonomyId to listOf(term)))
        val constraint = DietaryConstraint(termRef, ConstraintStrength.SOFTEST)
        val withConstraint = profile().copy(constraints = listOf(constraint))

        val chip = sections(catalogue, withConstraint).single().terms.single()

        assertEquals("Vegan", chip.label)
        assertEquals(ConstraintStrength.SOFTEST, chip.strength)
        assertEquals(true, chip.selected)
    }

    @Test
    fun `a validation error carries its field and the domain's own wording`() {
        val error = AppError.Validation("household.servings", "must be positive")

        val result = error.toProfileError()

        assertEquals("household.servings", result.field)
        assertEquals(UiText.Raw("must be positive"), result.message)
    }

    @Test
    fun `a non-validation error carries no field`() {
        val result = AppError.Network().toProfileError()

        assertNull(result.field)
    }

    private fun profile(): UserProfile = UserProfile.newFor(userId, listOf("en"), now)

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).data
}
