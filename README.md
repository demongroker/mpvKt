# <img alt="app icon" src=".github/assets/app_icon.svg" width="48" /> Vela

A media player for Android, built on [mpv-android](https://github.com/mpv-android/mpv-android) with a
Jetpack Compose interface. This is a personal fork of [mpvKt](https://github.com/abdallahmehiz/mpvKt)
by Abdallah Mehiz, extended with a device video library and folder tools.

Application id: `app.vela.player`

## What this fork adds

**Library**
- Browse the videos on the device **by folder**, with thumbnails and counts
- Search across folders and files, and sort by name, date added, size or last played
- Pin folders to the top, hide folders, expand and collapse
- **Continue watching** strip: started-but-unfinished files, most recent first
- Resume position remembered per file, with a progress bar on each card
- Permanent delete through the system's own confirmation dialog (Android 11+)

**Playback**
- Folder queues with Next / Previous, shuffle per folder, and loop
- Auto-play the next file when one ends; skip a file that fails to open instead of freezing
- **Locked folders**: hide a folder behind a PIN. It disappears from the library, search,
  Continue watching and queues until unlocked. The PIN is stored only as a salted PBKDF2
  hash, never in plain text
- Rotation control (follow the device or lock the current orientation), remembered
- Volume remembered between sessions, long-press to play at a faster rate
- Double-tap to seek, gesture volume and brightness, Picture-in-Picture

**Subtitles and input**
- In-player subtitle search and download, with an optional OpenSubtitles.com key
- Play a URL or a picked file directly

**Updates**
- In-app updater that verifies the downloaded APK against the release's published SHA-256
  before installing anything

## Installation

Download the APK from this repository's [releases page](https://github.com/demongroker/vela/releases).
There is **no F-Droid, IzzyOnDroid or Google Play listing** for this app — links of that kind
point at a different application.

- Single **universal APK** (all ABIs), signed with certificate `3f6aaaf0…`
- Each release publishes `app-release.apk` and `app-release.apk.sha256`; the in-app updater
  refuses an APK whose hash does not match
- Android 5.0 (API 21) and newer

## Building

Requires JDK 17 and the Android SDK.

```bash
./gradlew assembleRelease
```

Release signing reads `MPVKT_KEYSTORE`, `MPVKT_STORE_PASSWORD`, `MPVKT_KEY_PASSWORD` and
`MPVKT_KEY_ALIAS` from the environment; no key material is stored in this repository. Builds
are produced by CI in the companion repository
[demongroker/vela-build](https://github.com/demongroker/vela-build), and the unit tests run
with `./gradlew testDebugUnitTest`.

## Project layout

Logic that decides behaviour — update verification, queue order, resume rules, folder
grouping, lock hashing — lives in plain Kotlin objects with JVM unit tests, and the Compose
layer stays thin on top of them.

## Acknowledgments

- [mpv-android](https://github.com/mpv-android) for the mpv library and player core
- [Abdallah Mehiz](https://github.com/abdallahmehiz) for
  [mpvKt](https://github.com/abdallahmehiz/mpvKt), the upstream project this fork is based on
- [mpv](https://mpv.io) itself, plus the Jetpack Compose and AndroidX projects

## License

Apache License 2.0 — see [LICENSE](LICENSE).
