# Vela changelog

Newest first. Each entry describes one reviewable step.

## Unreleased

Nothing yet.

### 0.1.22 — a forgotten PIN is no longer a dead end
- **feat(lock): "Forgot PIN" escape hatch.** The PIN prompt now offers Forgot PIN, which
  explains exactly what will happen before it happens: the PIN is cleared and every locked
  folder becomes visible again. It cannot be undone without setting a new PIN, which is why
  it asks first and says so plainly.
- Before this, a forgotten PIN left folders hidden until app data was cleared by hand.

### 0.1.21 — see and edit exactly which folders are locked
- **feat(library): Manage locked folders.** The + menu now lists every locked folder by
  name (behind the PIN) with a Remove button per row, so a folder can be made visible again
  without unlocking the rest. Before this, the only control was all-or-nothing: the count in
  the menu told you how many were locked but not which, and nothing could be un-locked
  individually.
- Locking one folder still locks **only that folder** — long-press a folder row → "Lock
  folder (PIN)". The count accumulates across everything ever locked, which is what the
  manager now makes visible.

### 0.1.20 — unlocking is repeatable
- **fix(library): unlocking a second time did nothing.** The + menu had one entry whose job
  changed depending on state ("Locked folders" vs "Lock them again"), so after a re-lock the
  tap landed on the wrong action. It is now two explicit entries: "Unlock locked folders (n)"
  always opens the PIN dialog, and "Lock them again (n)" only appears while a folder is open.
- **fix(library): the exclusion set is refreshed by the action itself**, not only by watching
  state, so the library repaints even when the unlock state happens to look unchanged.
- Diagnostics: every lock/unlock step logs under `VelaLock` (menu taps, PIN result, unlock
  count, refreshed exclusion count) for `adb logcat -s VelaLock` if anything still misbehaves.

### 0.1.19 — unlocks actually come back
- **fix(library): an unlocked folder stayed hidden.** The screen cached the exclusion set
  once at load, so unlocking only changed half the picture: the cached half kept hiding the
  folder. The exclusion source is now re-asked whenever a folder is unlocked or locked, so
  the library repaints immediately after the PIN is accepted.
- **fix(privacy): locked files no longer appeared in Continue watching.** The strip was built
  from the raw video list instead of the exclusion-filtered one, so a half-watched file inside
  a locked folder showed up at the top of the library. It now uses the filtered list.

### 0.1.18 — locked folders
- **feat(library): lock a folder behind a PIN.** Long-press a folder → "Lock folder (PIN)".
  Locked folders leave the library entirely — they are filtered at the same single
  exclusion point hidden folders already use, so search, Continue watching, queues and
  the folder list all lose them together. Unlock from the + menu ("Locked folders (n)"),
  and they re-lock themselves after the timeout (default 5 min) or immediately via
  "Lock them again".
- **The PIN is never stored.** Only a salted PBKDF2 hash is written (PBKDF2WithHmacSHA256,
  with the SHA1 PRF as the fallback for API 21–25), verified in constant time; the PIN
  itself is asked twice when created and never logged. Nothing about an unlock survives
  the app closing.
- Lock state is session-only: locked automatically on restart, which is the point.

### 0.1.17 — continue watching, remembered volume
- **feat(library): "Continue watching" strip** at the top of the library. Files that were
  started and not finished appear once, most recently played first, above the folders;
  tapping one opens it with the strip as the queue, so Next walks what you were watching.
  Finished files (95 %+) and barely-started ones (under 10 s) stay out. Hidden while a
  search is active.
- **feat(player): the volume level is remembered between sessions.** Saved when the player
  pauses or leaves, applied once per session on start, clamped to mpv's legal range. New
  switch under Preferences → Player → "Remember volume between sessions" (default on);
  turning it off leaves mpv's own default alone instead of forcing a value.
- build: `.kotlin/` is ignored.

### 0.1.16 — the queue stays in the player
- **fix(next): Next and Previous no longer drop you back to the library.** They used
  to start a second PlayerActivity and finish the current one. The file is now
  swapped inside the running session: the player stays on screen, the new file starts
  from the top (its own saved position is applied), the queue and the shuffle state
  are kept, and resume/delete/rotation all follow the new file.
- **fix(next): a file that will not open no longer freezes the player.** If no
  `FILE_LOADED` arrives within 4 s the item is marked failed, a short message is
  shown, and the following item is tried — up to the existing skip cap. The session
  is never finished.
- **fix(play/pause): the centre icon follows mpv, not the last tap.** It is driven by
  mpv's `pause` property (Pause while playing, Play while paused), refreshed on every
  property change, on file load and on return from background, so gesture pauses and
  system media buttons are reflected too. The glyph only flips once mpv confirms.
- Diagnostics: every Next/Previous tap logs `VelaQueue … current=<key> target=<key>
  activityFinished=false queue=<n>`, failures log `VelaQueue open failed: …`, and the
  pause state is logged under `VelaPause` on load and on resume.

### 0.1.15 — player UI fixes
- **fix(more sheet): labels are no longer squeezed.** The title has its own line and
  the actions sit in a wrapping row, each with a minimum width, wrapping at word
  boundaries over at most two lines — so "Delete file" can no longer render one
  letter per line on a narrow portrait screen at large font scale.
- **feat(player): Previous file button** in the centre row, shown only when the
  queue has an item before the current one, mirroring Next (which moved from the
  top-right row into the centre row so the pair is symmetric). Double-tap seek
  (10 s back / forward) is untouched.
- **feat(player): optional skip buttons.** Preferences → Player gains "Show skip
  ±10 s buttons" (default off), which puts back/forward 10 s beside Play, plus the
  missing "Play the next file automatically" and "Loop the folder" switches.
- **fix(a11y): the Picture-in-picture and Video aspect ratio buttons now announce
  themselves** to TalkBack; behaviour and position are unchanged.

### 0.1.14 — playback fixes
- **fix(rotate): the button now actually changes orientation.** Auto mode asks for
  `SCREEN_ORIENTATION_SENSOR` (sensor-driven, follows the screen); Locked mode sets
  the current orientation explicitly. The value the activity keeps is logged after
  every change, and the state is read from preferences whenever the player starts.
  When Auto is on but the system's own auto-rotate is off, the player says
  "Turn on auto-rotate in Quick Settings to follow the screen" instead of doing
  nothing silently.
- **fix(next): the next file plays.** `MPV_EVENT_END_FILE` is logged with its
  reason (EOF / STOP / ERROR) and the queue position. On EOF the next item is
  found by key, then by uri, path or name, so a resolved descriptor no longer
  breaks advancement; without a queue the folder is looked up and rebuilt from the
  library. A file that fails to open is skipped (up to three) instead of freezing
  on the last frame, and a URL with no folder simply stops.
- **fix(overlay): the brightness/volume slider hides 1.5 s after the last
  gesture** (it waited 2 s before and could stick), and tapping anywhere on the
  video dismisses it immediately.

### 0.1.13 — playback controls
- **feat(delete): delete a file for good, from the library or from playback.** The
  in-app confirmation ("Delete <name> permanently?", red button) comes first, then
  `MediaStore.createDeleteRequest` makes the system ask too. Only a RESULT_OK from
  the system dialog removes the file's resume row, its library row and its queue
  entry; cancelling either dialog changes nothing.
- **feat(player): auto-play next.** When a file plays to its end the next file in
  the folder starts, in the folder's current sort order — or the shuffled order
  when shuffle is on. Toggle in Preferences → Player (default on), a Next button in
  the player, and playback stops at the end of the folder unless "loop folder" is
  on.
- **feat(player): shuffle the queue.** Shuffle reorders the folder being played
  only — the library's sort is never touched — and the state is remembered per
  folder with a seed, so the order is stable between sessions.
- **feat(player): rotate button.** Cycles auto (sensor-driven, follows the device)
  and locked to the current orientation; the icon shows which is active and the
  choice persists. The existing Orientation preference keeps working and its
  default is unchanged.

## Released

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
