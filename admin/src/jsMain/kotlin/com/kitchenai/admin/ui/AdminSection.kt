package com.kitchenai.admin.ui

data class AdminSection(
    val path: String,
    val title: String,
    /** The issue that fills the section; shown until it lands. */
    val issue: Int,
)

val ADMIN_SECTIONS: List<AdminSection> =
    listOf(
        AdminSection("ingredients", "Ingredients", 403),
        AdminSection("recipes", "Recipes", 406),
        AdminSection("taxonomies", "Taxonomies", 407),
        AdminSection("free-text", "Free-text queue", 404),
        AdminSection("usage", "Usage", 409),
    )

/** `#/recipes` to its section; anything unknown, or nothing, is the first one. */
fun sectionFor(hash: String): AdminSection =
    ADMIN_SECTIONS.firstOrNull { section -> hash.removePrefix("#").trim('/') == section.path }
        ?: ADMIN_SECTIONS.first()

fun AdminSection.href(): String = "#/$path"
