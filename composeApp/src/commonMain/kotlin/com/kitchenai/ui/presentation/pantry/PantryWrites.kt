package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.pantry.AddPantryItem
import com.kitchenai.shared.domain.usecase.pantry.RemovePantryItem
import com.kitchenai.shared.domain.usecase.pantry.UpdatePantryItem

/**
 * What the pantry screen changes. [time] belongs here rather than on the ViewModel because the
 * only thing it stamps is an edit. Like [PantryReads], it holds no logic and decides nothing.
 */
class PantryWrites(
    val add: AddPantryItem,
    val update: UpdatePantryItem,
    val remove: RemovePantryItem,
    val time: TimeProvider,
)
