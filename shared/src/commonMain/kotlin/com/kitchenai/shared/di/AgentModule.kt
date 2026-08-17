package com.kitchenai.shared.di

import com.kitchenai.shared.domain.agent.AgentOrchestrator
import com.kitchenai.shared.domain.agent.AgentRegistry
import com.kitchenai.shared.domain.agent.AgentSelectionStrategy
import com.kitchenai.shared.domain.agent.DefaultAgentOrchestrator
import com.kitchenai.shared.domain.agent.DefaultAgentSelectionStrategy
import com.kitchenai.shared.domain.agent.RecipeAgent
import com.kitchenai.shared.domain.usecase.recipe.SuggestRecipesUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The agent seam. No [RecipeAgent] is bound here: the registry hands over whatever the data
 * layer registered, which is what makes a second agent one binding instead of a rewrite.
 */
val agentModule: Module =
    module {
        single<AgentRegistry> { AgentRegistry { getAll<RecipeAgent>() } }
        single<AgentSelectionStrategy> { DefaultAgentSelectionStrategy() }
        single<AgentOrchestrator> { DefaultAgentOrchestrator(get(), get(), get()) }
        factory { SuggestRecipesUseCase(get(), get(), get()) }
    }
