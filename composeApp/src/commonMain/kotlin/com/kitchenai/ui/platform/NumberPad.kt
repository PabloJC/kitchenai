package com.kitchenai.ui.platform

/** False on iOS, whose number pad has no Return key: a focused amount field then offers its own Done. */
expect val numberPadHasReturnKey: Boolean
