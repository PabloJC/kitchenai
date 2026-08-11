package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.ShoppingList
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.ShoppingListPort
import com.kitchenai.shared.domain.port.TimeProvider
import kotlinx.coroutines.flow.first

/**
 * Returns the user's list and creates one only when there is none, so calling it on every
 * launch is harmless.
 *
 * [labels] comes from the caller because presentation is what resolves a localised name; a
 * default written here would be a contextual constant.
 */
class EnsureDefaultShoppingList(
    private val shoppingList: ShoppingListPort,
    private val ids: IdGenerator,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        userId: UserId,
        labels: Map<String, String>,
    ): AppResult<ShoppingListId> {
        val snapshot = shoppingList.observeLists(userId).first()
        if (snapshot is AppResult.Failure) return snapshot
        // The oldest list wins, so two devices racing on a first launch settle on one of them.
        val existing = (snapshot as AppResult.Success).data.minByOrNull { it.updatedAt }
        if (existing != null) return AppResult.Success(existing.id)
        val id = ShoppingListId.of(ids.newId())
        if (id is AppResult.Failure) return id
        val list = ShoppingList((id as AppResult.Success).data, userId, labels, time.now())
        return shoppingList.upsertList(userId, list).map { list.id }
    }
}
