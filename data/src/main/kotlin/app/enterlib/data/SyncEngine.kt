package app.enterlib.data

import app.enterlib.catalog.TmdbEnricher
import app.enterlib.core.CatalogSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SourceResult(
    val sourceId: String, val success: Boolean, val added: Int, val updated: Int,
    val newEpisodes: Int, val removed: Int, val error: String?,
)

class SyncEngine(
    private val repo: ContentRepository,
    private val dao: LibraryDao,
    private val sources: () -> List<CatalogSource>,
    private val enricher: () -> TmdbEnricher?,
) {
    private val mutex = Mutex()

    suspend fun syncAll(): List<SourceResult> = mutex.withLock { sources().map { syncOne(it) } }

    private suspend fun syncOne(source: CatalogSource): SourceResult {
        val start = System.currentTimeMillis()
        var added = 0; var updated = 0; var newEp = 0; var removed = 0
        var error: String? = null
        var success = false
        try {
            val items = source.fetch()
            val tmdb = enricher()
            for (raw in items) {
                var item = raw
                var attempted = false
                if (tmdb != null && repo.needsEnrichment(raw)) {
                    try { item = tmdb.enrich(raw); attempted = true } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                }
                val r = repo.upsert(item, start, attempted)
                if (r.added) added++ else if (r.updated) updated++
                newEp += r.newEpisodes
            }
            if (items.isEmpty()) {
                error = "Source returned no items; existing entries were kept"
            } else {
                removed = dao.pruneDestinations(source.id, start)
            }
            dao.markUnavailable(); dao.markAvailable()
            success = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
        dao.insertHistory(SyncHistoryEntity(
            sourceId = source.id, startedAt = start, finishedAt = System.currentTimeMillis(), success = success,
            added = added, updated = updated, newEpisodes = newEp, removed = removed, error = error))
        return SourceResult(source.id, success, added, updated, newEp, removed, error)
    }
}
