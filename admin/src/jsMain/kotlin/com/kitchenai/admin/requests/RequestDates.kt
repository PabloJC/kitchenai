package com.kitchenai.admin.requests

import kotlin.js.Date

/** In the admin's own locale and zone: it is read, never stored. */
fun shortDate(millis: Long): String = Date(millis.toDouble()).toLocaleDateString()
