package io.devnogari.gajaedeck

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.devnogari.gajaedeck.di.initGajaeDeckKoinOnce
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initGajaeDeckKoinOnce()
    ComposeViewport(document.body!!) {
        App()
    }
}
