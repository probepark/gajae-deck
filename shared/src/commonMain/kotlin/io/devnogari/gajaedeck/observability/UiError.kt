package io.devnogari.gajaedeck.observability

/** Broad category for a user-facing error, for icon/copy selection in the UI. */
enum class UiErrorCategory {
    AUTH,
    PERMISSION,
    NETWORK,
    PROTOCOL,
    UNKNOWN,
}

/**
 * A user-safe error: [message] is already redacted/mapped and contains no secret, so it can be shown
 * directly (and stored in SessionUiState.error). Internal diagnostics stay in the (redacted) logs.
 */
data class UiError(
    val message: String,
    val category: UiErrorCategory,
)
