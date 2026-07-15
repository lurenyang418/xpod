# XPOD

XPOD is an Android 13+ local-first RSS podcast client built with Kotlin, Jetpack Compose, Room, Hilt, and Media3.

## Development

```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew testDebugUnitTest
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.
The smaller release APK is produced at `app/build/outputs/apk/release/app-arm64-v8a-release.apk`.

## Current Scope

- Add HTTPS RSS feeds and import or export OPML subscriptions.
- Browse subscriptions and episodes in phone and tablet layouts.
- Play audio through a Media3 foreground service.
- Download individual episodes only on unmetered networks.
