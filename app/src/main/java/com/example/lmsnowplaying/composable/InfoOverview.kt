package com.example.lmsnowplaying.composable

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lmsnowplaying.GetAlbumArt
import com.example.lmsnowplaying.R
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.lmsnowplaying.network.jellyfin.JellyfinApi
import com.example.lmsnowplaying.network.logitechmediaserver.BrowseItem
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
import com.example.lmsnowplaying.settings.AppConfig
import com.example.lmsnowplaying.settings.NavState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

// Info view like the Material skin's: artist, release and track of the playing track, one section at
// a time behind tabs. Left/Right pick a tab, Up/Down scroll the section; Back (handled by the caller)
// closes it. The texts come from the Music and Artist Information plugin on the server, and each
// section fills in as its answer arrives.
@Composable
fun InfoOverview(artist: String, album: String, title: String, coverUrl: String, source: String) {
    val trackId = LMS.currentTrackId
    val hasArtist = artist.isNotBlank()

    var artistInfo by remember(artist) { mutableStateOf<LMS.ArtistInfo?>(null) }
    var review by remember(artist, album) { mutableStateOf<String?>(null) }
    var lyrics by remember(artist, title) { mutableStateOf<String?>(null) }
    var tracks by remember(trackId) { mutableStateOf<List<LMS.AlbumTrack>?>(null) }

    // The album text and the track list are asked by album id when the track is a library track
    // (as Material does). null = not known yet, empty = no album (streams).
    var albumId by remember(trackId) { mutableStateOf<String?>(null) }
    LaunchedEffect(trackId) {
        albumId = if ((trackId.toIntOrNull() ?: -1) >= 0) withContext(Dispatchers.IO) { LMS.getAlbumIdOfTrack(trackId) } else ""
    }
    LaunchedEffect(artist) {
        artistInfo = if (hasArtist) withContext(Dispatchers.IO) { LMS.getArtistInfo(artist) } else LMS.ArtistInfo("", "")
    }
    var albums by remember(artist) { mutableStateOf<List<BrowseItem>?>(null) }
    LaunchedEffect(artist) {
        albums = if (hasArtist) withContext(Dispatchers.IO) { LMS.getArtistAlbums(artist) } else emptyList()
    }
    LaunchedEffect(artist, album, albumId) {
        val id = albumId ?: return@LaunchedEffect
        review = if (hasArtist && album.isNotBlank()) withContext(Dispatchers.IO) { LMS.getAlbumReview(artist, album, id) } else ""
    }
    LaunchedEffect(artist, title) {
        lyrics = if (hasArtist && title.isNotBlank()) withContext(Dispatchers.IO) { LMS.getLyrics(artist, title) } else ""
    }
    LaunchedEffect(albumId) {
        val id = albumId ?: return@LaunchedEffect
        // Streams have no library track to look an album up for
        tracks = if (id.isBlank()) emptyList() else withContext(Dispatchers.IO) { LMS.getAlbumTracks(id) }
    }

    // Details of the track (year, genre, replay gain) for library tracks
    val isLibrary = (trackId.toIntOrNull() ?: -1) >= 0
    var tech by remember(trackId) { mutableStateOf<Map<String, String>?>(null) }
    LaunchedEffect(trackId) {
        tech = if (isLibrary) withContext(Dispatchers.IO) { LMS.getTrackTech(trackId) } else emptyMap()
    }
    val techLine by LMS.techLineFlow.collectAsState()

    // Tabs: 0 artist, 1 release, 2 track (details), 3 lyrics. It opens on the track details.
    var active by remember { mutableStateOf(2) }
    val scrolls = List(4) { rememberScrollState() }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    // Album picked in the artist tab (-1 = none) and the action menu under it (-1 = closed, else 0..2)
    var albumSel by remember(artist) { mutableStateOf(-1) }
    var menuIdx by remember(artist) { mutableStateOf(-1) }
    BackHandler(enabled = menuIdx >= 0) { menuIdx = -1 }
    // Where the album list (tab 0) and the track list (tab 1) start and end in their scrolled content:
    // [albums top, albums end, tracks top, tracks end]. Down picks an item as soon as the list is on screen.
    val anchors = remember { IntArray(4) }
    val stepPx = with(LocalDensity.current) { 90.dp.toPx() }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (_: Exception) { } }

    val none = stringResource(R.string.info_overview_none)
    val loading = stringResource(R.string.common_loading)
    val tabLabels = listOf(
        stringResource(R.string.info_tab_artist),
        stringResource(R.string.info_tab_release),
        stringResource(R.string.info_tab_track),
        stringResource(R.string.info_tab_lyrics),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                val albumItems = albums.orEmpty()
                val trackItems = tracks.orEmpty()
                val size = when (active) { 0 -> albumItems.size; 1 -> trackItems.size; else -> 0 }
                val onList = size > 0
                val enter = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                val dpad = e.key == Key.DirectionLeft || e.key == Key.DirectionRight ||
                    e.key == Key.DirectionUp || e.key == Key.DirectionDown
                val back = e.key == Key.Back && menuIdx >= 0
                if (!dpad && !back && !(enter && onList && albumSel >= 0)) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown) {
                    if (menuIdx >= 0) {
                        // Action menu under the picked album
                        when {
                            // Up from the first action, Left, Right and Back go back to the picked album
                            back || e.key == Key.DirectionLeft || e.key == Key.DirectionRight -> menuIdx = -1
                            e.key == Key.DirectionUp -> menuIdx = if (menuIdx == 0) -1 else menuIdx - 1
                            e.key == Key.DirectionDown -> menuIdx = (menuIdx + 1).coerceAtMost(if (active == 1) 3 else 2)
                            enter -> {
                                val cmd = listOf("load", "insert", "add", "load")[menuIdx]
                                // On the release tab the fourth action plays the whole album
                                val isAlbum = active == 0 || menuIdx == 3
                                val msg = if (isAlbum) listOf(R.string.folder_album_playing, R.string.info_album_next, R.string.folder_album_added, R.string.folder_album_playing)[menuIdx]
                                    else listOf(R.string.track_playing, R.string.track_next, R.string.track_added)[menuIdx]
                                val id = if (active == 0) albumItems[albumSel].id else if (menuIdx == 3) albumId.orEmpty() else trackItems[albumSel].id
                                menuIdx = -1
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        if (isAlbum) LMS.playlistControl(cmd, albumId = id) else LMS.playlistControl(cmd, trackId = id)
                                    }
                                    Toast.makeText(appContext, appContext.getString(if (ok) msg else R.string.common_no_player), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    fun listOnScreen(): Boolean {
                        val sc = scrolls[active]
                        val top = anchors[2 * active]
                        val viewport = anchors[2 * active + 1] - sc.maxValue
                        return top > 0 && (sc.value >= sc.maxValue || sc.value + viewport * 0.6f >= top)
                    }
                    when (e.key) {
                        Key.DirectionLeft -> { active = (active - 1).coerceAtLeast(0); albumSel = -1 }
                        Key.DirectionRight -> { active = (active + 1).coerceAtMost(3); albumSel = -1 }
                        Key.DirectionUp ->
                            if (onList && albumSel >= 0) albumSel-- else scope.launch { scrolls[active].animateScrollBy(-stepPx) }
                        Key.DirectionDown ->
                            if (onList && albumSel >= 0) albumSel = (albumSel + 1).coerceAtMost(size - 1)
                            else if (onList && listOnScreen()) {
                                // Start on the album / track that is playing
                                albumSel = if (active == 0) albumItems.indexOfFirst { it.name == album }.coerceAtLeast(0)
                                    else trackItems.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
                            }
                            else scope.launch { scrolls[active].animateScrollBy(stepPx) }
                        else -> if (enter) menuIdx = 0
                    }
                }
                true
            }
    ) {
        InfoTabs(tabLabels, active)
        when (active) {
            0 -> InfoSection(artist, scrolls[0]) {
                val info = artistInfo
                if (info != null && info.portraitId.isNotBlank()) {
                    Box(Modifier.size(220.dp).clip(RoundedCornerShape(8.dp)).align(Alignment.CenterHorizontally)) {
                        GetAlbumArt("${AppConfig.current.lmsUrl}/contributor/${info.portraitId}/image")
                    }
                    Spacer(Modifier.height(12.dp))
                } else if (info != null && hasArtist) {
                    // Lyrion has no photo for this artist: use Jellyfin's, when it has one.
                    JellyfinArtistPhoto(artist)
                }
                InfoText(info?.bio, loading, none)
                val albumList = albums
                if (!albumList.isNullOrEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(if (albumList.size == 1) R.string.info_albums_one else R.string.info_albums_many, albumList.size),
                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.onGloballyPositioned { anchors[0] = it.positionInParent().y.toInt() },
                    )
                    Spacer(Modifier.height(8.dp))
                    albumList.forEachIndexed { i, a ->
                        InfoAlbumRow(a, i == albumSel, if (i == albumSel) menuIdx else -1)
                    }
                    Spacer(Modifier.height(1.dp).onGloballyPositioned { anchors[1] = it.positionInParent().y.toInt() })
                }
            }
            1 -> InfoSection(album, scrolls[1]) {
                if (coverUrl.isNotBlank()) {
                    Box(Modifier.size(220.dp).clip(RoundedCornerShape(8.dp)).align(Alignment.CenterHorizontally)) { GetAlbumArt(coverUrl) }
                    Spacer(Modifier.height(12.dp))
                }
                InfoText(review, loading, none)
                val list = tracks
                if (!list.isNullOrEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    val total = list.sumOf { it.seconds }
                    Text(
                        text = stringResource(if (list.size == 1) R.string.info_tracks_one else R.string.info_tracks_many, list.size, infoTime(total)),
                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.onGloballyPositioned { anchors[2] = it.positionInParent().y.toInt() },
                    )
                    Spacer(Modifier.height(8.dp))
                    list.forEachIndexed { i, t ->
                        InfoTrackRow(t, t.id == trackId, i == albumSel, if (i == albumSel) menuIdx else -1)
                    }
                    Spacer(Modifier.height(1.dp).onGloballyPositioned { anchors[3] = it.positionInParent().y.toInt() })
                }
            }
            2 -> InfoSection(title, scrolls[2]) {
                TrackDetails(artist, album, source, isLibrary, tech, techLine)
            }
            else -> InfoSection(title, scrolls[3]) {
                InfoText(lyrics, loading, none, centered = true)
            }
        }
    }
}

// Tab bar: the active tab is underlined in the accent colour
@Composable
private fun InfoTabs(labels: List<String>, active: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.2f)),
        horizontalArrangement = Arrangement.Center
    ) {
        labels.forEachIndexed { i, label ->
            // Intrinsic width: the underline is as wide as the label and does not take over the row
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(IntrinsicSize.Max).padding(horizontal = 20.dp)
            ) {
                Text(
                    text = label,
                    color = if (i == active) NavState.accentColor else Color.White.copy(alpha = 0.75f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                )
                Box(Modifier.height(3.dp).fillMaxWidth().background(if (i == active) NavState.accentColor else Color.Transparent))
            }
        }
    }
}

// One section: a heading band, then the scrollable content in a column that is not wider than
// comfortable to read.
@Composable
private fun ColumnScope.JellyfinArtistPhoto(artist: String) {
    if (AppConfig.current.jellyfinUrl.isBlank() || JellyfinApi.APITOKEN.isNullOrEmpty()) return
    var urls by remember(artist) { mutableStateOf<List<String>?>(null) }
    var idx by remember(artist) { mutableStateOf(0) }
    LaunchedEffect(artist) { urls = withContext(Dispatchers.IO) { JellyfinApi.GetArtistImageUrls(artist) } }
    val list = urls ?: return
    if (idx >= list.size) return
    val context = LocalContext.current
    val loader = remember {
        ImageLoader.Builder(context)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(chain.request().newBuilder()
                            .header("Authorization", "MediaBrowser Client=\"LMS Jellyfin\", Device=\"my-script\", DeviceId=\"0.0.0\", Version=\"0.0.0\", Token=\"${JellyfinApi.APITOKEN}\"")
                            .build())
                    }
                    .build()
            }
            .build()
    }
    Box(Modifier.size(220.dp).clip(RoundedCornerShape(8.dp)).align(Alignment.CenterHorizontally)) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(list[idx]).crossfade(500).build(),
            imageLoader = loader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onError = { idx++ },
        )
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun InfoSection(
    heading: String,
    scroll: ScrollState,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = heading,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = 8.dp, vertical = 10.dp)
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
                content = content
            )
        }
    }
}

// The details block of the track tab: artist, album, year, genre, source, the technical line
// (source, and below it the transcoded stream) and the track and album replay gain.
@Composable
private fun TrackDetails(
    artist: String,
    album: String,
    source: String,
    isLibrary: Boolean,
    tech: Map<String, String>?,
    techLine: String,
) {
    val lArtist = stringResource(R.string.info_row_artist)
    val lAlbum = stringResource(R.string.info_row_album)
    val lYear = stringResource(R.string.info_row_year)
    val lGenre = stringResource(R.string.info_row_genre)
    val lSource = stringResource(R.string.info_row_source)
    val lTech = stringResource(R.string.info_row_tech)
    val lTrackGain = stringResource(R.string.info_gain_track)
    val lAlbumGain = stringResource(R.string.info_gain_album)
    val local = stringResource(R.string.info_source_local)

    fun v(key: String) = tech?.get(key)?.trim().orEmpty()
    fun db(s: String) = if (s.contains("dB", ignoreCase = true)) s else "$s dB"
    val rows = mutableListOf<Pair<String, String>>()
    if (artist.isNotBlank()) rows.add(lArtist to artist)
    if (album.isNotBlank()) rows.add(lAlbum to album)
    v("year").takeIf { it.isNotEmpty() && it != "0" }?.let { rows.add(lYear to it) }
    v("genre").takeIf { it.isNotEmpty() }?.let { rows.add(lGenre to it) }
    (if (source.isNotBlank()) source else if (isLibrary) local else "").takeIf { it.isNotEmpty() }?.let { rows.add(lSource to it) }
    techLine.takeIf { it.isNotBlank() }?.let {
        val arrow = it.indexOf('\u2192')
        rows.add(lTech to if (arrow < 0) it else it.substring(0, arrow).trim() + "\n\u21B3 " + it.substring(arrow + 1).trim())
    }
    v("replay_gain").takeIf { it.isNotEmpty() }?.let { rows.add(lTrackGain to db(it)) }
    v("album_replay_gain").takeIf { it.isNotEmpty() }?.let { rows.add(lAlbumGain to db(it)) }

    Text(
        text = stringResource(R.string.info_details),
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    rows.forEach { (label, value) ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp, modifier = Modifier.width(140.dp))
            Text(value, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        }
    }
}

// null = still loading, empty = the plugin has nothing
@Composable
private fun InfoText(text: String?, loading: String, none: String, centered: Boolean = false) {
    Text(
        text = when {
            text == null -> loading
            text.isBlank() -> none
            else -> text
        },
        color = Color.White.copy(alpha = if (text.isNullOrBlank()) 0.55f else 0.9f),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth()
    )
}

// One album of the artist; when picked, the actions to play it show right under it.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoAlbumRow(album: BrowseItem, selected: Boolean, menuIdx: Int) {
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(selected, menuIdx) { if (selected) bring.bringIntoView() }
    val year = album.year.takeIf { it.isNotBlank() && it != "0" }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bring)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected && menuIdx < 0) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.1f))) {
                coverUrl(album.artworkId)?.let { GetAlbumArt(it) }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (year != null) "${album.name} ($year)" else album.name,
                color = Color.White, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
        if (selected && menuIdx >= 0) InfoActionRows(menuIdx, 56)
    }
}

// The three ways to play the picked album or track; menuIdx is the highlighted one.
@Composable
private fun InfoActionRows(menuIdx: Int, startDp: Int, withAlbum: Boolean = false) {
    val labels = listOf(R.string.action_play_now, R.string.action_play_next, R.string.action_add_end)
    (if (withAlbum) labels + R.string.info_play_album else labels).forEachIndexed { i, res ->
        Text(
            text = stringResource(res), color = Color.White, fontSize = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = startDp.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (i == menuIdx) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

// One track of the release: title, artist and technical line, length; the play actions show under it when picked.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoTrackRow(t: LMS.AlbumTrack, current: Boolean, selected: Boolean, menuIdx: Int) {
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(selected, menuIdx) { if (selected) bring.bringIntoView() }
    val bg = when {
        selected && menuIdx < 0 -> Color.White.copy(alpha = 0.25f)
        current -> Color.White.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bring)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = t.title,
                    color = if (current) NavState.accentColor else Color.White,
                    fontWeight = if (current || selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val sub = listOf(t.artist, t.tech).filter { it.isNotBlank() }.joinToString(" \u2022 ")
                if (sub.isNotEmpty()) {
                    Text(sub, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(infoTime(t.seconds), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        if (selected && menuIdx >= 0) InfoActionRows(menuIdx, 16, withAlbum = true)
    }
}

// 4:40 or 1:16:55
private fun infoTime(seconds: Double): String {
    val s = Math.round(seconds).toInt()
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
