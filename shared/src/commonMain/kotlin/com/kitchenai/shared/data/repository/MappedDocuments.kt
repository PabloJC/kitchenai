package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppResult

/**
 * What a snapshot decoded into: the documents that mapped, and how many did not.
 *
 * A document that fails to map is dropped instead of failing the whole list — one corrupt row
 * must not empty a user's pantry. [dropped] is the only trace kept, because a log line would
 * carry the user's own content.
 */
internal data class MappedDocuments<T>(
    val items: List<T>,
    val dropped: Int,
)

internal fun <T> List<AppResult<T>>.mapped(): MappedDocuments<T> {
    val items = mutableListOf<T>()
    var dropped = 0
    forEach { result ->
        when (result) {
            is AppResult.Success -> items += result.data
            is AppResult.Failure -> dropped++
        }
    }
    return MappedDocuments(items, dropped)
}
