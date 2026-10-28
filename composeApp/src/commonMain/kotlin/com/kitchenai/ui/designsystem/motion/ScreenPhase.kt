package com.kitchenai.ui.designsystem.motion

/** What a list screen is showing as a whole, so moving between them can cross-fade instead of cut. */
enum class ScreenPhase {
    LOADING,
    FAILED,
    EMPTY,
    CONTENT,
}
