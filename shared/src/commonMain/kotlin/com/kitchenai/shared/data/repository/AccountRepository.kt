package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.data.remote.firebase.AccountRemoteDataSource
import com.kitchenai.shared.domain.port.AccountRepositoryContract

/** The server does the deleting: Firestore rules let no client remove another member's kitchen or an Auth user. */
class AccountRepository(
    private val remote: AccountRemoteDataSource,
) : AccountRepositoryContract {
    override suspend fun deleteAccount(): AppResult<Unit> = remote.deleteAccount()
}
