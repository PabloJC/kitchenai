package com.kitchenai.admin.catalogue

private const val INGREDIENT_FOLDER = "catalogue/ingredients"

/** A new name per upload, so a cached old picture is never served for the new one. */
fun ingredientPicturePath(
    ingredientId: String,
    epochMillis: Long,
): String = "$INGREDIENT_FOLDER/$ingredientId-$epochMillis.webp"

/** Only files this admin uploaded are deleted; a seed URL pointing elsewhere is left alone. */
fun isIngredientPictureUrl(url: String): Boolean = "/o/${INGREDIENT_FOLDER.replace("/", "%2F")}%2F" in url

/** The size that fits within [maxSide] on both sides, keeping the ratio; never scaled up. */
fun scaledToFit(
    width: Int,
    height: Int,
    maxSide: Int,
): Pair<Int, Int> {
    val longest = maxOf(width, height)
    if (longest <= maxSide) return width to height
    val ratio = maxSide.toDouble() / longest
    return maxOf(1, (width * ratio).toInt()) to maxOf(1, (height * ratio).toInt())
}
