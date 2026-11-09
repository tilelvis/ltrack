// Top-level build file. Plugins declared here, applied in :app.
// Toolchain (audited 2026-11-08):
//   AGP 8.7.3 / Kotlin 2.0.21 / Compose Compiler 2.0.21 / KSP 2.0.21-1.0.28
//   compileSdk 35, minSdk 24, targetSdk 35, Java 17 bytecode.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
