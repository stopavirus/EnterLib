package app.enterlib.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.enterlib.data.*
import coil.compose.AsyncImage

@Composable
fun HomeScreen(spec: UiSpec, onOpen: (Long) -> Unit, onPlay: (Long, Long) -> Unit, onSettings: () -> Unit) {
    val vm = graphViewModel { HomeViewModel(it) }
    val st by vm.state.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val play = rememberPlayAction(onPlay)
    when {
        !st.loaded -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        st.hero == null && st.rows.isEmpty() -> EmptyLibrary(onSettings, vm::syncNow)
        else -> {
            val focus = remember { FocusRequester() }
            LaunchedEffect(st.hero?.id) { if (spec.tv && st.hero != null) runCatching { focus.requestFocus() } }
            LazyColumn(Modifier.fillMaxSize()) {
                st.hero?.let { h -> item { Hero(h, spec, focus, { onOpen(h.id) }, { play(h.id, 0) }) } }
                if (syncing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                items(st.rows, key = { it.title }) { ContentRow(it.title, it.items, spec, onOpen) }
            }
        }
    }
}

@Composable
fun Hero(item: ContentEntity, spec: UiSpec, focus: FocusRequester, onDetails: () -> Unit, onWatch: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(spec.heroH)) {
        AsyncImage(item.backdropUrl ?: item.posterUrl, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
        Column(Modifier.align(Alignment.BottomStart).padding(spec.pad)) {
            Text(item.title, style = if (spec.tv) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium)
            Text(
                listOfNotNull(item.year?.toString(), if (item.isSeries()) "Series" else "Movie", item.genreList().firstOrNull())
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
            )
            item.overview?.let {
                Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 6.dp).widthIn(max = 640.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onDetails, modifier = Modifier.focusRequester(focus)) { Text("View Details") }
                if (!item.isSeries()) OutlinedButton(onClick = onWatch) { Text("Watch Now") }
            }
        }
    }
}

@Composable
fun BrowseScreen(spec: UiSpec, initialKind: String?, showSearch: Boolean, onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val vm = graphViewModel(key = "browse-$initialKind-$showSearch") { BrowseViewModel(it, initialKind) }
    val items by vm.items.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val kind by vm.kind.collectAsStateWithLifecycle()
    val cat by vm.category.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        if (showSearch) {
            OutlinedTextField(
                query, { vm.query.value = it }, singleLine = true, label = { Text("Title, actor, director, genre, year, language…") },
                modifier = Modifier.fillMaxWidth().padding(spec.pad, 8.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = spec.pad), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(kind == null, { vm.kind.value = null }, { Text("All") }) }
                item { FilterChip(kind == "MOVIE", { vm.kind.value = "MOVIE" }, { Text("Movies") }) }
                item { FilterChip(kind == "SERIES", { vm.kind.value = "SERIES" }, { Text("TV & Series") }) }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = spec.pad, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(cat == null, { vm.category.value = null }, { Text("All categories") }) }
            categoryLabels.forEach { (k, v) -> item { FilterChip(cat == k, { vm.category.value = if (cat == k) null else k }, { Text(v) }) } }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = spec.pad), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortMode.values().forEach { m -> item { AssistChip({ vm.sort.value = m }, { Text(if (sort == m) "✓ ${m.label}" else m.label) }) } }
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(if (query.isBlank()) "Nothing here yet. Add a source in Settings and sync." else "No matches",
                    style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyVerticalGrid(
                GridCells.Adaptive(spec.cardW), contentPadding = PaddingValues(spec.pad),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { gridItems(items, key = { it.id }) { PosterCard(it, spec, { onOpen(it.id) }) } }
        }
    }
}

@Composable
fun LibraryScreen(spec: UiSpec, onOpen: (Long) -> Unit) {
    val vm = graphViewModel { LibraryViewModel(it) }
    val watch by vm.watchlist.collectAsStateWithLifecycle()
    val fav by vm.favourites.collectAsStateWithLifecycle()
    val cont by vm.continueWatching.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    if (watch.isEmpty() && cont.isEmpty() && recent.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Your watchlist and history will appear here.") }
    } else LazyColumn(Modifier.fillMaxSize()) {
        if (cont.isNotEmpty()) item { ContentRow("Continue Watching", cont, spec, onOpen) }
        if (watch.isNotEmpty()) item { ContentRow("Watchlist", watch, spec, onOpen) }
        if (fav.isNotEmpty()) item { ContentRow("Favourites", fav, spec, onOpen) }
        if (recent.isNotEmpty()) item { ContentRow("Recently Viewed", recent, spec, onOpen) }
    }
}

private fun fmtMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun DetailScreen(contentId: Long, spec: UiSpec, onBack: () -> Unit, onPlay: (Long, Long) -> Unit, onOpen: (Long) -> Unit) {
    val vm = graphViewModel(key = "detail-$contentId") { DetailViewModel(it, contentId) }
    val st by vm.state.collectAsStateWithLifecycle()
    val play = rememberPlayAction(onPlay)
    val c = st.content
    if (c == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            if (!st.loaded) CircularProgressIndicator() else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("This title is no longer available."); Button(onClick = onBack) { Text("Back") }
            }
        }
        return
    }
    val seasons = st.episodes.map { it.season }.distinct()
    var season by rememberSaveable(contentId) { mutableStateOf<Int?>(null) }
    val activeSeason = season ?: st.episodes.firstOrNull { it.id == st.playEpisodeId }?.season ?: seasons.firstOrNull()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().height(spec.heroH)) {
                AsyncImage(c.backdropUrl ?: c.posterUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
                Row(Modifier.align(Alignment.BottomStart).padding(spec.pad), verticalAlignment = Alignment.Bottom) {
                    if (c.posterUrl != null) {
                        AsyncImage(c.posterUrl, c.title, Modifier.size(spec.cardW, spec.cardH).padding(end = 12.dp), contentScale = ContentScale.Crop)
                    }
                    Column {
                        Text(c.title, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            listOfNotNull(c.year?.toString(), c.runtimeMinutes?.let { "$it min" }, c.language, c.country,
                                c.rating?.let { "★ %.1f".format(it) }, if (c.isSeries()) "${seasons.size} season(s)" else "Movie").joinToString(" · "),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(spec.pad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    st.playEpisodeId?.let { eid -> Button(onClick = { play(contentId, eid) }) { Text(st.playLabel) } }
                    OutlinedButton(onClick = { vm.toggleWatchlist(st) }) { Text(if (st.inWatchlist) "In Watchlist ✓" else "Add to Watchlist") }
                    OutlinedButton(onClick = { vm.toggleFavourite(st) }) { Text(if (st.favourite) "Favourite ♥" else "Favourite") }
                }
                c.overview?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                if (c.genreList().isNotEmpty()) Text("Genres: " + c.genreList().joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                c.director?.let { Text("Director: $it", style = MaterialTheme.typography.bodyMedium) }
                if (c.castList().isNotEmpty()) Text("Cast: " + c.castList().joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                if (st.sources.isNotEmpty()) Text("Available from: " + st.sources.joinToString(", "), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (c.isSeries() && seasons.isNotEmpty()) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = spec.pad), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons) { s -> FilterChip(activeSeason == s, { season = s }, { Text("Season $s") }) }
                }
            }
            items(st.episodes.filter { it.season == activeSeason }, key = { it.id }) { ep ->
                val ok = ep.id in st.playableEpisodes
                val p = st.progress[ep.id]
                Row(
                    Modifier.fillMaxWidth().then(if (ok) Modifier.clickable { play(contentId, ep.id) } else Modifier)
                        .padding(horizontal = spec.pad, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(spec.still, spec.still * 9 / 16).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        if (ep.stillUrl != null) AsyncImage(ep.stillUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("E${ep.episode}. ${ep.title ?: "Episode ${ep.episode}"}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(ep.runtimeMinutes?.let { "$it min" }, ep.airDate,
                                when { p == null -> null; p.completed -> "Watched"; else -> "Resume at ${fmtMs(p.positionMs)}" },
                                if (!ok) "Not available" else null).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ep.overview?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        if (st.similar.isNotEmpty()) item { Spacer(Modifier.height(8.dp)); ContentRow("Similar Content", st.similar, spec, onOpen) }
    }
}
