package com.kitchenai.admin.ingredients

/** The admin reads English first, then whatever the entry has; a vocabulary with no labels shows [fallback]. */
fun Map<String, String>.displayLabel(fallback: String): String = this["en"] ?: values.firstOrNull() ?: fallback
