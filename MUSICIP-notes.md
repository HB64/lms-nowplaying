# MusicIP (MusicMagic) integration – notes for the Mix menu extension

Goal: add the Lyrion MusicIP plugin to LMSNP's Mix menu (branch `feature/mix-settings`).
Source studied: the plugin's `Plugin.pm`, `PlayerSettings.pm`, `Prefs/*.pm` (MusicMagic.tar).

## Working agreements (unchanged)
- UI texts in English (`values/`) and Dutch (`values-nl/`), never hardcoded.
- One change at a time; Claude cannot build or push, the user builds in Android Studio.
- Never commit `app/src/main/java/com/example/lmsnowplaying/network/denon/`.

## What the plugin does (as used in Lyrion's own interface)
1. Page "MusicIP mood mix" -> list of moods.
2. A mood -> list of tracks that comes straight from the MusicIP server and respects the per-player settings.
3. Play the whole list, add it, or pick a single track.

The plugin must be enabled on the server (it is also what imports the MusicIP library). It is not an auto-mix plugin: there is no on/off state, so it does not get a checkbox like SugarCube/MIPster/Random Flow.

## JSON-RPC commands (player id is the player mac, as with the other plugins)
- `["musicip", "moods"]` -> moods (`titles_loop`, field `name`; with `menu:1` the response is a Jive menu instead).
- `["musicip", "mix", "mood:<name>"]` -> prepares and returns the mix (`titles_loop`). Other filters: `track_id`, `song_id`, `artist_id`, `contributor_id`, `album_id`, `genre_id`, `year`, `playlist`. `tags` defaults to `al`. The exact fields returned must be checked against the real server (log the raw response first).
- `["musicip", "play"]` / `["musicip", "add"]` / `["musicip", "insert"]` -> play / append / insert the last prepared mix (internally `playlist playtracks|addtracks|inserttracks listref=musicmagic_mix`). Prepare with `musicip mix` first.
- For a single track from the list, use the same track commands the browser already uses (play now / play next / add to end).

## Per-player settings (the plugin's own player settings page)
Namespace `plugin.musicip`, read/write with `["playerpref", "plugin.musicip:<name>", "?"]` and `["playerpref", "plugin.musicip:<name>", "<value>"]`, the same way SugarCube/MIPster settings are handled:

- `mix_size` – number of tracks (UI also has a unit via `mix_type`)
- `mix_type` – unit of the size
- `mix_style` – style, slider (example value 26)
- `mix_variety` – variety, slider (example value 2)
- `mix_genre` – mix genre (yes/no)
- `mix_filter` – mix filter (e.g. "All")
- `reject_size` – reject size
- `reject_type` – unit of the reject size
- `mix_genre_filter` – excluded genres

The server applies these to every mix automatically; the app only has to show and change them.

## Wanted behaviour in the app
Inside the Mix menu (not the library browser):
1. New row "MusicIP mood mix" (an entry, not a checkbox); only shown when the plugin is available.
2. Moods list -> mix list (covers/titles like the other lists) with "Play all", "Add all", "Play all next", and per track: play now / play next / add to end.
3. A "MusicIP settings" screen with the settings above, saved per player.
4. Everything localized (EN + NL).

## Open points to verify on the user's server
- The exact fields in the `musicip mix` response.
- How to detect that the plugin is available (e.g. an answer to `["musicip", "moods"]`).
- Value ranges and labels of `mix_type`, `mix_filter`, `mix_genre`.
