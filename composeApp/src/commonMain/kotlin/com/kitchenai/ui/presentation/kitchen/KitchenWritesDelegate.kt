package com.kitchenai.ui.presentation.kitchen

import com.kitchenai.shared.domain.usecase.kitchen.JoinKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.LeaveKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.RegenerateKitchenJoinCodeUseCase
import com.kitchenai.shared.domain.usecase.kitchen.RemoveKitchenMemberUseCase

/**
 * What the kitchen screen changes: joining another kitchen, leaving this one, and the two
 * actions only its owner may take. Like the other screens' write delegates, it holds no logic
 * and decides nothing.
 */
class KitchenWritesDelegate(
    val join: JoinKitchenUseCase,
    val leave: LeaveKitchenUseCase,
    val removeMember: RemoveKitchenMemberUseCase,
    val regenerateJoinCode: RegenerateKitchenJoinCodeUseCase,
)
