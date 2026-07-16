# XPOD

XPOD is an Android 13+ local-first RSS podcast and article reader built with Kotlin, Jetpack Compose, Room, Hilt, and Media3.

## Development

```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew testDebugUnitTest
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.
The smaller release APK is produced at `app/build/outputs/apk/release/app-arm64-v8a-release.apk`.

## Current Scope

- Add HTTPS podcast RSS or article RSS/Atom feeds and import or export mixed OPML subscriptions.
- Browse subscriptions and episodes in phone and tablet layouts.
- Read structured article content in a native Compose reader, open original pages in-app, and track read and favorite state locally.
- Play audio through a Media3 foreground service.
- Download individual episodes only on unmetered networks.
