package io.devnogari.gajaedeck.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation routes. Routes carry only non-secret ids, never control or bridge tokens. */
@Serializable
data object ProjectsRoute

@Serializable
data class ProjectSessionsRoute(val projectId: String)

@Serializable
data class SessionRoute(val sessionId: String)

@Serializable
data object SettingsRoute

/** Legacy migration route retained for saved/back-stack compatibility. */
@Serializable
data object PairingsListRoute
