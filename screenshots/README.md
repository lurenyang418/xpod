# XPOD screenshots

This directory contains the full-resolution device screenshots used by the project documentation. The files and their capture notes intentionally stay together in one flat directory.

## Included screenshots

| File | Screen | Notes |
| --- | --- | --- |
| `01-podcasts-home.png` | Podcast home | Four showcase subscriptions, new episodes, and the mini player |
| `02-now-playing.png` | Now playing | Artwork, paused progress, playback speed, and skip controls |
| `03-reader-list.png` | Article list | Feed filters, unread and favorite states, and article summaries |
| `04-article-reader.png` | Native article reader | Complete title, hero artwork, and formatted article content |
| `05-library.png` | Podcast library | Library filters and prepared episode states |
| `06-local-music.png` | Local music | Eight fictional tracks with artist, album, and duration metadata |
| `07-episode-list.png` | Podcast details | Episode actions, metadata, and the mini player |

The main English and Chinese README files currently display `01`, `02`, and `04`. The remaining images are available for release notes and other project documentation.

## Capture environment

The current set was captured from the Debug build on a physical device using prepared, non-sensitive showcase data.

- Device: vivo V2465A
- OS: Android 16 / API 36
- Locale: `zh-CN`
- Native image size: 1216 × 2640
- Density: 560 dpi
- Debug application ID: `tech.lury.xpod.debug`

These details describe the checked-in images; they are not general device requirements for XPOD.

## Reproduce the showcase data

The Debug source set provides an ADB-only activity that replaces the Debug database with deterministic podcast, article, playback, and local-music data. It also selects the light theme, disables dynamic color, hides the unconfigured Memos tab, prepares a paused queue, and opens the main screen.

```bash
./gradlew :app:installDebug
adb shell am start -n tech.lury.xpod.debug/app.xpod.debug.ShowcaseSeedActivity
```

The activity requires the platform `DUMP` permission, has no launcher entry, and is excluded from release builds. Running it clears existing data in `tech.lury.xpod.debug`; it does not modify the release app.

The fictional cover artwork is stored in `app/src/debug/res/drawable-nodpi/`, and the silent sample audio is stored in `app/src/debug/res/raw/`. Both are excluded from release builds with the rest of the Debug source set.

## Add or replace a screenshot

Use a two-digit sequence followed by a short lowercase identifier, for example `08-tablet-layout.png`. Add a suffix only for a meaningful variant, such as `04-article-reader-dark.png`.

1. Confirm that `adb devices -l` lists the device as `device`.
2. Enable Do Not Disturb, clear notifications, and keep the device unlocked.
3. Use prepared sample data and a fixed app theme for a consistent series.
4. Open the target state and wait for artwork, content, and progress indicators to settle.
5. Capture at the device's native resolution:

   ```bash
   adb exec-out screencap -p > screenshots/08-tablet-layout.png
   ```

6. Review the result, add it to the table above, and update any README references that should display it.

Do not publish screenshots containing notifications, loading states, clipped text, API tokens, instance URLs, account details, private subscriptions, listening history, local filenames, Memos content, or other personal information. Do not stretch images; preserve the original aspect ratio when an optimized copy is needed.
