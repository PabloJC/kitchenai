package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.model.ShoppingSnapshot
import com.kitchenai.shared.domain.port.KitchenRepositoryContract
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract
import com.kitchenai.shared.domain.port.ShoppingListRepositoryContract
import kotlinx.coroutines.flow.first

/**
 * The signed-in user's list, read once. It never creates a list: that is the app's first launch,
 * and a widget writing one would race it. No kitchen or no list yet answers null.
 */
class GetShoppingSnapshotUseCase(
    private val session: SessionRepositoryContract,
    private val kitchens: KitchenRepositoryContract,
    private val shoppingLists: ShoppingListRepositoryContract,
    private val shoppingItems: ShoppingItemRepositoryContract,
) {
    suspend operator fun invoke(): AppResult<ShoppingSnapshot?> {
        val signedIn =
            session.observeSession().first() as? Session.SignedIn
                ?: return AppResult.Failure(AppError.Unauthorized())
        return when (val kitchen = kitchens.getMyKitchen(signedIn.userId)) {
            is AppResult.Success -> snapshotOf(kitchen.data.id)
            is AppResult.Failure -> if (kitchen.error is AppError.NotFound) AppResult.Success(null) else kitchen
        }
    }

    private suspend fun snapshotOf(kitchenId: KitchenId): AppResult<ShoppingSnapshot?> {
        val lists = shoppingLists.getLists(kitchenId)
        if (lists is AppResult.Failure) return lists
        // The oldest list, the one EnsureDefaultShoppingListUseCase settles on.
        val list = (lists as AppResult.Success).data.minByOrNull { it.updatedAt } ?: return AppResult.Success(null)
        return shoppingItems.getItems(kitchenId, list.id).map { items ->
            ShoppingSnapshot(kitchenId, list.id, items.filterNot { it.checked }.sortedBy { it.updatedAt })
        }
    }
}
