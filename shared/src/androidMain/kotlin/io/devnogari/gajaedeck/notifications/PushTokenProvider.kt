package io.devnogari.gajaedeck.notifications

/** Platform push-token access. Phase: real token in G008. */
actual interface PushTokenProvider {
    actual suspend fun currentToken(): String?
}

private class DeferredPushTokenProvider : PushTokenProvider {
    /** Phase: real token in G008. */
    override suspend fun currentToken(): String? = null
}

actual fun platformPushTokenProvider(): PushTokenProvider = DeferredPushTokenProvider()
