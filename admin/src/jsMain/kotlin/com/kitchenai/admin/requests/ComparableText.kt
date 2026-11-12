package com.kitchenai.admin.requests

private val COMBINING_MARKS = Regex("[̀-ͯ]")

/** Case and accents dropped, as the queue's own key does: "Jalapeño" and "jalapeno" compare equal. */
fun comparable(text: String): String =
    text
        .lowercase()
        .asDynamic()
        .normalize("NFD")
        .unsafeCast<String>()
        .replace(COMBINING_MARKS, "")
        .replace(Regex("\\s+"), " ")
        .trim()
