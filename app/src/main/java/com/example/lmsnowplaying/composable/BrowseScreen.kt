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
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Library browsing panel, modelled on the Material skin's "Browse" page: a
// top-level list that leads to sub-lists (Artists -> Albums -> Tracks,
// plus Search), with play / queue actions on albums and tracks.
private enum class BrowseLevel {
    ROOT, ARTISTS, ALBUMS, TRACKS, TRACK_ACTIONS, SEARCH, GENRES, GENRE_ARTISTS, FOLDER, MIX,
    FAVORITES, FAVORITE_ACTIONS, PLAYLISTS, PLAYLIST_ACTIONS, PLAYLIST_TRACKS, RADIO, RADIO_ACTIONS, RADIO_SEARCH, MY_MUSIC
}

// The part of the panel a level belongs to, and the way back up to it from the
// root (deeper levels need data that is gone once the panel was closed).
private fun browseSection(level: BrowseLevel): String = when (level) {
    BrowseLevel.ARTISTS, BrowseLevel.ALBUMS, BrowseLevel.TRACKS, BrowseLevel.TRACK_ACTIONS -> "ARTISTS"
    BrowseLevel.GENRES, BrowseLevel.GENRE_ARTISTS -> "GENRES"
    BrowseLevel.FAVORITES, BrowseLevel.FAVORITE_ACTIONS -> "FAVORITES"
    BrowseLevel.PLAYLISTS, BrowseLevel.PLAYLIST_ACTIONS, BrowseLevel.PLAYLIST_TRACKS -> "PLAYLISTS"
    BrowseLevel.RADIO, BrowseLevel.RADIO_ACTIONS, BrowseLevel.RADIO_SEARCH -> "RADIO"
    else -> level.name
}

private fun restoreBrowse(section: String): Pair<BrowseLevel, List<BrowseLevel>> = when (section) {
    "MY_MUSIC" -> BrowseLevel.MY_MUSIC to listOf(BrowseLevel.ROOT)
    "ARTISTS", "GENRES", "FOLDER", "FAVORITES", "PLAYLISTS", "SEARCH" ->
        BrowseLevel.valueOf(section) to listOf(BrowseLevel.ROOT, BrowseLevel.MY_MUSIC)
    "RADIO", "MIX" -> BrowseLevel.valueOf(section) to listOf(BrowseLevel.ROOT)
    else -> BrowseLevel.ROOT to emptyList()
}

// Lyrion's default "ignored articles" list.
private val ignoredArticles = Regex("^(the|el|la|los|las|le|les)\\s+", RegexOption.IGNORE_CASE)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BrowseScreen() {
    val appContext = LocalContext.current.applicationContext
    // Open where it was left last time
    val restored = remember { restoreBrowse(NavState.lastBrowse) }
    var level by remember { mutableStateOf(restored.first) }
    // Where we came from, so Back/left retraces the actual route (also when
    // an artist/album was reached through Search instead of the artist list).
    val stack = remember { mutableStateListOf<BrowseLevel>().apply { addAll(restored.second) } }
    LaunchedEffect(level) { NavState.lastBrowse = browseSection(level) }
    val go: (BrowseLevel) -> Unit = { to ->
        stack.add(level)
        level = to
    }

    // Artist list, kept so going back returns to the same spot without reloading.
    var artists by remember { mutableStateOf<List<BrowseItem>?>(null) }
    val artistListState = rememberLazyListState()
    var selectedArtistIndex by remember { mutableIntStateOf(0) }
    var chosenArtist by remember { mutableStateOf<BrowseItem?>(null) }

    // Albums of the chosen artist.
    var albums by remember { mutableStateOf<List<BrowseItem>?>(null) }
    var albumsArtistId by remember { mutableStateOf("") }
    val albumListState = rememberLazyListState()
    var selectedAlbumIndex by remember { mutableIntStateOf(0) }
    var chosenAlbum by remember { mutableStateOf<BrowseItem?>(null) }

    // The track the action menu (play now / next / add) is open for.
    var actionTrack by remember { mutableStateOf<BrowseTrack?>(null) }
    // Track list of an album: the track last chosen (focus returns to it) and the scroll position.
    var selectedTrackId by remember { mutableStateOf("") }
    val trackListState = rememberLazyListState()

    // Genres, and the artists within the chosen genre.
    var genres by remember { mutableStateOf<List<BrowseItem>?>(null) }
    val genreListState = rememberLazyListState()
    var selectedGenreIndex by remember { mutableIntStateOf(0) }
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

    // Radio: the path of levels opened so far (the first is the list of radio
    // plugins, then the levels inside the chosen plugin) and that plugin's command.
    val radioPath = remember {
        mutableStateListOf<BrowseItem>().apply {
            if (restored.first == BrowseLevel.RADIO) add(BrowseItem("", appContext.getString(R.string.browse_radio)))
        }
    }
    var radioCmd by remember { mutableStateOf("") }
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
        if (artist.id != albumsArtistId) {
            albums = null
            selectedAlbumIndex = 0
            albumsArtistId = artist.id
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

    // Shown as a panel on top of Now Playing (like the player menu), not
    // as a full screen.
    Box(
        modifier = Modifier
            .width(300.dp)
            .heightIn(max = 366.dp)
            // Keep D-pad focus inside the panel: it stays open until Back,
            // so focus must not wander off to the buttons behind it.
            .focusProperties { exit = { FocusRequester.Cancel } }
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
                    BrowseLevel.RADIO -> radioPath.lastOrNull()?.name ?: stringResource(R.string.browse_radio)
                    BrowseLevel.RADIO_ACTIONS -> actionRadio?.name ?: stringResource(R.string.browse_radio)
                    BrowseLevel.RADIO_SEARCH -> radioSearch.name
                    BrowseLevel.MY_MUSIC -> stringResource(R.string.browse_my_music)
                    BrowseLevel.PLAYLIST_ACTIONS, BrowseLevel.PLAYLIST_TRACKS -> actionPlaylist?.name ?: stringResource(R.string.browse_playlist)
                },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )
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
                    onMix = { go(BrowseLevel.MIX) },
                )
                BrowseLevel.MY_MUSIC -> MyMusicList(
                    onSearch = { go(BrowseLevel.SEARCH) },
                    onArtists = { go(BrowseLevel.ARTISTS) },
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
                BrowseLevel.RADIO_ACTIONS -> actionRadio?.let { FavoriteActions(it, onDone = goBack, plugin = radioCmd) }
                BrowseLevel.MIX -> MixPanel()
                BrowseLevel.FAVORITES -> favPath.lastOrNull()?.let { folder ->
                    FavoritesList(
                        folder = folder,
                        onFolder = { favPath.add(it) },
                        onItem = {
                            actionFavorite = it
                            go(BrowseLevel.FAVORITE_ACTIONS)
                        },
                    )
                }
                BrowseLevel.FAVORITE_ACTIONS -> actionFavorite?.let { FavoriteActions(it, onDone = goBack) }
                BrowseLevel.PLAYLISTS -> PlaylistList(onItem = {
                    actionPlaylist = it
                    go(BrowseLevel.PLAYLIST_ACTIONS)
                })
                BrowseLevel.PLAYLIST_ACTIONS -> actionPlaylist?.let {
                    PlaylistActions(it, onDone = goBack, onTracks = { go(BrowseLevel.PLAYLIST_TRACKS) })
                }
                BrowseLevel.PLAYLIST_TRACKS -> actionPlaylist?.let { PlaylistTracks(it, onTrack = chooseTrack) }
                BrowseLevel.GENRES -> LoadedList(
                    items = genres,
                    load = { LMS.getGenres() },
                    onLoaded = { genres = it },
                    emptyText = stringResource(R.string.browse_no_genres),
                    listState = genreListState,
                    selectedIndex = selectedGenreIndex,
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
                BrowseLevel.ARTISTS -> ArtistList(
                    artists = artists,
                    onLoaded = { artists = it },
                    listState = artistListState,
                    selectedIndex = selectedArtistIndex,
                    onSelect = { index ->
                        selectedArtistIndex = index
                        artists?.getOrNull(index)?.let { chooseArtist(it) }
                    }
                )
                BrowseLevel.ALBUMS -> AlbumList(
                    artistId = chosenArtist?.id ?: "",
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
    onMix: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Column {
        BrowseRow(stringResource(R.string.browse_my_music), onClick = onMyMusic, modifier = Modifier.focusRequester(firstFocus), icon = MyMusicIcon)
        BrowseRow(stringResource(R.string.browse_radio), onClick = onRadio, icon = RadioMenuIcon)
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
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Column {
        BrowseRow(stringResource(R.string.browse_search), onClick = onSearch, modifier = Modifier.focusRequester(firstFocus), icon = SearchIcon)
        BrowseRow(stringResource(R.string.browse_artists), onClick = onArtists, icon = ArtistsIcon)
        BrowseRow(stringResource(R.string.browse_genres), onClick = onGenres, icon = GenresIcon)
        BrowseRow(stringResource(R.string.browse_music_folder), onClick = onFolders, icon = MusicFolderIcon)
        BrowseRow(stringResource(R.string.browse_favorites), onClick = onFavorites, icon = FavoritesIcon)
        BrowseRow(stringResource(R.string.browse_playlists), onClick = onPlaylists, icon = PlaylistsIcon)
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
    val folderId = path.lastOrNull()?.id ?: ""
    var radios by remember { mutableStateOf<List<RadioEntry>?>(null) }
    var entries by remember(cmd, path.size, folderId) { mutableStateOf<List<FavoriteEntry>?>(null) }
    LaunchedEffect(atRoot, cmd, path.size, folderId) {
        if (atRoot) {
            if (radios == null) radios = withContext(Dispatchers.IO) { LMS.getRadios() }
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
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(atRoot, path.size, folderId, shownCount) {
        try { firstFocus.requestFocus() } catch (_: Exception) { }
    }

    HintLazyColumn {
        if (atRoot) {
            val list = radios ?: emptyList()
            items(list.size) { index ->
                val radio = list[index]
                BrowseRow(
                    radio.name,
                    icon = radioEntryIcon(radio.cmd),
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = {
                        if (radio.isSearch) onSearch(radio.cmd, BrowseItem("", radio.name))
                        else onRadio(radio.cmd, radio.name)
                    }
                )
            }
        } else {
            val list = entries ?: emptyList()
            items(list.size) { index ->
                RadioEntryRow(
                    entry = list[index],
                    imageLoader = imageLoader,
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onSearch = { onSearch(cmd, BrowseItem(it.id, it.name)) },
                    onFolder = onFolder,
                    onItem = onItem,
                )
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
    val open = { if (entry.isFolder) onFolder(BrowseItem(entry.id, entry.name)) else onItem(BrowseItem(entry.id, entry.name)) }
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
    HintLazyColumn {
        if (hasActions) {
            item {
                BrowseRow(stringResource(R.string.fav_play_all),
                    modifier = Modifier.focusRequester(firstFocus),
                    onClick = { runFolder("load", context.getString(R.string.fav_playing)) })
            }
            item {
                BrowseRow(stringResource(R.string.fav_add_all),
                    onClick = { runFolder("add", context.getString(R.string.fav_added)) })
            }
        }
        items(list.size) { index ->
            val entry = list[index]
            ThumbRow(
                text = entry.name,
                imageUrl = null,
                placeholder = if (entry.isFolder) Icons.Filled.Folder else Icons.Filled.Star,
                round = false,
                imageLoader = imageLoader,
                onClick = {
                    if (entry.isFolder) onFolder(BrowseItem(entry.id, entry.name))
                    else onItem(BrowseItem(entry.id, entry.name))
                },
                modifier = if (!hasActions && index == 0) Modifier.focusRequester(firstFocus) else Modifier
            )
        }
    }
}

// Action menu for one favorite: play now / next / add to the end.
@Composable
private fun FavoriteActions(favorite: BrowseItem, onDone: () -> Unit, plugin: String = "favorites") {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val run: (String, String) -> Unit = { cmd, message ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { LMS.favoritesControl(cmd, favorite.id, plugin) }
            Toast.makeText(context, if (ok) message else context.getString(R.string.common_no_player), Toast.LENGTH_SHORT).show()
        }
        onDone()
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(favorite) { firstFocus.requestFocus() }

    Column {
        BrowseRow(stringResource(R.string.action_play_now), modifier = Modifier.focusRequester(firstFocus),
            onClick = { run("load", context.getString(R.string.fav_item_playing)) })
        BrowseRow(stringResource(R.string.action_play_next), onClick = { run("insert", context.getString(R.string.fav_item_next)) })
        BrowseRow(stringResource(R.string.action_add_end), onClick = { run("add", context.getString(R.string.fav_item_added)) })
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
    LaunchedEffect(list) {
        try { firstFocus.requestFocus() } catch (_: Exception) { }
    }
    HintLazyColumn {
        items(list.size) { index ->
            BrowseRow(list[index].name, onClick = { onItem(list[index]) },
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
        }
    }
}

// Action menu for one playlist: play now / next / add to the end.
@Composable
private fun PlaylistActions(playlist: BrowseItem, onDone: () -> Unit, onTracks: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val run: (String, String) -> Unit = { cmd, message ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { LMS.playlistControl(cmd, playlistId = playlist.id) }
            Toast.makeText(context, if (ok) message else context.getString(R.string.common_no_player), Toast.LENGTH_SHORT).show()
        }
        onDone()
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(playlist) { firstFocus.requestFocus() }

    Column {
        BrowseRow(stringResource(R.string.action_play_now), modifier = Modifier.focusRequester(firstFocus),
            onClick = { run("load", context.getString(R.string.playlist_playing)) })
        BrowseRow(stringResource(R.string.action_play_next), onClick = { run("insert", context.getString(R.string.playlist_next)) })
        BrowseRow(stringResource(R.string.action_add_end), onClick = { run("add", context.getString(R.string.playlist_added)) })
        BrowseRow(stringResource(R.string.playlist_show_tracks), onClick = onTracks)
    }
}

// The tracks of one playlist; OK on a track opens its action menu.
@Composable
private fun PlaylistTracks(playlist: BrowseItem, onTrack: (BrowseTrack) -> Unit) {
    var entries by remember(playlist.id) { mutableStateOf<List<Pair<BrowseTrack, String>>?>(null) }
    LaunchedEffect(playlist.id) { entries = withContext(Dispatchers.IO) { LMS.getPlaylistTracks(playlist.id) } }
    TrackDetailList(entries, onTrack)
}

// A list of tracks with cover, title and artist · album.
@Composable
private fun TrackDetailList(
    entries: List<Pair<BrowseTrack, String>>?,
    onTrack: (BrowseTrack) -> Unit,
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
    LaunchedEffect(list) {
        try { firstFocus.requestFocus() } catch (_: Exception) { }
    }
    HintLazyColumn {
        items(list.size) { index ->
            val (track, detail) = list[index]
            val interactionSource = remember { MutableInteractionSource() }
            val focused by interactionSource.collectIsFocusedAsState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = (if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (focused) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable(interactionSource = interactionSource, indication = null, onClick = { onTrack(track) })
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                    val url = coverUrl(track.coverId)
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

// Auto Mix control: which plugin keeps the queue filled (only one at a
// time - switching one on switches the others off), plus start/add actions
// for the active one.
@Composable
private fun MixPanel() {
    var state by remember { mutableStateOf<MixState?>(null) }
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
            state = withPending(withContext(Dispatchers.IO) { LMS.getMixState() }, pendingOn)
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
            item { SectionHeader(active.label) }
            item {
                BrowseRow(stringResource(R.string.mix_start_new), icon = StartMixIcon, onClick = {
                    scope.launch(Dispatchers.IO) { LMS.mixAction(active, MixAction.START_MIX) }
                    Toast.makeText(context, context.getString(R.string.mix_started), Toast.LENGTH_SHORT).show()
                })
            }
            // Lyrion's Random Mix has no batches
            if (active != MixPlugin.RANDOMPLAY && hasBatch) {
                item {
                    BrowseRow(stringResource(R.string.mix_start_batch), icon = BatchStartIcon, onClick = {
                        scope.launch(Dispatchers.IO) { LMS.mixAction(active, MixAction.START_BATCH) }
                        Toast.makeText(context, context.getString(R.string.mix_batch_started), Toast.LENGTH_SHORT).show()
                    })
                }
                item {
                    BrowseRow(stringResource(R.string.mix_add_batch), icon = BatchAddIcon, onClick = {
                        scope.launch(Dispatchers.IO) { LMS.mixAction(active, MixAction.ADD_BATCH) }
                        Toast.makeText(context, context.getString(R.string.mix_batch_added), Toast.LENGTH_SHORT).show()
                    })
                }
            }
            if (settings.isNotEmpty()) {
                item {
                    BrowseRow(
                        (if (settingsOpen) "▾ " else "▸ ") + stringResource(R.string.menu_settings),
                        icon = SettingsMenuIcon,
                        onClick = { settingsOpen = !settingsOpen }
                    )
                }
                settingItems(settingsOpen, { settings }, { settings = it }, firstFocus = settingsFocus, onPicked = { cmd ->
                    // The icon follows the Random Mix type
                    if (cmd.firstOrNull() == LMS.APP_MIX_TYPE) {
                        NavState.activeMixIcon = randomMixIcon(cmd.getOrNull(1) as? String ?: "tracks")
                    }
                    // Mix Mode decides which other settings exist (e.g. Batch size)
                    if (active != null && cmd.getOrNull(1) == "setmixmode") {
                        settings = LMS.pluginSettings(active)
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
                modifier = if (index == selectedIndex) Modifier.focusRequester(startFocus) else Modifier
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
    HintLazyColumn {
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
                modifier = if (!hasActions && index == 0) Modifier.focusRequester(firstFocus) else Modifier
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
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor(BasicAuthInterceptor(AppConfig.current.lmsUsername, AppConfig.current.lmsPassword))
                    .build()
            }
            .build()
    }
}

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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current

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
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            // Placeholder icon underneath; the picture covers it once loaded
            Icon(
                imageVector = placeholder,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(imageUrl).crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        // Long lines scroll by themselves while the row has focus
        Column {
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
    artists: List<BrowseItem>?,
    onLoaded: (List<BrowseItem>) -> Unit,
    listState: LazyListState,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    LaunchedEffect(Unit) {
        if (artists == null) {
            onLoaded(withContext(Dispatchers.IO) { LMS.getArtists(count = 100000) })
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
                    modifier = if (index == selectedIndex) Modifier.focusRequester(startFocus) else Modifier
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
private fun AlphabetStrip(letters: List<String>, selected: Int, onMove: (Int) -> Unit) {
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
                    Key.DirectionLeft, Key.DirectionCenter, Key.Enter -> {
                        focusManager.moveFocus(FocusDirection.Left); true
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = interactionSource),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEachIndexed { i, letter ->
            val isSelected = focused && i == selected
            Text(
                text = letter,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.Black else Color.White.copy(alpha = if (focused) 0.9f else 0.55f),
                modifier = Modifier
                    .width(18.dp)
                    .height(11.5.dp)
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
    albums: List<BrowseItem>?,
    onLoaded: (List<BrowseItem>) -> Unit,
    listState: LazyListState,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    LaunchedEffect(artistId, albums == null) {
        if (albums == null) {
            onLoaded(withContext(Dispatchers.IO) { LMS.getAlbums(artistId) })
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
    HintLazyColumn(state = listState) {
        items(albums.size) { index ->
            ThumbRow(
                text = albums[index].name,
                imageUrl = coverUrl(albums[index].artworkId),
                placeholder = Icons.Outlined.MusicNote,
                round = false,
                imageLoader = imageLoader,
                onClick = { onSelect(index) },
                modifier = if (index == selectedIndex) Modifier.focusRequester(startFocus) else Modifier
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
            // +2: the two album rows come before the tracks
            listState.scrollToItem(selectedIndex + 2)
            try { selectedFocus.requestFocus() } catch (_: Exception) { }
        } else {
            listState.scrollToItem(0)
            try { firstFocus.requestFocus() } catch (_: Exception) { }
        }
    }

    HintLazyColumn(state = listState) {
        // Two rows for the whole album first, then the tracks.
        item {
            BrowseRow(
                text = stringResource(R.string.folder_play_album),
                modifier = Modifier.focusRequester(firstFocus),
                onClick = { runAlbum("load", context.getString(R.string.folder_album_playing)) }
            )
        }
        item {
            BrowseRow(
                text = stringResource(R.string.folder_add_album),
                onClick = { runAlbum("add", context.getString(R.string.folder_album_added)) }
            )
        }
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

// Action menu for one track: play now / next / add to the end.
@Composable
private fun TrackActions(track: BrowseTrack, onDone: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val run: (String, String) -> Unit = { cmd, message ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { LMS.playlistControl(cmd, trackId = track.id) }
            Toast.makeText(context, if (ok) message else context.getString(R.string.common_no_player), Toast.LENGTH_SHORT).show()
        }
        onDone()
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(track) { firstFocus.requestFocus() }

    Column {
        BrowseRow(stringResource(R.string.action_play_now), modifier = Modifier.focusRequester(firstFocus),
            onClick = { run("load", context.getString(R.string.track_playing)) })
        BrowseRow(stringResource(R.string.action_play_next), onClick = { run("insert", context.getString(R.string.track_next)) })
        BrowseRow(stringResource(R.string.action_add_end), onClick = { run("add", context.getString(R.string.track_added)) })
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
