package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.dish_baked_goods
import com.kitchenai.ui.resources.dish_bbq
import com.kitchenai.ui.resources.dish_bowl
import com.kitchenai.ui.resources.dish_breakfast
import com.kitchenai.ui.resources.dish_casserole
import com.kitchenai.ui.resources.dish_curry
import com.kitchenai.ui.resources.dish_dessert
import com.kitchenai.ui.resources.dish_dumplings
import com.kitchenai.ui.resources.dish_eggs
import com.kitchenai.ui.resources.dish_flatbread
import com.kitchenai.ui.resources.dish_grilled
import com.kitchenai.ui.resources.dish_noodles
import com.kitchenai.ui.resources.dish_pasta
import com.kitchenai.ui.resources.dish_pizza
import com.kitchenai.ui.resources.dish_rice_dish
import com.kitchenai.ui.resources.dish_roast
import com.kitchenai.ui.resources.dish_salad
import com.kitchenai.ui.resources.dish_sandwich
import com.kitchenai.ui.resources.dish_seafood
import com.kitchenai.ui.resources.dish_smoothie
import com.kitchenai.ui.resources.dish_soup
import com.kitchenai.ui.resources.dish_stew
import com.kitchenai.ui.resources.dish_stir_fry
import com.kitchenai.ui.resources.dish_tacos_wraps
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private const val ASPECT_RATIO = 16f / 9f

/** The taxonomy #195 seeds one term from per suggestion; not a domain concept, only a lookup key. */
private const val DISH_TYPES_TAXONOMY = "dish-types"

/**
 * `TermId.value` (#195's `dish-types` terms) to the bundled drawable standing in for it.
 *
 * Interim art: flat-colored vectors, not photographs — see `drawable/NOTICE.md`. A term #195
 * grows before this map catches up is a miss like any other, not a crash.
 */
private val dishTypeImages: Map<String, DrawableResource> =
    mapOf(
        "pasta" to Res.drawable.dish_pasta,
        "soup" to Res.drawable.dish_soup,
        "salad" to Res.drawable.dish_salad,
        "stew" to Res.drawable.dish_stew,
        "curry" to Res.drawable.dish_curry,
        "stir-fry" to Res.drawable.dish_stir_fry,
        "roast" to Res.drawable.dish_roast,
        "grilled" to Res.drawable.dish_grilled,
        "sandwich" to Res.drawable.dish_sandwich,
        "pizza" to Res.drawable.dish_pizza,
        "rice-dish" to Res.drawable.dish_rice_dish,
        "noodles" to Res.drawable.dish_noodles,
        "dessert" to Res.drawable.dish_dessert,
        "baked-goods" to Res.drawable.dish_baked_goods,
        "breakfast" to Res.drawable.dish_breakfast,
        "seafood" to Res.drawable.dish_seafood,
        "tacos-wraps" to Res.drawable.dish_tacos_wraps,
        "casserole" to Res.drawable.dish_casserole,
        "bowl" to Res.drawable.dish_bowl,
        "bbq" to Res.drawable.dish_bbq,
        "eggs" to Res.drawable.dish_eggs,
        "dumplings" to Res.drawable.dish_dumplings,
        "flatbread" to Res.drawable.dish_flatbread,
        "smoothie" to Res.drawable.dish_smoothie,
    )

/**
 * The first [dish-types][DISH_TYPES_TAXONOMY] tag with a bundled image, if any.
 *
 * A miss (no such tag, or a term not yet in [dishTypeImages]) returns null and the caller
 * renders [RecipeImagePlaceholder] — the same "a miss returns null" rule `LabelResolver` uses
 * for catalogue lookups, applied to a picture instead of a word.
 */
internal fun dishTypeDrawable(tags: List<TermRef>): DrawableResource? =
    tags.firstOrNull { it.taxonomy.value == DISH_TYPES_TAXONOMY }?.let { dishTypeImages[it.term.value] }

/**
 * A recipe's photo, by dish type. Same `16f / 9f` slot as [RecipeImagePlaceholder] either way,
 * so picking one over the other is never a layout change.
 */
@Composable
fun RecipeImage(
    tags: List<TermRef>,
    modifier: Modifier = Modifier,
) {
    val drawable = dishTypeDrawable(tags)
    if (drawable == null) {
        RecipeImagePlaceholder(modifier)
    } else {
        Image(
            painter = painterResource(drawable),
            contentDescription = null,
            modifier = modifier.fillMaxWidth().aspectRatio(ASPECT_RATIO),
            contentScale = ContentScale.Crop,
        )
    }
}
