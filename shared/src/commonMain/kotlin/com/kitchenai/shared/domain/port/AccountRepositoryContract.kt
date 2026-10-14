package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppResult

/** The account as something that can be deleted, data and all; the session itself is [SessionRepositoryContract]'s. */
interface AccountRepositoryContract {
    /** Deletes the signed-in account on the server: its profile, its solo kitchens, and the Auth user. */
    suspend fun deleteAccount(): AppResult<Unit>
}
