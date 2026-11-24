## v1.0.11

### New
- **Playing from Browse takes you straight back to Now Playing.** Every play action (favorites, playlists, albums, tracks, folders, radio and app items, MusicIP and the Mix menu start actions) closes Browse. Browse reopens where you left it: the list you came from in My Music, and the level and row in Radio and Apps.
- **"Are you sure?" when you exit while the player is playing**, so a back press after an input timeout no longer closes the app by accident. It does not show while the player is paused or stopped.
- **Default player "Last used"**: when no default player is set, LMSNP starts with the player you used last.

### Fixes
- **Playback from Browse sometimes did not start**, mostly with slow plugin streams: the request was cancelled when the action menu closed. It now always completes.
- **The message after a failed start says why**: no player selected, or Lyrion gave no answer.
- **Podcast episodes, Recently played and the Deezer Explore menu were missing or empty**: those plugins give their text as "title" instead of "name". LMSNP now reads both.

### Changed
- **Material icons for the Podcast plugin**: Podcasts in the Apps list, and the New episodes and Recently played rows inside it.
- **Radio list**: a radio plugin that ships a one-colour icon (such as Radio Browser) shows it, like the Material skin.

### Tested and not tested
- Tested: playing from every Browse menu and returning to Now Playing; Browse reopening at My Music, Radio and Apps; the exit question while playing; the last used player; the Podcast plugin (episodes, Recently played, icons); Radio Browser icon; Deezer menus.
- Not tested: a start that fails with a time-out from a very slow plugin beyond the message shown. Browse does not reopen at the album tracks or at a MusicIP mix list (it opens at the album list and the moods list).
