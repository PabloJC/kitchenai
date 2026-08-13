package com.kitchenai.ui.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * The only spacing scale in the app. A literal `16.dp` in a screen is a review finding:
 * it is what makes four screens written in parallel drift apart.
 */
object Dimens {
    val none = 0.dp
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 24.dp
    val huge = 32.dp

    /** Minimum touch target; below this the gesture is unreliable on a phone held in one hand. */
    val touchTarget = 48.dp

    /** Corner radius shared by cards and the swipe background, so the two line up. */
    val corner = 12.dp

    /** Top-level containers — sheets and the outer cards — sit a step rounder than what is inside. */
    val cornerLarge = 16.dp

    /** Input fields stay squarer: a field that reads as a pill reads as a button. */
    val cornerSmall = 8.dp
}
