plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
}

// Force a patched `ws` (transitive npm dependency pulled in via the Kotlin/Wasm toolchain) to
// resolve Dependabot alert GHSA-58qx-3vcg-4xpx (ws uninitialized memory disclosure, < 8.20.1)
// recorded in kotlin-js-store/wasm/yarn.lock. Regenerate with `./gradlew kotlinWasmUpgradeYarnLock`.
rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension>().resolution("ws", "8.20.1")
}
