package com.example.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable
    data object Onboarding : AppRoute

    @Serializable
    data object ToolsHome : AppRoute

    @Serializable
    data object Notes : AppRoute

    @Serializable
    data object More : AppRoute

    @Serializable
    data object Library : AppRoute

    @Serializable
    data object Vault : AppRoute

    @Serializable
    data object Feedback : AppRoute

    @Serializable
    data object DesignSystem : AppRoute

    @Serializable
    data class Reader(val documentId: String) : AppRoute

    @Serializable
    data class ToolWorkspace(val toolId: String) : AppRoute
}
