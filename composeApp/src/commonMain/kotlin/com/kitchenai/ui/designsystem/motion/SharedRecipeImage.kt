@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.kitchenai.ui.designsystem.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.kitchenai.ui.designsystem.theme.LocalReducedMotion

/*
 * A recipe's photograph moves from its card to the detail header and back. Both ends name it by
 * the recipe id; outside the navigation host, or with reduced motion, the modifier does nothing.
 */

private val LocalSharedTransitions = staticCompositionLocalOf<SharedTransitionScope?> { null }
private val LocalDestinationScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Around the navigation host, so a card on one destination and a header on the next can meet. */
@Composable
fun SharedTransitions(content: @Composable () -> Unit) {
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitions provides this, content = content)
    }
}

/** Inside each destination that shows a recipe photograph: its enter and exit drive the move. */
@Composable
fun AnimatedDestination(
    scope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDestinationScope provides scope, content = content)
}

@Composable
fun Modifier.sharedRecipeImage(recipeId: String): Modifier {
    val shared = LocalSharedTransitions.current
    val destination = LocalDestinationScope.current
    if (shared == null || destination == null || LocalReducedMotion.current) return this
    return with(shared) {
        this@sharedRecipeImage.sharedElement(rememberSharedContentState("recipe-image-$recipeId"), destination)
    }
}
