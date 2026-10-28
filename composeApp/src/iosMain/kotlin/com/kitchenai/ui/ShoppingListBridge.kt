package com.kitchenai.ui

import com.kitchenai.ui.presentation.quickadd.QuickAddOutcome
import com.kitchenai.ui.presentation.quickadd.QuickAddToList
import org.koin.mp.KoinPlatform

/** Called from the Siri intent with what the user said. Swift sees it as `async`; call it on the main actor. */
@Suppress("unused") // Swift calls it
suspend fun addToShoppingList(text: String): QuickAddOutcome = KoinPlatform.getKoin().get<QuickAddToList>().add(text)
