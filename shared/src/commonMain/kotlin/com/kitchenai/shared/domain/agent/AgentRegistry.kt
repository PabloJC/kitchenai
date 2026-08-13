package com.kitchenai.shared.domain.agent

/** Every agent the app was built with, in registration order. */
fun interface AgentRegistry {
    fun agents(): List<RecipeAgent>
}
