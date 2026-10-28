package com.kitchenai.ui.presentation.onboarding

/**
 * The three pages, in order: what the app does, what is already in the kitchen, what to avoid.
 * [JOIN] is off that path: an invite code, reached from the first page instead of the staples.
 */
enum class OnboardingStep {
    VALUE,
    STAPLES,
    PREFERENCES,
    JOIN,
    ;

    companion object {
        /** What "Step 1 of 3" counts: the join page stands apart from it. */
        val pages: List<OnboardingStep> = listOf(VALUE, STAPLES, PREFERENCES)
    }
}
