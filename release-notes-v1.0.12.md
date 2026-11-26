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
