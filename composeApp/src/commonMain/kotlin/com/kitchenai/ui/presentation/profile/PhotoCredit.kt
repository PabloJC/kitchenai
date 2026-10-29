package com.kitchenai.ui.presentation.profile

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The credit a bundled dish photograph's licence asks for: title, author, licence and source.
 * Read from `files/photo_credits.json`, which `drawable/NOTICE.md` mirrors.
 */
@Serializable
data class PhotoCredit(
    val image: String,
    val title: String,
    val author: String,
    val licence: String,
    val source: String,
    // Absent for public-domain photographs, which have no deed to link to.
    val licenceUrl: String? = null,
)

private val creditsJson = Json { ignoreUnknownKeys = true }

/** Null when the bundled file is malformed: the screen says so rather than crediting nobody in silence. */
internal fun parsePhotoCredits(json: String): List<PhotoCredit>? =
    try {
        creditsJson.decodeFromString<List<PhotoCredit>>(json)
    } catch (ignored: IllegalArgumentException) {
        // SerializationException is one: malformed JSON and a missing field both land here.
        null
    }
