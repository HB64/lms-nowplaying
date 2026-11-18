## v1.0.7

### New
- **Info overview** with Artist, Release, Track and Lyrics tabs. Artist albums and release tracks can be played, queued or played next.
- **Apps** in the library browser: Lyrion apps such as YouTube Music, Bandcamp, SoundCloud and Mixcloud. Playable items open a play menu with a Browse option.
- **Streaming service logos** on the cover (loaded from the server's Material plugin, with a text label as fallback) and **streaming badges** on albums in the lists.
- **Queue**: move the queue to another player, remove duplicates (the playing track is never removed), clear the queue (the panel closes and the screen resets), and the start-mix button also shows when the queue is empty. The queue opens on the playing track.
- **MusicIP** mood mix in the Mix menu, with the per-player MusicIP settings inside the app: mix size and unit, style, variety, genre, reject size and unit, mix filter and excluded genres.
- **Server discovery**: in Settings, search for a Lyrion or Jellyfin server on the local network instead of typing the address. This needs the server to answer broadcasts on the same network (for Docker: host networking or the UDP ports 3483 for Lyrion and 7359 for Jellyfin published). Typing the address still works.
- **Player menu options**: choose a default player and "Only control default" right in the player list. The list stays open after a choice.
- The installer now shows an **Open** button after installing.

### Changed
- Icons with a caption (shown for 3 seconds) instead of text actions on albums, favorites, radio items, the queue and the Mix page. The Mix page icons are pinned above the list, and Back closes the settings first.
- Smaller Play/Pause button.
- Technical info line comes from the actual stream (source to stream when transcoded, with replay gain); transcoded MP3 bitrate shows as CBR.
- The Browse/menu button moved to the bottom row.

### Fixes
- Icon rows no longer scroll away with the track list.
- Clearing the queue also clears the now-playing screen.
