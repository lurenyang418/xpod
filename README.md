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
- Optionally connect a [Cloud Memos](https://github.com/lurenyang418/cloud-memos) instance with a read-write API token, browse/search/create memos in a dedicated tab, and save episodes or articles as private Markdown memos. The token is encrypted with Android Keystore.
- Reorder or hide optional navigation tabs from Settings; the visible order is shared by the phone bottom bar and tablet navigation rail.
- Play audio through a Media3 foreground service.
- Download individual episodes only on unmetered networks.
