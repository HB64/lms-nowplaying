package com.example.lmsnowplaying.composable

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import okhttp3.OkHttpClient
import com.example.lmsnowplaying.network.logitechmediaserver.BasicAuthInterceptor
import com.example.lmsnowplaying.settings.AppConfig
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lmsnowplaying.network.logitechmediaserver.BrowseItem
import com.example.lmsnowplaying.network.logitechmediaserver.BrowseTrack
import com.example.lmsnowplaying.R
import com.example.lmsnowplaying.network.logitechmediaserver.FavoriteEntry
import com.example.lmsnowplaying.network.logitechmediaserver.FolderEntry
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
import com.example.lmsnowplaying.network.logitechmediaserver.MixAction
import com.example.lmsnowplaying.network.logitechmediaserver.MixPlugin
import com.example.lmsnowplaying.network.logitechmediaserver.MixState
import com.example.lmsnowplaying.network.logitechmediaserver.PluginSetting
import com.example.lmsnowplaying.network.logitechmediaserver.RadioEntry
import com.example.lmsnowplaying.network.logitechmediaserver.SearchResults
import com.example.lmsnowplaying.settings.NavState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Library browsing panel, modelled on the Material skin's "Browse" page: a
// top-level list that leads to sub-lists (Artists -> Albums -> Tracks,
// plus Search), with play / queue actions on albums and tracks.
private enum class BrowseLevel {
    ROOT, ARTISTS, ALBUMS, TRACKS, TRACK_ACTIONS, SEARCH, GENRES, GENRE_ARTISTS, FOLDER, MIX,
    FAVORITES, FAVORITE_ACTIONS, PLAYLISTS, PLAYLIST_TRACKS, RADIO, RADIO_ACTIONS, RADIO_SEARCH, MY_MUSIC,
    NEW_MUSIC, ALBUM_ARTISTS, COMPOSERS, YEARS, YEAR_ALBUMS, RELEASES, RELEASES_SORT,
    MUSICIP_MOODS, MUSICIP_TRACKS, MUSICIP_SETTINGS
}

// The part of the panel a level belongs to, and the way back up to it from the
// root (deeper levels need data that is gone once the panel was closed).
private fun browseSection(level: BrowseLevel): String = when (level) {
    BrowseLevel.ARTISTS, BrowseLevel.ALBUMS, BrowseLevel.TRACKS, BrowseLevel.TRACK_ACTIONS -> "ARTISTS"
    BrowseLevel.GENRES, BrowseLevel.GENRE_ARTISTS -> "GENRES"
    BrowseLevel.YEARS, BrowseLevel.YEAR_ALBUMS -> "YEARS"
    BrowseLevel.RELEASES, BrowseLevel.RELEASES_SORT -> "RELEASES"
    BrowseLevel.FAVORITES, BrowseLevel.FAVORITE_ACTIONS -> "FAVORITES"
    BrowseLevel.PLAYLISTS, BrowseLevel.PLAYLIST_TRACKS -> "PLAYLISTS"
    BrowseLevel.MUSICIP_MOODS, BrowseLevel.MUSICIP_TRACKS -> "MUSICIP_MOODS"
    BrowseLevel.MUSICIP_SETTINGS -> "MUSICIP_MOODS"
    BrowseLevel.RADIO, BrowseLevel.RADIO_ACTIONS, BrowseLevel.RADIO_SEARCH -> "RADIO"
    else -> level.name
}

// The lists an album (or its tracks) can be opened from
private val albumOrigins = setOf(
    BrowseLevel.ARTISTS, BrowseLevel.GENRES, BrowseLevel.NEW_MUSIC, BrowseLevel.ALBUM_ARTISTS,
    BrowseLevel.COMPOSERS, BrowseLevel.YEARS, BrowseLevel.RELEASES, BrowseLevel.SEARCH,
)

private fun restoreBrowse(section: String): Pair<BrowseLevel, List<BrowseLevel>> = when (section) {
    "MY_MUSIC" -> BrowseLevel.MY_MUSIC to listOf(BrowseLevel.ROOT)
    "ARTISTS", "GENRES", "FOLDER", "FAVORITES", "PLAYLISTS", "SEARCH", "NEW_MUSIC", "ALBUM_ARTISTS", "COMPOSERS", "YEARS", "RELEASES" ->
        BrowseLevel.valueOf(section) to listOf(BrowseLevel.ROOT, BrowseLevel.MY_MUSIC)
    "RADIO", "MIX" -> BrowseLevel.valueOf(section) to listOf(BrowseLevel.ROOT)
    // Apps are browsed with the radio levels
    "APPS" -> BrowseLevel.RADIO to listOf(BrowseLevel.ROOT)
    "MUSICIP_MOODS" -> BrowseLevel.MUSICIP_MOODS to listOf(BrowseLevel.ROOT, BrowseLevel.MIX)
    else -> BrowseLevel.ROOT to emptyList()
}

// Decade a release year belongs to as its first year ("1960"); "?" = no year.
private fun decadeKey(year: String): String {
    val y = year.toIntOrNull() ?: 0
    return if (y <= 0) "?" else (y / 10 * 10).toString()
}

// Remembers which row of a list was focused last (see NavState.listFocus).
private fun Modifier.trackFocus(key: String, index: Int): Modifier =
    if (key.isEmpty()) this else this.onFocusChanged { if (it.hasFocus) NavState.listFocus[key] = index }

// Lyrion's default "ignored articles" list.
private val ignoredArticles = Regex("^(the|el|la|los|las|le|les)\\s+", RegexOption.IGNORE_CASE)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BrowseScreen() {
    val appContext = LocalContext.current.applicationContext
    // Open where it was left last time
    val startSection = remember { NavState.lastBrowse }
    val restored = remember { restoreBrowse(startSection) }
    var level by remember { mutableStateOf(restored.first) }
    // Where we came from, so Back/left retraces the actual route (also when
    // an artist/album was reached through Search instead of the artist list).
    val stack = remember { mutableStateListOf<BrowseLevel>().apply { addAll(restored.second) } }
    val go: (BrowseLevel) -> Unit = { to ->
        stack.add(level)
        level = to
    }

    // Artist list, kept so going back returns to the same spot without reloading.
    var artists by remember { mutableStateOf<List<BrowseItem>?>(null) }
    val artistListState = rememberLazyListState()
    var selectedArtistIndex by remember { mutableIntStateOf(NavState.listFocus["ARTISTS"] ?: 0) }
    // Album artists: same list, restricted to the album-artist role.
    var albumArtists by remember { mutableStateOf<List<BrowseItem>?>(null) }
    val albumArtistListState = rememberLazyListState()
    var selectedAlbumArtistIndex by remember { mutableIntStateOf(NavState.listFocus["ALBUM_ARTISTS"] ?: 0) }
    // Years: list of years, then the albums of the chosen year.
    var years by remember { mutableStateOf<List<BrowseItem>?>(null) }
    val yearListState = rememberLazyListState()
    var selectedYearIndex by remember { mutableIntStateOf(NavState.listFocus["YEARS"] ?: 0) }
    var chosenYear by remember { mutableStateOf<BrowseItem?>(null) }
    var yearAlbums by remember { mutableStateOf<List<BrowseItem>?>(null) }
    val yearAlbumListState = rememberLazyListState()
    var selectedYearAlbumIndex by remember { mutableIntStateOf(0) }

    // Composers: same list, restricted to the composer role.
    var composers by remember { mutableStateOf<List<BrowseItem>?>(null) }
    val composerListState = rememberLazyListState()
    var selectedComposerIndex by remember { mutableIntStateOf(NavState.listFocus["COMPOSERS"] ?: 0) }
    var chosenArtist by remember { mutableStateOf<BrowseItem?>(null) }

    // Albums of the chosen artist.
    var albums by remember { mutableStateOf<List<BrowseItem>?>(null) }
    var albumsArtistId by remember { mutableStateOf("") }
    // Role the albums list is filtered on (composers); "" = normal artist albums.
    var albumsRole by remember { mutableStateOf("") }
    var pendingAlbumsRole by remember { mutableStateOf("") }
    val albumListState = rememberLazyListState()
    var selectedAlbumIndex by remember { mutableIntStateOf(0) }

    // New Music: the newest albums, kept while the panel is open.
    var newAlbums by remember { mutableStateOf<List<BrowseItem>?>(null) }
    val newListState = rememberLazyListState()
    var selectedNewIndex by remember { mutableIntStateOf(NavState.listFocus["NEW_MUSIC"] ?: 0) }
    // Releases: all albums by artist and title, kept while the panel is open.
    var releases by remember { mutableStateOf<List<BrowseItem>?>(null) }
    val releasesListState = rememberLazyListState()
    var selectedReleaseIndex by remember { mutableIntStateOf(NavState.listFocus["RELEASES"] ?: 0) }
    var chosenAlbum by remember { mutableStateOf<BrowseItem?>(null) }

    // The track the action menu (play now / next / add) is open for.
    var actionTrack by remember { mutableStateOf<BrowseTrack?>(null) }
    // Track list of an album: the track last chosen (focus returns to it) and the scroll position.
    var selectedTrackId by remember { mutableStateOf("") }
    val trackListState = rememberLazyListState()

    // Genres, and the artists within the chosen genre.
    var genres by remember { mutableStateOf<List<BrowseItem>?>(null) }
    val genreListState = rememberLazyListState()
    var selectedGenreIndex by remember { mutableIntStateOf(NavState.listFocus["GENRES"] ?: 0) }
    var chosenGenre by remember { mutableStateOf<BrowseItem?>(null) }
    var genreArtists by remember { mutableStateOf<List<BrowseItem>?>(null) }
    var genreArtistsGenreId by remember { mutableStateOf("") }
    val genreArtistListState = rememberLazyListState()
    var selectedGenreArtistIndex by remember { mutableIntStateOf(0) }

    // Music folder: the path of folders opened so far (last = the one shown).
    val folderPath = remember {
        mutableStateListOf<BrowseItem>().apply {
            if (restored.first == BrowseLevel.FOLDER) add(BrowseItem("", appContext.getString(R.string.browse_music_folder)))
        }
    }

    // Favorites: the path of favorite folders opened so far (last = the one
    // shown), and the favorite the action menu is open for.
    val favPath = remember {
        mutableStateListOf<BrowseItem>().apply {
            if (restored.first == BrowseLevel.FAVORITES) add(BrowseItem("", appContext.getString(R.string.browse_favorites)))
        }
    }
    var actionFavorite by remember { mutableStateOf<BrowseItem?>(null) }
    var actionPlaylist by remember { mutableStateOf<BrowseItem?>(null) }

    // MusicIP mood mix: the chosen mood and its mix (kept while its menu and track list are open).
    var chosenMood by remember { mutableStateOf("") }
    var mipMix by remember { mutableStateOf<List<Pair<BrowseTrack, String>>?>(null) }

    // Radio: the path of levels opened so far (the first is the list of radio
    // plugins, then the levels inside the chosen plugin) and that plugin's command.
    // Reopening Radio or Apps returns to the level that was open (if it was the same one of the two)
    val savedRadio = remember {
        NavState.radioPath.takeIf {
            restored.first == BrowseLevel.RADIO && it.isNotEmpty() && (it.first().id == "apps") == (startSection == "APPS")
        }
    }
    val radioPath = remember {
        mutableStateListOf<BrowseItem>().apply {
            if (savedRadio != null) addAll(savedRadio)
            else if (restored.first == BrowseLevel.RADIO) {
                if (startSection == "APPS") add(BrowseItem("apps", appContext.getString(R.string.browse_apps)))
                else add(BrowseItem("", appContext.getString(R.string.browse_radio)))
            }
        }
    }
    // Remember where we are; Apps and Radio share their levels but are separate sections
    LaunchedEffect(level) {
        // Albums and tracks are reopened at the list they were reached from (New Music, Genres, ...), not at Artists
        val origin = if (level == BrowseLevel.ALBUMS || level == BrowseLevel.TRACKS || level == BrowseLevel.TRACK_ACTIONS)
            stack.lastOrNull { it in albumOrigins } else null
        val section = if (origin != null) browseSection(origin) else browseSection(level)
        NavState.lastBrowse = if (section == "RADIO" && radioPath.firstOrNull()?.id == "apps") "APPS" else section
    }
    var radioCmd by remember { mutableStateOf(if (savedRadio != null) NavState.radioCmd else "") }
    LaunchedEffect(radioPath.size, radioPath.firstOrNull()?.id, radioPath.lastOrNull()?.id, radioCmd) {
        NavState.radioPath = radioPath.toList()
        NavState.radioCmd = radioCmd
    }
    var actionRadio by remember { mutableStateOf<BrowseItem?>(null) }
    // The radio search being typed in: the item it belongs to ("" = the plugin itself)
    var radioSearch by remember { mutableStateOf(BrowseItem("", "")) }

    // Search
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    var searching by remember { mutableStateOf(false) }
    var lastTerm by remember { mutableStateOf("") }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // Explanation next to the "Mix" title (stays visible for a while)
    var showMixInfo by remember { mutableStateOf(false) }
    LaunchedEffect(showMixInfo) {
        if (showMixInfo) {
            kotlinx.coroutines.delay(10_000)
            showMixInfo = false
        }
    }

    val chooseArtist: (BrowseItem) -> Unit = { artist ->
        // A different artist means a different album list; the same artist
        // keeps its list and position.
        val role = pendingAlbumsRole
        pendingAlbumsRole = ""
        if (artist.id != albumsArtistId || role != albumsRole) {
            albums = null
            selectedAlbumIndex = 0
            albumsArtistId = artist.id
            albumsRole = role
        }
        chosenArtist = artist
        go(BrowseLevel.ALBUMS)
    }
    val chooseAlbum: (BrowseItem) -> Unit = { album ->
        chosenAlbum = album
        selectedTrackId = ""
        go(BrowseLevel.TRACKS)
    }
    val chooseTrack: (BrowseTrack) -> Unit = { track ->
        actionTrack = track
        selectedTrackId = track.id
        go(BrowseLevel.TRACK_ACTIONS)
    }

    val chooseGenre: (BrowseItem) -> Unit = { genre ->
        if (genre.id != genreArtistsGenreId) {
            genreArtists = null
            selectedGenreArtistIndex = 0
            genreArtistsGenreId = genre.id
        }
        chosenGenre = genre
        go(BrowseLevel.GENRE_ARTISTS)
    }

    // Back goes up one step (inside the music folder: one folder up), and
    // from the top level closes the panel.
    val goBack: () -> Unit = {
        if (level == BrowseLevel.FOLDER && folderPath.size > 1) {
            folderPath.removeAt(folderPath.size - 1)
        } else if (level == BrowseLevel.FAVORITES && favPath.size > 1) {
            favPath.removeAt(favPath.size - 1)
        } else if (level == BrowseLevel.RADIO && radioPath.size > 1) {
            radioPath.removeAt(radioPath.size - 1)
        } else if (stack.isEmpty()) NavState.showBrowse = false
        else level = stack.removeAt(stack.size - 1)
    }
    BackHandler(enabled = true) { goBack() }

    // After a mouse click cleared the focus, a D-pad key comes back here (see NowPlaying)
    val panelFocus = remember { FocusRequester() }
    DisposableEffect(Unit) {
        NavState.panelRefocus = { try { panelFocus.requestFocus(); true } catch (_: Exception) { false } }
        onDispose { NavState.panelRefocus = null }
    }

    // Shown as a panel on top of Now Playing (like the player menu), not
    // as a full screen.
    Box(
        modifier = Modifier
            .width(300.dp)
            .heightIn(max = 366.dp)
            // Keep D-pad focus inside the panel: it stays open until Back,
            // so focus must not wander off to the buttons behind it.
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusRequester(panelFocus)
            .focusGroup()
            // D-pad left on a list row = one step back, like Back (the A-Z
            // strip handles its own left key first, so it isn't affected).
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    goBack()
                    true
                } else false
            }
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF2181818))
            .padding(8.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (level) {
                    BrowseLevel.ROOT -> stringResource(R.string.browse_title)
                    BrowseLevel.ARTISTS -> stringResource(R.string.browse_artists)
                    BrowseLevel.ALBUM_ARTISTS -> stringResource(R.string.browse_album_artists)
                    BrowseLevel.COMPOSERS -> stringResource(R.string.browse_composers)
                    BrowseLevel.YEARS -> stringResource(R.string.browse_years)
                    BrowseLevel.YEAR_ALBUMS -> chosenYear?.name ?: stringResource(R.string.browse_years)
                    BrowseLevel.ALBUMS -> chosenArtist?.name ?: stringResource(R.string.browse_albums)
                    BrowseLevel.TRACKS -> chosenAlbum?.name ?: stringResource(R.string.browse_tracks)
                    BrowseLevel.TRACK_ACTIONS -> actionTrack?.title ?: stringResource(R.string.browse_track)
                    BrowseLevel.SEARCH -> stringResource(R.string.browse_search)
                    BrowseLevel.GENRES -> stringResource(R.string.browse_genres)
                    BrowseLevel.GENRE_ARTISTS -> chosenGenre?.name ?: stringResource(R.string.browse_genre)
                    BrowseLevel.FOLDER -> folderPath.lastOrNull()?.name ?: stringResource(R.string.browse_music_folder)
                    BrowseLevel.MIX -> stringResource(R.string.browse_mix)
                    BrowseLevel.FAVORITES -> favPath.lastOrNull()?.name ?: stringResource(R.string.browse_favorites)
                    BrowseLevel.FAVORITE_ACTIONS -> actionFavorite?.name ?: stringResource(R.string.browse_favorite)
                    BrowseLevel.PLAYLISTS -> stringResource(R.string.browse_playlists)
                    BrowseLevel.MUSICIP_MOODS -> stringResource(R.string.browse_musicip)
                    BrowseLevel.MUSICIP_TRACKS -> chosenMood
                    BrowseLevel.MUSICIP_SETTINGS -> stringResource(R.string.musicip_settings)
                    BrowseLevel.NEW_MUSIC -> stringResource(R.string.browse_new_music)
                    BrowseLevel.RELEASES -> stringResource(R.string.browse_releases)
                    BrowseLevel.RELEASES_SORT -> stringResource(R.string.sort_by)
                    BrowseLevel.RADIO -> radioPath.lastOrNull()?.name ?: stringResource(R.string.browse_radio)
                    BrowseLevel.RADIO_ACTIONS -> actionRadio?.name ?: stringResource(R.string.browse_radio)
                    BrowseLevel.RADIO_SEARCH -> radioSearch.name
                    BrowseLevel.MY_MUSIC -> stringResource(R.string.browse_my_music)
                    BrowseLevel.PLAYLIST_TRACKS -> actionPlaylist?.name ?: stringResource(R.string.browse_playlist)
                },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )
            if (level == BrowseLevel.RELEASES) {
                FocusIconButton(
                    onClick = { go(BrowseLevel.RELEASES_SORT) },
                    size = 28.dp,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = stringResource(R.string.sort_by),
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (level == BrowseLevel.MIX) {
                FocusIconButton(
                    onClick = { showMixInfo = !showMixInfo },
                    size = 28.dp,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Info",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (NavState.activeMixLabel.isNotEmpty()) {
                    NavState.activeMixIcon?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 10.dp, bottom = 8.dp).size(18.dp)
                        )
                    }
                    Text(
                        text = NavState.activeMixLabel,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(start = if (NavState.activeMixIcon != null) 6.dp else 10.dp, bottom = 8.dp)
                    )
                }
            }
            }
            if (level == BrowseLevel.MIX && showMixInfo) {
                Text(
                    stringResource(R.string.mix_info),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                )
            }
            when (level) {
                BrowseLevel.ROOT -> RootList(
                    onMyMusic = { go(BrowseLevel.MY_MUSIC) },
                    onRadio = {
                        radioPath.clear()
                        radioPath.add(BrowseItem("", appContext.getString(R.string.browse_radio)))
                        go(BrowseLevel.RADIO)
                    },
                    onApps = {
                        // Same levels as Radio; the id "apps" on the first entry tells the list what to load
                        radioPath.clear()
                        radioPath.add(BrowseItem("apps", appContext.getString(R.string.browse_apps)))
                        go(BrowseLevel.RADIO)
                    },
                    onMix = { go(BrowseLevel.MIX) },
                )
                BrowseLevel.MY_MUSIC -> MyMusicList(
                    onSearch = { go(BrowseLevel.SEARCH) },
                    onArtists = { go(BrowseLevel.ARTISTS) },
                    onAlbumArtists = { go(BrowseLevel.ALBUM_ARTISTS) },
                    onComposers = { go(BrowseLevel.COMPOSERS) },
                    onYears = { go(BrowseLevel.YEARS) },
                    onGenres = { go(BrowseLevel.GENRES) },
                    onFolders = {
                        folderPath.clear()
                        folderPath.add(BrowseItem("", appContext.getString(R.string.browse_music_folder)))
                        go(BrowseLevel.FOLDER)
                    },
                    onFavorites = {
                        favPath.clear()
                        favPath.add(BrowseItem("", appContext.getString(R.string.browse_favorites)))
                        go(BrowseLevel.FAVORITES)
                    },
                    onPlaylists = { go(BrowseLevel.PLAYLISTS) },
                    onNewMusic = { go(BrowseLevel.NEW_MUSIC) },
                    onReleases = { go(BrowseLevel.RELEASES) },
                )
                BrowseLevel.RELEASES -> AlbumList(
                    artistId = "",
                    all = true,
                    sort = NavState.releasesSort,
                    descending = NavState.releasesDesc,
                    albums = releases,
                    onLoaded = { releases = it },
                    listState = releasesListState,
                    selectedIndex = selectedReleaseIndex,
                    focusKey = "RELEASES",
                    onSelect = { index ->
                        selectedReleaseIndex = index
                        releases?.getOrNull(index)?.let { chooseAlbum(it) }
                    }
                )
                BrowseLevel.RELEASES_SORT -> ReleasesSortList(
                    onChanged = {
                        // A new order means a new list, starting at the top
                        releases = null
                        selectedReleaseIndex = 0
                        NavState.listFocus["RELEASES"] = 0
                    },
                    onDone = goBack,
                )
                BrowseLevel.NEW_MUSIC -> AlbumList(
                    artistId = "",
                    newest = true,
                    albums = newAlbums,
                    onLoaded = { newAlbums = it },
                    listState = newListState,
                    selectedIndex = selectedNewIndex,
                    focusKey = "NEW_MUSIC",
                    onSelect = { index ->
                        selectedNewIndex = index
                        newAlbums?.getOrNull(index)?.let { chooseAlbum(it) }
                    }
                )
                BrowseLevel.RADIO -> RadioList(
                    path = radioPath,
                    cmd = radioCmd,
                    onRadio = { cmd, name ->
                        radioCmd = cmd
                        radioPath.add(BrowseItem("", name))
                    },
                    onSearch = { cmd, item ->
                        radioCmd = cmd
                        radioSearch = item
                        go(BrowseLevel.RADIO_SEARCH)
                    },
                    onFolder = { radioPath.add(it) },
                    onItem = {
                        actionRadio = it
                        go(BrowseLevel.RADIO_ACTIONS)
                    },
                )
                BrowseLevel.RADIO_SEARCH -> RadioSearch(
                    cmd = radioCmd,
                    itemId = radioSearch.id,
                    onFolder = {
                        radioPath.add(it)
                        go(BrowseLevel.RADIO)
                    },
                    onItem = {
                        actionRadio = it
                        go(BrowseLevel.RADIO_ACTIONS)
                    },
                )
                BrowseLevel.RADIO_ACTIONS -> actionRadio?.let { item ->
                    FavoriteActions(
                        item, onDone = goBack, plugin = radioCmd,
                        onOpen = if (item.openable) ({
                            goBack()
                            radioPath.add(BrowseItem(item.id, item.name))
                        }) else null,
                    )
                }
                BrowseLevel.MIX -> MixPanel(onMusicIp = { go(BrowseLevel.MUSICIP_MOODS) })
                BrowseLevel.MUSICIP_SETTINGS -> MusicIpSettingsList()
                BrowseLevel.FAVORITES -> favPath.lastOrNull()?.let { folder ->
                    FavoritesList(
                        folder = folder,
                        onFolder = { favPath.add(it) },
                        onAlbum = chooseAlbum,
                        onRemoved = { if (favPath.size > 1) favPath.removeAt(favPath.size - 1) },
                        onItem = {
                            actionFavorite = it
                            go(BrowseLevel.FAVORITE_ACTIONS)
                        },
                    )
                }
                BrowseLevel.FAVORITE_ACTIONS -> actionFavorite?.let { FavoriteActions(it, onDone = goBack) }
                BrowseLevel.PLAYLISTS -> PlaylistList(onItem = {
                    actionPlaylist = it
                    selectedTrackId = ""
                    go(BrowseLevel.PLAYLIST_TRACKS)
                })
                BrowseLevel.PLAYLIST_TRACKS -> actionPlaylist?.let { PlaylistTracks(it, selectedId = selectedTrackId, onTrack = chooseTrack) }
                BrowseLevel.MUSICIP_MOODS -> MusicIpMoodList(
                    onMood = {
                        chosenMood = it
                        mipMix = null
                        selectedTrackId = ""
                        go(BrowseLevel.MUSICIP_TRACKS)
                    },
                    onSettings = { go(BrowseLevel.MUSICIP_SETTINGS) },
                )
                BrowseLevel.MUSICIP_TRACKS -> {
                    LaunchedEffect(chosenMood) {
                        if (mipMix == null) mipMix = withContext(Dispatchers.IO) { LMS.getMusicIpMix(chosenMood) }
                    }
                    MusicIpTracks(mipMix, selectedId = selectedTrackId, onTrack = chooseTrack)
                }
                BrowseLevel.GENRES -> LoadedList(
                    items = genres,
                    load = { LMS.getGenres() },
                    onLoaded = { genres = it },
                    emptyText = stringResource(R.string.browse_no_genres),
                    listState = genreListState,
                    selectedIndex = selectedGenreIndex,
                    focusKey = "GENRES",
                    onSelect = { index ->
                        selectedGenreIndex = index
                        genres?.getOrNull(index)?.let { chooseGenre(it) }
                    }
                )
                BrowseLevel.GENRE_ARTISTS -> LoadedList(
                    items = genreArtists,
                    load = { LMS.getArtistsByGenre(chosenGenre?.id ?: "") },
                    onLoaded = { genreArtists = it },
                    emptyText = stringResource(R.string.browse_no_artists),
                    listState = genreArtistListState,
                    selectedIndex = selectedGenreArtistIndex,
                    onSelect = { index ->
                        selectedGenreArtistIndex = index
                        genreArtists?.getOrNull(index)?.let { chooseArtist(it) }
                    }
                )
                BrowseLevel.FOLDER -> folderPath.lastOrNull()?.let { folder ->
                    FolderList(
                        folder = folder,
                        onFolder = { folderPath.add(it) },
                        onTrack = chooseTrack,
                    )
                }
                BrowseLevel.YEARS -> LoadedList(
                    items = years,
                    load = { LMS.getYears() },
                    onLoaded = { years = it },
                    emptyText = stringResource(R.string.browse_no_albums),
                    listState = yearListState,
                    selectedIndex = selectedYearIndex,
                    focusKey = "YEARS",
                    onSelect = { index ->
                        selectedYearIndex = index
                        years?.getOrNull(index)?.let {
                            if (it.id != chosenYear?.id) { yearAlbums = null; selectedYearAlbumIndex = 0 }
                            chosenYear = it
                            go(BrowseLevel.YEAR_ALBUMS)
                        }
                    }
                )
                BrowseLevel.YEAR_ALBUMS -> AlbumList(
                    artistId = "",
                    year = chosenYear?.id ?: "",
                    albums = yearAlbums,
                    onLoaded = { yearAlbums = it },
                    listState = yearAlbumListState,
                    selectedIndex = selectedYearAlbumIndex,
                    onSelect = { index ->
                        selectedYearAlbumIndex = index
                        yearAlbums?.getOrNull(index)?.let { chooseAlbum(it) }
                    }
                )
                BrowseLevel.COMPOSERS -> ArtistList(
                    role = "COMPOSER",
                    artists = composers,
                    onLoaded = { composers = it },
                    listState = composerListState,
                    selectedIndex = selectedComposerIndex,
                    focusKey = "COMPOSERS",
                    onSelect = { index ->
                        selectedComposerIndex = index
                        pendingAlbumsRole = "COMPOSER"
                        composers?.getOrNull(index)?.let { chooseArtist(it) }
                    }
                )
                BrowseLevel.ALBUM_ARTISTS -> ArtistList(
                    role = "ALBUMARTIST",
                    artists = albumArtists,
                    onLoaded = { albumArtists = it },
                    listState = albumArtistListState,
                    selectedIndex = selectedAlbumArtistIndex,
                    focusKey = "ALBUM_ARTISTS",
                    onSelect = { index ->
                        selectedAlbumArtistIndex = index
                        albumArtists?.getOrNull(index)?.let { chooseArtist(it) }
                    }
                )
                BrowseLevel.ARTISTS -> ArtistList(
                    artists = artists,
                    onLoaded = { artists = it },
                    listState = artistListState,
                    selectedIndex = selectedArtistIndex,
                    focusKey = "ARTISTS",
                    onSelect = { index ->
                        selectedArtistIndex = index
                        artists?.getOrNull(index)?.let { chooseArtist(it) }
                    }
                )
                BrowseLevel.ALBUMS -> AlbumList(
                    artistId = chosenArtist?.id ?: "",
                    role = albumsRole,
                    albums = albums,
                    onLoaded = { albums = it },
                    listState = albumListState,
                    selectedIndex = selectedAlbumIndex,
                    onSelect = { index ->
                        selectedAlbumIndex = index
                        albums?.getOrNull(index)?.let { chooseAlbum(it) }
                    }
                )
                BrowseLevel.TRACKS -> TrackList(
                    albumId = chosenAlbum?.id ?: "",
                    listState = trackListState,
                    selectedTrackId = selectedTrackId,
                    onTrack = chooseTrack,
                )
                BrowseLevel.TRACK_ACTIONS -> actionTrack?.let { TrackActions(it, onDone = goBack) }
                BrowseLevel.SEARCH -> SearchPanel(
                    query = query,
                    onQueryChange = {
                        query = it
                        NavState.lastTyped = System.currentTimeMillis()
                    },
                    results = results,
                    searching = searching,
                    lastTerm = lastTerm,
                    onSearch = {
                        val term = query.trim()
                        if (term.isNotEmpty()) {
                            // A newer search replaces one still running
                            searchJob?.cancel()
                            lastTerm = term
                            searchJob = scope.launch {
                                searching = true
                                results = withContext(Dispatchers.IO) { LMS.search(term) }
                                searching = false
                            }
                        }
                    },
                    onArtist = chooseArtist,
                    onAlbum = chooseAlbum,
                    onTrack = chooseTrack,
                )
            }
        }
    }
}

@Composable
private fun RootList(
    onMyMusic: () -> Unit,
    onRadio: () -> Unit,
    onApps: () -> Unit,
    onMix: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Column {
        BrowseRow(stringResource(R.string.browse_my_music), onClick = onMyMusic, modifier = Modifier.focusRequester(firstFocus), icon = MyMusicIcon)
        BrowseRow(stringResource(R.string.browse_radio), onClick = onRadio, icon = RadioMenuIcon)
        BrowseRow(stringResource(R.string.browse_apps), onClick = onApps, icon = Icons.Outlined.Apps)
        BrowseRow(stringResource(R.string.browse_mix), onClick = onMix, icon = MixMenuIcon)
    }
}

// Everything about the own music, behind one item of the top level.
@Composable
private fun MyMusicList(
    onSearch: () -> Unit,
    onArtists: () -> Unit,
    onGenres: () -> Unit,
    onFolders: () -> Unit,
    onFavorites: () -> Unit,
    onPlaylists: () -> Unit,
    onNewMusic: () -> Unit,
    onReleases: () -> Unit,
    onAlbumArtists: () -> Unit,
    onComposers: () -> Unit,
    onYears: () -> Unit,
) {
    // Items switched off in Settings (My Music items) are left out; Search always stays.
    val hidden = AppConfig.current.hiddenMyMusic
    val all = mapOf(
        "albumartists" to Triple(stringResource(R.string.browse_album_artists), onAlbumArtists, ArtistsIcon),
        "artists" to Triple(stringResource(R.string.browse_artists), onArtists, ArtistsIcon),
        "composers" to Triple(stringResource(R.string.browse_composers), onComposers, ArtistsIcon),
        "genres" to Triple(stringResource(R.string.browse_genres), onGenres, GenresIcon),
        "years" to Triple(stringResource(R.string.browse_years), onYears, Icons.Outlined.MusicNote),
        "releases" to Triple(stringResource(R.string.browse_releases), onReleases, Icons.Outlined.MusicNote),
        "newmusic" to Triple(stringResource(R.string.browse_new_music), onNewMusic, Icons.Outlined.MusicNote),
        "folders" to Triple(stringResource(R.string.browse_music_folder), onFolders, MusicFolderIcon),
        "favorites" to Triple(stringResource(R.string.browse_favorites), onFavorites, FavoritesIcon),
        "playlists" to Triple(stringResource(R.string.browse_playlists), onPlaylists, PlaylistsIcon),
    )
    // Chosen order (items missing from it follow in the standard order); Search always stays on top
    val order = AppConfig.current.myMusicOrder
    val rows = listOf(Triple(stringResource(R.string.browse_search), onSearch, SearchIcon)) +
        (order.filter { it in all } + all.keys.filter { it !in order }).distinct()
            .filter { it !in hidden }.mapNotNull { all[it] }
    // Back from a level lands on the row it was opened from
    val startIndex = remember { (NavState.listFocus["MY_MUSIC"] ?: 0).coerceIn(0, rows.size - 1) }
    val startFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { startFocus.requestFocus() }

    // Scrolls (with the fade + arrow hint) when more rows are on than fit.
    HintLazyColumn(state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)) {
        itemsIndexed(rows) { i, (label, onClick, icon) ->
            BrowseRow(
                label, onClick = onClick, icon = icon,
                modifier = Modifier.trackFocus("MY_MUSIC", i).then(if (i == startIndex) Modifier.focusRequester(startFocus) else Modifier),
            )
        }
    }
}

// A list that shows a fade with an arrow at the bottom while more items follow below.
@Composable
internal fun HintLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    Box(modifier) {
        LazyColumn(state = state, content = content)
        if (state.canScrollForward) {
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
            }
        }
    }
}

// The Radio menu: first the radio plugins of the server (Radio Now Playing on
// top), then the levels inside the chosen plugin.
@Composable
private fun RadioList(
    path: List<BrowseItem>,
    cmd: String,
    onRadio: (String, String) -> Unit,
    onSearch: (String, BrowseItem) -> Unit,
    onFolder: (BrowseItem) -> Unit,
    onItem: (BrowseItem) -> Unit,
) {
    val atRoot = path.size <= 1
    val apps = path.firstOrNull()?.id == "apps"
    val folderId = path.lastOrNull()?.id ?: ""
    var radios by remember { mutableStateOf<List<RadioEntry>?>(null) }
    var entries by remember(cmd, path.size, folderId) { mutableStateOf<List<FavoriteEntry>?>(null) }
    LaunchedEffect(atRoot, cmd, path.size, folderId) {
        if (atRoot) {
            if (radios == null) radios = withContext(Dispatchers.IO) { if (apps) LMS.getApps() else LMS.getRadios() }
        } else {
            entries = withContext(Dispatchers.IO) { LMS.getRadioItems(cmd, folderId) }
        }
    }

    val shownCount = if (atRoot) radios?.size else entries?.size
    if (shownCount == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    if (shownCount == 0) {
        Text(stringResource(R.string.browse_no_radios), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    val imageLoader = rememberLmsImageLoader()
    // Each level remembers the row last focused
    val memKey = (if (apps) "APPS" else "RADIO") + ":" + path.joinToString("/") { it.id + ":" + it.name }
    val startIndex = remember(memKey, shownCount) { (NavState.listFocus[memKey] ?: 0).coerceIn(0, (shownCount - 1).coerceAtLeast(0)) }
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    // An opened playable item (album, playlist, stream) has play icons pinned above its list
    val folderActions = !atRoot && path.lastOrNull()?.openable == true
    // Favorite id and name while the rename field (after saving the opened item) is shown
    var rename by remember(folderId) { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(atRoot, path.size, folderId, shownCount) {
        if (!folderActions && startIndex > 0) listState.scrollToItem(startIndex)
        try { (if (folderActions) rowFocus else firstFocus).requestFocus() } catch (_: Exception) { }
    }

    Column {
    if (folderActions) {
        IconActionRow(
            playActions(folderId, cmd, {}, onSaved = { id, title -> rename = id to title }),
            rowFocus, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        rename?.let { (id, name) -> RenameFavoriteField(id, name) { rename = null } }
    }
    HintLazyColumn(state = listState) {
        if (atRoot) {
            val list = radios ?: emptyList()
            items(list.size) { index ->
                val radio = list[index]
                val open = {
                    if (radio.isSearch) onSearch(radio.cmd, BrowseItem("", radio.name))
                    else onRadio(radio.cmd, radio.name)
                }
                if (radio.icon.isNotBlank()) {
                    // A plugin icon "…_svg.png" has a one-colour svg beside it: shown in white, like Material does
                    // Radio Paradise ships no "_svg.png"; Material has its own svg for it
                    val svgPath = when {
                        radio.icon.endsWith("_svg.png") -> radio.icon.removeSuffix("_svg.png") + ".svg"
                        radio.name.equals("Radio Paradise", ignoreCase = true) -> "material/svg/radioparadise"
                        else -> materialAppSvg[radio.cmd]
                    }
                    ThumbRow(
                        text = radio.name,
                        imageUrl = radioImageUrl(svgPath ?: radio.icon),
                        placeholder = Icons.Outlined.Apps,
                        round = false,
                        imageLoader = imageLoader,
                        onClick = { open() },
                        modifier = (if (index == startIndex) Modifier.focusRequester(firstFocus) else Modifier).trackFocus(memKey, index),
                        tintWhite = svgPath != null,
                        fallbackUrl = radioImageUrl(radio.icon),
                    )
                } else {
                    BrowseRow(
                        radio.name,
                        icon = radioEntryIcon(radio.cmd),
                        modifier = (if (index == startIndex) Modifier.focusRequester(firstFocus) else Modifier).trackFocus(memKey, index),
                        onClick = { open() }
                    )
                }
            }
        } else {
            val list = entries ?: emptyList()
            items(list.size) { index ->
                RadioEntryRow(
                    entry = list[index],
                    imageLoader = imageLoader,
                    modifier = (if (index == startIndex) Modifier.focusRequester(firstFocus) else Modifier).trackFocus(memKey, index),
                    onSearch = { onSearch(cmd, BrowseItem(it.id, it.name)) },
                    onFolder = onFolder,
                    onItem = onItem,
                )
            }
        }
    }
    }
}

// A row of a radio level: a search entry, a station with its logo, or just the name.
@Composable
private fun RadioEntryRow(
    entry: FavoriteEntry,
    imageLoader: ImageLoader,
    modifier: Modifier,
    onSearch: (FavoriteEntry) -> Unit,
    onFolder: (BrowseItem) -> Unit,
    onItem: (BrowseItem) -> Unit,
) {
    // An item with items below it opens its list (like Lyrion); if it is also playable, the list gets play icons on top
    val open = {
        if (entry.isFolder) onFolder(BrowseItem(entry.id, entry.name, openable = entry.isAudio))
        else onItem(BrowseItem(entry.id, entry.name))
    }
    when {
        entry.isSearch -> BrowseRow(entry.name, icon = SearchIcon, modifier = modifier, onClick = { onSearch(entry) })
        entry.icon.isNotBlank() -> ThumbRow(
            text = entry.name,
            imageUrl = radioImageUrl(entry.icon),
            placeholder = if (entry.isFolder) Icons.Filled.Folder else Icons.Outlined.MusicNote,
            round = false,
            imageLoader = imageLoader,
            onClick = { open() },
            modifier = modifier
        )
        else -> BrowseRow(entry.name, modifier = modifier, onClick = { open() })
    }
}

// Search inside a radio plugin: type a term, the results follow like in the library search.
@Composable
private fun RadioSearch(
    cmd: String,
    itemId: String,
    onFolder: (BrowseItem) -> Unit,
    onItem: (BrowseItem) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var lastTerm by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FavoriteEntry>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val imageLoader = rememberLmsImageLoader()

    val run: () -> Unit = {
        val term = query.trim()
        if (term.isNotEmpty() && term != lastTerm) {
            lastTerm = term
            scope.launch {
                searching = true
                val found = withContext(Dispatchers.IO) { LMS.getRadioItems(cmd, itemId, search = term) }
                // A newer search replaces this one
                if (lastTerm == term) results = found
                searching = false
            }
        }
    }

    LaunchedEffect(Unit) { try { fieldFocus.requestFocus() } catch (_: Exception) { } }
    // Live search shortly after typing stops
    LaunchedEffect(query) {
        if (query.trim().length >= 2 && query.trim() != lastTerm) {
            kotlinx.coroutines.delay(800)
            run()
        }
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.browse_type_to_search), color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = {
                    query = it
                    NavState.lastTyped = System.currentTimeMillis()
                },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    run()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                            keyboard?.show()
                            true
                        } else false
                    }
            )
        }
        val list = results
        if (searching) {
            Text(stringResource(R.string.browse_searching), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        } else if (list != null) {
            if (list.isEmpty()) {
                Text(stringResource(R.string.browse_no_radios), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
            } else {
                HintLazyColumn {
                    items(list.size) { index ->
                        RadioEntryRow(
                            entry = list[index],
                            imageLoader = imageLoader,
                            modifier = Modifier,
                            onSearch = { },
                            onFolder = onFolder,
                            onItem = onItem,
                        )
                    }
                }
            }
        }
    }
}

// One level of the Lyrion favorites: favorite folders and favorites, plus
// play / add actions for a whole folder (not for the top level).
@Composable
private fun FavoritesList(
    folder: BrowseItem,
    onFolder: (BrowseItem) -> Unit,
    onAlbum: (BrowseItem) -> Unit,
    onRemoved: () -> Unit,
    onItem: (BrowseItem) -> Unit,
) {
    var entries by remember(folder.id) { mutableStateOf<List<FavoriteEntry>?>(null) }
    LaunchedEffect(folder.id) {
        entries = withContext(Dispatchers.IO) { LMS.getFavorites(folder.id) }
    }

    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val runFolder: (String, String) -> Unit = { cmd, message ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { LMS.favoritesControl(cmd, folder.id) }
            Toast.makeText(context, if (ok) message else context.getString(R.string.common_no_player), Toast.LENGTH_SHORT).show()
            // Playing now: back to Now Playing (Browse reopens where it was left)
            if (ok && cmd == "load") NavState.showBrowse = false
        }
    }

    val list = entries
    if (list == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    if (list.isEmpty()) {
        Text(stringResource(R.string.browse_no_favorites), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    val imageLoader = rememberLmsImageLoader()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(list) {
        try { firstFocus.requestFocus() } catch (_: Exception) { }
    }

    val hasActions = folder.id.isNotBlank()
    // At the top level the list remembers the last focused entry.
    val memKey = if (!hasActions) "FAVORITES" else ""
    val startIndex = remember(folder.id) { if (memKey.isEmpty()) 0 else (NavState.listFocus[memKey] ?: 0).coerceIn(0, (list.size - 1).coerceAtLeast(0)) }
    val listState = rememberLazyListState()
    LaunchedEffect(list) { if (startIndex > 0) listState.scrollToItem(startIndex) }
    // In a folder the icons for the whole folder stay in place above the list
    Column {
    if (hasActions) {
        // A playable favorite with items below it (album, playlist) also gets rename and remove; a real folder only play and add
        var rename by remember(folder.id) { mutableStateOf<Pair<String, String>?>(null) }
        val actions = if (folder.openable) buildList<Triple<ImageVector, String, () -> Unit>> {
            addAll(playActions(folder.id, "favorites", {}))
            add(Triple(Icons.Outlined.Edit, stringResource(R.string.action_rename), { rename = folder.id to folder.name }))
            add(Triple(ActionDeleteIcon, stringResource(R.string.action_delete_favorite), {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { LMS.removeFavorite(folder.id) }
                    Toast.makeText(context, context.getString(if (ok) R.string.fav_removed else R.string.fav_remove_failed), Toast.LENGTH_SHORT).show()
                    if (ok) onRemoved()
                }
            }))
        } else listOf(
            Triple(ActionPlayIcon, stringResource(R.string.action_play_now), { runFolder("load", context.getString(R.string.fav_playing)) }),
            Triple(ActionAddIcon, stringResource(R.string.action_add_end), { runFolder("add", context.getString(R.string.fav_added)) }),
        )
        IconActionRow(
            actions,
            firstFocus,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            wrapLeft = false,
        )
        rename?.let { (id, name) ->
            RenameFavoriteField(id, name) { renamed ->
                rename = null
                if (renamed) onRemoved()
            }
        }
    }
    HintLazyColumn(state = listState) {
        items(list.size) { index ->
            val entry = list[index]
            ThumbRow(
                text = entry.name,
                imageUrl = radioImageUrl(entry.icon),
                placeholder = if (entry.isFolder) Icons.Filled.Folder else Icons.Filled.Star,
                round = false,
                imageLoader = imageLoader,
                onClick = {
                    val openEntry = {
                        if (entry.isFolder) onFolder(BrowseItem(entry.id, entry.name, openable = entry.isAudio))
                        else onItem(BrowseItem(entry.id, entry.name))
                    }
                    if (entry.url.startsWith("db:album.title=")) {
                        // A favorite album of the library opens as the album itself
                        scope.launch {
                            val albumId = withContext(Dispatchers.IO) { LMS.albumIdFromFavoriteUrl(entry.url) }
                            if (albumId != null) onAlbum(BrowseItem(albumId, entry.name)) else openEntry()
                        }
                    } else openEntry()
                },
                modifier = (if (!hasActions && index == startIndex) Modifier.focusRequester(firstFocus) else Modifier).trackFocus(memKey, index)
            )
        }
    }
    }
}

// The heart that adds something to the favorites (plus) or removes it again (minus). source gives the
// address and name; onSaved: it was just added (favorites id and name), e.g. to offer a new name.
@Composable
private fun favoriteHeartAction(
    key: Any,
    source: suspend () -> LMS.FavTarget?,
    onSaved: (String, String) -> Unit = { _, _ -> },
): Triple<ImageVector, String, () -> Unit> {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    // The id in the favorites when it is one already (heart with minus), else null (heart with plus)
    var favoriteId by remember(key) { mutableStateOf<String?>(null) }
    LaunchedEffect(key) {
        favoriteId = withContext(Dispatchers.IO) { source()?.let { LMS.favoriteIdOf(it) } }
    }
    val toggle: () -> Unit = {
        scope.launch {
            val current = favoriteId
            if (current != null) {
                val ok = withContext(Dispatchers.IO) { LMS.removeFavorite(current) }
                if (ok) favoriteId = null
                Toast.makeText(context, context.getString(if (ok) R.string.fav_removed else R.string.fav_remove_failed), Toast.LENGTH_SHORT).show()
            } else {
                val target = withContext(Dispatchers.IO) { source() }
                val saved = target != null && withContext(Dispatchers.IO) { LMS.addFavorite(target) }
                if (target == null || !saved) {
                    Toast.makeText(context, context.getString(R.string.fav_save_failed), Toast.LENGTH_SHORT).show()
                } else {
                    val newId = withContext(Dispatchers.IO) { LMS.favoriteIdOf(target) }
                    favoriteId = newId
                    Toast.makeText(context, context.getString(R.string.fav_saved), Toast.LENGTH_SHORT).show()
                    if (newId != null) onSaved(newId, target.title)
                }
            }
        }
    }
    val isFavorite = favoriteId != null
    return Triple(
        if (isFavorite) ActionFavoriteRemoveIcon else ActionFavoriteAddIcon,
        stringResource(if (isFavorite) R.string.action_unfavorite else R.string.action_favorite),
        toggle
    )
}

// The icons play now / play next / add to the end for one item of a plugin or of the favorites, plus the
// heart (add to / remove from favorites) for plugin items. onSaved: a favorite was just added (its id and name).
@Composable
private fun playActions(
    id: String,
    plugin: String,
    onDone: () -> Unit,
    onSaved: (String, String) -> Unit = { _, _ -> },
): List<Triple<ImageVector, String, () -> Unit>> {
    val context = LocalContext.current.applicationContext
    // Not rememberCoroutineScope: onDone() closes this menu, which would cancel the request half way
    val run: (String, String) -> Unit = { cmd, message ->
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            val ok = withContext(Dispatchers.IO) { LMS.favoritesControl(cmd, id, plugin) }
            // A failed request is no player, or a server that did not answer in time (the stream may still start)
            val text = when {
                ok -> message
                LMS.playerMac == "00:00:00:00:00:00" -> context.getString(R.string.common_no_player)
                else -> context.getString(R.string.common_no_answer)
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            // Playing now: back to Now Playing (Browse reopens where it was left)
            if (ok && cmd == "load") NavState.showBrowse = false
        }
        onDone()
    }
    // Not for the favorites themselves: they are saved already
    val heart = if (plugin != "favorites") favoriteHeartAction(id to plugin, { LMS.pluginFavTarget(plugin, id) }, onSaved) else null
    return buildList {
        add(Triple(ActionPlayIcon, stringResource(R.string.action_play_now), { run("load", context.getString(R.string.fav_item_playing)) }))
        add(Triple(ActionNextIcon, stringResource(R.string.action_play_next), { run("insert", context.getString(R.string.fav_item_next)) }))
        add(Triple(ActionAddIcon, stringResource(R.string.action_add_end), { run("add", context.getString(R.string.fav_item_added)) }))
        if (heart != null) add(heart)
    }
}

// Text field with the current name of a favorite; Done renames it. onFinished(true) when it was renamed.
@Composable
private fun RenameFavoriteField(favoriteId: String, current: String, onFinished: (Boolean) -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldFocus = remember { FocusRequester() }
    var name by remember(favoriteId, current) { mutableStateOf(current) }
    LaunchedEffect(favoriteId) { try { fieldFocus.requestFocus() } catch (_: Exception) { } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        BasicTextField(
            value = name,
            onValueChange = { name = it; NavState.lastTyped = System.currentTimeMillis() },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboard?.hide()
                val title = name.trim()
                if (title.isEmpty() || title == current) onFinished(false)
                else scope.launch {
                    val ok = withContext(Dispatchers.IO) { LMS.renameFavorite(favoriteId, title) }
                    Toast.makeText(context, context.getString(if (ok) R.string.fav_renamed else R.string.fav_rename_failed), Toast.LENGTH_SHORT).show()
                    onFinished(ok)
                }
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fieldFocus)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                        keyboard?.show()
                        true
                    } else false
                }
        )
    }
}

// Action menu for one favorite or plugin item: play now / next / add to the end, heart, rename (favorites).
@Composable
private fun FavoriteActions(favorite: BrowseItem, onDone: () -> Unit, plugin: String = "favorites", onOpen: (() -> Unit)? = null) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(favorite) { firstFocus.requestFocus() }
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    // Favorite id and current name while the rename field is open
    var rename by remember(favorite) { mutableStateOf<Pair<String, String>?>(null) }

    // Icons side by side; the last one (if any) looks at what is inside (the tracks of an album)
    val actions = buildList<Triple<ImageVector, String, () -> Unit>> {
        addAll(playActions(favorite.id, plugin, onDone, onSaved = { id, title -> rename = id to title }))
        if (plugin == "favorites") {
            add(Triple(Icons.Outlined.Edit, stringResource(R.string.action_rename), { rename = favorite.id to favorite.name }))
            add(Triple(ActionDeleteIcon, stringResource(R.string.action_delete_favorite), {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { LMS.removeFavorite(favorite.id) }
                    Toast.makeText(context, context.getString(if (ok) R.string.fav_removed else R.string.fav_remove_failed), Toast.LENGTH_SHORT).show()
                    if (ok) onDone()
                }
            }))
        }
        if (onOpen != null) add(Triple(ActionBrowseIcon, stringResource(R.string.action_browse), onOpen))
    }
    Column {
        IconActionRow(actions, firstFocus, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        rename?.let { (id, name) ->
            RenameFavoriteField(id, name) { renamed ->
                rename = null
                if (renamed && plugin == "favorites") onDone()
            }
        }
    }
}

// The saved playlists of the server.
@Composable
private fun PlaylistList(onItem: (BrowseItem) -> Unit) {
    var entries by remember { mutableStateOf<List<BrowseItem>?>(null) }
    LaunchedEffect(Unit) { entries = withContext(Dispatchers.IO) { LMS.getPlaylists() } }

    val list = entries
    if (list == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    if (list.isEmpty()) {
        Text(stringResource(R.string.browse_no_playlists), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    val firstFocus = remember { FocusRequester() }
    val startIndex = remember { (NavState.listFocus["PLAYLISTS"] ?: 0).coerceIn(0, list.size - 1) }
    val listState = rememberLazyListState()
    LaunchedEffect(list) {
        if (startIndex > 0) listState.scrollToItem(startIndex)
        try { firstFocus.requestFocus() } catch (_: Exception) { }
    }
    HintLazyColumn(state = listState) {
        items(list.size) { index ->
            BrowseRow(list[index].name, onClick = { onItem(list[index]) },
                modifier = (if (index == startIndex) Modifier.focusRequester(firstFocus) else Modifier).trackFocus("PLAYLISTS", index))
        }
    }
}

// The MusicIP moods; OK on one makes a mix for it. The first row opens the MusicIP settings.
@Composable
private fun MusicIpMoodList(onMood: (String) -> Unit, onSettings: () -> Unit) {
    var moods by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(Unit) { moods = withContext(Dispatchers.IO) { LMS.getMusicIpMoods() } }

    val list = moods
    if (list == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    if (list.isEmpty()) {
        Text(stringResource(R.string.musicip_no_moods), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    val firstFocus = remember { FocusRequester() }
    val startIndex = remember { (NavState.listFocus["MUSICIP_MOODS"] ?: 0).coerceIn(0, list.size - 1) }
    val listState = rememberLazyListState()
    LaunchedEffect(list) {
        // +1: the settings row comes before the moods
        if (startIndex > 0) listState.scrollToItem(startIndex + 1)
        try { firstFocus.requestFocus() } catch (_: Exception) { }
    }
    HintLazyColumn(state = listState) {
        item { BrowseRow(stringResource(R.string.musicip_settings), onClick = onSettings, icon = SettingsMenuIcon) }
        items(list.size) { index ->
            BrowseRow(list[index], onClick = { onMood(list[index]) },
                modifier = (if (index == startIndex) Modifier.focusRequester(firstFocus) else Modifier).trackFocus("MUSICIP_MOODS", index))
        }
    }
}

// The tracks of one playlist; OK on a track opens its action menu.
@Composable
private fun PlaylistTracks(playlist: BrowseItem, selectedId: String, onTrack: (BrowseTrack) -> Unit) {
    var entries by remember(playlist.id) { mutableStateOf<List<Pair<BrowseTrack, String>>?>(null) }
    LaunchedEffect(playlist.id) { entries = withContext(Dispatchers.IO) { LMS.getPlaylistTracks(playlist.id) } }
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    // The row of icons above the tracks acts on the whole playlist
    val run: (String, Int) -> Unit = { cmd, message ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { LMS.playlistControl(cmd, playlistId = playlist.id) }
            Toast.makeText(context, if (ok) context.getString(message) else context.getString(R.string.common_no_player), Toast.LENGTH_SHORT).show()
            // Playing now: back to Now Playing (Browse reopens where it was left)
            if (ok && cmd == "load") NavState.showBrowse = false
        }
    }
    TrackDetailList(
        entries, onTrack, selectedId = selectedId,
        headerActions = listOf(
            Triple(ActionPlayIcon, stringResource(R.string.action_play_now), { run("load", R.string.playlist_playing) }),
            Triple(ActionNextIcon, stringResource(R.string.action_play_next), { run("insert", R.string.playlist_next) }),
            Triple(ActionAddIcon, stringResource(R.string.action_add_end), { run("add", R.string.playlist_added) }),
        ),
    )
}

// The tracks of a MusicIP mix; the row of icons above them acts on the whole mix.
@Composable
private fun MusicIpTracks(mix: List<Pair<BrowseTrack, String>>?, selectedId: String, onTrack: (BrowseTrack) -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val run: (String, Int) -> Unit = { cmd, message ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { LMS.playTrackIds(cmd, mix.orEmpty().map { it.first.id }) }
            Toast.makeText(context, if (ok) context.getString(message) else context.getString(R.string.common_no_player), Toast.LENGTH_SHORT).show()
            // Playing now: back to Now Playing (Browse reopens where it was left)
            if (ok && cmd == "load") NavState.showBrowse = false
        }
    }
    TrackDetailList(
        mix, onTrack, selectedId = selectedId,
        headerActions = listOf(
            Triple(ActionPlayIcon, stringResource(R.string.action_play_now), { run("load", R.string.musicip_playing) }),
            Triple(ActionNextIcon, stringResource(R.string.action_play_next), { run("insert", R.string.musicip_next) }),
            Triple(ActionAddIcon, stringResource(R.string.action_add_end), { run("add", R.string.musicip_added) }),
        ),
    )
}

// MusicIP's own settings of the active player (not the excluded genres: those stay in Material).
@Composable
private fun MusicIpSettingsList() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    var settings by remember { mutableStateOf<LMS.MusicIpSettings?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val s = withContext(Dispatchers.IO) { LMS.getMusicIpSettings() }
        if (s == null) unavailable = true else settings = s
    }
    if (unavailable) {
        Text(stringResource(R.string.musicip_settings_unavailable), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    val s = settings
    if (s == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { firstFocus.requestFocus() } catch (_: Exception) { } }
    val unitNames = listOf(
        stringResource(R.string.musicip_unit_tracks),
        stringResource(R.string.musicip_unit_min),
        stringResource(R.string.musicip_unit_mb),
    )
    val noneLabel = stringResource(R.string.musicip_none)

    // Mix filter: the list comes from MusicIP itself, so it is fetched when the row is opened
    var filter by remember { mutableStateOf(if (s.filter == "0") "" else s.filter) }
    var filters by remember { mutableStateOf<List<String>?>(null) }
    var picking by remember { mutableStateOf(false) }
    val filterRowFocus = remember { FocusRequester() }
    var wasPicking by remember { mutableStateOf(false) }
    // Excluded genres: a list to tick (ticked = left out of the mix); each change is sent after a short pause
    var excluded by remember { mutableStateOf<List<String>?>(null) }
    var allGenres by remember { mutableStateOf<List<String>?>(null) }
    var pickingGenres by remember { mutableStateOf(false) }
    val genreRowFocus = remember { FocusRequester() }
    var wasPickingGenres by remember { mutableStateOf(false) }
    var genreSendJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(Unit) { excluded = withContext(Dispatchers.IO) { LMS.getMusicIpExcludedGenres() } ?: emptyList() }
    BackHandler(enabled = picking || pickingGenres) { picking = false; pickingGenres = false }
    LaunchedEffect(picking) {
        if (!picking && wasPicking) { try { filterRowFocus.requestFocus() } catch (_: Exception) { } }
        wasPicking = picking
    }
    LaunchedEffect(pickingGenres) {
        if (!pickingGenres && wasPickingGenres) { try { genreRowFocus.requestFocus() } catch (_: Exception) { } }
        wasPickingGenres = pickingGenres
    }
    if (pickingGenres) {
        val names = allGenres
        val ticked = excluded ?: emptyList()
        if (names == null) {
            Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
            return
        }
        val pickFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { try { pickFocus.requestFocus() } catch (_: Exception) { } }
        val change = { next: List<String> ->
            excluded = next
            genreSendJob?.cancel()
            genreSendJob = scope.launch {
                kotlinx.coroutines.delay(500)
                withContext(Dispatchers.IO) { LMS.setMusicIpExcludedGenres(next) }
            }
        }
        HintLazyColumn {
            item {
                BrowseRow(stringResource(R.string.musicip_genres_clear), modifier = Modifier.focusRequester(pickFocus), onClick = { change(emptyList()) })
            }
            item {
                BrowseRow(stringResource(R.string.musicip_genres_all), onClick = { change(names) })
            }
            items(names.size) { index ->
                val name = names[index]
                val on = ticked.any { it.equals(name, ignoreCase = true) }
                BrowseRow(
                    text = name,
                    trailing = if (on) "☑" else "☐",
                    onClick = { change(if (on) ticked.filterNot { it.equals(name, ignoreCase = true) } else ticked + name) }
                )
            }
        }
        return
    }
    if (picking) {
        val list = filters
        if (list == null) {
            Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
            return
        }
        val pickFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { try { pickFocus.requestFocus() } catch (_: Exception) { } }
        val choices = listOf("") + list
        HintLazyColumn {
            items(choices.size) { index ->
                val name = choices[index]
                BrowseRow(
                    text = (if (name == filter) "●  " else "○  ") + (if (name.isEmpty()) noneLabel else name),
                    modifier = if (index == choices.indexOf(filter).coerceAtLeast(0)) Modifier.focusRequester(pickFocus) else Modifier,
                    onClick = {
                        filter = name
                        picking = false
                        // "0" is how the plugin stores no filter
                        scope.launch(Dispatchers.IO) { LMS.setMusicIpPref("mix_filter", if (name.isEmpty()) "0" else name) }
                    }
                )
            }
        }
        return
    }

    HintLazyColumn {
        item { MusicIpSliderRow(stringResource(R.string.musicip_set_size), s.size.coerceIn(1, 200), 1, 200, "mix_size", Modifier.focusRequester(firstFocus)) }
        item { MusicIpCycleRow(stringResource(R.string.musicip_set_unit), unitNames, s.type, "mix_type") }
        item { MusicIpSliderRow(stringResource(R.string.musicip_set_style), s.style.coerceIn(0, 200), 0, 200, "mix_style") }
        item { MusicIpSliderRow(stringResource(R.string.musicip_set_variety), s.variety.coerceIn(0, 9), 0, 9, "mix_variety") }
        item { MusicIpCycleRow(stringResource(R.string.musicip_set_genre), listOf(stringResource(R.string.musicip_no), stringResource(R.string.musicip_yes)), s.genre, "mix_genre") }
        item { MusicIpSliderRow(stringResource(R.string.musicip_set_reject), s.rejectSize.coerceIn(0, 200), 0, 200, "reject_size") }
        item { MusicIpCycleRow(stringResource(R.string.musicip_set_reject_unit), unitNames, s.rejectType, "reject_type") }
        item {
            BrowseRow(
                text = stringResource(R.string.musicip_set_genres),
                trailing = excluded?.size?.takeIf { it > 0 }?.toString() ?: noneLabel,
                modifier = Modifier.focusRequester(genreRowFocus),
                onClick = {
                    pickingGenres = true
                    scope.launch {
                        if (allGenres == null) allGenres = withContext(Dispatchers.IO) { LMS.getGenres() }.map { it.name }
                    }
                }
            )
        }
        item {
            BrowseRow(
                text = stringResource(R.string.musicip_set_filter),
                trailing = filter.ifEmpty { noneLabel },
                modifier = Modifier.focusRequester(filterRowFocus),
                onClick = {
                    scope.launch {
                        val found = withContext(Dispatchers.IO) { LMS.getMusicIpFilters() }
                        if (found == null) {
                            Toast.makeText(context, context.getString(R.string.musicip_filters_failed, LMS.musicIpFiltersAddress), Toast.LENGTH_LONG).show()
                        } else {
                            filters = found
                            picking = true
                        }
                    }
                }
            )
        }
    }
}

// A MusicIP setting with a range: Left/Right change it (holding goes faster). The value changes at once,
// the command follows when the key has been left alone for a moment, so a held key doesn't flood the server.
@Composable
private fun MusicIpSliderRow(label: String, initial: Int, min: Int, max: Int, pref: String, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var value by remember { mutableIntStateOf(initial) }
    var sendJob by remember { mutableStateOf<Job?>(null) }
    val move = { delta: Int ->
        val next = (value + delta).coerceIn(min, max)
        if (next != value) {
            value = next
            sendJob?.cancel()
            sendJob = scope.launch {
                kotlinx.coroutines.delay(400)
                withContext(Dispatchers.IO) { LMS.setMusicIpPref(pref, next) }
            }
        }
    }
    SliderRow(
        label = label, value = value, min = min, max = max, valueText = value.toString(),
        modifier = modifier.onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false
            else {
                // Holding the key for a while goes faster (relative to the size of the range)
                val fast = if (max - min > 20) 5 else 1
                val step = if (event.nativeKeyEvent.repeatCount >= 15) fast else 1
                when (event.key) {
                    Key.DirectionRight -> { move(step); true }
                    Key.DirectionLeft -> { move(-step); true }
                    else -> false
                }
            }
        }
    )
}

// A MusicIP setting with a few choices: OK goes to the next one.
@Composable
private fun MusicIpCycleRow(label: String, names: List<String>, initial: Int, pref: String) {
    val scope = rememberCoroutineScope()
    var index by remember { mutableIntStateOf(initial.coerceIn(0, names.size - 1)) }
    BrowseRow(
        text = label,
        trailing = names[index],
        onClick = {
            val next = (index + 1) % names.size
            index = next
            scope.launch(Dispatchers.IO) { LMS.setMusicIpPref(pref, next) }
        }
    )
}

// A list of tracks with cover, title and artist · album.
@Composable
private fun TrackDetailList(
    entries: List<Pair<BrowseTrack, String>>?,
    onTrack: (BrowseTrack) -> Unit,
    // Optional row of icons above the tracks (play the whole list)
    headerActions: List<Triple<ImageVector, String, () -> Unit>>? = null,
    // The track that was last opened: the list comes back on it
    selectedId: String = "",
) {
    val list = entries
    if (list == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    if (list.isEmpty()) {
        Text(stringResource(R.string.browse_no_tracks), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    val firstFocus = remember { FocusRequester() }
    val imageLoader = rememberLmsImageLoader()
    val context = LocalContext.current
    val startIndex = remember(list, selectedId) { list.indexOfFirst { it.first.id == selectedId } }
    val startFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(list) {
        if (startIndex >= 0) {
            listState.scrollToItem(startIndex)
            try { startFocus.requestFocus() } catch (_: Exception) { }
        } else {
            try { firstFocus.requestFocus() } catch (_: Exception) { }
        }
    }
    // The icons stay in place above the list while it scrolls
    Column {
    if (headerActions != null) {
        // Left on the first icon is one step back, like on every list
        IconActionRow(headerActions, firstFocus, wrapLeft = false)
    }
    HintLazyColumn(state = listState) {
        items(list.size) { index ->
            val (track, detail) = list[index]
            val interactionSource = remember { MutableInteractionSource() }
            val focused by interactionSource.collectIsFocusedAsState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = when {
                    index == startIndex -> Modifier.focusRequester(startFocus)
                    index == 0 && headerActions == null -> Modifier.focusRequester(firstFocus)
                    else -> Modifier
                }
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (focused) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable(interactionSource = interactionSource, indication = null, onClick = { onTrack(track.copy(subtitle = detail)) })
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                    // A full address (stream cover) is used as it is, a library cover id gets the server address
                    val url = if (track.coverId.startsWith("http")) track.coverId else coverUrl(track.coverId)
                    if (url != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                            imageLoader = imageLoader,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(track.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (detail.isNotBlank()) {
                        Text(detail, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    }
}

// Auto Mix control: which plugin keeps the queue filled (only one at a
// time - switching one on switches the others off), plus start/add actions
// for the active one.
@Composable
private fun MixPanel(onMusicIp: () -> Unit) {
    var state by remember { mutableStateOf<MixState?>(null) }
    // MusicIP's own mood mix, offered when its plugin answers
    var musicIpAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { musicIpAvailable = withContext(Dispatchers.IO) { LMS.getMusicIpMoods().isNotEmpty() } }
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    // The title of the panel shows which plugin is on; cleared when the Mix list closes
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { NavState.activeMixLabel = ""; NavState.activeMixIcon = null } }

    // Read the state now and keep it fresh (a plugin may switch another off).
    // A plugin that was just switched on: keep showing it as on for a few
    // seconds, until the server reports it as on as well.
    var pendingOn by remember { mutableStateOf<Pair<MixPlugin, Long>?>(null) }
    fun withPending(s: MixState, p: Pair<MixPlugin, Long>?): MixState =
        if (p != null && System.currentTimeMillis() - p.second < 6000 && p.first !in s.on)
            s.copy(on = setOf(p.first))
        else s

    LaunchedEffect(Unit) {
        while (true) {
            // No answer (server restarting): keep what is shown, so the list and focus stay put
            val fresh = withContext(Dispatchers.IO) { LMS.getMixStateOrNull() }
            if (fresh != null) state = withPending(fresh, pendingOn)
            kotlinx.coroutines.delay(3000)
        }
    }

    val current = state
    if (current == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    // Don't Stop The Music is not listed here (it is set with the repeat button)
    if (current.available.none { it != MixPlugin.DSTM }) {
        Text(stringResource(R.string.mix_no_plugins), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { firstFocus.requestFocus() } catch (_: Exception) { }
    }

    val order = listOf(MixPlugin.SUGARCUBE, MixPlugin.MIPSTER, MixPlugin.RANDOMFLOW, MixPlugin.RANDOMPLAY)
    val shown = order.filter { it in current.available }
    // The plugin the start/batch actions belong to (Don't Stop The Music has none)
    val active = current.on.firstOrNull { it != MixPlugin.DSTM }
    LaunchedEffect(active) {
        NavState.activeMixLabel = active?.label ?: ""
        NavState.activeMixIcon = when (active) {
            MixPlugin.SUGARCUBE -> SugarCubeIcon
            MixPlugin.MIPSTER -> MipsterIcon
            MixPlugin.RANDOMFLOW -> RandomFlowIcon
            MixPlugin.RANDOMPLAY -> randomMixIcon(LMS.currentRandomMixType)
            else -> null
        }
    }

    // Random Mix genres: loaded when the list is opened
    var genres by remember { mutableStateOf<List<Pair<String, Boolean>>?>(null) }
    var genresOpen by remember { mutableStateOf(false) }
    LaunchedEffect(genresOpen, active) {
        if (genresOpen && active == MixPlugin.RANDOMPLAY) {
            genres = withContext(Dispatchers.IO) { LMS.loadGenres() }
        }
    }
    // The settings are folded away until opened
    var settingsOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val genresFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }

    // Older SugarCube versions have no batches
    var hasBatch by remember { mutableStateOf(true) }
    LaunchedEffect(active) {
        hasBatch = active != MixPlugin.SUGARCUBE ||
            withContext(Dispatchers.IO) { LMS.hasCommand(listOf("sugarcube", "startbatch")) }
    }

    // Settings of the active plugin, read from its own menu on the server
    var settings by remember { mutableStateOf<List<PluginSetting>>(emptyList()) }
    LaunchedEffect(active) {
        settings = if (active != null) {
            withContext(Dispatchers.IO) { LMS.pluginSettings(active) }
        } else emptyList()
        // The lists (Filter, Recipe, Mood) are loaded one by one once the rest is shown
        for (i in settings.indices) {
            val row = settings.getOrNull(i) ?: break
            if (row.listCommand != null) {
                val loaded = withContext(Dispatchers.IO) { LMS.loadPluginList(row) }
                settings = settings.toMutableList().also { list ->
                    if (list.getOrNull(i)?.text == row.text) list[i] = loaded
                }
            }
        }
    }

    // Don't Stop The Music: its own list (Off or a provider), read from the server
    var dstm by remember {
        mutableStateOf(PluginSetting(MixPlugin.DSTM.label, emptyList(), emptyList(), -1, listCommand = listOf("dontstopthemusicsetting")))
    }
    LaunchedEffect(Unit) {
        dstm = withContext(Dispatchers.IO) { LMS.loadPluginList(dstm) }
    }

    // Rows that open below the screen are not shown (nor can they get the focus)
    // until the list has scrolled to them: scroll to the header that was opened
    // (its position is counted back from the end of the list), then focus the first row.
    val genreRows = if (genresOpen) (genres?.let { it.size + 2 } ?: 0) else 0
    val rowsAfterSettings = settings.size + if (active == MixPlugin.RANDOMPLAY) 1 + genreRows else 0
    LaunchedEffect(settingsOpen) {
        if (settingsOpen) {
            kotlinx.coroutines.delay(60)
            val header = listState.layoutInfo.totalItemsCount - rowsAfterSettings - 1
            if (header >= 0) listState.animateScrollToItem(header)
            repeat(10) {
                try { settingsFocus.requestFocus(); return@LaunchedEffect } catch (_: Exception) { }
                kotlinx.coroutines.delay(60)
            }
        }
    }
    LaunchedEffect(genresOpen, genres != null) {
        if (genresOpen && genres != null) {
            kotlinx.coroutines.delay(60)
            val header = listState.layoutInfo.totalItemsCount - genreRows - 1
            if (header >= 0) listState.animateScrollToItem(header)
            repeat(10) {
                try { genresFocus.requestFocus(); return@LaunchedEffect } catch (_: Exception) { }
                kotlinx.coroutines.delay(60)
            }
        }
    }

    // The rows of a list of settings (sliders and choices); get/set read and
    // write the list the rows belong to, onPicked is told which command a choice sent.
    fun LazyListScope.settingItems(
        open: Boolean,
        get: () -> List<PluginSetting>,
        set: (List<PluginSetting>) -> Unit,
        onPicked: suspend (List<Any>) -> Unit,
        firstFocus: FocusRequester? = null,
    ) {
        items(if (open) get().size else 0) { index ->
            val setting = get().getOrNull(index) ?: return@items
            val slider = setting.slider
            if (slider != null) {
                // The value changes at once; the command follows when the
                // key has been left alone for a moment, so holding a key
                // doesn't flood the server
                var sendJob by remember { mutableStateOf<Job?>(null) }
                val move = { delta: Int ->
                    val spec = get().getOrNull(index)?.slider
                    if (spec != null) {
                        val next = (spec.value + delta).coerceIn(spec.min, spec.max)
                        if (next != spec.value) {
                            set(get().toMutableList().also {
                                it[index] = it[index].copy(slider = spec.copy(value = next))
                            })
                            sendJob?.cancel()
                            sendJob = scope.launch {
                                kotlinx.coroutines.delay(400)
                                withContext(Dispatchers.IO) { LMS.runPluginCommand(spec.command(next)) }
                            }
                        }
                    }
                }
                SliderRow(
                    // Without the explanation in brackets, like on the Live page
                    label = setting.text.replace(Regex("\\s*\\([^)]*\\)"), ""),
                    value = slider.value,
                    min = slider.min,
                    max = slider.max,
                    valueText = if (slider.noLimitAtMax && slider.value == slider.max)
                        setting.choices.firstOrNull() ?: "-" else slider.value.toString(),
                    modifier = (if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier).onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) false
                        else {
                            // Holding the key for a while goes faster
                            val spec = get().getOrNull(index)?.slider
                            val unit = spec?.step ?: 1
                            val step = if (event.nativeKeyEvent.repeatCount >= 15) maxOf(spec?.fastStep ?: 5, unit) else unit
                            when (event.key) {
                                Key.DirectionRight -> { move(step); true }
                                Key.DirectionLeft -> { move(-step); true }
                                else -> false
                            }
                        }
                    }
                )
            } else {
                // A choice from a list (Filter, Mix Mode): a drop-down; the
                // command is sent once, when a choice is picked
                ChoiceRow(setting, if (index == 0) firstFocus else null) { next ->
                    val cmd = setting.commands[next]
                    set(get().toMutableList().also { list ->
                        list.getOrNull(index)?.let { list[index] = it.copy(selected = next) }
                    })
                    scope.launch(Dispatchers.IO) {
                        LMS.runPluginCommand(cmd)
                        onPicked(cmd)
                    }
                }
            }
        }
    }

    Column {
        if (active != null) {
            val rowFocus = remember { FocusRequester() }
            // Back closes the open settings first, instead of leaving the Mix list
            BackHandler(enabled = settingsOpen) {
                settingsOpen = false
                genresOpen = false
                scope.launch {
                    kotlinx.coroutines.delay(60)
                    try { rowFocus.requestFocus() } catch (_: Exception) { }
                }
            }
            val actions = buildList<Triple<ImageVector, String, () -> Unit>> {
                // Closing Browse would cancel a request in this panel's own scope, so these two use their own
                add(Triple(StartMixIcon, stringResource(R.string.mix_start_new), {
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { LMS.mixAction(active, MixAction.START_MIX) }
                    Toast.makeText(context, context.getString(R.string.mix_started), Toast.LENGTH_SHORT).show()
                    NavState.showBrowse = false
                }))
                // Lyrion's Random Mix has no batches
                if (active != MixPlugin.RANDOMPLAY && hasBatch) {
                    add(Triple(BatchStartIcon, stringResource(R.string.mix_start_batch), {
                        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { LMS.mixAction(active, MixAction.START_BATCH) }
                        Toast.makeText(context, context.getString(R.string.mix_batch_started), Toast.LENGTH_SHORT).show()
                        NavState.showBrowse = false
                    }))
                    add(Triple(BatchAddIcon, stringResource(R.string.mix_add_batch), {
                        scope.launch(Dispatchers.IO) { LMS.mixAction(active, MixAction.ADD_BATCH) }
                        Toast.makeText(context, context.getString(R.string.mix_batch_added), Toast.LENGTH_SHORT).show()
                    }))
                }
                if (settings.isNotEmpty()) {
                    add(Triple(SettingsMenuIcon, stringResource(R.string.menu_settings), { settingsOpen = !settingsOpen }))
                }
            }
            IconActionRow(actions, rowFocus, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    HintLazyColumn(state = listState) {
        items(shown.size) { index ->
            val plugin = shown[index]
            val isOn = plugin in current.on
            BrowseRow(
                text = plugin.label,
                trailing = if (isOn) "☑" else "☐",
                icon = when (plugin) {
                    MixPlugin.SUGARCUBE -> SugarCubeIcon
                    MixPlugin.MIPSTER -> MipsterIcon
                    MixPlugin.RANDOMFLOW -> RandomFlowIcon
                    MixPlugin.RANDOMPLAY -> randomMixIcon(LMS.currentRandomMixType)
                    else -> DstmIcon
                },
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = {
                    // Show the new state right away, then really switch
                    state = current.copy(on = if (isOn) current.on - plugin else setOf(plugin))
                    scope.launch(Dispatchers.IO) {
                        LMS.setMixPlugin(plugin, !isOn, current)
                        // A plugin can take a moment before it reports itself
                        // as on (Random Mix starts its mix right away but
                        // answers "not active" for a second or two), so the
                        // checkmark is held for a few seconds (see pendingOn).
                        pendingOn = if (isOn) null else plugin to System.currentTimeMillis()
                        state = withPending(LMS.getMixState(), pendingOn)
                    }
                }
            )
        }
        if (musicIpAvailable) {
            item { BrowseRow(stringResource(R.string.browse_musicip), onClick = onMusicIp, icon = MusicIpIcon) }
        }
        if (MixPlugin.DSTM in current.available) {
            item {
                ChoiceRow(dstm, icon = DstmIcon) { next ->
                    val cmd = dstm.commands[next]
                    dstm = dstm.copy(selected = next)
                    scope.launch(Dispatchers.IO) { LMS.runPluginCommand(cmd) }
                }
            }
        }
        if (active != null) {
            if (settings.isNotEmpty()) {
                settingItems(settingsOpen, { settings }, { settings = it }, firstFocus = settingsFocus, onPicked = { cmd ->
                    // The icon follows the Random Mix type
                    if (cmd.firstOrNull() == LMS.APP_MIX_TYPE) {
                        NavState.activeMixIcon = randomMixIcon(cmd.getOrNull(1) as? String ?: "tracks")
                    }
                    // A choice can change which other settings exist (Mix Mode: Batch size;
                    // a MIPster recipe: no Style/Variety), so read them again
                    if (active != null && cmd.firstOrNull() != LMS.APP_MIX_TYPE) {
                        val fresh = LMS.pluginSettings(active)
                        settings = fresh.map { if (it.listCommand != null) LMS.loadPluginList(it) else it }
                    }
                })
                // Random Mix genres: a folded list; OK switches a genre on or off
                if (settingsOpen && active == MixPlugin.RANDOMPLAY) {
                    val list = genres
                    item {
                        val on = list?.count { it.second }
                        BrowseRow(
                            (if (genresOpen) "▾ " else "▸ ") + "Genres" +
                                (if (list != null && on != null) ": $on/${list.size}" else ""),
                            icon = GenresIcon,
                            onClick = { genresOpen = !genresOpen }
                        )
                    }
                    if (genresOpen && list != null) {
                        item {
                            BrowseRow("Select all", icon = SelectAllIcon, modifier = Modifier.focusRequester(genresFocus), onClick = {
                                genres = list.map { it.first to true }
                                scope.launch(Dispatchers.IO) { LMS.setAllGenres(true) }
                            })
                        }
                        item {
                            BrowseRow("Select none", icon = SelectNoneIcon, onClick = {
                                genres = list.map { it.first to false }
                                scope.launch(Dispatchers.IO) { LMS.setAllGenres(false) }
                            })
                        }
                        items(list.size) { gi ->
                            val (name, enabled) = list[gi]
                            BrowseRow(name, trailing = if (enabled) "☑" else "☐", onClick = {
                                genres = list.toMutableList().also { it[gi] = name to !enabled }
                                scope.launch(Dispatchers.IO) { LMS.setGenre(name, !enabled) }
                            })
                        }
                    }
                }
            }
        }
    }
    }
}

// A plain scrollable list of items loaded on first use and then kept (so
// going back returns to the same spot without reloading).
@Composable
private fun LoadedList(
    items: List<BrowseItem>?,
    load: suspend () -> List<BrowseItem>,
    onLoaded: (List<BrowseItem>) -> Unit,
    emptyText: String,
    listState: LazyListState,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    focusKey: String = "",
) {
    LaunchedEffect(items == null) {
        if (items == null) {
            onLoaded(withContext(Dispatchers.IO) { load() })
        }
    }

    if (items == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    if (items.isEmpty()) {
        Text(emptyText, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    val startFocus = remember { FocusRequester() }
    LaunchedEffect(items) {
        listState.scrollToItem(selectedIndex.coerceIn(0, items.size - 1))
        try { startFocus.requestFocus() } catch (_: Exception) { }
    }

    HintLazyColumn(state = listState) {
        items(items.size) { index ->
            BrowseRow(
                text = items[index].name,
                onClick = { onSelect(index) },
                modifier = (if (index == selectedIndex) Modifier.focusRequester(startFocus) else Modifier).trackFocus(focusKey, index)
            )
        }
    }
}

// One level of the music folder tree: sub folders and tracks, plus play /
// add actions for the whole folder (not for the top level).
@Composable
private fun FolderList(
    folder: BrowseItem,
    onFolder: (BrowseItem) -> Unit,
    onTrack: (BrowseTrack) -> Unit,
) {
    var entries by remember(folder.id) { mutableStateOf<List<FolderEntry>?>(null) }
    LaunchedEffect(folder.id) {
        entries = withContext(Dispatchers.IO) { LMS.getFolder(folder.id) }
    }

    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val runFolder: (String, String) -> Unit = { cmd, message ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { LMS.playlistControl(cmd, folderId = folder.id) }
            Toast.makeText(context, if (ok) message else context.getString(R.string.common_no_player), Toast.LENGTH_SHORT).show()
            // Playing now: back to Now Playing (Browse reopens where it was left)
            if (ok && cmd == "load") NavState.showBrowse = false
        }
    }

    val list = entries
    if (list == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    val imageLoader = rememberLmsImageLoader()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(list) {
        try { firstFocus.requestFocus() } catch (_: Exception) { }
    }

    if (list.isEmpty() && folder.id.isBlank()) {
        Text(stringResource(R.string.browse_no_folders), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    val hasActions = folder.id.isNotBlank()
    // At the top level the list remembers the last focused entry.
    val memKey = if (!hasActions) "FOLDER" else ""
    val startIndex = remember(folder.id) { if (memKey.isEmpty()) 0 else (NavState.listFocus[memKey] ?: 0).coerceIn(0, (list.size - 1).coerceAtLeast(0)) }
    val listState = rememberLazyListState()
    LaunchedEffect(list) { if (startIndex > 0) listState.scrollToItem(startIndex) }
    HintLazyColumn(state = listState) {
        if (hasActions) {
            item {
                // A folder that holds more folders (e.g. an artist) plays all
                // its albums; one that only holds tracks is a single album.
                val many = list.any { it.isFolder }
                BrowseRow(if (many) stringResource(R.string.folder_play_all_albums) else stringResource(R.string.folder_play_album),
                    modifier = Modifier.focusRequester(firstFocus),
                    onClick = { runFolder("load", if (many) context.getString(R.string.folder_albums_playing) else context.getString(R.string.folder_album_playing)) })
            }
            item {
                val many = list.any { it.isFolder }
                BrowseRow(if (many) stringResource(R.string.folder_add_all_albums) else stringResource(R.string.folder_add_album),
                    onClick = { runFolder("add", if (many) context.getString(R.string.folder_albums_added) else context.getString(R.string.folder_album_added)) })
            }
        }
        items(list.size) { index ->
            val entry = list[index]
            ThumbRow(
                text = entry.name,
                imageUrl = null,
                placeholder = if (entry.isFolder) Icons.Filled.Folder else Icons.Outlined.MusicNote,
                round = false,
                imageLoader = imageLoader,
                onClick = {
                    if (entry.isFolder) onFolder(BrowseItem(entry.id, entry.name))
                    else onTrack(BrowseTrack(entry.id, entry.name, 0))
                },
                modifier = (if (!hasActions && index == startIndex) Modifier.focusRequester(firstFocus) else Modifier).trackFocus(memKey, index)
            )
        }
    }
}

@Composable
private fun SearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    results: SearchResults?,
    searching: Boolean,
    lastTerm: String,
    onSearch: () -> Unit,
    onArtist: (BrowseItem) -> Unit,
    onAlbum: (BrowseItem) -> Unit,
    onTrack: (BrowseTrack) -> Unit,
) {
    val fieldFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Only an explicit search (keyboard's search key) moves focus to the
    // results; live results while typing must leave focus in the field.
    var jumpToResults by remember { mutableStateOf(false) }

    // Land in the text field when opening a fresh search.
    LaunchedEffect(Unit) {
        if (results == null) fieldFocus.requestFocus()
    }
    // Live search: shortly after typing stops, search what's typed so far.
    // (Restarts on every keystroke, which gives the pause its debounce.)
    LaunchedEffect(query) {
        val term = query.trim()
        if (term.length >= 2 && term != lastTerm) {
            kotlinx.coroutines.delay(500)
            onSearch()
        }
    }
    // After an explicit search, jump to the first result.
    LaunchedEffect(results) {
        if (results != null && jumpToResults) {
            jumpToResults = false
            try { firstResultFocus.requestFocus() } catch (_: Exception) { }
        }
    }

    Column {
        // Search field: OK on the remote opens the on-screen keyboard.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.browse_type_to_search), color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    jumpToResults = true
                    // Already searched live for this exact text: just move on
                    if (query.trim() == lastTerm) {
                        try { firstResultFocus.requestFocus() } catch (_: Exception) { }
                        jumpToResults = false
                    } else onSearch()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                            keyboard?.show()
                            true
                        } else false
                    }
            )
        }

        if (searching) {
            Text(stringResource(R.string.browse_searching), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
            return@Column
        }
        if (results == null) return@Column
        if (results.artists.isEmpty() && results.albums.isEmpty() && results.tracks.isEmpty()) {
            Text(stringResource(R.string.browse_nothing_found), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
            return@Column
        }

        // The very first result row (whichever group it is in) gets the
        // focus after a search.
        val imageLoader = rememberLmsImageLoader()

        // Only the first few artists, so albums and tracks stay within reach
        val maxArtists = 5
        var artistsExpanded by remember(results) { mutableStateOf(false) }
        val shownArtists = if (artistsExpanded) results.artists else results.artists.take(maxArtists)

        HintLazyColumn {
            if (results.artists.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.browse_artists)) }
                items(shownArtists.size) { i ->
                    // No artist photos available: generic round placeholder
                    ThumbRow(shownArtists[i].name, imageUrl = null, placeholder = Icons.Filled.Person,
                        round = true, imageLoader = imageLoader,
                        onClick = { onArtist(shownArtists[i]) },
                        modifier = if (i == 0) Modifier.focusRequester(firstResultFocus) else Modifier)
                }
                if (!artistsExpanded && results.artists.size > maxArtists) {
                    item {
                        BrowseRow(stringResource(R.string.browse_more_artists, results.artists.size - maxArtists),
                            onClick = { artistsExpanded = true })
                    }
                }
            }
            if (results.albums.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.browse_albums)) }
                items(results.albums.size) { i ->
                    ThumbRow(results.albums[i].name, imageUrl = coverUrl(results.albums[i].artworkId),
                        placeholder = Icons.Outlined.MusicNote, round = false, imageLoader = imageLoader,
                        onClick = { onAlbum(results.albums[i]) },
                        subtitle = results.albums[i].subtitle,
                        modifier = if (results.artists.isEmpty() && i == 0) Modifier.focusRequester(firstResultFocus) else Modifier)
                }
            }
            if (results.tracks.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.browse_tracks)) }
                items(results.tracks.size) { i ->
                    ThumbRow(results.tracks[i].let { if (it.trackNum > 0) "${it.trackNum} • ${it.title}" else it.title }, imageUrl = coverUrl(results.tracks[i].coverId),
                        placeholder = Icons.Outlined.MusicNote, round = false, imageLoader = imageLoader,
                        onClick = { onTrack(results.tracks[i]) },
                        subtitle = results.tracks[i].subtitle,
                        modifier = if (results.artists.isEmpty() && results.albums.isEmpty() && i == 0)
                            Modifier.focusRequester(firstResultFocus) else Modifier)
                }
            }
        }
    }
}

// Cover thumbnails come from the Lyrion server (needs its login).
@Composable
internal fun rememberLmsImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember {
        ImageLoader.Builder(context)
            // The server serves some icons (Material's service logos) as SVG
            .components { add(coil.decode.SvgDecoder.Factory()) }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor(BasicAuthInterceptor(AppConfig.current.lmsUsername, AppConfig.current.lmsPassword))
                    .build()
            }
            .build()
    }
}

// Apps whose plugin icon is a coloured tile: Material has its own one-colour svg for them (address per app command).
private val materialAppSvg = mapOf(
    "deezer" to "material/html/images/deezer.svg",
    "qobuz" to "material/html/images/qobuz.svg",
    "spoton" to "material/svg/spoton",
    "spotty" to "material/html/images/spotify.svg",
    "tidal" to "material/html/images/tidal.svg",
    "squeezecloud" to "material/html/images/soundcloud.svg",
    "youtube" to "material/html/images/youtube.svg",
)

// Address of a picture the server gives for a radio item (relative to the server, or already absolute).
private fun radioImageUrl(path: String): String? = when {
    path.isBlank() -> null
    path.startsWith("http") -> path
    else -> "${AppConfig.current.lmsUrl}/" + path.trimStart('/')
}

// Small cover picture address for a cover id / artwork track id, or null if unknown.
internal fun coverUrl(id: String): String? =
    if (id.isBlank()) null else "${AppConfig.current.lmsUrl}/music/$id/cover_80x80_o.jpg"

// A list row with a small picture in front (cover, or a generic icon when
// there is none / while it is loading).
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ThumbRow(
    text: String,
    imageUrl: String?,
    placeholder: ImageVector,
    round: Boolean,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    subtitle: String = "",
    // Streaming service of the item (lower case); shows its logo at the end of the row
    service: String = "",
    // imageUrl is a one-colour svg that is shown in white; fallbackUrl is used when it cannot be loaded
    tintWhite: Boolean = false,
    fallbackUrl: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current
    var svgFailed by remember(imageUrl) { mutableStateOf(false) }
    var svgLoaded by remember(imageUrl) { mutableStateOf(false) }
    val tinted = tintWhite && !svgFailed

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(if (round) CircleShape else RoundedCornerShape(4.dp))
                .background(if (tinted) Color.Transparent else Color.White.copy(alpha = 0.1f))
        ) {
            // Placeholder icon underneath; the picture covers it once loaded
            // A transparent svg would show the placeholder through it, so hide it once the svg is in
            if (!(tinted && svgLoaded)) Icon(
                imageVector = placeholder,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
            val shownUrl = if (tintWhite && svgFailed) fallbackUrl else imageUrl
            if (shownUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(shownUrl).crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = if (tinted) ContentScale.Fit else ContentScale.Crop,
                    colorFilter = if (tinted) androidx.compose.ui.graphics.ColorFilter.tint(Color.White) else null,
                    onError = { if (tintWhite) svgFailed = true },
                    onSuccess = { if (tintWhite) svgLoaded = true },
                    modifier = if (tinted) Modifier.fillMaxSize().padding(4.dp) else Modifier.fillMaxSize()
                )
            }
            // Streaming service logo in the corner of the cover
            if (service.isNotEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) { ServiceBadge(service, imageLoader, size = 11.dp) }
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        // Long lines scroll by themselves while the row has focus
        Column(modifier = Modifier) {
            Text(
                text = text, color = Color.White, fontSize = 14.sp, maxLines = 1,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                overflow = TextOverflow.Ellipsis,
                modifier = if (focused) Modifier.basicMarquee() else Modifier
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = if (focused) Modifier.basicMarquee() else Modifier
                )
            }
        }
    }
}

// The logo of a streaming service from the server's Material plugin; nothing when it has none.
@Composable
private fun ServiceBadge(service: String, imageLoader: ImageLoader, size: androidx.compose.ui.unit.Dp = 22.dp) {
    var logo by remember(service) { mutableIntStateOf(0) } // 0 loading, 1 shown, 2 none
    Box(contentAlignment = Alignment.Center, modifier = Modifier.height(size)) {
        AsyncImage(
            model = "${com.example.lmsnowplaying.settings.AppConfig.current.lmsUrl}/material/svg/$service?c=fff",
            imageLoader = imageLoader,
            contentDescription = service,
            onSuccess = { logo = 1 },
            onError = { logo = 2 },
            modifier = Modifier.size(size)
        )
        if (logo == 2 && size >= 22.dp) {
            Text(
                text = service.replaceFirstChar { it.uppercase() },
                color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun ArtistList(
    role: String = "",
    artists: List<BrowseItem>?,
    onLoaded: (List<BrowseItem>) -> Unit,
    listState: LazyListState,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    focusKey: String = "",
) {
    LaunchedEffect(Unit) {
        if (artists == null) {
            onLoaded(withContext(Dispatchers.IO) { LMS.getArtists(count = 100000, role = role) })
        }
    }

    if (artists == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    if (artists.isEmpty()) {
        Text(stringResource(R.string.browse_no_artists), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    // Put focus on the artist we came from (the first one on first open).
    val startFocus = remember { FocusRequester() }
    LaunchedEffect(artists) {
        listState.scrollToItem(selectedIndex.coerceIn(0, artists.size - 1))
        try { startFocus.requestFocus() } catch (_: Exception) { }
    }

    // First list position of each starting letter (anything that isn't
    // A-Z, such as digits, counts as "#"); a letter without artists jumps
    // to the next letter that has some.
    val letters = remember { listOf("#") + ('A'..'Z').map { it.toString() } }
    val letterStart = remember(artists) {
        val first = mutableMapOf<String, Int>()
        artists.forEachIndexed { i, a ->
            // Lyrion sorts ignoring leading articles ("The 3 Jays" sorts
            // under 3), so the index must do the same.
            val c = a.name.trim().replace(ignoredArticles, "").firstOrNull()?.uppercaseChar()
            // Digits and symbols (which sort first) are grouped under "#"
            val key = if (c != null && c in 'A'..'Z') c.toString() else "#"
            if (key !in first) first[key] = i
        }
        letters.indices.map { li ->
            (li until letters.size).firstNotNullOfOrNull { first[letters[it]] } ?: (artists.size - 1)
        }
    }
    var stripIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Row {
        HintLazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            items(artists.size) { index ->
                BrowseRow(
                    text = artists[index].name,
                    onClick = { onSelect(index) },
                    modifier = (if (index == selectedIndex) Modifier.focusRequester(startFocus) else Modifier).trackFocus(focusKey, index)
                )
            }
        }
        AlphabetStrip(
            letters = letters,
            selected = stripIndex,
            onMove = { newIndex ->
                stripIndex = newIndex
                scope.launch { listState.scrollToItem(letterStart[newIndex]) }
            },
        )
    }
}

// Narrow A-Z index on the right side of a long list, like in the Material
// skin. D-pad right from the list enters it, up/down jumps through the
// letters (the list scrolls along), left/OK goes back to the list.
@Composable
private fun AlphabetStrip(
    letters: List<String>,
    selected: Int,
    onMove: (Int) -> Unit,
    // Optional: handle OK / left first; return true when it was handled here.
    onOk: (() -> Boolean)? = null,
    onLeft: (() -> Boolean)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .padding(start = 4.dp)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { onMove((selected - 1).coerceAtLeast(0)); true }
                    Key.DirectionDown -> { onMove((selected + 1).coerceAtMost(letters.size - 1)); true }
                    Key.DirectionLeft -> {
                        if (onLeft?.invoke() != true) focusManager.moveFocus(FocusDirection.Left)
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (onOk?.invoke() != true) focusManager.moveFocus(FocusDirection.Left)
                        true
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = interactionSource),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // A short strip (decades, years) gets roomy rows so the digits are not cut off;
        // the 27-letter A-Z strip has to stay compact to fit the panel.
        val roomy = letters.size <= 14
        letters.forEachIndexed { i, letter ->
            val isSelected = focused && i == selected
            Text(
                text = letter,
                fontSize = if (roomy) 13.sp else 11.sp,
                lineHeight = if (roomy) 20.sp else 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.Black else Color.White.copy(alpha = if (focused) 0.9f else 0.55f),
                modifier = Modifier
                    .width(if (roomy) 28.dp else 18.dp)
                    .height(if (roomy) 20.dp else 11.5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isSelected) Color.White else Color.Transparent),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AlbumList(
    artistId: String,
    year: String = "",
    role: String = "",
    newest: Boolean = false,
    all: Boolean = false,
    sort: String = "artistalbum",
    descending: Boolean = false,
    albums: List<BrowseItem>?,
    onLoaded: (List<BrowseItem>) -> Unit,
    listState: LazyListState,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    focusKey: String = "",
) {
    LaunchedEffect(artistId, year, newest, all, albums == null) {
        if (albums == null) {
            onLoaded(withContext(Dispatchers.IO) { if (newest) LMS.getNewAlbums() else if (all) LMS.getReleases(sort).let { if (descending) it.asReversed() else it } else if (year.isNotEmpty()) LMS.getAlbumsByYear(year) else LMS.getAlbums(artistId, role = role) })
        }
    }

    if (albums == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    if (albums.isEmpty()) {
        Text(stringResource(R.string.browse_no_albums), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    // Focus on the album we came back from (the first one on first open).
    val startFocus = remember { FocusRequester() }
    LaunchedEffect(albums) {
        listState.scrollToItem(selectedIndex.coerceIn(0, albums.size - 1))
        try { startFocus.requestFocus() } catch (_: Exception) { }
    }

    val imageLoader = rememberLmsImageLoader()
    val rows: LazyListScope.() -> Unit = {
        items(albums.size) { index ->
            ThumbRow(
                text = albums[index].name,
                subtitle = albums[index].subtitle,
                service = albums[index].service,
                imageUrl = coverUrl(albums[index].artworkId),
                placeholder = Icons.Outlined.MusicNote,
                round = false,
                imageLoader = imageLoader,
                onClick = { onSelect(index) },
                modifier = (if (index == selectedIndex) Modifier.focusRequester(startFocus) else Modifier)
                    .trackFocus(focusKey, index)
            )
        }
    }

    // Releases: strip on what the list is sorted by (first letter of artist or album title,
    // or the decade); in list order, so a reversed list gets a reversed strip.
    val stripBy: ((BrowseItem) -> String)? = when {
        !all -> null
        sort == "artistalbum" || sort == "artflow" -> ({ a: BrowseItem -> a.subtitle })
        sort == "album" -> ({ a: BrowseItem -> a.name })
        sort == "yearalbum" || sort == "yearartistalbum" -> ({ a: BrowseItem -> a.year })
        else -> null
    }
    if (stripBy == null) {
        HintLazyColumn(state = listState, content = rows)
        return
    }
    val yearMode = sort == "yearalbum" || sort == "yearartistalbum"
    // Year orders: one entry per decade present, in list order ('60, '70, ...; ? = no year).
    val decades = remember(albums, sort, descending) {
        val m = LinkedHashMap<String, Int>()
        if (yearMode) albums.forEachIndexed { i, a ->
            val key = decadeKey(a.year)
            if (key !in m) m[key] = i
        }
        m
    }
    // Decades show as '60, '70 ... without the century, like Lyrion; 1900 and 2000 are still
    // two separate entries (both '00) at their own place in the list.
    val decadeLabels = remember(decades) {
        decades.keys.map { if (it == "?") it else "'" + it.takeLast(2) }
    }
    val letters = remember(albums, sort, descending) {
        if (yearMode) decadeLabels
        else (listOf("#") + ('A'..'Z').map { it.toString() }).let { if (descending) it.reversed() else it }
    }
    val letterStart = remember(albums, sort, descending) {
        if (yearMode) return@remember decades.values.toList()
        val first = mutableMapOf<String, Int>()
        albums.forEachIndexed { i, a ->
            val c = stripBy(a).trim().replace(ignoredArticles, "").firstOrNull()?.uppercaseChar()
            val key = if (c != null && c in 'A'..'Z') c.toString() else "#"
            if (key !in first) first[key] = i
        }
        letters.indices.map { li ->
            (li until letters.size).firstNotNullOfOrNull { first[letters[it]] } ?: (albums.size - 1)
        }
    }
    // OK on a decade opens the single years of that decade in the strip.
    var openDecade by remember(albums, sort, descending) { mutableStateOf<String?>(null) }
    val yearsOfDecade = remember(albums, sort, descending, openDecade) {
        val m = LinkedHashMap<String, Int>()
        val d = openDecade
        if (yearMode && d != null) albums.forEachIndexed { i, a ->
            if (decadeKey(a.year) == d) {
                val label = "'" + ((a.year.toIntOrNull() ?: 0) % 100).toString().padStart(2, '0')
                if (label !in m) m[label] = i
            }
        }
        m
    }
    val labels = if (openDecade != null) yearsOfDecade.keys.toList() else letters
    val starts = if (openDecade != null) yearsOfDecade.values.toList() else letterStart
    var stripIndex by remember(albums, sort, descending, openDecade) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    Row {
        HintLazyColumn(state = listState, modifier = Modifier.weight(1f), content = rows)
        AlphabetStrip(
            letters = labels,
            selected = stripIndex,
            onMove = { newIndex ->
                stripIndex = newIndex
                scope.launch { listState.scrollToItem(starts[newIndex]) }
            },
            onOk = {
                when {
                    !yearMode -> false
                    openDecade != null -> { openDecade = null; false }
                    decades.keys.toList().getOrNull(stripIndex).let { it == null || it == "?" } -> false
                    else -> { openDecade = decades.keys.toList()[stripIndex]; true }
                }
            },
            onLeft = {
                if (openDecade != null) { openDecade = null; true } else false
            },
        )
    }
}

// Sort choices of the Releases list: one order (radio) plus a reverse switch (checkbox),
// like Material's "Sort by" dialog. A new order closes the dialog; Descending stays open.
@Composable
private fun ReleasesSortList(onChanged: () -> Unit, onDone: () -> Unit) {
    val options = listOf(
        "album" to stringResource(R.string.sort_release),
        "artistalbum" to stringResource(R.string.sort_artist_release),
        "artflow" to stringResource(R.string.sort_artist_year_release),
        "yearalbum" to stringResource(R.string.sort_year_release),
        "yearartistalbum" to stringResource(R.string.sort_year_artist_release),
    )
    var current by remember { mutableStateOf(NavState.releasesSort) }
    var desc by remember { mutableStateOf(NavState.releasesDesc) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { firstFocus.requestFocus() } catch (_: Exception) { } }
    HintLazyColumn {
        items(options.size) { i ->
            val (key, label) = options[i]
            BrowseRow(
                text = label,
                trailing = if (key == current) "\u25C9" else "\u25CB",
                modifier = if (key == current) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = {
                    if (key != current) {
                        NavState.releasesSort = key
                        onChanged()
                    }
                    onDone()
                },
            )
        }
        item {
            BrowseRow(
                text = stringResource(R.string.sort_descending),
                trailing = if (desc) "\u2611" else "\u2610",
                onClick = {
                    desc = !desc
                    NavState.releasesDesc = desc
                    onChanged()
                },
            )
        }
    }
}

@Composable
private fun TrackList(albumId: String, listState: LazyListState, selectedTrackId: String, onTrack: (BrowseTrack) -> Unit) {
    var tracks by remember(albumId) { mutableStateOf<List<BrowseTrack>?>(null) }
    LaunchedEffect(albumId) {
        if (albumId.isNotBlank()) {
            tracks = withContext(Dispatchers.IO) { LMS.getTracks(albumId) }
        }
    }

    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    // Runs a play/queue command for the whole album and tells the user what happened.
    val runAlbum: (String, String) -> Unit = { cmd, message ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { LMS.playlistControl(cmd, albumId = albumId) }
            Toast.makeText(context, if (ok) message else context.getString(R.string.common_no_player), Toast.LENGTH_SHORT).show()
            // Playing now: back to Now Playing (Browse reopens where it was left)
            if (ok && cmd == "load") NavState.showBrowse = false
        }
    }

    val list = tracks
    if (list == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    if (list.isEmpty()) {
        Text(stringResource(R.string.browse_no_tracks), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    // Focus on the track we came back from; the first row on first open.
    val firstFocus = remember { FocusRequester() }
    val selectedFocus = remember { FocusRequester() }
    val selectedIndex = list.indexOfFirst { it.id == selectedTrackId }
    LaunchedEffect(list) {
        if (selectedIndex >= 0) {
            listState.scrollToItem(selectedIndex)
            try { selectedFocus.requestFocus() } catch (_: Exception) { }
        } else {
            listState.scrollToItem(0)
            try { firstFocus.requestFocus() } catch (_: Exception) { }
        }
    }

    // The icons stay in place above the list while it scrolls
    // Favorite id and name while the rename field (after saving the album) is shown
    var rename by remember(albumId) { mutableStateOf<Pair<String, String>?>(null) }
    val albumHeart = favoriteHeartAction(albumId, { LMS.albumFavTarget(albumId) }, onSaved = { id, title -> rename = id to title })
    Column {
        IconActionRow(
            listOf(
                Triple(ActionPlayIcon, stringResource(R.string.action_play_now), { runAlbum("load", context.getString(R.string.folder_album_playing)) }),
                Triple(ActionNextIcon, stringResource(R.string.action_play_next), { runAlbum("insert", context.getString(R.string.folder_album_next)) }),
                Triple(ActionAddIcon, stringResource(R.string.action_add_end), { runAlbum("add", context.getString(R.string.folder_album_added)) }),
                albumHeart,
            ),
            firstFocus,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            wrapLeft = false,
        )
        rename?.let { (id, name) -> RenameFavoriteField(id, name) { rename = null } }
        HintLazyColumn(state = listState) {
            items(list.size) { index ->
                val track = list[index]
                BrowseRow(
                    text = if (track.trackNum > 0) "${track.trackNum}. ${track.title}" else track.title,
                    subtitle = track.subtitle,
                    onClick = { onTrack(track) },
                    modifier = if (index == selectedIndex) Modifier.focusRequester(selectedFocus) else Modifier,
                )
            }
        }
    }
}

// Action menu for one track: play now / next / add to the end.
@Composable
private fun TrackActions(track: BrowseTrack, onDone: () -> Unit) {
    val context = LocalContext.current.applicationContext
    // Not rememberCoroutineScope: onDone() closes this menu, which would cancel the request half way
    val run: (String, String) -> Unit = { cmd, message ->
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            val ok = withContext(Dispatchers.IO) { LMS.playlistControl(cmd, trackId = track.id) }
            Toast.makeText(context, if (ok) message else context.getString(R.string.common_no_player), Toast.LENGTH_SHORT).show()
            // Playing now: back to Now Playing (Browse reopens where it was left)
            if (ok && cmd == "load") NavState.showBrowse = false
        }
        onDone()
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(track) { firstFocus.requestFocus() }

    val imageLoader = rememberLmsImageLoader()
    Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        // Cover (a track id works as cover id too) and artist · album, so the menu says which track it is for
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.1f))
        ) {
            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
            val url = coverUrl(track.coverId.ifBlank { track.id })
            if (url != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            if (track.subtitle.isNotBlank()) {
                Text(track.subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            // Favorite id and name while the rename field (after saving the track) is shown
            var rename by remember(track.id) { mutableStateOf<Pair<String, String>?>(null) }
            val trackHeart = favoriteHeartAction(track.id, { LMS.trackFavTarget(track.id) }, onSaved = { id, title -> rename = id to title })
            IconActionRow(
                listOf(
                    Triple(ActionPlayIcon, stringResource(R.string.action_play_now), { run("load", context.getString(R.string.track_playing)) }),
                    Triple(ActionNextIcon, stringResource(R.string.action_play_next), { run("insert", context.getString(R.string.track_next)) }),
                    Triple(ActionAddIcon, stringResource(R.string.action_add_end), { run("add", context.getString(R.string.track_added)) }),
                    trackHeart,
                ),
                firstFocus,
                modifier = Modifier.padding(top = 4.dp),
            )
            rename?.let { (id, name) -> RenameFavoriteField(id, name) { rename = null } }
        }
    }
}

// Actions as icons side by side (left/right to move, OK to run); the name of the
// action that has the focus is shown below the row.
@Composable
internal fun IconActionRow(
    actions: List<Triple<ImageVector, String, () -> Unit>>,
    firstFocus: FocusRequester,
    buttonSize: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    modifier: Modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    wrapLeft: Boolean = true,
) {
    var focusedIndex by remember { mutableIntStateOf(0) }
    // The name of the action fades out after a few seconds; it comes back each time the focus enters the row
    var captionShown by remember { mutableStateOf(true) }
    var focusTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusedIndex, focusTick) {
        captionShown = true
        kotlinx.coroutines.delay(3000)
        captionShown = false
    }
    val others = remember(actions.size) { List(actions.size) { FocusRequester() } }
    val target: (Int) -> FocusRequester = { if (it == 0) firstFocus else others[it] }
    Column(modifier = modifier) {
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            actions.forEachIndexed { index, (icon, label, onClick) ->
                val interactionSource = remember { MutableInteractionSource() }
                val focused by interactionSource.collectIsFocusedAsState()
                LaunchedEffect(focused) { if (focused) { focusedIndex = index; focusTick++ } }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.focusRequester(target(index))
                        // Left/right wrap around, so the row never lets the focus go
                        .onPreviewKeyEvent { event ->
                            if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) false
                            else when (event.key) {
                                androidx.compose.ui.input.key.Key.DirectionRight -> { target((index + 1) % actions.size).requestFocus(); true }
                                androidx.compose.ui.input.key.Key.DirectionLeft ->
                                    if (index == 0 && !wrapLeft) false
                                    else { target((index + actions.size - 1) % actions.size).requestFocus(); true }
                                else -> false
                            }
                        }
                        .size(buttonSize)
                        .clip(CircleShape)
                        .background(if (focused) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f))
                        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                ) {
                    Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(iconSize))
                }
            }
        }
        Text(
            text = actions.getOrNull(focusedIndex)?.second ?: "",
            color = Color.White.copy(alpha = if (captionShown) 0.7f else 0f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowseRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, trailing: String? = null, subtitle: String = "") {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (subtitle.isNotEmpty()) 4.dp else 10.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
        }
        if (subtitle.isNotEmpty()) {
            // Long lines scroll by themselves while the row has focus
            Column(modifier = if (trailing != null) Modifier.weight(1f) else Modifier) {
                Text(text = text, color = Color.White, fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = if (focused) Modifier.basicMarquee() else Modifier)
                Text(text = subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = if (focused) Modifier.basicMarquee() else Modifier)
            }
        } else {
            Text(text = text, color = Color.White, fontSize = 14.sp, maxLines = 1, modifier = if (trailing != null) Modifier.weight(1f) else Modifier)
        }
        if (trailing != null) Text(text = trailing, color = Color.White, fontSize = 14.sp)
    }
}

// A setting with a range, on one line: label, a bar with a knob, and the
// value. The caller handles left/right (through the modifier) so every value
// is reachable.
@Composable
private fun SliderRow(label: String, value: Int, min: Int, max: Int, valueText: String, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val fraction = if (max > min) ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f) else 0f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White.copy(alpha = 0.25f) else Color.Transparent)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.width(80.dp).height(14.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f)))
            Box(Modifier.width(80.dp * fraction).height(4.dp).clip(RoundedCornerShape(2.dp)).background(NavState.accentColor))
            Box(Modifier.offset(x = 70.dp * fraction).size(10.dp).clip(CircleShape).background(NavState.accentColor))
        }
        Text(
            text = valueText,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            textAlign = TextAlign.End,
            // Fixed width so all bars start and end at the same place
            modifier = Modifier.padding(start = 8.dp).width(64.dp)
        )
    }
}

// A setting with a list of choices (Filter, Mix Mode): shows the current choice;
// OK opens the list, OK on a choice picks it (onSelect), Back or left closes the
// list without a change.
@Composable
private fun ChoiceRow(setting: PluginSetting, externalFocus: FocusRequester? = null, icon: ImageVector? = null, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var wasOpen by remember { mutableStateOf(false) }
    val ownFocus = remember { FocusRequester() }
    val rowFocus = externalFocus ?: ownFocus
    val currentFocus = remember { FocusRequester() }

    BackHandler(enabled = open) { open = false }

    // Focus goes to the current choice when the list opens, and back to the row when it closes
    LaunchedEffect(open) {
        if (open) {
            try { currentFocus.requestFocus() } catch (_: Exception) { }
        } else if (wasOpen) {
            try { rowFocus.requestFocus() } catch (_: Exception) { }
        }
        wasOpen = open
    }

    if (setting.choices.isEmpty()) {
        // The list is still being loaded
        BrowseRow(text = setting.text + " ...", onClick = { }, modifier = Modifier.focusRequester(rowFocus), icon = icon)
    } else if (!open) {
        BrowseRow(
            text = setting.display + " \u25BE",
            onClick = { open = true },
            modifier = Modifier.focusRequester(rowFocus),
            icon = icon
        )
    } else {
        Column(
            modifier = Modifier.onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) { open = false; true } else false
            }
        ) {
            SectionHeader(setting.text)
            setting.choices.forEachIndexed { i, name ->
                BrowseRow(
                    text = name,
                    trailing = if (i == setting.selected) "\u25CF" else "\u25CB",
                    modifier = if (i == maxOf(setting.selected, 0)) Modifier.focusRequester(currentFocus) else Modifier,
                    onClick = {
                        open = false
                        if (i != setting.selected) onSelect(i)
                    }
                )
            }
        }
    }
}
