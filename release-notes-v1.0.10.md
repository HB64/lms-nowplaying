## v1.0.10

### Fixes
- **A mix plugin that is switched off or removed is now gone from LMSNP.** The Mix menu, the start-mix button and the repeat button (Don't Stop The Music) only show plugins the server really has. Settings that a removed plugin leaves behind no longer count, and one missing plugin no longer hides the others.
- **Mix plugin settings are read again after you pick a choice**, so settings that depend on it appear or disappear straight away (for example Style and Variety in MIPster when a recipe is chosen).
- **The Mix menu keeps what it shows while Lyrion restarts** or is briefly unreachable, instead of emptying and moving the focus.
- **No crash on an unexpected answer from the server**: the call is logged and treated as failed.
- **After a mouse click has cleared the focus**, the next D-pad key puts the focus back on Play/Pause, or inside the open queue or Browse panel, instead of on a button picked by Android.
- The play/pause button also gets the focus back within a second when it is lost without a key press.

### Changed
- The YouTube app has the Material icon in the Apps list.

### Tested and not tested
- Tested: switching each of Don't Stop The Music, SugarCube, MIPster, Random Flow, Random Mix and MusicIP off and on; the MIPster settings after choosing a recipe; the YouTube icon; the focus after a mouse click (Now Playing, queue panel, Browse panel).
- Not tested: keeping the Mix menu while Lyrion restarts, the protection against unexpected server answers, SugarCube settings that disappear with a recipe (this depends on the plugin's own menu), and the quicker return of the focus to Play/Pause.
