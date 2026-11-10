package com.kitchenai.admin.ingredients

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.w3c.files.Blob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IngredientsModelTest {
    private val ingredients = FakeIngredients()
    private val writer = FakeWriter()
    private val pictures = FakePictures()
    private val model = IngredientsModel(ingredients, FakeTaxonomies(), writer, pictures, resize = { it })

    private val egg = ingredient("egg", "en" to "Egg", imageUrl = "https://storage.example/egg-0.webp")

    @Test
    fun `the list follows the catalogue`() =
        runTest {
            model.start(backgroundScope)
            runCurrent()
            ingredients.catalogue.value = listOf(egg)
            runCurrent()

            assertEquals(listOf(egg), model.state.value.shown)
            assertEquals(false, model.state.value.isLoading)
        }

    @Test
    fun `replacing a picture leaves one file, the saved one`() =
        runTest {
            pictures.stored += egg.imageUrl!!
            model.edit(egg)

            model.attachPicture(Blob())
            model.attachPicture(Blob())
            model.save()

            assertEquals(listOf("https://storage.example/egg-2.webp"), pictures.stored)
            assertEquals("https://storage.example/egg-2.webp", writer.written.single().second.imageUrl)
            assertNull(model.state.value.editor)
        }

    @Test
    fun `cancelling removes what was uploaded and keeps the stored picture`() =
        runTest {
            pictures.stored += egg.imageUrl!!
            model.edit(egg)

            model.attachPicture(Blob())
            model.close()

            assertEquals(listOf(egg.imageUrl), pictures.stored)
            assertTrue(writer.written.isEmpty())
        }

    @Test
    fun `a refused save keeps the editor open with the callable's message`() =
        runTest {
            writer.refusal = "labels needs 1 to 20 languages"
            model.edit(egg)

            model.save()

            assertEquals("labels needs 1 to 20 languages", model.state.value.editor?.message)
        }

    @Test
    fun `an invalid draft never reaches the callable`() =
        runTest {
            model.create()
            model.updateDraft { IngredientDraft(id = "olive oil", labels = listOf(LabelRow("en", "Olive oil"))) }

            model.save()

            assertTrue(writer.written.isEmpty())
            assertEquals("id must not contain free text", model.state.value.editor?.message)
        }

    @Test
    fun `changes made before a recomposition all land on the draft`() =
        runTest {
            model.create()

            model.updateDraft { it.copy(id = "tofu") }
            model.updateDraft { it.copy(labels = listOf(LabelRow("en", "Tofu"))) }
            model.updateDraft { it.copy(purchasedWhole = true) }

            assertEquals(
                IngredientDraft(id = "tofu", labels = listOf(LabelRow("en", "Tofu")), purchasedWhole = true),
                model.state.value.editor?.draft,
            )
        }

    @Test
    fun `no picture is uploaded before the id is valid, since the file is named after it`() =
        runTest {
            model.create()

            model.attachPicture(Blob())

            assertTrue(pictures.stored.isEmpty())
            assertNotNull(model.state.value.editor?.message)
        }

    @Test
    fun `archiving rewrites the stored ingredient with the flag on`() =
        runTest {
            model.setArchived(egg, archived = true)

            val (id, document) = writer.written.single()
            assertEquals("egg", id)
            assertEquals(true, document.archived)
            assertEquals(egg.imageUrl, document.imageUrl)
        }

    @Test
    fun `an ingredient a recipe names cannot be deleted`() =
        runTest {
            writer.references = listOf("tortilla")
            model.edit(egg)
            model.checkReferences()
            model.onDeleteConfirmationChange("egg")

            model.delete()

            assertEquals(false, model.state.value.editor?.canDelete)
            assertTrue(writer.deleted.isEmpty())
        }

    @Test
    fun `a delete needs the id typed again and takes the picture with it`() =
        runTest {
            pictures.stored += egg.imageUrl!!
            model.edit(egg)
            model.checkReferences()

            model.onDeleteConfirmationChange("eg")
            model.delete()
            assertTrue(writer.deleted.isEmpty())

            model.onDeleteConfirmationChange("egg")
            model.delete()
            assertEquals(listOf("egg"), writer.deleted)
            assertTrue(pictures.stored.isEmpty())
        }
}
