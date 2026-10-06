package com.kitchenai.ui.presentation.profile

import com.kitchenai.shared.domain.usecase.session.ObserveSessionUseCase
import com.kitchenai.shared.domain.usecase.session.SignInWithGoogleUseCase
import com.kitchenai.shared.domain.usecase.session.SignOutUseCase
import com.kitchenai.ui.presentation.common.PendingDisplayName

/** The account role of the profile screen (#189): session stream, Google sign-in and out, the name to carry over. */
class ProfileAccountDelegate(
    val observeSession: ObserveSessionUseCase,
    val signInWithGoogle: SignInWithGoogleUseCase,
    val signOut: SignOutUseCase,
    val pendingDisplayName: PendingDisplayName,
)
