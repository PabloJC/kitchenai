package com.kitchenai.ui.presentation.quickadd

/** What a voice command answers with. A flat enum: Swift reads it, and nested types translate poorly. */
enum class QuickAddOutcome {
    ADDED,
    NOTHING_TO_ADD,

    /** Signed out, or no list yet: only opening the app can fix that. */
    NO_LIST,
    FAILED,
}
