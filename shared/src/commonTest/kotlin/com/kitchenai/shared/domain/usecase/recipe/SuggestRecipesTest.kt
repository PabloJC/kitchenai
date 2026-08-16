package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.agent.AgentOrchestrator
import com.kitchenai.shared.domain.agent.SuggestionOptions
import com.kitchenai.shared.domain.agent.profile
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.UserProfileRepositoryContract
import com.kitchenai.shared.domain.usecase.pantry.FakePantryRepositoryContract
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.pantry.termRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuggestRecipesTest {
    private val stored = profile()
    private val held = listOf(pantryItem("item-1", "ing-1", Quantity(1.0, termRef("term-1"))))

    @Test
    fun `hands the stored profile and pantry to the orchestrator`() =
        runTest {
            val orchestrator = RecordingOrchestrator()
            val useCase =
                SuggestRecipes(FakeProfilePort(flowOf(stored)), FakePantryRepositoryContract(held), orchestrator)

            val result = useCase(user, SuggestionOptions(useOnlyPantry = true))

            assertTrue(result is AppResult.Success)
            assertEquals(stored, orchestrator.profile)
            assertEquals(held, orchestrator.pantry)
            assertEquals(true, orchestrator.options?.useOnlyPantry)
        }

    @Test
    fun `a failing pantry read is reported`() =
        runTest {
            val pantry = FakePantryRepositoryContract(readError = AppError.Network())
            val useCase = SuggestRecipes(FakeProfilePort(flowOf(stored)), pantry, RecordingOrchestrator())

            assertTrue(useCase(user) is AppResult.Failure)
        }

    @Test
    fun `a profile listener that has already failed is reported and never hangs`() =
        runTest {
            val useCase =
                SuggestRecipes(
                    FakeProfilePort(emptyFlow()),
                    FakePantryRepositoryContract(held),
                    RecordingOrchestrator(),
                )

            val result = useCase(user)

            assertEquals(AppError.NotFound("profile"), (result as AppResult.Failure).error)
        }
}

private class RecordingOrchestrator : AgentOrchestrator {
    var profile: UserProfile? = null
        private set
    var pantry: List<PantryItem>? = null
        private set
    var options: SuggestionOptions? = null
        private set

    override suspend fun suggest(
        profile: UserProfile,
        pantry: List<PantryItem>,
        options: SuggestionOptions,
    ): AppResult<List<RecipeSuggestion>> {
        this.profile = profile
        this.pantry = pantry
        this.options = options
        return AppResult.Success(emptyList())
    }
}

private class FakeProfilePort(
    private val stream: Flow<UserProfile>,
) : UserProfileRepositoryContract {
    override fun observeProfile(userId: UserId): Flow<UserProfile> = stream

    override fun profileErrors(userId: UserId): Flow<AppError> = emptyFlow()

    override suspend fun save(profile: UserProfile): AppResult<Unit> = AppResult.Success(Unit)
}
