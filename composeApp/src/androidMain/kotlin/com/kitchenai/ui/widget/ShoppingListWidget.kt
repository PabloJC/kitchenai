package com.kitchenai.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kitchenai.ui.navigation.LaunchTarget
import com.kitchenai.ui.platform.platformLanguageTags
import com.kitchenai.ui.presentation.shoppingwidget.ShoppingWidgetLine
import com.kitchenai.ui.presentation.shoppingwidget.ShoppingWidgetLoader
import com.kitchenai.ui.presentation.shoppingwidget.ShoppingWidgetState
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** The shopping list on the home screen: the next unchecked lines, a tick for each and a way to add. */
class ShoppingListWidget :
    GlanceAppWidget(),
    KoinComponent {
    private val loader: ShoppingWidgetLoader by inject()

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val state = loader.load(platformLanguageTags())
        val texts = ShoppingWidgetTexts.load()
        val openList = actionStartActivity(appIntent(context, link = null))
        val openAdd = actionStartActivity(appIntent(context, LaunchTarget.SHOPPING_ADD.link))
        provideContent {
            GlanceTheme { WidgetBody(state, texts, openList, openAdd) }
        }
    }
}

// MainActivity lives in :androidApp, out of reach here: the launcher's own intent names it.
private fun appIntent(
    context: Context,
    link: String?,
): Intent =
    (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()).apply {
        data = link?.let(Uri::parse)
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

@Composable
private fun WidgetBody(
    state: ShoppingWidgetState,
    texts: ShoppingWidgetTexts,
    openList: Action,
    openAdd: Action,
) {
    Column(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(12.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = texts.title,
                modifier = GlanceModifier.defaultWeight().clickable(openList),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Button(text = texts.add, onClick = openAdd)
        }
        when (state) {
            is ShoppingWidgetState.Lines ->
                if (state.lines.isEmpty()) Message(texts.empty, openList) else PendingLines(state.lines)
            ShoppingWidgetState.NothingYet -> Message(texts.nothingYet, openList)
            ShoppingWidgetState.Failed -> Message(texts.failed, openList)
        }
    }
}

@Composable
private fun PendingLines(lines: List<ShoppingWidgetLine>) {
    LazyColumn(GlanceModifier.fillMaxSize()) {
        items(lines, itemId = { line -> line.id.value.hashCode().toLong() }) { line ->
            CheckBox(
                checked = false,
                onCheckedChange =
                    actionRunCallback<CheckShoppingLineAction>(
                        actionParametersOf(CheckShoppingLineAction.itemKey to line.id.value),
                    ),
                text = line.label,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Message(
    text: String,
    open: Action,
) {
    Text(
        text = text,
        modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp).clickable(open),
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
    )
}
