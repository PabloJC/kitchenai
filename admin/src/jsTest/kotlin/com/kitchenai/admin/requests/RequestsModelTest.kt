package com.kitchenai.admin.requests

import com.kitchenai.admin.ingredients.FakeIngredients
import com.kitchenai.admin.ingredients.FakePictures
import com.kitchenai.admin.ingredients.FakeTaxonomies
import com.kitchenai.admin.ingredients.FakeWriter
import com.kitchenai.admin.ingredients.IngredientsModel
import com.kitchenai.admin.ingredients.ingredient
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestsModelTest {
    private val catalogue = FakeIngredients()
    private val writer = FakeWriter()
    private val queue = FakeRequestStore(request("r1", "Tomate cherry"), request("r2", "Jitomate"))
    private val ingredients = IngredientsModel(catalogue, FakeTaxonomies(), writer, FakePictures(), resize = { it })
    private val converter = FakeConverter()
    private val model = RequestsModel(queue.store, writer, converter, ingredients)

    private val tomato = ingredient("tomato", "en" to "Tomato", "es" to "Tomate")

    private fun TestScope.started() {
        catalogue.catalogue.value = listOf(tomato)
        model.start(backgroundScope)
        runCurrent()
    }

    @Test
    fun `promoting writes the ingredient, then closes the request with its id`() =
        runTest {
            started()
            model.promote(queue.status("r1"))
            assertEquals("tomate-cherry", ingredients.state.value.editor?.draft?.id)
            assertEquals(RequestStatus.OPEN, queue.status("r1").status)

            ingredients.save()

            assertEquals("tomate-cherry", writer.written.single().first)
            assertEquals(RequestStatus.PROMOTED, queue.status("r1").status)
            assertEquals("tomate-cherry", queue.status("r1").ingredientId)
        }

    @Test
    fun `a refused ingredient write leaves the request open`() =
        runTest {
            started()
            writer.refusal = "labels needs 1 to 20 languages"
            model.promote(queue.status("r1"))

            ingredients.save()

            assertEquals(RequestStatus.OPEN, queue.status("r1").status)
            assertNotNull(ingredients.state.value.editor)
        }

    @Test
    fun `promoting onto an existing id is refused before the callable replaces it`() =
        runTest {
            started()
            model.promote(queue.status("r1"))
            ingredients.updateDraft { it.copy(id = "tomato") }

            ingredients.save()

            assertTrue(writer.written.isEmpty())
            assertEquals(RequestStatus.OPEN, queue.status("r1").status)
        }

    @Test
    fun `a cancelled promotion never closes the request on a later save`() =
        runTest {
            started()
            model.promote(queue.status("r1"))
            ingredients.close()
            ingredients.create()
            ingredients.updateDraft { it.copy(id = "basil", labels = listOf(it.labels.first().copy(text = "Basil"))) }

            ingredients.save()

            assertEquals(RequestStatus.OPEN, queue.status("r1").status)
        }

    @Test
    fun `a status write that fails after the ingredient landed says both`() =
        runTest {
            started()
            queue.refusal = "offline"
            model.promote(queue.status("r1"))

            ingredients.save()

            assertEquals("Created tomate-cherry, but \"Tomate cherry\" is still open: offline", model.state.value.error)
        }

    @Test
    fun `aliasing closes the request with the chosen id`() =
        runTest {
            started()
            model.startAlias(queue.status("r2"))
            model.updateAlias { it.copy(ingredientId = "tomato") }

            model.alias()

            assertEquals(RequestStatus.ALIASED, queue.status("r2").status)
            assertEquals("tomato", queue.status("r2").ingredientId)
            assertTrue(writer.written.isEmpty())
            assertNull(model.state.value.alias)
        }

    @Test
    fun `aliasing can keep the text as a name in a language the ingredient lacks`() =
        runTest {
            started()
            model.startAlias(queue.status("r2"))
            model.updateAlias { it.copy(ingredientId = "tomato", addLabel = true, language = "es-MX") }

            model.alias()

            val (id, document) = writer.written.single()
            assertEquals("tomato", id)
            assertEquals(mapOf("en" to "Tomato", "es" to "Tomate", "es-MX" to "Jitomate"), document.labels)
            assertEquals(RequestStatus.ALIASED, queue.status("r2").status)
        }

    @Test
    fun `a name in a language the ingredient already has is refused`() =
        runTest {
            started()
            model.startAlias(queue.status("r2"))
            model.updateAlias { it.copy(ingredientId = "tomato", addLabel = true, language = "es") }

            model.alias()

            assertTrue(writer.written.isEmpty())
            assertEquals(RequestStatus.OPEN, queue.status("r2").status)
            assertEquals("tomato already has a name in es", model.state.value.alias?.message)
        }

    @Test
    fun `a refused new name leaves the request open`() =
        runTest {
            started()
            writer.refusal = "label must be non-empty text"
            model.startAlias(queue.status("r2"))
            model.updateAlias { it.copy(ingredientId = "tomato", addLabel = true, language = "es-MX") }

            model.alias()

            assertEquals(RequestStatus.OPEN, queue.status("r2").status)
            assertEquals("Could not add the name: label must be non-empty text", model.state.value.alias?.message)
        }

    @Test
    fun `a spelling the ingredient already has is not offered as a new name`() {
        val alias = AliasState(request("r", "tomate"), ingredientId = "tomato", addLabel = true, language = "es")
        assertEquals(false, alias.isNewSpelling(tomato))
        assertNull(alias.problem(listOf(tomato)))
    }

    @Test
    fun `a dismissed request can be reopened, without an ingredient`() =
        runTest {
            started()
            model.dismiss(queue.status("r1"))
            assertEquals(RequestStatus.DISMISSED, queue.status("r1").status)

            model.reopen(queue.status("r1"))

            assertEquals(RequestStatus.OPEN, queue.status("r1").status)
            assertNull(queue.status("r1").ingredientId)
            assertNull(model.state.value.working)
        }

    @Test
    fun `a failed dismiss is reported and leaves the request as it was`() =
        runTest {
            started()
            queue.refusal = "permission denied"

            model.dismiss(queue.status("r1"))

            assertEquals(RequestStatus.OPEN, queue.status("r1").status)
            assertEquals("Could not update \"Tomate cherry\": permission denied", model.state.value.error)
        }

    @Test
    fun `converting counts first, and writes only once confirmed`() =
        runTest {
            started()
            val promoted = request("p", "Tomate cherry", status = RequestStatus.PROMOTED, ingredientId = "tomato")

            model.previewConversion(promoted)

            assertEquals(listOf("p" to true), converter.calls)
            assertEquals(3, model.state.value.conversion?.preview?.total)

            model.convert()

            assertEquals(listOf("p" to true, "p" to false), converter.calls)
            assertNull(model.state.value.conversion)
            assertEquals("Converted 2 pantry and 1 shopping items.", model.state.value.notice)
        }

    @Test
    fun `cancelling after the count writes nothing`() =
        runTest {
            started()
            model.previewConversion(
                request("p", "Tomate cherry", status = RequestStatus.ALIASED, ingredientId = "tomato"),
            )

            model.cancelConversion()

            assertEquals(listOf("p" to true), converter.calls)
            assertNull(model.state.value.conversion)
        }

    @Test
    fun `nothing to convert asks for no confirmation`() =
        runTest {
            started()
            converter.counts = ConversionCounts(pantryItems = 0, shoppingItems = 0, dryRun = false)

            model.previewConversion(
                request("p", "Tomate cherry", status = RequestStatus.PROMOTED, ingredientId = "tomato"),
            )

            assertNull(model.state.value.conversion)
            assertEquals("No item still holds \"Tomate cherry\".", model.state.value.notice)
        }

    @Test
    fun `a refused count or conversion is reported`() =
        runTest {
            started()
            val promoted = request("p", "Tomate cherry", status = RequestStatus.PROMOTED, ingredientId = "tomato")
            converter.dryRunRefusal = "the ingredient is missing or archived"

            model.previewConversion(promoted)

            assertEquals("Could not count the items: the ingredient is missing or archived", model.state.value.error)
            assertNull(model.state.value.working)

            converter.dryRunRefusal = null
            converter.refusal = "deadline exceeded"
            model.previewConversion(promoted)
            model.convert()

            assertEquals("Could not convert: deadline exceeded", model.state.value.conversion?.message)
            assertEquals(false, model.state.value.conversion?.isBusy)
        }
}
