package com.kitchenai.ui.widget

import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.shopping_default_list
import com.kitchenai.ui.resources.widget_shopping_add
import com.kitchenai.ui.resources.widget_shopping_empty
import com.kitchenai.ui.resources.widget_shopping_failed
import com.kitchenai.ui.resources.widget_shopping_nothing_yet
import org.jetbrains.compose.resources.getString

/** Read before drawing: Glance content cannot suspend, and Compose resources are suspend reads. */
internal data class ShoppingWidgetTexts(
    val title: String,
    val add: String,
    val empty: String,
    val nothingYet: String,
    val failed: String,
) {
    companion object {
        suspend fun load(): ShoppingWidgetTexts =
            ShoppingWidgetTexts(
                title = getString(Res.string.shopping_default_list),
                add = getString(Res.string.widget_shopping_add),
                empty = getString(Res.string.widget_shopping_empty),
                nothingYet = getString(Res.string.widget_shopping_nothing_yet),
                failed = getString(Res.string.widget_shopping_failed),
            )
    }
}
