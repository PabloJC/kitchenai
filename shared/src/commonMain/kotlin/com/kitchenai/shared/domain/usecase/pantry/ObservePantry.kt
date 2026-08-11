package com.kitchenai.shared.domain.usecase.pantry

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.PantryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/** The pantry as the user should read it: what runs out first comes first. */
class ObservePantry(
    private val pantry: PantryPort,
) {
    // Sorting is domain policy: the UI has no business deciding what "urgent" means. Items
    // without an expiry are not urgent, so they go last.
    private val urgentFirst: Comparator<PantryItem> =
        compareBy<PantryItem, Instant?>(nullsLast<Instant>()) { it.expiresAt }
            .thenByDescending { it.updatedAt }

    operator fun invoke(userId: UserId): Flow<List<PantryItem>> =
        pantry.observePantry(userId).map { items -> items.sortedWith(urgentFirst) }

    /** The listener's failures, collected alongside the stream above. */
    fun errors(): Flow<AppError> = pantry.streamErrors()
}
