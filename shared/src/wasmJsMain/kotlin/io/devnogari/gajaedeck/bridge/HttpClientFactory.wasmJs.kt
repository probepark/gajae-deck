package io.devnogari.gajaedeck.bridge

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js

actual fun bridgeEngineFactory(): HttpClientEngineFactory<*> = Js
