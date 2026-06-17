package io.devnogari.gajaedeck.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Type-safe navigation routes. Routes carry only non-secret ids, never control or bridge tokens. */
@SerialName("projects")
@Serializable
data object ProjectsRoute

@SerialName("project")
@Serializable
data class ProjectSessionsRoute(val projectId: String)

@SerialName("session")
@Serializable
data class SessionRoute(val sessionId: String)

@SerialName("settings")
@Serializable
data object SettingsRoute

/** Legacy migration route retained for saved/back-stack compatibility. */
@SerialName("pairings")
@Serializable
data object PairingsListRoute
