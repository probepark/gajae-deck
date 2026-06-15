package io.devnogari.gajaedeck.notifications

/** Platform push-token access. Phase 3 replaces no-op actuals with APNs/FCM/Web Push. */
expect interface PushTokenProvider {
    suspend fun currentToken(): String?
}

expect fun platformPushTokenProvider(): PushTokenProvider
