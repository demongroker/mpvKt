# Vela changelog

Newest first. Each entry describes one reviewable step.

## Unreleased

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
