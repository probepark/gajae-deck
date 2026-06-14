package io.devnogari.gajaedeck.bridge

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun bridgeEngineFactory(): HttpClientEngineFactory<*> = Darwin
