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
