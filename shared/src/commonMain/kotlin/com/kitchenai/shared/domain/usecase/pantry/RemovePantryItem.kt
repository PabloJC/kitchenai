package com.kitchenai.shared.domain.usecase.pantry

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.PantryPort

/** Drops a holding. Removing something that is not held is not an error worth surfacing. */
class RemovePantryItem(
    private val pantry: PantryPort,
) {
    suspend operator fun invoke(
        userId: UserId,
        id: PantryItemId,
    ): AppResult<Unit> = pantry.remove(userId, id)
}
