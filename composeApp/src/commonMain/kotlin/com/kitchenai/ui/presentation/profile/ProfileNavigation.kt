package com.kitchenai.ui.presentation.profile

/** Where Profile's rows lead. The nav host supplies it, so the screen never holds the controller. */
class ProfileNavigation(
    val onOpenKitchen: () -> Unit = {},
    val onOpenPhotoCredits: () -> Unit = {},
    val onOpenLicences: () -> Unit = {},
)
