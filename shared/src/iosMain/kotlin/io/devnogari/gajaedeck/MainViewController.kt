package io.devnogari.gajaedeck

import androidx.compose.ui.window.ComposeUIViewController
import io.devnogari.gajaedeck.di.initGajaeDeckKoinOnce
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initGajaeDeckKoinOnce()
    return ComposeUIViewController { App() }
}
