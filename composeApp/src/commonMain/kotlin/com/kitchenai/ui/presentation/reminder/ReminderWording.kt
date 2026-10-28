package com.kitchenai.ui.presentation.reminder

import com.kitchenai.shared.domain.model.ExpiryDigest
import com.kitchenai.shared.domain.model.PantryItem

/** Words a digest. An interface so tests need no resource bundle. */
fun interface ReminderWording {
    /** [nameOf] turns an item into the words the pantry shows for it. */
    suspend fun describe(
        digest: ExpiryDigest,
        nameOf: (PantryItem) -> String,
    ): ReminderText
}
