package com.kitchenai.admin.requests

data class RequestsUiState(
    val requests: List<IngredientRequest> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val query: RequestQuery = RequestQuery(),
    /** The language the apps' users type in; promoting fills that label, aliasing proposes it. */
    val typedLanguage: String = "es",
    val alias: AliasState? = null,
    /** A request whose status is being written; its buttons wait. */
    val working: String? = null,
) {
    val shown: List<IngredientRequest> get() = requests.matching(query)
}
