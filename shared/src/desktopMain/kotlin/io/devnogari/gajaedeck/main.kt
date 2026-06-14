package io.devnogari.gajaedeck

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.devnogari.gajaedeck.di.initGajaeDeckKoinOnce

fun main() {
    initGajaeDeckKoinOnce()
    application {
        Window(onCloseRequest = ::exitApplication, title = "gajae-deck") {
            App()
        }
    }
}
