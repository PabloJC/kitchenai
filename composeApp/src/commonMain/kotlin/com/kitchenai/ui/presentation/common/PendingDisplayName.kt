package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.domain.model.UserId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The Google display name waiting for the profile of the uid the sign-in produces. A Koin single,
 * so it outlives the screen that offers it; `SessionViewModel` is the one that applies it.
 */
class PendingDisplayName {
    /** [userId] is null until the sign-in's uid is known; only a bound entry is ever applied. */
    data class Entry(
        val name: String,
        val userId: UserId? = null,
    )

    private val entry = MutableStateFlow<Entry?>(null)
    val current: StateFlow<Entry?> = entry.asStateFlow()

    /** Offered before the sign-in starts: the screen can be torn down before the sign-in answers. */
    fun expect(name: String) {
        entry.value = Entry(name)
    }

    /** Whoever learns the uid first binds it; a bound entry never moves to another uid. */
    fun bind(userId: UserId) {
        entry.update { pending ->
            if (pending != null && pending.userId == null) pending.copy(userId = userId) else pending
        }
    }

    /** A sign-in that failed produced no uid, so an unbound name has nothing to wait for. */
    fun dropUnbound() {
        entry.update { pending -> if (pending?.userId == null) null else pending }
    }

    /** A different uid became active: a name bound to another one is never going to apply. */
    fun dropUnless(userId: UserId) {
        entry.update { pending -> if (pending?.userId != null && pending.userId != userId) null else pending }
    }

    /** Only the entry that was applied is cleared; a newer offer made meanwhile stays. */
    fun consume(applied: Entry) {
        entry.compareAndSet(applied, null)
    }
}
