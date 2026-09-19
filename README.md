**English** | [简体中文](README.zh-CN.md)

# XPOD

XPOD is a local-first podcast and article reader for Android 13+. It brings podcast RSS, article RSS/Atom, offline playback, a native article reader, and local music and video libraries into one Jetpack Compose app. Libraries can use platform-wide scanning or a user-selected folder through Android's Storage Access Framework.

Current version: **0.9.9** · Android **13+** · **arm64-v8a** · [Apache-2.0](LICENSE)

## Screenshots

<p align="center">
  <img src="screenshots/01-podcasts-home.png" width="30%" alt="XPOD podcast subscriptions with original showcase covers and the mini player" />
  <img src="screenshots/02-now-playing.png" width="30%" alt="XPOD full podcast player with artwork, progress, speed, and skip controls" />
  <img src="screenshots/04-article-reader.png" width="30%" alt="XPOD native article reader with title, artwork, and formatted content" />
</p>

More captured screens and the reproducible showcase-data workflow are available in [`screenshots/`](screenshots/README.md).

## Features

### Podcasts and playback

- Add podcast feeds over HTTPS and keep stable podcast and episode identifiers across refreshes.
- Browse subscriptions, new episodes, unplayed episodes, favorites, recent playback, and continue-listening items.
- Play audio through a Media3 foreground service with persistent playback state.
- Use speed controls, 10-second rewind, 30-second forward, previous/next actions, and a full player.
- Build a queue with play-next and add-to-queue actions, then reorder, remove, or clear items.
- Download episodes into app-specific storage. Downloads use unmetered networks by default, with an option to allow cellular networks.

### Articles

- Add RSS or Atom article feeds through the same subscription flow used for podcasts.
- The `Reader` tab is for RSS/Atom articles; local books are kept in the separate `Books` tab.
- Filter by feed, unread state, or favorites and update read state individually or in bulk.
- Read structured content in a native Compose reader, including headings, images, quotes, lists, code, and tables.
- Open the original page inside the app when the feed does not provide enough content.

### Local music

- Scan the device's MediaStore audio library after granting `READ_MEDIA_AUDIO`, or select a folder with Android's Storage Access Framework for limited access.
- Recursively index supported audio documents while keeping the original files in place.
- Search by title, artist, or album and use play-all, queue, shuffle, and repeat controls.
- Recognized extensions include AAC, AMR, FLAC, M4A, MP3, OGA, OGG, Opus, WAV, and WMA, subject to device codec support.

### Local video

- Scan the device video library automatically through MediaStore after granting `READ_MEDIA_VIDEO`, or select a folder with the Storage Access Framework for limited access. MediaStore covers indexed device videos; use folder selection for locations outside that library.
- Recursively index common video files including 3GP, AVI, FLV, M4V, MKV, MOV, MP4, MPEG, MPG, TS, WebM, and WMV.
- Play videos in an immersive Media3 player with fit-to-view rendering, pause/resume, 10-second rewind, 30-second forward, playback speed, and saved viewing progress.
- While playing, open the current-folder queue from the player to switch videos; the video library also provides compact icon-only actions for rename, properties, and deletion.
- Video playback is separate from the podcast/music background queue and pauses audio playback while a video is open.

### Books

- Select a folder with Android's Storage Access Framework and build a private shelf for EPUB and PDF files.
- Read EPUB chapters in a native Compose reader with a spine-based table of contents, adjustable font size, line spacing, and reading theme.
- Read PDF files with on-demand system `PdfRenderer` pages, zoom, and bounded bitmap caching.
- Keep reading positions, favorites, and the selected folder on device; original book files stay in place.

### Local Markdown notes

- Create and edit local Markdown notes in the independent `Notes` tab; this remains available even when Cloud Memos is hidden or unused.
- Switch between source editing and themed preview. Each note remembers its own GitHub, Newsprint, Night, Follow app, or imported custom theme; import/export custom themes as validated JSON (no arbitrary CSS or scripts), with up to 24 custom themes stored locally. [Use this JSON example](docs/markdown-theme-example.json) as a starting point. New notes use the last selected theme, remote images are limited to HTTPS URLs, and fenced Kotlin/Java/Python code gets lightweight syntax highlighting.
- Insert JPEG, PNG, WebP, or GIF images through the system picker. Selected images (up to 20 MB each) are copied to app-private storage; the notes ZIP export includes these files and rewrites their links for portable Markdown.
- Use the keyboard Markdown toolbar with bounded undo/redo, search title/body text, sort notes, and share either Markdown text or a Markdown file.
- Import existing `.md` files with the system document picker; an H1 supplies the title when present, otherwise the filename is used.
- Export an individual note as UTF-8 Markdown, themed HTML, or paginated PDF, or export all notes as a ZIP containing Markdown files, image attachments, per-note theme metadata, custom theme definitions, and a manifest. Use the ZIP when you need to move notes with local images or custom themes; an individual `.md` export does not bundle image files, and HTML/PDF currently do not embed local attachments. PDF tables are rendered as text rows and do not preserve Markdown column alignment.
- Notes are stored only in the local Room database. App backup is disabled, so export a Markdown file or ZIP before clearing app data.

### Subscriptions and organization

- Import and export mixed podcast and article subscriptions as OPML.
- Refresh podcast and article feeds once per day when a network connection is available.
- Reorder or hide optional tabs. The same order is used by the phone bottom bar and tablet navigation rail.
- Choose system, light, or dark theme and optionally use Android dynamic colors.
- Use adaptive phone and tablet layouts; screens at 600dp or wider use the large-screen navigation and content arrangement.

### Optional Cloud Memos integration

- Connect an HTTPS [Cloud Memos](https://github.com/lurenyang418/cloud-memos) instance with a `cm_pat_` read-write token.
- Browse, search, filter, create, archive, restore, share, and move supported Memos to the recycle bin.
- Save podcast episodes or articles as Markdown Memos.
- Upload a local Markdown note manually from its editor as a private Memo; this is one-way, not sync. Local image attachments are not uploaded and are replaced by their alt text.
- Store the API token encrypted with Android Keystore; disconnecting removes the stored credential and key.

## Local-first and privacy

XPOD does not require an XPOD account. Podcast, episode, article, playback, queue, preference, local media index, and Markdown note data are stored on the device with Room or DataStore. App backup is disabled; local Markdown notes should be exported before clearing app data.

Network access is used only for actions that inherently need it: retrieving feeds and artwork, streaming or downloading media, opening original pages, and communicating with a Cloud Memos instance configured by the user. Downloads stay in app-specific storage. Music library scanning uses Android's `READ_MEDIA_AUDIO` permission, and automatic video scanning uses `READ_MEDIA_VIDEO` to query Android's MediaStore index. Users can instead choose a folder through the system picker for limited video access.

Cloud Memos is optional and is not a general cross-device sync service for XPOD's local database.

## Requirements

- JDK 17
- Android SDK Platform 36.1
- An Android 13+ arm64 device or emulator for installation

The repository includes the Gradle 9.6.1 wrapper, so a separate Gradle installation is not required.

## Build and install

```bash
git clone https://github.com/lurenyang418/xpod.git
cd xpod
./gradlew assembleDebug
```

The Debug APK is generated at:

```text
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Install it on a connected device with:

```bash
./gradlew installDebug
```

The Debug application ID is `tech.lury.xpod.debug`, so it can coexist with the release application ID `tech.lury.xpod`.

To build the optimized release variant:

```bash
./gradlew assembleRelease
```

The output is `app/build/outputs/apk/release/app-arm64-v8a-release.apk`. Configure `keystore.properties` before distributing a production build; without it, the local release variant falls back to the debug signing key. Tagged builds are created by [the release workflow](.github/workflows/build-apk.yml) and published to [GitHub Releases](https://github.com/lurenyang418/xpod/releases).

## Verification

Run the recommended checks before submitting a change:

```bash
./gradlew spotlessCheck testDebugUnitTest assembleDebug lintDebug
```

Spotless uses the repository-pinned ktfmt version to check Kotlin and Gradle
formatting. Run `./gradlew spotlessApply` when formatting changes are needed,
then review the resulting diff before committing.

Before publishing a release, run the release-oriented checks:

```bash
./gradlew spotlessCheck testDebugUnitTest lintDebug assembleRelease
```

With a connected device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

## Architecture

| Layer | Responsibility | Main technology |
| --- | --- | --- |
| UI | Adaptive Compose screens; actions flow through `MainViewModel`; state is exposed with `StateFlow` | Jetpack Compose, Material 3, Lifecycle |
| Data | Feed parsing, stable entities, persistence, settings, OPML, local music/video, and Cloud Memos | Room, DataStore, OkHttp, Android Keystore, SAF, MediaStore |
| Playback | Background audio, playback restoration, queues, shuffle/repeat, and media-library integration | Media3 `MediaLibraryService` |
| Downloads | App-private episode downloads with configurable network requirements | Media3 `DownloadService` and `DownloadManager` |
| Background work | Daily podcast and article refresh with network constraints and retry behavior | WorkManager |
| Dependency injection | Application-wide repositories, database, network client, and clock | Hilt |

The normal data path is: Compose UI → `MainViewModel` → repositories/controllers → Room, DataStore, Media3, SAF, MediaStore, or explicit external I/O.

## Project boundaries

- Android 13 / API 33 is the minimum supported version.
- Current APK outputs target `arm64-v8a` only.
- Feed URLs and podcast audio URLs must use HTTPS.
- XPOD is local-first; it does not currently synchronize the Room database between devices.

## Contributing

Read [`AGENTS.md`](AGENTS.md) for the project commands and engineering rules. Keep persistence and external I/O in repositories, expose UI state from ViewModels with `StateFlow`, preserve stable feed identifiers, and run the focused test suite for every changed behavior.

## License

XPOD is available under the [Apache License 2.0](LICENSE).
