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
