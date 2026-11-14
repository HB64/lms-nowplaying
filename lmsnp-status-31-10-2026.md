# LMS Now Playing (LMSNP) - status 31-10-2026

Android TV app (Nvidia Shield, D-pad, native Compose, Lyrion JSON-RPC, no WebView).
Project: `C:\Server-Diversen\Android-Studio\LMS-NowPlaying-browsertree`, code under `app/src/main/java/com/example/lmsnowplaying/`.
Repo (renamed by the owner): https://github.com/HB64/lms-nowplaying. App name stays "LMS Now Playing".

## State: v1.0.3 ready for release (versionCode 4, versionName 1.0.3)
Build tested quickly in Android Studio on 31-10-2026: all good.

## What changed in 1.0.3
- Material Design Icons (Pictogrammers, Apache 2.0) everywhere: menus, browse, mix panel, settings, header; player-type icon (Squeezelite-X/Windows, Boom, Touch, speaker) from `players` fields `model`/`modelname`; checkmarks and bullets moved to the right. Files: `PlayerIcons.kt`, `BrowseIcons.kt` (new).
- Radio menu: `radios` query (loop `radioss_loop`) lists radio plugins; each plugin has its own CLI tag; browse, search and play stations; Radio Now Playing (`radionowplaying`) on top only when installed; icons per radio type.
- "My Music" group (search, artists, genres, folders, favorites, playlists); last browse section is remembered (in memory only).
- Scroll indicator (fade + arrow) on long lists (`HintLazyColumn`).
- Search: panel no longer closes after 15 s while typing (`NavState.lastTyped`).
- Library search results show a second line: albums show the matching track ("18 • Title · Artist", "(+N)" for more) or the artist; tracks show "N • Title" with "artist · album" below. Album track lists show the artist per track. Long lines scroll (marquee) while focused. Rows with a second line use tighter padding.
- Standby switch in settings fixed (left, visible focus).

## Files
- Changed: `LMS.kt`, `BrowseScreen.kt`, `Dropdown.kt`, `PlayerSelector.kt`, `QueuePanel.kt`, `SettingsScreen.kt`, `NavState.kt`, `NowPlaying.kt`, `strings.xml` (en/nl), `app/build.gradle.kts`, `README.md` (features list, credits section, release link).
- New: `PlayerIcons.kt`, `BrowseIcons.kt`, `release-notes-v1.0.3.md`.
- Checked: no real first name in the repo files (only in the git-ignored `local.properties`).

## Release steps (done by the owner, from Android Studio)
1. Commit and push the changes (release notes file included).
2. Create release `v1.0.3`, paste `release-notes-v1.0.3.md`, attach the built APK.
3. Optional cleanup: empty folder `LMS-NowPlaying-main` in the project root (owner's decision).

## Notes
- App changes need a rebuild in Android Studio; no Lyrion restart needed.
- Never modify the Lyrion MusicIP plugin.
- A very large playlist slowness was a server-side issue (MusicIP library), not an app issue.

## Possible later work
- Remember browse position across app restarts / deeper levels.
- Scroll hint in the Settings screen and the three-dot menu.
- Merge the two identical `titles` lookups in `LMS.search()` into one.
