package io.devnogari.gajaedeck.bridge

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory

/** Per-platform Ktor engine (OkHttp on JVM/Android, Darwin on iOS, Js on Web). */
expect fun bridgeEngineFactory(): HttpClientEngineFactory<*>

/** Builds the shared Ktor client used by [KtorBridgeTransport]. */
fun createBridgeHttpClient(): HttpClient = HttpClient(bridgeEngineFactory())
