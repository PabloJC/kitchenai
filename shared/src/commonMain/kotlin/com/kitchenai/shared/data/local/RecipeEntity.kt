package com.kitchenai.shared.data.local

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * One row per locally stored recipe.
 *
 * [id], [title], [source] and [savedAtMillis] are real columns rather than part of [payload]
 * because the favourites list this table also serves will filter and order by them; everything
 * else a recipe carries — ingredients, steps, tags, the full provenance — is folded into
 * [payload] as JSON instead of normalised across tables. That trade was made once, here, rather
 * than per query.
 */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val source: String,
    val savedAtMillis: Long,
    val payload: String,
)
