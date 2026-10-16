package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.HouseholdContext
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.port.UserProfileRepositoryContract
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredientsUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObservePantryUseCase
import com.kitchenai.shared.domain.usecase.recipe.GetStoredSuggestionsUseCase
import com.kitchenai.shared.domain.usecase.recipe.MatchRecipeAgainstPantryUseCase
import com.kitchenai.shared.domain.usecase.recipe.ObserveSavedRecipesUseCase
import com.kitchenai.shared.domain.usecase.recipe.RemoveSavedRecipeUseCase
import com.kitchenai.shared.domain.usecase.recipe.SaveRecipeUseCase
import com.kitchenai.shared.domain.usecase.recipe.StoreSuggestionsUseCase
import com.kitchenai.shared.domain.usecase.recipe.SuggestRecipesUseCase
import com.kitchenai.shared.domain.usecase.shopping.AddMissingIngredientsToShoppingListUseCase
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingListUseCase
import com.kitchenai.ui.presentation.common.FakeIngredientPort
import com.kitchenai.ui.presentation.common.FakeKitchenPort
import com.kitchenai.ui.presentation.common.FakePantryPort
import com.kitchenai.ui.presentation.common.FakeRecipePort
import com.kitchenai.ui.presentation.common.FakeShoppingItemPort
import com.kitchenai.ui.presentation.common.FakeShoppingListPort
import com.kitchenai.ui.presentation.common.FakeTaxonomyPort
import com.kitchenai.ui.presentation.common.noUnits
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.time.Instant

/** The screen's real use cases over fake ports, shared by the ViewModel test classes. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class SuggestionsViewModelHarness {
    protected val dispatcher = StandardTestDispatcher()
    protected val now = Instant.fromEpochSeconds(0)
    protected val catalogue = FakeIngredientPort()
    protected val agent = RecordingOrchestrator()
    protected val profile =
        UserProfile(
            userId = UserId.of("user-1").orFail(),
            displayName = null,
            languageTags = listOf("en"),
            household = HouseholdContext(servings = 2),
            constraints = emptyList(),
            preferences = emptyList(),
            avoidedIngredients = emptyList(),
            updatedAt = kotlin.time.Instant.fromEpochSeconds(0),
        )

    protected fun riceHolding(): PantryItem =
        PantryItem(
            id = PantryItemId.of("item-1").orFail(),
            ingredient = IngredientId.of("rice").orFail(),
            freeText = null,
            quantity = Quantity(1.0),
            location = null,
            expiresAt = null,
            updatedAt = now,
        )

    protected val kitchens = FakeKitchenPort()
    protected val items = FakeShoppingItemPort()
    protected val lists = FakeShoppingListPort()


    protected fun started(
        recipes: FakeRecipePort = FakeRecipePort(),
        languageTags: List<String> = listOf("en"),
        pantryPort: PantryRepositoryContract = FakePantryPort(),
    ): SuggestionsViewModel {
        return SuggestionsViewModel(
            suggestRecipes =
                SuggestRecipesUseCase(
                    StubProfilePort(profile),
                    pantryPort,
                    agent,
                    noUnits(),
                    FakeTaxonomyPort(),
                ),
            getStoredSuggestions =
                GetStoredSuggestionsUseCase(
                    recipes,
                    pantryPort,
                    TimeProvider { now },
                    noUnits(),
                ),
            storeSuggestions = StoreSuggestionsUseCase(recipes),
            matchRecipe = MatchRecipeAgainstPantryUseCase(recipes, pantryPort, TimeProvider { now }, noUnits()),
            reads =
                SuggestionsReadsDelegate(
                    ingredients = ObserveIngredientsUseCase(catalogue),
                    savedRecipes = ObserveSavedRecipesUseCase(recipes),
                    pantry = ObservePantryUseCase(pantryPort),
                    kitchen = ObserveKitchenUseCase(kitchens),
                ),
            writes =
                SuggestionsWritesDelegate(
                    addMissing =
                        AddMissingIngredientsToShoppingListUseCase(
                            recipes,
                            pantryPort,
                            items,
                            catalogue,
                            sequentialIds(),
                            TimeProvider { now },
                            noUnits(),
                        ),
                    defaultList =
                        EnsureDefaultShoppingListUseCase(lists, IdGenerator { "list-1" }, TimeProvider { now }),
                    save = SaveRecipeUseCase(recipes),
                    unsave = RemoveSavedRecipeUseCase(recipes),
                ),
        ).also { it.start(UserId.of("user-1").orFail(), languageTags, "List") }
    }

    /** Distinct ids: a constant one would fold two drafted lines into a single item. */
    protected fun sequentialIds(): IdGenerator {
        var next = 0
        return IdGenerator { "shopping-${++next}" }
    }
}

private class StubProfilePort(private val profile: UserProfile) : UserProfileRepositoryContract {
    override fun observeProfile(userId: UserId): Flow<UserProfile> = flowOf(profile)

    override fun profileErrors(userId: UserId): Flow<AppError> = emptyFlow()

    override suspend fun getProfile(userId: UserId): AppResult<UserProfile> = AppResult.Success(profile)

    override suspend fun save(profile: UserProfile): AppResult<Unit> = AppResult.Success(Unit)
}
