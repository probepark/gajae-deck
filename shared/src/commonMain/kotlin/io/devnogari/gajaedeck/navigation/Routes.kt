package io.devnogari.gajaedeck.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation routes. Routes carry only non-secret data (a pairing id, never a token). */
@Serializable
data object PairingsListRoute

@Serializable
data class SessionRoute(val pairingId: String)

@Serializable
data object SettingsRoute
