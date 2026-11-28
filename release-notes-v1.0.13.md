## v1.0.13

### New
- **Power button per player**, in the player list and for the active player in the menu.
- **Next alarm**: an alarm icon behind the player name on Now playing, and the time and date of the next alarm in the player list.
- **The volume slider shows when the volume is changed on the player itself** (its remote or its own controls).
- **Long press on Play/Pause stops playback** (OK key and the media key of a remote such as a Harmony). The Stop media key stops too. While stopped, the progress bar shows 0:00 and cannot be moved.
- **Update notice**: after a fresh start LMSNP looks on GitHub once a day for a newer release and tells you. Settings > About shows it too, and OK there installs the update. "Check for updates" in Settings switches the automatic check off. Android asks once for permission to install apps and always asks you to confirm.
- **"Switch off player when switching"** in Player settings (off by default).

### Fixes
- **Queue panel**: no more getting stuck in the button row when the current track is not in the list.
- **The text next to the cover** is centred against the cover.
- **The Now playing settings page scrolls**, with a hint that there is more below.
- **The menu and a tip no longer fight for the focus** (the "Got it" button flickered).

### Changed
- **Switching to another player** no longer switches the previous player off by default, and LMSNP no longer sends a pause of its own: Lyrion pauses as set for that player.
- **Standby on exit** only switches the player off, for the same reason.
- Material icons for Standby on exit, Switch off player and My Music items in Settings.

### Tested and not tested
- Tested on the Shields: everything above, including installing an update from the notice and from About.
- Not tested: the update notice for a release later than this one; the first real run is the update after v1.0.13.
