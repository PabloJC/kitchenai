package com.kitchenai.ui.presentation.pantry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.designsystem.component.TermChip
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.pantry_filter_all
import com.kitchenai.ui.resources.pantry_filter_search
import com.kitchenai.ui.resources.pantry_search_clear
import org.jetbrains.compose.resources.stringResource

/**
 * "Do we have cumin?" answered without scrolling: a name search, and the storage places as
 * chips because fridge, freezer and cupboard are how people actually look. The chips only show
 * when some row has a place.
 */
@Composable
internal fun PantrySearchBar(
    query: String,
    onQuery: (String) -> Unit,
    locations: List<Pair<TermRef, String>>,
    selected: TermRef?,
    onLocation: (TermRef?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.extraSmall)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text(stringResource(Res.string.pantry_filter_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.pantry_search_clear))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large),
        )
        if (locations.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.large),
                horizontalArrangement = Arrangement.spacedBy(Dimens.small),
            ) {
                item(key = "all") {
                    TermChip(
                        label = stringResource(Res.string.pantry_filter_all),
                        selected = selected == null,
                        onToggle = { onLocation(null) },
                    )
                }
                items(locations, key = { (ref, _) -> "${ref.taxonomy.value}/${ref.term.value}" }) { (ref, label) ->
                    TermChip(label = label, selected = ref == selected, onToggle = { onLocation(ref) })
                }
            }
        }
    }
}
