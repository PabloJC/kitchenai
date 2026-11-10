package com.kitchenai.admin.ingredients

/** One language of an ingredient's name, as typed: a blank [text] is a row the admin left empty. */
data class LabelRow(
    val language: String,
    val text: String,
)
