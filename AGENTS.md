# XPOD Contributor Guide

## Project

XPOD is a Kotlin and Jetpack Compose Android podcast client. The app targets Android 13+ and keeps user data local by default.

## Commands

- `./gradlew assembleDebug` builds the debug APK.
- `./gradlew testDebugUnitTest` runs local unit tests.
- `./gradlew connectedDebugAndroidTest` runs instrumented tests on a connected device or emulator.

## Engineering Rules

- Keep all UI state in ViewModels and expose it with `StateFlow`.
- UI composables call ViewModel actions; repositories own persistence and external I/O.
- Preserve stable podcast and episode identifiers when changing feed code.
- Keep Media3 dependencies on the version declared in `gradle/libs.versions.toml`.
- Use the Storage Access Framework for user-selected files; do not request broad storage access.
- Run the focused test suite for every changed behavior before committing.
