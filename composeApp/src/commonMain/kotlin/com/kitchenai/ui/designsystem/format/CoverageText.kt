package com.kitchenai.ui.designsystem.format

/**
 * The 0..1 fraction a progress indicator expects. A zero total is an empty recipe, not an
 * error, so it reads as no coverage instead of dividing by zero.
 */
fun coverageFraction(
    covered: Int,
    total: Int,
): Float {
    if (total <= 0) return 0f
    return (covered.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}
