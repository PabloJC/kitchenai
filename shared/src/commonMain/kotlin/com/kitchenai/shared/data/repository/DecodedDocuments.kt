package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppResult

/**
 * Keeps the documents that decoded and drops the ones that did not: one corrupt row must not empty
 * a user's collection. Nothing is logged and nothing is counted, because the payload is the user's
 * own content and a number no screen reads is a number nobody will ever act on.
 */
internal fun <T> List<AppResult<T>>.decodedOrDropped(): List<T> =
    mapNotNull { result -> (result as? AppResult.Success)?.data }
