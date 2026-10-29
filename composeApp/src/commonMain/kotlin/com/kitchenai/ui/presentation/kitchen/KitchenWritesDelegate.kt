package com.kitchenai.ui.presentation.kitchen

import com.kitchenai.shared.domain.usecase.kitchen.JoinKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.LeaveKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.RegenerateKitchenJoinCodeUseCase
import com.kitchenai.shared.domain.usecase.kitchen.RemoveKitchenMemberUseCase
import com.kitchenai.shared.domain.usecase.kitchen.TransferKitchenOwnershipUseCase

/**
 * What the kitchen screen changes: joining another kitchen, leaving this one, and the three
 * actions only its owner may take. Like the other screens' write delegates, it holds no logic
 * and decides nothing.
 */
class KitchenWritesDelegate(
    val join: JoinKitchenUseCase,
    val leave: LeaveKitchenUseCase,
    val removeMember: RemoveKitchenMemberUseCase,
    val regenerateJoinCode: RegenerateKitchenJoinCodeUseCase,
    val transferOwnership: TransferKitchenOwnershipUseCase,
)
