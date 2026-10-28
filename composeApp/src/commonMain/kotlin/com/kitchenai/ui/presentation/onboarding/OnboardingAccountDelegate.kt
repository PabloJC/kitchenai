package com.kitchenai.ui.presentation.onboarding

import com.kitchenai.shared.domain.usecase.session.SignInWithAppleUseCase
import com.kitchenai.shared.domain.usecase.session.SignInWithGoogleUseCase
import com.kitchenai.shared.domain.usecase.session.SwitchToAppleAccountUseCase
import com.kitchenai.shared.domain.usecase.session.SwitchToGoogleAccountUseCase
import com.kitchenai.ui.presentation.common.PendingDisplayName

/** Signing in from the first run: the same use cases Profile uses, without its switch dialog. */
class OnboardingAccountDelegate(
    val signInWithGoogle: SignInWithGoogleUseCase,
    val switchToGoogleAccount: SwitchToGoogleAccountUseCase,
    val signInWithApple: SignInWithAppleUseCase,
    val switchToAppleAccount: SwitchToAppleAccountUseCase,
    val pendingDisplayName: PendingDisplayName,
)
