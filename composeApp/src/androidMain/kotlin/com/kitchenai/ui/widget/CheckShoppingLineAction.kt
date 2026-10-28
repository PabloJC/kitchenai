package com.kitchenai.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.kitchenai.ui.presentation.shoppingwidget.ShoppingWidgetLoader
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** A tick on the widget. A failed write is redrawn as the line still unticked, which it is. */
class CheckShoppingLineAction :
    ActionCallback,
    KoinComponent {
    private val loader: ShoppingWidgetLoader by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        parameters[itemKey]?.let { itemId -> loader.check(itemId) }
        ShoppingListWidget().update(context, glanceId)
    }

    companion object {
        val itemKey = ActionParameters.Key<String>("itemId")
    }
}
