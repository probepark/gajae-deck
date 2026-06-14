package io.devnogari.gajaedeck.bridge

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual fun bridgeEngineFactory(): HttpClientEngineFactory<*> = OkHttp
