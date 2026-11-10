package com.kitchenai.shared.core

// Importing the module registers the IANA rules with js-joda, which kotlinx-datetime runs on.
@JsModule("@js-joda/timezone")
@JsNonModule
private external object JsJodaTimeZoneModule

@Suppress("unused")
private val jsJodaTimeZone = JsJodaTimeZoneModule
