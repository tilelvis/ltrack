# Loan Tracker — Native Android (Kotlin + Jetpack Compose)

A native rewrite of the Flutter loan-tracker app. Same data model (Loan + Payment),
same Kenyan M-Pesa orientation, but built with Kotlin + Jetpack Compose + Room —
producing a **~11 MB APK** instead of the 67 MB Flutter fat-APK (84% smaller).

## Why so much smaller than Flutter?

| | Flutter | This (native) |
|---|---|---|
| Engine | libflutter.so × 4 ABIs (~40 MB) | none (uses platform ART) |
| Dart AOT | ~8 MB | none |
| Material widgets | bundled | uses platform |
| **Release APK** | **67 MB** | **11 MB** |

With R8 minification enabled (needs ≥ 8 GB RAM build host), this drops to ~4 MB.
The sandbox that built this APK has only 4 GB RAM, so R8 was disabled — see
`app/build.gradle.kts` for the one-line re-enable.

## What's included (feature parity with the Flutter app's core)

- ✅ Loan CRUD (title, principal, interest, dates, lender contact)
- ✅ Payment tracking (manual + M-Pesa code field)
- ✅ Room SQLite persistence (offline-first)
- ✅ Dashboard with outstanding totals
- ✅ Loan detail with progress bar + payment history
- ✅ Material 3 theme (emerald, light/dark)
- ✅ Signed release APK (persistent keystore → in-place updates)

Deferred to a follow-up (present in the Flutter app, not yet ported):
- M-Pesa SMS auto-import (needs SMS permission + parser)
- PDF reports / receipts
- Daily payment reminders (WorkManager)
- Home-screen widget (AppWidgetProvider)
- Payment trend chart

## Build

### Prerequisites
- JDK 17
- Android SDK (compileSdk 35, build-tools 35.0.0)
- Gradle 8.11.1 (use the bundled wrapper: `./gradlew`)

### One-time setup
```bash
# Point the project at your Android SDK install
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

### Build a signed release
```bash
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

The release APK is signed with `keystore/loan-tracker-release.keystore`
(credentials in `key.properties`). Every build shares this certificate, so
Android applies updates in-place — **no uninstall needed between versions**.

### Re-enable R8 (smaller APK, needs ≥ 8 GB RAM)
In `app/build.gradle.kts`, in the `release` build type:
```kotlin
isMinifyEnabled = true
isShrinkResources = true
```
This shrinks the APK from ~11 MB to ~4 MB.

## Signing details

| | Value |
|---|---|
| Keystore | `keystore/loan-tracker-release.keystore` |
| Alias | `loan-tracker` |
| Password | `loantracker123` |
| Validity | 100 years (until 2126) |
| Signer | CN=Loan Tracker, O=tilelvis, L=Nairobi, C=KE |
| SHA-256 | 7ec273c2769f427f21a994afaa2ee44f1bc4851fe1c33cd96a468a4319dc075a |

**Before publishing to Google Play**, replace this self-signed dev keystore
with a real upload key stored in GitHub Secrets (never committed to the repo).

## Toolchain

- AGP 8.7.3 / Kotlin 2.0.21 / Compose Compiler 2.0.21
- Compose BOM 2024.12.01 / Material 3
- Room 2.6.1 (KSP annotation processing)
- minSdk 24 / targetSdk 35 / Java 17 bytecode
