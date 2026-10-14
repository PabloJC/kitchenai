package com.kitchenai.ui.presentation.profile

import com.kitchenai.shared.domain.usecase.session.DeleteAccountUseCase
import com.kitchenai.shared.domain.usecase.session.ObserveSessionUseCase
import com.kitchenai.shared.domain.usecase.session.SignInWithAppleUseCase
import com.kitchenai.shared.domain.usecase.session.SignInWithGoogleUseCase
import com.kitchenai.shared.domain.usecase.session.SignOutUseCase
import com.kitchenai.shared.domain.usecase.session.SwitchToAppleAccountUseCase
import com.kitchenai.shared.domain.usecase.session.SwitchToGoogleAccountUseCase
import com.kitchenai.ui.presentation.common.PendingDisplayName

/** The account role of the profile screen (#189): session, Google or Apple sign-in or switch, sign-out, the name. */
class ProfileAccountDelegate(
    val observeSession: ObserveSessionUseCase,
    val signInWithGoogle: SignInWithGoogleUseCase,
    val switchToGoogleAccount: SwitchToGoogleAccountUseCase,
    val signInWithApple: SignInWithAppleUseCase,
    val switchToAppleAccount: SwitchToAppleAccountUseCase,
    val signOut: SignOutUseCase,
    val deleteAccount: DeleteAccountUseCase,
    val pendingDisplayName: PendingDisplayName,
)
