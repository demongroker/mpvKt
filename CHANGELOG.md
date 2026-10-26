# Vela changelog

Newest first. Each entry describes one reviewable step.

## Unreleased

### 0.1.12 — the library becomes the home screen
- **feat(resume): per-file positions in their own table.** `resume_state` is keyed
  by MediaStore id plus a path hash (so same-named files in two folders stay
  apart) and stores position, duration, last-played and a completed flag.
  Database version 6 with a migration; the player saves on pause, on stop and
  every 5 seconds, and seeks back to the stored position when it is 10 s+ in and
  not finished. Tests cover the write/read contract and the migration DDL.
- **feat(library): folder-first home.** Folders with a cover thumbnail and item
  count, expandable into a grid of videos that show thumbnail, duration and a
  resume bar. Pin a folder to the top, hide it, or sort by name, date, size or
  last played; search filters folders and videos at once.
- **feat(library): one repository, one exclusion point.** All media queries go
  through `MediaStoreLibraryRepository`; `LibraryExclusions` (hidden folders, plus
  file keys) is the single filter, and `LibraryExclusionSource` is the seam the
  locked-folder feature will bind to later. Nothing else knows what locking is.
- **feat(library): lazy folder paging and an LRU thumbnail cache** so a large
  library does not decode the same frame twice.
- **feat(home): URL playback and the file pickers moved behind the + button.**

### 0.1.11
- **fix(update): the banner no longer offers the version you already have.**
  The release tag is parsed into major.minor.patch plus the `-vela<N>` build and
  compared numerically with the installed build (versionName, git tag, and the
  versionCode as a fallback). The banner appears only when the release is
  strictly newer, and the check runs fresh on every launch — nothing about
  "update available" is cached.
- **fix(home): neutral URL field.** The address box no longer shows "Invalid
  protocol" on a fresh screen; the message appears only once what you typed
  cannot be played. Validation moved into `UrlValidation` with unit tests.
- **fix(home): one way to open a file.** "Pick a file" and "Open file picker"
  were two buttons doing nearly the same thing. The document picker stays the
  primary action and folder browsing is now a labelled secondary action.
- **fix(home): update banner no longer moves the screen.** It is drawn over the
  home content instead of inside it, and can be dismissed for that version
  (remembered in preferences, so it stays gone until the next release).
- **feat(subtitles): pluggable provider with a shared error language.** The
  keyless OpenSubtitles endpoint stays the default; OpenSubtitles.com can be
  switched on in Preferences → Subtitles with an API key (username and password
  too, because its download endpoint needs an account). Credentials are sealed
  with an Android Keystore AES-GCM key, never stored in the clear and never in
  source. Both providers report `ProviderUnavailable`, `RateLimited` or
  `KeyMissing`, so the player shows the same sentence either way. Results now
  show rating next to download count and format.

## 0.1.10
- **feat(library): browse the device library by folder** — folder grid with
  cover thumbnail and item count, folder opens its own grid, back returns to the
  folder list first.
- **feat(update): verify every download** against the release SHA-256 before the
  installer is opened, so a truncated download retries instead of failing with
  "package appears to be invalid".

## 0.1.9
- **feat(subtitles): download subtitles from inside the player** — search by the
  playing file's own name, no account needed, loaded as the selected track.
- **feat(update): unit-tested updater decisions** and CI that runs them.
