package com.kitchenai.ui.platform

/**
 * The device's languages as BCP-47 tags, most preferred first.
 *
 * It is read from the platform rather than declared: a default list in the source would be a
 * guess about who is holding the phone.
 */
expect fun platformLanguageTags(): List<String>
