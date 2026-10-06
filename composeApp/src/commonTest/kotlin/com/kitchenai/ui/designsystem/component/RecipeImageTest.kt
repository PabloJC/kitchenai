package com.kitchenai.ui.designsystem.component

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.dish_pasta
import com.kitchenai.ui.resources.dish_salad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecipeImageTest {
    @Test
    fun `a known dish-types term resolves to its bundled drawable`() {
        val drawable = dishTypeDrawable(listOf(termRef("dish-types", "pasta")))

        assertEquals(Res.drawable.dish_pasta, drawable)
    }

    @Test
    fun `no dish-types tag at all is a miss`() {
        val drawable = dishTypeDrawable(listOf(termRef("cuisines", "italian")))

        assertNull(drawable)
    }

    @Test
    fun `no tags at all is a miss`() {
        assertNull(dishTypeDrawable(emptyList()))
    }

    @Test
    fun `a dish-types term the map does not know is a miss not a crash`() {
        val drawable = dishTypeDrawable(listOf(termRef("dish-types", "not-seeded-yet")))

        assertNull(drawable)
    }

    @Test
    fun `the first dish-types tag wins when a recipe somehow carries more than one`() {
        val tags = listOf(termRef("dish-types", "pasta"), termRef("dish-types", "soup"))

        val drawable = dishTypeDrawable(tags)

        assertEquals(Res.drawable.dish_pasta, drawable)
    }

    @Test
    fun `a dish-types tag after an unrelated one is still found`() {
        val tags = listOf(termRef("cuisines", "italian"), termRef("dish-types", "salad"))

        val drawable = dishTypeDrawable(tags)

        assertEquals(Res.drawable.dish_salad, drawable)
    }

    private fun termRef(
        taxonomy: String,
        term: String,
    ): TermRef = TermRef(taxonomy.value(), term.termValue())

    private fun String.value(): TaxonomyId = (TaxonomyId.of(this) as AppResult.Success).data

    private fun String.termValue(): TermId = (TermId.of(this) as AppResult.Success).data
}
