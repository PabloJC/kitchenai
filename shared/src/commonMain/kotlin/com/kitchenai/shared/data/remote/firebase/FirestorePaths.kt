package com.kitchenai.shared.data.remote.firebase

import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.UserId
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FirebaseFirestore

/**
 * The single place that knows the Firestore layout; no other file builds a path from strings.
 * The document conventions that go with it are in `docs/data-model.md`.
 */
class FirestorePaths(
    private val firestore: FirebaseFirestore,
) {
    // No accessor for the `users` collection itself: the rules deny listing it, so a
    // reference to it could only ever produce a permission denial.
    fun user(uid: UserId): DocumentReference = firestore.collection(USERS).document(uid.value)

    fun pantry(uid: UserId): CollectionReference = user(uid).collection(PANTRY)

    fun pantryItem(
        uid: UserId,
        itemId: PantryItemId,
    ): DocumentReference = pantry(uid).document(itemId.value)

    fun shoppingLists(uid: UserId): CollectionReference = user(uid).collection(SHOPPING_LISTS)

    fun shoppingList(
        uid: UserId,
        listId: ShoppingListId,
    ): DocumentReference = shoppingLists(uid).document(listId.value)

    fun shoppingListItems(
        uid: UserId,
        listId: ShoppingListId,
    ): CollectionReference = shoppingList(uid, listId).collection(ITEMS)

    fun shoppingListItem(
        uid: UserId,
        listId: ShoppingListId,
        itemId: ShoppingItemId,
    ): DocumentReference = shoppingListItems(uid, listId).document(itemId.value)

    fun savedRecipes(uid: UserId): CollectionReference = user(uid).collection(SAVED_RECIPES)

    fun savedRecipe(
        uid: UserId,
        recipeId: RecipeId,
    ): DocumentReference = savedRecipes(uid).document(recipeId.value)

    fun taxonomies(): CollectionReference = firestore.collection(TAXONOMIES)

    fun taxonomy(taxonomyId: TaxonomyId): DocumentReference = taxonomies().document(taxonomyId.value)

    fun terms(taxonomyId: TaxonomyId): CollectionReference = taxonomy(taxonomyId).collection(TERMS)

    fun term(
        taxonomyId: TaxonomyId,
        termId: TermId,
    ): DocumentReference = terms(taxonomyId).document(termId.value)

    fun ingredients(): CollectionReference = firestore.collection(INGREDIENTS)

    fun ingredient(ingredientId: IngredientId): DocumentReference = ingredients().document(ingredientId.value)

    fun recipes(): CollectionReference = firestore.collection(RECIPES)

    fun recipe(recipeId: RecipeId): DocumentReference = recipes().document(recipeId.value)

    private companion object {
        const val USERS = "users"
        const val PANTRY = "pantry"
        const val SHOPPING_LISTS = "shoppingLists"
        const val ITEMS = "items"
        const val SAVED_RECIPES = "savedRecipes"
        const val TAXONOMIES = "taxonomies"
        const val TERMS = "terms"
        const val INGREDIENTS = "ingredients"
        const val RECIPES = "recipes"
    }
}
