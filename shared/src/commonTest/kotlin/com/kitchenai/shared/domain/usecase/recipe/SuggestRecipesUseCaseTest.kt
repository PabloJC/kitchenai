package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.agent.AgentOrchestrator
import com.kitchenai.shared.domain.agent.SuggestionOptions
import com.kitchenai.shared.domain.agent.profile
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.DietaryConstraint
import com.kitchenai.shared.domain.model.HouseholdAvoids
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.UserProfileRepositoryContract
import com.kitchenai.shared.domain.service.UnitConverter
import com.kitchenai.shared.domain.usecase.kitchen.FakeKitchenRepositoryContract
import com.kitchenai.shared.domain.usecase.kitchen.kitchen
import com.kitchenai.shared.domain.usecase.kitchen.otherUser
import com.kitchenai.shared.domain.usecase.pantry.FakePantryRepositoryContract
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.pantry.termRef
import com.kitchenai.shared.domain.usecase.profile.FakeTaxonomyRepositoryContract
import com.kitchenai.shared.domain.usecase.profile.GetUnitConverterUseCase
import com.kitchenai.shared.domain.usecase.profile.metricUnitTerms
import com.kitchenai.shared.domain.usecase.profile.metricUnits
import com.kitchenai.shared.domain.usecase.profile.noUnits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuggestRecipesUseCaseTest {
    private val stored = profile()
    private val kitchens = FakeKitchenRepositoryContract()
    private val held = listOf(pantryItem("item-1", "ing-1", Quantity(1.0, termRef("term-1"))))

    @Test
    fun `hands the stored profile and pantry to the orchestrator`() =
        runTest {
            val orchestrator = RecordingOrchestrator()
            val useCase =
                SuggestRecipesUseCase(
                    FakeProfilePort(flowOf(stored)),
                    FakePantryRepositoryContract(held),
                    orchestrator,
                    noUnits(),
                    FakeTaxonomyRepositoryContract(),
                    kitchens,
                )

            val result = useCase(user, kitchen, listOf("en"), SuggestionOptions(useOnlyPantry = true))

            assertTrue(result is AppResult.Success)
            assertEquals(stored, orchestrator.profile)
            assertEquals(held, orchestrator.pantry)
            assertEquals(true, orchestrator.options?.useOnlyPantry)
        }

    /** #131: the profile's own stored tags are a fact about the person, not about this request. */
    @Test
    fun `the caller's language reaches the orchestrator rather than the profile's stored one`() =
        runTest {
            val orchestrator = RecordingOrchestrator()
            val useCase =
                SuggestRecipesUseCase(
                    FakeProfilePort(flowOf(stored)),
                    FakePantryRepositoryContract(held),
                    orchestrator,
                    noUnits(),
                    FakeTaxonomyRepositoryContract(),
                    kitchens,
                )

            useCase(user, kitchen, listOf("es"))

            assertEquals(listOf("xx"), stored.languageTags)
            assertEquals(listOf("es"), orchestrator.languageTags)
        }

    @Test
    fun `terms of a taxonomy that declares a purpose never reach the agent`() =
        runTest {
            val orchestrator = RecordingOrchestrator()
            val dish = termIn("dish-types", "pasta")
            val diet = termIn("diets", "vegan")
            val withDishType =
                stored.copy(
                    constraints =
                        listOf(
                            DietaryConstraint(dish, ConstraintStrength.SOFTEST),
                            DietaryConstraint(diet, ConstraintStrength.AVOID),
                        ),
                    preferences = listOf(dish, diet),
                )
            val catalogue =
                FakeTaxonomyRepositoryContract(
                    others =
                        listOf(
                            Taxonomy(dish.taxonomy, emptyMap(), purpose = TaxonomyPurpose.RECIPE_CLASSIFICATION),
                            Taxonomy(diet.taxonomy, emptyMap()),
                        ),
                )

            SuggestRecipesUseCase(
                FakeProfilePort(flowOf(withDishType)),
                FakePantryRepositoryContract(held),
                orchestrator,
                noUnits(),
                catalogue,
                kitchens,
            )(user, kitchen, listOf("en"))

            assertEquals(listOf(DietaryConstraint(diet, ConstraintStrength.AVOID)), orchestrator.profile?.constraints)
            assertEquals(listOf(diet), orchestrator.profile?.preferences)
        }

    @Test
    fun `a catalogue that cannot be read is reported and the orchestrator is never asked`() =
        runTest {
            val orchestrator = RecordingOrchestrator()

            val result =
                SuggestRecipesUseCase(
                    FakeProfilePort(flowOf(stored)),
                    FakePantryRepositoryContract(held),
                    orchestrator,
                    noUnits(),
                    FakeTaxonomyRepositoryContract(taxonomiesError = AppError.Network()),
                    kitchens,
                )(user, kitchen, listOf("en"))

            assertTrue(result is AppResult.Failure)
            assertEquals(null, orchestrator.profile)
        }

    @Test
    fun `a failing pantry read is reported`() =
        runTest {
            val pantry = FakePantryRepositoryContract(readError = AppError.Network())
            val useCase =
                SuggestRecipesUseCase(
                    FakeProfilePort(flowOf(stored)),
                    pantry,
                    RecordingOrchestrator(),
                    noUnits(),
                    FakeTaxonomyRepositoryContract(),
                    kitchens,
                )

            assertTrue(useCase(user, kitchen, listOf("en")) is AppResult.Failure)
        }

    @Test
    fun `a profile listener that has already failed is reported and never hangs`() =
        runTest {
            val useCase =
                SuggestRecipesUseCase(
                    FakeProfilePort(emptyFlow()),
                    FakePantryRepositoryContract(held),
                    RecordingOrchestrator(),
                    noUnits(),
                    FakeTaxonomyRepositoryContract(),
                    kitchens,
                )

            val result = useCase(user, kitchen, listOf("en"))

            assertEquals(AppError.NotFound("profile"), (result as AppResult.Failure).error)
        }

    @Test
    fun `a converter that cannot be read is reported and the orchestrator is never asked`() =
        runTest {
            val orchestrator = RecordingOrchestrator()
            val broken =
                GetUnitConverterUseCase(
                    FakeTaxonomyRepositoryContract(metricUnitTerms("taxonomy-1"), termsError = AppError.Network()),
                )

            val result =
                SuggestRecipesUseCase(
                    FakeProfilePort(flowOf(stored)),
                    FakePantryRepositoryContract(held),
                    orchestrator,
                    broken,
                    FakeTaxonomyRepositoryContract(),
                    kitchens,
                )(
                    user,
                    kitchen,
                    listOf("en"),
                )

            assertTrue(result is AppResult.Failure)
            assertEquals(null, orchestrator.profile)
        }

    @Test
    fun `the converter built from the units taxonomy is what the orchestrator verifies with`() =
        runTest {
            val orchestrator = RecordingOrchestrator()

            SuggestRecipesUseCase(
                FakeProfilePort(flowOf(stored)),
                FakePantryRepositoryContract(held),
                orchestrator,
                metricUnits("taxonomy-1"),
                FakeTaxonomyRepositoryContract(),
                kitchens,
            )(user, kitchen, listOf("en"))

            val converter = requireNotNull(orchestrator.units)
            assertEquals(0.5, converter.amountIn(Quantity(500.0, termRef("gram")), termRef("kilogram")))
        }

    private fun suggestWith(
        own: UserProfile,
        kitchenPort: FakeKitchenRepositoryContract,
        orchestrator: RecordingOrchestrator,
    ) = SuggestRecipesUseCase(
        FakeProfilePort(flowOf(own)),
        FakePantryRepositoryContract(held),
        orchestrator,
        noUnits(),
        FakeTaxonomyRepositoryContract(),
        kitchenPort,
    )

    @Test
    fun `another member's Avoid joins the request and overrides the requester's Prefer on it`() =
        runTest {
            val shared = termIn("taxonomy-1", "term-a")
            val theirsOnly = termIn("taxonomy-1", "term-b")
            val mine = termIn("taxonomy-1", "term-c")
            val own =
                stored.copy(
                    constraints =
                        listOf(
                            DietaryConstraint(shared, ConstraintStrength.PREFER),
                            DietaryConstraint(mine, ConstraintStrength.PREFER),
                        ),
                    avoidedIngredients = listOf(ingredientIdOf("ing-mine")),
                )
            val other = HouseholdAvoids(setOf(shared, theirsOnly), setOf(ingredientIdOf("ing-theirs")))
            val household =
                FakeKitchenRepositoryContract(
                    kitchen(memberIds = setOf(user, otherUser)).copy(memberAvoids = mapOf(otherUser.value to other)),
                )
            val orchestrator = RecordingOrchestrator()

            suggestWith(own, household, orchestrator)(user, kitchen, listOf("en"))

            assertEquals(
                listOf(
                    DietaryConstraint(shared, ConstraintStrength.AVOID),
                    DietaryConstraint(mine, ConstraintStrength.PREFER),
                    DietaryConstraint(theirsOnly, ConstraintStrength.AVOID),
                ),
                orchestrator.profile?.constraints,
            )
            assertEquals(listOf("ing-mine", "ing-theirs"), orchestrator.profile?.avoidedIngredients?.map { it.value })
        }

    @Test
    fun `past the callable's cap the requester's Prefers go and every Avoid stays`() =
        runTest {
            val avoids = (1..SuggestRecipesUseCase.MAX_CONSTRAINTS).map { termIn("taxonomy-1", "avoid-$it") }.toSet()
            val own =
                stored.copy(
                    constraints = listOf(DietaryConstraint(termIn("taxonomy-1", "mine"), ConstraintStrength.PREFER)),
                )
            val household =
                FakeKitchenRepositoryContract(
                    kitchen(
                        memberIds = setOf(user, otherUser),
                    ).copy(memberAvoids = mapOf(otherUser.value to HouseholdAvoids(avoids))),
                )
            val orchestrator = RecordingOrchestrator()

            suggestWith(own, household, orchestrator)(user, kitchen, listOf("en"))

            val sent = requireNotNull(orchestrator.profile).constraints
            assertEquals(SuggestRecipesUseCase.MAX_CONSTRAINTS, sent.size)
            assertTrue(sent.all { it.strength == ConstraintStrength.AVOID })
        }

    @Test
    fun `a kitchen that cannot be read is reported and the orchestrator is never asked`() =
        runTest {
            val orchestrator = RecordingOrchestrator()

            val result =
                suggestWith(stored, FakeKitchenRepositoryContract(readError = AppError.Network()), orchestrator)(
                    user,
                    kitchen,
                    listOf("en"),
                )

            assertEquals(AppResult.Failure(AppError.Network()), result)
            assertEquals(null, orchestrator.profile)
        }
}

private fun ingredientIdOf(raw: String): IngredientId = (IngredientId.of(raw) as AppResult.Success).data

private fun termIn(
    taxonomy: String,
    term: String,
): TermRef = TermRef((TaxonomyId.of(taxonomy) as AppResult.Success).data, (TermId.of(term) as AppResult.Success).data)

private class RecordingOrchestrator : AgentOrchestrator {
    var profile: UserProfile? = null
        private set
    var pantry: List<PantryItem>? = null
        private set
    var options: SuggestionOptions? = null
        private set
    var languageTags: List<String>? = null
        private set
    var units: UnitConverter? = null
        private set

    override suspend fun suggest(
        profile: UserProfile,
        pantry: List<PantryItem>,
        options: SuggestionOptions,
        languageTags: List<String>,
        units: UnitConverter,
    ): AppResult<List<RecipeSuggestion>> {
        this.profile = profile
        this.pantry = pantry
        this.options = options
        this.languageTags = languageTags
        this.units = units
        return AppResult.Success(emptyList())
    }
}

private class FakeProfilePort(
    private val stream: Flow<UserProfile>,
) : UserProfileRepositoryContract {
    override fun observeProfile(userId: UserId): Flow<UserProfile> = stream

    override fun profileErrors(userId: UserId): Flow<AppError> = emptyFlow()

    override suspend fun getProfile(userId: UserId): AppResult<UserProfile> =
        AppResult.Failure(
            AppError.NotFound("profile"),
        )

    override suspend fun save(profile: UserProfile): AppResult<Unit> = AppResult.Success(Unit)
}
