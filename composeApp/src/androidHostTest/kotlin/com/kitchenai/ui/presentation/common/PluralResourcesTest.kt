package com.kitchenai.ui.presentation.common

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The plural tables read straight from the resource files: the Compose resource API needs an
 * Android runtime to resolve anything, which a host test does not have. One and other are the
 * only categories Spanish and English use for these counts.
 */
class PluralResourcesTest {
    private val english = plurals("values/strings.xml")
    private val spanish = plurals("values-es/strings.xml")

    @Test
    fun `both locales declare the same plurals`() {
        assertTrue(english.isNotEmpty())
        assertEquals(english.keys, spanish.keys)
    }

    @Test
    fun `every plural carries both forms in both locales`() {
        listOf(english, spanish).forEach { table ->
            table.forEach { (name, forms) -> assertEquals(setOf("one", "other"), forms.keys, name) }
        }
    }

    @Test
    fun `Spanish agrees with one and with the rest`() {
        assertEquals("1 movido a la despensa", spanish.form("snack_moved_count", 1))
        assertEquals("4 movidos a la despensa", spanish.form("snack_moved_count", 4))
        assertEquals("1 se queda en la lista", spanish.form("snack_left_on_list_count", 1))
        assertEquals("4 se quedan en la lista", spanish.form("snack_left_on_list_count", 4))
        assertEquals("1 añadido", spanish.form("snack_added_count", 1))
        assertEquals("4 añadidos", spanish.form("snack_added_count", 4))
        assertEquals("1 que no hace falta", spanish.form("snack_not_needed_count", 1))
        assertEquals("4 que no hacen falta", spanish.form("snack_not_needed_count", 4))
        assertEquals("1 línea vaciada", spanish.form("shopping_cleared_count", 1))
        assertEquals("3 líneas vaciadas", spanish.form("shopping_cleared_count", 3))
    }

    @Test
    fun `the cook hint agrees with the count in both locales`() {
        assertEquals("Falta 1 ingrediente para cocinar esto", spanish.form("detail_cook_missing_hint", 1))
        assertEquals("Faltan 3 ingredientes para cocinar esto", spanish.form("detail_cook_missing_hint", 3))
        assertEquals("Missing 1 ingredient to cook this", english.form("detail_cook_missing_hint", 1))
        assertEquals("Missing 3 ingredients to cook this", english.form("detail_cook_missing_hint", 3))
    }

    @Test
    fun `zero takes the plural form`() {
        assertEquals("0 añadidos", spanish.form("snack_added_count", 0))
        assertEquals("0 lines cleared", english.form("shopping_cleared_count", 0))
    }

    @Test
    fun `English reads the same counts in its own words`() {
        assertEquals("1 moved to the pantry", english.form("snack_moved_count", 1))
        assertEquals("4 left on the list", english.form("snack_left_on_list_count", 4))
        assertEquals("1 line cleared", english.form("shopping_cleared_count", 1))
        assertEquals("3 lines cleared", english.form("shopping_cleared_count", 3))
    }

    private fun Map<String, Map<String, String>>.form(
        name: String,
        count: Int,
    ): String {
        val forms = getValue(name)
        return forms.getValue(if (count == 1) "one" else "other").replace("%1\$d", count.toString())
    }

    private fun plurals(path: String): Map<String, Map<String, String>> {
        val xml = File("src/commonMain/composeResources/$path").readText()
        val block = Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
        val item = Regex("""<item quantity="([^"]+)">([^<]*)</item>""")
        return block.findAll(xml).associate { match ->
            val forms = item.findAll(match.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
            match.groupValues[1] to forms
        }
    }
}
