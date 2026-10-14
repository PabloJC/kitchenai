package com.kitchenai.ui.presentation.profile

import com.kitchenai.shared.domain.usecase.session.ObserveSessionUseCase
import com.kitchenai.shared.domain.usecase.session.SignInWithGoogleUseCase
import com.kitchenai.shared.domain.usecase.session.SignOutUseCase
import com.kitchenai.shared.domain.usecase.session.SwitchToGoogleAccountUseCase
import com.kitchenai.ui.presentation.common.PendingDisplayName

/** The account role of the profile screen (#189): session, Google sign-in or switch, sign-out, the name to carry. */
class ProfileAccountDelegate(
    val observeSession: ObserveSessionUseCase,
    val signInWithGoogle: SignInWithGoogleUseCase,
    val switchToGoogleAccount: SwitchToGoogleAccountUseCase,
    val signOut: SignOutUseCase,
    val pendingDisplayName: PendingDisplayName,
)
