## v1.0.4

### New
- **Local player**: start and stop SB Player or Squeezelite on the device LMSNP runs on, from Settings and from the ⋮ menu ("Start local player" / "Stop local player", shown when Auto-start is off). Options for Auto-start and stopping the player when you close LMSNP.
- **Control device volume**: the slider and the volume buttons can control the volume of the Android or Google TV device itself. **Volume slider** (SB Player) can be hidden, and Squeezelite can be fixed at 100% in Lyrion.
- **Default player in Settings** with a list of the connected players ("None" clears it), and the option **Only control default**, which hides the player list and always controls the default player.
- **Settings save automatically**; server addresses are saved when you leave the field.
- **All artists of a track** are shown ("A, B"), not only the first one.
- **Progress bar** and time move every second instead of every two seconds.
- Offline players are no longer shown in the player list, which refreshes by itself.

### Changed
- The default player is no longer a checkbox in the ⋮ menu. Left or right on the D-pad closes the menu.
- "Player app" is now called "Local player".

### Fixes
- Crash on artists with special characters in their name (for example St. Vincent, P!nk, Florence + The Machine) when Jellyfin is not set up, and a crash on an empty Jellyfin address.
- After "Play now" on a track, the track list keeps the focus on that track instead of jumping to "Play whole album".
- A player that goes offline is deselected ("No Player") instead of staying selected.
- No more crash when no server address is entered yet (choosing a local player, keys in Settings).
- Closing the app with Back no longer restarts it.
- The "Got it" tip no longer flickers.
