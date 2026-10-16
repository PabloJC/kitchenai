package com.kitchenai.ui.presentation.kitchen

/** Codes are UUIDs, so one can be picked out of a whole pasted invite message. */
private val CODE_PATTERN = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")

/** The code inside [text] in the casing it is shown in, or null when there is none to find. */
internal fun extractJoinCode(text: String): String? = CODE_PATTERN.find(text)?.value?.uppercase()

/** Stored lower case; shown upper case, where 0/O and 1/l are harder to confuse in a monospace face. */
internal fun displayJoinCode(code: String): String = code.uppercase()
