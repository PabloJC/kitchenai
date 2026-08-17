package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.pantry.AddPantryItemUseCase
import com.kitchenai.shared.domain.usecase.pantry.RemovePantryItemUseCase
import com.kitchenai.shared.domain.usecase.pantry.UpdatePantryItemUseCase

/**
 * What the pantry screen changes. [time] belongs here rather than on the ViewModel because the
 * only thing it stamps is an edit. Like [PantryReadsDelegate], it holds no logic and decides nothing.
 */
class PantryWritesDelegate(
    val add: AddPantryItemUseCase,
    val update: UpdatePantryItemUseCase,
    val remove: RemovePantryItemUseCase,
    val time: TimeProvider,
)
