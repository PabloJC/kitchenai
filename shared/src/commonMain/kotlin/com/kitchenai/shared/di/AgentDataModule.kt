package com.kitchenai.shared.di

import com.kitchenai.shared.core.getOrElse
import com.kitchenai.shared.data.remote.agent.CallableFunctionRecipeAgent
import com.kitchenai.shared.data.remote.agent.CallableTransport
import com.kitchenai.shared.data.remote.agent.FunctionsCallableTransport
import com.kitchenai.shared.data.remote.agent.SuggestionValidator
import com.kitchenai.shared.domain.agent.AgentCapability
import com.kitchenai.shared.domain.agent.RecipeAgent
import com.kitchenai.shared.domain.model.AgentId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.TimeProvider
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * How this app is registered with itself, not with any provider. It names the agent, never the
 * model behind it: which model answers is the function's choice and arrives in the response.
 */
private const val SUGGEST_AGENT_ID = "pantry-suggest"

/**
 * Must match where the function is deployed. Without it the SDK calls `us-central1`, which is
 * not a misconfiguration the caller can see: the request simply reaches nothing.
 */
private const val SUGGEST_REGION = "europe-southwest1"

/**
 * The one agent implementation. A second one is another `single<RecipeAgent>` here and nothing
 * else — `agentModule` already collects them with `getAll`, so no registry is written twice.
 */
val agentDataModule: Module =
    module {
        single { Firebase.functions(SUGGEST_REGION) }
        single<CallableTransport> { FunctionsCallableTransport(get()) }
        single {
            val ids: IdGenerator = get()
            val time: TimeProvider = get()
            SuggestionValidator(newRecipeId = ids::newId, now = time::now)
        }
        single<RecipeAgent> {
            CallableFunctionRecipeAgent(
                // A blank literal here is a programming error, caught at startup rather than
                // carried as an AppResult nothing above could act on.
                id = AgentId.of(SUGGEST_AGENT_ID).getOrElse { error("the agent id must not be blank") },
                capabilities = setOf(AgentCapability.SUGGEST_FROM_PANTRY),
                transport = get(),
                validator = get(),
                ids = get(),
                dispatchers = get(),
            )
        }
    }
