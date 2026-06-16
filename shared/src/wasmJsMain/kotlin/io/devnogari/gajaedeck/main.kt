package io.devnogari.gajaedeck

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.devnogari.gajaedeck.di.initGajaeDeckKoinOnce
import io.devnogari.gajaedeck.ui.DemoApp
import kotlinx.browser.document
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    if (window.location.search.contains("demo")) {
        ComposeViewport(document.body!!) {
            DemoApp()
        }
    } else {
        initGajaeDeckKoinOnce()
        ComposeViewport(document.body!!) {
            App()
        }
    }
}
