package com.kitchenai.ui.platform

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/** `preferredLanguages` already returns BCP-47 tags, unlike a locale identifier. */
actual fun platformLanguageTags(): List<String> = NSLocale.preferredLanguages.filterIsInstance<String>()
