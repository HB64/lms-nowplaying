## v1.0.13

### New
- **Power button per player**, in the player list and for the active player in the menu.
- **Next alarm**: an alarm icon behind the player name on Now playing, and the time and date of the next alarm in the player list.
- **The volume slider shows when the volume is changed on the player itself** (its remote or its own controls).
- **Long press on Play/Pause stops playback** (OK key and the media key of a remote such as a Harmony). The Stop media key stops too. While stopped, the progress bar shows 0:00 and cannot be moved.
- **Update notice**: after a fresh start LMSNP looks on GitHub once a day for a newer release and tells you. Settings > About shows it too, and OK there installs the update. "Check for updates" in Settings switches the automatic check off. Android asks once for permission to install apps and always asks you to confirm.
- **"Switch off player when switching"** in Player settings (off by default).

### Fixes
- **Queue panel**: no more getting stuck in the button row.
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

## v1.0.12

### New
- **One "Now playing settings" page** in Settings, with a live preview of title, artist and album. It holds the background and its movement, the "by" and "from" switch, the text size, the cover size, the track info (button or line) and the volume and seek steps.
- **"by" and "from" can be switched off**, and the artist and album text can be made larger in steps (the title keeps its size).
- **The big cover can be made larger** in steps from the current size. The default stays as it was.
- **One "Player settings" page** in Settings with the local player, the default player and Synchronize.
- **My Music items and their order are one list now**: OK picks an item up and moves it, Right shows or hides it.
- **Browse remembers the search**: after you start something from the library search, Browse opens at that search again with the same term.
- **Info button next to Recipe** in the settings of MIPster and SugarCube, shown while a recipe is selected: it explains that Style and Variety are hidden then.

### Fixes
- **The top level of Browse returns to the row you came from** (My Music, Radio, Apps or Mix) instead of always to My Music.
- **Playing a whole folder of favorites** is no longer cancelled when you leave the list too soon, and the message after a failed start says why (no player, or no answer from Lyrion).

### Changed
- The Settings list is shorter: Lyrion, Jellyfin, Now playing settings, Standby, Player settings, My Music and About.

### Tested and not tested
- Tested: the Now playing settings page with all its rows, the preview, the Player settings page, the merged My Music list, the library search returning with its term, the Info button at Recipe (SugarCube and MIPster), the focus on the top level of Browse.
- Not tested: the message when playing a favorites folder fails. Browse does not reopen inside a search of a plugin (Deezer, Radio Browser and the like); it opens at the level of that plugin.

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

## v1.0.10

### Fixes
- **A mix plugin that is switched off or removed is now gone from LMSNP.** The Mix menu, the start-mix button and the repeat button (Don't Stop The Music) only show plugins the server really has. Settings that a removed plugin leaves behind no longer count, and one missing plugin no longer hides the others.
- **Mix plugin settings are read again after you pick a choice**, so settings that depend on it appear or disappear straight away (for example Style and Variety in MIPster when a recipe is chosen).
- **The Mix menu keeps what it shows while Lyrion restarts** or is briefly unreachable, instead of emptying and moving the focus.
- **No crash on an unexpected answer from the server**: the call is logged and treated as failed.
- **After a mouse click has cleared the focus**, the next D-pad key puts the focus back on Play/Pause, or inside the open queue or Browse panel, instead of on a button picked by Android.
- The play/pause button also gets the focus back within a second when it is lost without a key press.

### Changed
- Settings has a new row, **About**, that shows the version of LMSNP and the release notes of all versions.
- The YouTube app has the Material icon in the Apps list.

### Tested and not tested
- Tested: switching each of Don't Stop The Music, SugarCube, MIPster, Random Flow, Random Mix and MusicIP off and on; the MIPster settings after choosing a recipe; the YouTube icon; the focus after a mouse click (Now Playing, queue panel, Browse panel).
- Not tested: keeping the Mix menu while Lyrion restarts, the protection against unexpected server answers, SugarCube settings that disappear with a recipe (this depends on the plugin's own menu), and the quicker return of the focus to Play/Pause.

## v1.0.9

### New
- **Save the queue as a Lyrion playlist**: the save icon in the queue panel asks for a name and saves the queue of the selected player as a playlist on your Lyrion server. LMSNP checks afterwards that the playlist exists and tells you if it does not. Keep Lyrion's playlist folder outside your music folder, and do not save while Lyrion is scanning (see the README, "Saving the queue as a playlist").
- **My Music order**: Settings has a new row, "My Music order". Press OK on an item to pick it up, move it with Up and Down, press OK to put it down. Search always stays on top.
- **Sync icon** next to the player name when that player is in a sync group.
- **Streaming service logo on the cover** of albums from services such as Deezer, Qobuz and TIDAL, in the top right corner, instead of at the end of the row.
- **Start-mix button for streaming tracks**: the button on the Now Playing screen now also shows for tracks of a streaming service that Lyrion has in its library, while SugarCube, MIPster or Random Flow is on.
- **Material icons in the Apps list** for Deezer, Qobuz, SoundCloud, SpotOn, Spotty and TIDAL: the same one-colour logos as the Material skin shows.

### Changed
- Going back to My Music puts you on the row you came from instead of on Search.
- In the Mix menu, MusicIP mood mix is listed above Don't Stop The Music.
- An album year of 0 is no longer shown on the Now Playing screen.
- The technical info line asks Lyrion for the stream bitrate with the `b` tag (Lyrion 9.2 and newer).

### Fixes
- Changing the Lyrion server in Settings no longer needs a restart of the app; the players of the new server show right away.

### Tested and not tested
- Tested: the Apps list icons, the My Music order, saving a queue as a playlist (also while Lyrion scans), the start-mix button and the album logo with Deezer, the stream bitrate with Deezer and Radio Paradise.
- Not tested: the album logo with Spotty, SpotOn and SoundCloud, and the start-mix button with services other than Deezer. Feedback is welcome.

## v1.0.8

### New
- **Favorites**: a heart icon wherever you get the play buttons (plugin lists such as YouTube Music, radio items, library albums and tracks). A plus adds the item to your Lyrion favorites, a minus removes it. Right after saving you can rename it, and favorites can be renamed or deleted from the Favorites list. Saved items show the icon of their service, and saved albums open as an album.
- **Moving blurred background**: two new background choices, "Album cover (blurred, slowly moving)" and "Album cover (blurred, lava lamp)". A new setting, "Background movement" (Calm, Normal, Strong), sets how far the picture travels; it only applies to these two choices.
- **Artist photo from Jellyfin** in the info overview: when Lyrion has no photo for the artist, the portrait (or else the backdrop) from Jellyfin is shown. Only an artist with exactly the same name is used.
- **Play buttons in opened plugin lists**: opening an item such as a YouTube Music mood list or a Radio Paradise stream list now shows the play, play next and add icons pinned above the list.
- **Queue covers for streams**: tracks from streaming services show their cover in the queue.

### Changed
- The Radio menu follows the same order as the Material skin.
- Moving the queue to another player: the player list scrolls and hidden players are not shown.
- A bitrate of 0 in the technical info line is treated as unknown instead of being shown.

### Fixes
- With "No Player" the progress bar no longer keeps running.
- The caption under the icon rows starts again every time the focus moves.
- Radio Paradise logos show correctly and the placeholder blocks behind icons are gone.
- A saved YouTube Music album now shows its content in Favorites instead of "No favorites found".

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

## v1.0.6

### New
- **Releases list** in the library browser, with an A-Z strip, a sort dialog and a decade/year strip. List positions are remembered when you go back.
- **Sync management** in Settings, under "Synchroniseren…" (Sync…): sync a player with other players ("Sync with…"), and set the sync options of a player ("Sync settings…"): sync volume, sync power, maintain sync, start delay, audio delay, minimum sync adjustment and packet latency. A link icon marks players that are synced in the player list.
- **Short ⋮ menu**: the player list now sits behind a single "Players" row, so you no longer scroll through all players to reach Standby and Settings.
- **Streaming service label** on the cover: shows the service a track comes from (Qobuz, Tidal, Spotify, Deezer, YouTube Music, YouTube, SoundCloud, Bandcamp, Mixcloud and others). Text labels are used on purpose, because the rights to the service logos cannot be verified.
- **Track info** (format, bit depth, sample rate, bitrate, replay gain), selectable in Settings: off, a button next to the title, a line below the album, or both.

### Changed
- Settings rows are slightly more compact so all options fit on one screen.

### Fixes
- Titles keep scrolling (marquee) after switching track or screen.
- Switching players keeps a synced group playing, and playback is only started when the player did not resume by itself.

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

## v1.0.3

### New
- **Mix settings per plugin** (inside the Mix menu): a folded "Settings" section for Random Flow, MIPster, SugarCube (new and older versions, including the Wobble setting) and Lyrion's Random Mix (mix type, new and old tracks, library and genres). Lists that fold open scroll into view and take the focus.
- **Don't Stop The Music** is now a row in the Mix menu (shown as DSTM), with its own list of providers.
- **Active mix plugin in the header**: the Mix title shows the name and the icon of the plugin that is on. The icon of Random Mix follows the chosen mix type.
- **Playlists**: browse the saved playlists of your server, play them, play them next or add them to the queue, and show their tracks with cover, title and artist · album.
- **Radio**: the Radio menu of your server (TuneIn and other radio plugins) with browsing, search, station logos and play / play next / add to the end of the queue. Radio Now Playing is listed on top when the plugin is installed, also when it is set to "My Apps".
- **My Music**: Search, Artists, Genres, Music folder, Favorites and Playlists are grouped in one item. The browser opens where you left it, as long as the app runs.
- **Icons**: the browser, the Mix menu, the "⋮" menu and the settings screen now have icons from Material Design Icons. Players get an icon by their type (Squeezelite-X, Boom, Touch, SqueezePlay), also in front of the player name in the header.
- **Scroll hint**: lists in the browser and the queue show a fade with an arrow at the bottom while more items follow.
- **Older SugarCube versions** (without batches) can start a mix; the batch rows are hidden for them.
- **Search results** show the artist and album with every track (and the track number), the matching track under an album, and long lines scroll by themselves. Album track lists show the artist per track.

### Changed
- Checkmarks and radio markers are right-aligned, and the stand-by switch in Settings shows clearly where the focus is.
- The initial focus after starting is on play/pause.
- Don't Stop The Music is shown as "DSTM" in the Mix menu.

### Fixes
- Turning genres on or off for Random Mix now works from the list.
- Random Mix: the checkmark now switches continuous mode on and off, and a mix is started with the "Start new mix" row.
- Texts that come from Lyrion's plugin menus (settings and their choices, such as the Don't Stop The Music providers) now follow the language of the device instead of the language of the server.

- Server details are no longer built into the app. Settings are only the ones you enter on the settings screen, so a field you clear stays empty. Settings from the previous version stay as long as you update over it; after uninstalling you enter them again.

### Credits
- Icons: [Material Design Icons](https://pictogrammers.com/library/mdi/) by Pictogrammers (Apache License 2.0).

## v1.0.2

### New
- **Library browser** (☰ button, top left): browse artists (with an A–Z jump), albums, tracks, genres and the music folder, search your library, and open your Lyrion favorites. Lists show cover art, and every item can be played now, played next or added to the end of the queue.
- **Queue panel** (button at the bottom right): see everything that is queued, with covers and the current track highlighted. Pick a track to jump to it or remove it from the queue.
- **Mix menu** (inside the ☰ browser): switch the Auto Mix plugins SugarCube, MIPster, Random Flow and Lyrion's own Random Mix on or off for the selected player, start a new mix, start a batch or add a batch, and read a short info text about each plugin's own settings. Switching one mix plugin on switches the other mix plugins off.
- **Start-mix button on Now Playing**: while an internet radio stream plays and an Auto Mix plugin is on, a button next to the player controls (with that plugin's icon) starts a mix right away.
- **Material-style shuffle and repeat buttons**: shuffle songs / shuffle releases / off, and repeat queue / repeat one song / Don't Stop The Music / off. Don't Stop The Music keeps its own setting and now works alongside the mix plugins.
- **"Replace next track" follows the active mix plugin** (SugarCube, MIPster, Random Flow, then Don't Stop The Music, then a random track), so the old plugin choice in Settings is no longer needed. The button is hidden while a stream or a batch plays.
- **Progress bar with a position dot**: the not-yet-played part is now a neutral translucent white, so the bar stays readable on any background. Covers that are mostly grey or black get a light bar instead of blue.
- **English and Dutch interface**: all texts follow the language of the device.
- The browser and queue panels close by themselves after 15 seconds without remote input.
- After closing the "⋮" menu or the ☰ browser, focus returns to the button you opened it with, and moves to play/pause after the idle time.
- The name of the selected player is updated immediately, and the screen shows the state of the newly selected player, also when that player has nothing queued.

### Fixes
- Selecting a player in the "⋮" menu was unreliable: the screen could keep showing the previous player's track or name, a late answer from the old player could overwrite the new one, and the player list could be briefly empty or half filled.
- The "⋮" menu sometimes stayed open or reopened after choosing a player, and the focus could end up on the queue button instead of play/pause.
- The Random Mix checkmark in the Mix menu disappeared after a few seconds although the mix was running. The app now keeps track of a Random Mix it started.
- Opening the "⋮" menu no longer shifts the whole screen down, and the menu starts on the currently selected player.
- A hover-related crash on older Android versions is fixed.
- Removed unused code and resources left over from the old smart-replace plugin setting.

## v1.0.1

### New
- **Four Now Playing background styles to choose from**: artist photo, artist photo in grayscale, blurred album cover, or a calm animated starfield. Configurable from Settings, or switched instantly from the "⋮" menu without opening the settings screen.
- **Standby-on-exit is now optional**: previously the active player always went into standby when the app closed. This can now be turned on or off, either from Settings or with a quick toggle in the "⋮" menu.
- Tracks with multiple artists (e.g. "Pete Namlook / Tetsu Inoue") now automatically fall back to the blurred album cover, so you no longer see the same generic artist image repeated.
- Radio stations and streams without their own album art now show artwork too, using the `artwork_url` Lyrion provides for these sources.
- Clean placeholder (music note icon) instead of a blank box when no artwork is available at all.
- **Choice of smart-replace plugin**: the "replace next track" button can now use either SugarCube or RandomFlow for its music-similarity pick (configurable in Settings or the "⋮" menu), before falling back to Don't Stop The Music and then a plain random track.
- **Refreshed Now Playing layout**, inspired by Material skin: rounded corners on the album covers, a full-width progress bar with elapsed/total time on either side, and title/artist/album plus the "Next" block centered next to the cover.
- **Progress bar color follows the album cover**: the bar picks up the cover's main color (falling back to white when there's no artwork) and fades smoothly between tracks.
- **Softer blurred-cover background**: the blurred album cover background is now blurrier and zoomed in further, for a calmer color-wash look.
- **Material-style transport controls**: play/pause is a solid white circle with a dark symbol, previous/next use solid icons, and all buttons (including the "⋮" menu and replace-next button) have a compact focus circle.
- The not-yet-played part of the progress bar now uses a lighter tint of the played part's color (red becomes light red, blue becomes light blue).
- **Focus returns to play/pause when idle**: after 15 seconds without remote input, D-pad focus moves back to the play/pause button (never while the "⋮" menu is open), and after 30 seconds the focus ring is hidden until the next key press.
- The smart-replace plugin now defaults to "None" on a fresh install, so it works out of the box without SugarCube or RandomFlow. If the plugin you picked doesn't react (for example its Auto Mix/Chain is off), a clear message tells you to check its settings.

### Fixes
- Album art and artist backdrop could stay blank after a full library rescan, because stale queue entries with temporary negative IDs were incorrectly skipped.
- The artist backdrop could keep showing the previous track's photo when switching to a track with no album art; it's now cleared correctly.
- Some internet radio streams showed no cover art at all, because Lyrion reports a synthetic numeric coverid for these tracks (with no real cover behind it) and provides its `artwork_url` as a path relative to the server rather than a full URL. Both cases are now handled correctly, so streams with a matching Last.fm/imageproxy cover show it properly. (Coverage still depends on what the station itself reports, so results can vary a bit by station.)
- Opening Settings from the "⋮" menu and pressing Exit/Back without changing anything used to close the whole app. It now returns to the Now Playing screen instead.
- The "replace next track" button now waits up to 8 seconds for the chosen plugin to finish, so slower similarity requests (e.g. a large MusicIP ask via SugarCube) are no longer mistaken for "nothing happened".
- The "replace next track" button no longer disturbs a queued album or batch: if there's more than just one track left to play, it now leaves the queue alone instead of swapping in an unrelated track.
