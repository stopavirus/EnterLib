package app.enterlib.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.enterlib.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class HomeRow(val title: String, val items: List<ContentEntity>)
data class HomeState(val loaded: Boolean = false, val hero: ContentEntity? = null, val rows: List<HomeRow> = emptyList())

class HomeViewModel(private val g: EnterLibGraph) : ViewModel() {
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    val state: StateFlow<HomeState> = combine(g.dao.observeAvailable(), g.dao.observeContinueWatching()) { all, cont ->
        buildHome(all, cont)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    fun syncNow() {
        viewModelScope.launch { _syncing.value = true; try { g.sync.syncAll() } finally { _syncing.value = false } }
    }

    private fun buildHome(all: List<ContentEntity>, cont: List<ContentEntity>): HomeState {
        val rows = mutableListOf<HomeRow>()
        fun add(title: String, list: List<ContentEntity>, min: Int = 1) { if (list.size >= min) rows += HomeRow(title, list.take(30)) }
        add("Continue Watching", cont)
        val movies = all.filter { !it.isSeries() }
        val series = all.filter { it.isSeries() }
        add("Recently Added Movies", movies)
        add("Recently Added TV Series", series)
        add("Latest Releases", all.filter { it.year != null }.sortedByDescending { it.year }.take(20))
        add("Top Rated", all.filter { it.rating != null }.sortedByDescending { it.rating }.take(20))
        for ((cat, label) in categoryLabels) add(label, all.filter { it.category == cat })
        val byLang = all.mapNotNull { c -> c.language?.let { it to c } }.groupBy({ it.first }, { it.second })
        byLang.entries.filter { it.value.size >= 3 }.sortedByDescending { it.value.size }.take(6)
            .forEach { add("${it.key} Content", it.value) }
        val byGenre = all.flatMap { c -> c.genreList().map { it to c } }.groupBy({ it.first }, { it.second })
        byGenre.entries.filter { it.value.size >= 3 }.sortedByDescending { it.value.size }.take(8)
            .forEach { add(it.key, it.value) }
        val hero = all.firstOrNull { it.backdropUrl != null } ?: all.firstOrNull { it.posterUrl != null }
        return HomeState(true, hero, rows)
    }
}

enum class SortMode(val label: String) { RECENT("Recently added"), YEAR("Release year"), RATING("Rating"), TITLE("A–Z") }

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class BrowseViewModel(g: EnterLibGraph, initialKind: String?) : ViewModel() {
    val query = MutableStateFlow("")
    val kind = MutableStateFlow(initialKind)
    val category = MutableStateFlow<String?>(null)
    val sort = MutableStateFlow(SortMode.RECENT)

    private val base = query.debounce(150).flatMapLatest { q ->
        if (q.isBlank()) g.dao.observeAvailable() else g.dao.search("%${q.trim()}%")
    }

    val items: StateFlow<List<ContentEntity>> = combine(base, kind, category, sort) { list, k, c, s ->
        val f = list.filter { (k == null || it.kind == k) && (c == null || it.category == c) }
        when (s) {
            SortMode.RECENT -> f.sortedByDescending { it.addedAt }
            SortMode.YEAR -> f.sortedByDescending { it.year ?: 0 }
            SortMode.RATING -> f.sortedByDescending { it.rating ?: -1.0 }
            SortMode.TITLE -> f.sortedBy { it.title.lowercase() }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class LibraryViewModel(g: EnterLibGraph) : ViewModel() {
    private fun <T> Flow<T>.keep(init: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), init)
    val watchlist = g.dao.observeWatchlist().keep(emptyList())
    val favourites = g.dao.observeFavourites().keep(emptyList())
    val continueWatching = g.dao.observeContinueWatching().keep(emptyList())
    val recent = g.dao.observeRecentlyViewed().keep(emptyList())
}

data class DetailState(
    val loaded: Boolean = false,
    val content: ContentEntity? = null,
    val episodes: List<EpisodeEntity> = emptyList(),
    val playableEpisodes: Set<Long> = emptySet(),
    val sources: List<String> = emptyList(),
    val inWatchlist: Boolean = false,
    val favourite: Boolean = false,
    val progress: Map<Long, ProgressEntity> = emptyMap(),
    val playEpisodeId: Long? = null,
    val playLabel: String = "Watch Now",
    val similar: List<ContentEntity> = emptyList(),
)

class DetailViewModel(private val g: EnterLibGraph, private val id: Long) : ViewModel() {
    private val part1 = combine(
        g.dao.observeContent(id), g.dao.observeEpisodes(id), g.dao.observeDestinations(id), g.dao.observeProgress(id),
    ) { c, eps, dests, prog -> arrayOf<Any?>(c, eps, dests, prog) }

    val state: StateFlow<DetailState> = combine(
        part1, g.dao.observeWatchEntry(id), g.dao.observeAvailable(),
    ) { p, watch, all ->
        @Suppress("UNCHECKED_CAST")
        val c = p[0] as ContentEntity?
        val eps = p[1] as List<EpisodeEntity>
        val dests = p[2] as List<DestinationEntity>
        val prog = (p[3] as List<ProgressEntity>).associateBy { it.episodeId }
        if (c == null) return@combine DetailState(loaded = true)
        val playable = dests.filter { it.episodeId != 0L }.map { it.episodeId }.toSet()
        val playableEps = eps.filter { it.id in playable }
        var playEp: Long? = null
        var label = "Watch Now"
        if (!c.isSeries()) {
            val p0 = prog[0L]
            if (p0 != null && !p0.completed && p0.positionMs > 10_000) label = "Resume"
            playEp = 0L
        } else if (playableEps.isNotEmpty()) {
            val last = prog.values.maxByOrNull { it.updatedAt }
            val target = when {
                last == null -> playableEps.first()
                !last.completed -> playableEps.firstOrNull { it.id == last.episodeId } ?: playableEps.first()
                else -> {
                    val idx = playableEps.indexOfFirst { it.id == last.episodeId }
                    playableEps.getOrNull(idx + 1) ?: playableEps.first()
                }
            }
            playEp = target.id
            val resume = last != null && !last.completed && last.episodeId == target.id
            label = (if (resume) "Resume" else "Play") + " S${target.season}E${target.episode}"
        }
        val genres = c.genreList().toSet()
        val similar = all.asSequence().filter { it.id != c.id && it.kind == c.kind }
            .map { it to it.genreList().count { g -> g in genres } }.filter { it.second > 0 }
            .sortedByDescending { it.second }.take(15).map { it.first }.toList()
        DetailState(
            loaded = true, content = c, episodes = eps, playableEpisodes = playable,
            sources = dests.map { SourceNames.display(it.sourceId) }.distinct(),
            inWatchlist = watch != null, favourite = watch?.favourite == true,
            progress = prog, playEpisodeId = playEp, playLabel = label, similar = similar,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailState())

    fun toggleWatchlist(current: DetailState) {
        viewModelScope.launch { g.repo.setWatchlist(id, !current.inWatchlist, current.favourite) }
    }
    fun toggleFavourite(current: DetailState) {
        viewModelScope.launch { g.repo.setWatchlist(id, true, !current.favourite) }
    }
}

data class SettingsUi(val settings: AppSettings, val history: List<SyncHistoryEntity>)

class SettingsViewModel(private val g: EnterLibGraph) : ViewModel() {
    val state: StateFlow<SettingsUi> = combine(g.settings.state, g.dao.observeHistory(12)) { s, h -> SettingsUi(s, h) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUi(g.settings.state.value, emptyList()))
    val syncing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun save(s: AppSettings) {
        g.settings.save(s)
        SyncScheduler.apply(g.appContext, s.syncHours)
        message.value = "Settings saved"
    }

    fun syncNow() {
        viewModelScope.launch {
            syncing.value = true
            val r = try { g.sync.syncAll() } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            syncing.value = false
            message.value = when {
                r.isEmpty() -> "No sources are enabled"
                r.all { it.success } -> "Sync finished: ${r.sumOf { it.added }} added, ${r.sumOf { it.updated }} updated"
                else -> "Sync finished with errors; see history"
            }
        }
    }

    fun exportTo(cr: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            message.value = try {
                val text = g.backup.export()
                withContext(Dispatchers.IO) { cr.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
                "Backup saved"
            } catch (e: CancellationException) { throw e } catch (e: Exception) { "Backup failed: ${e.message}" }
        }
    }

    fun importFrom(cr: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            message.value = try {
                val text = withContext(Dispatchers.IO) { cr.openInputStream(uri)?.use { String(it.readBytes()) } ?: "" }
                "Restored ${g.backup.import(text)} entries"
            } catch (e: CancellationException) { throw e } catch (e: Exception) { "Restore failed: ${e.message}" }
        }
    }
}
