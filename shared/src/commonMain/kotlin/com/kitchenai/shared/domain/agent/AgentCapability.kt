package com.kitchenai.shared.domain.agent

/**
 * What an agent is able to do. This is app vocabulary and not user context, which is why it
 * may be an enum while everything describing the cook stays an opaque reference.
 *
 * The MVP calls [SUGGEST_FROM_PANTRY] only; the other two exist so that selecting an agent
 * by what it can do is a filter and not a rewrite.
 */
enum class AgentCapability {
    SUGGEST_FROM_PANTRY,
    SUBSTITUTE_INGREDIENTS,
    PLAN_WEEK,
}
