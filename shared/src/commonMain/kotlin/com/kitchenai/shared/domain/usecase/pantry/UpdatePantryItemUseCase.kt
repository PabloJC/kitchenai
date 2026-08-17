package com.kitchenai.shared.domain.usecase.pantry

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider

/** Overwrites a holding and restamps it. */
class UpdatePantryItemUseCase(
    private val pantry: PantryRepositoryContract,
    private val time: TimeProvider,
) {
    // An amount of zero is a removal, and a removal has to be explicit: silently deleting a
    // row the caller asked to update is the kind of surprise nobody debugs twice.
    suspend operator fun invoke(
        userId: UserId,
        item: PantryItem,
    ): AppResult<PantryItem> =
        if (item.quantity.amount <= 0.0) {
            AppResult.Failure(AppError.Validation("amount", "must be greater than zero; use RemovePantryItemUseCase"))
        } else {
            val stamped = item.copy(updatedAt = time.now())
            pantry.upsert(userId, stamped).map { stamped }
        }
}
