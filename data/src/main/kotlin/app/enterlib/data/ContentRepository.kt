package app.enterlib.data

import androidx.room.withTransaction
import app.enterlib.core.*

class ContentRepository(private val db: EnterLibDatabase) {
    private val dao = db.dao()

    data class UpsertResult(val added: Boolean, val updated: Boolean, val newEpisodes: Int)

    private fun safeUrl(u: String?) = u?.takeIf { UrlValidator.isSafeHttpUrl(it) }

    /** True when the title is new or lacks metadata and TMDB has not been tried recently. */
    suspend fun needsEnrichment(item: CatalogItem, now: Long = System.currentTimeMillis()): Boolean {
        val e = dao.findByKey(Matching.matchKey(item.kind, item.title, item.year)) ?: return true
        val missing = e.overview == null || e.posterUrl == null || e.genres.isBlank() || e.language == null
        return missing && now - e.tmdbCheckedAt > 7L * 24 * 3600 * 1000
    }

    suspend fun upsert(item: CatalogItem, syncStart: Long, enrichAttempted: Boolean = false): UpsertResult = db.withTransaction {
        val now = System.currentTimeMillis()
        val key = Matching.matchKey(item.kind, item.title, item.year)
        val existing = dao.findByKey(key)
        val poster = safeUrl(item.posterUrl)
        val backdrop = safeUrl(item.backdropUrl)
        val contentId: Long
        var added = false
        var updated = false
        if (existing == null) {
            val category = Classifier.categorize(item.genres, item.language).name
            contentId = dao.insertContent(
                ContentEntity(
                    matchKey = key, kind = item.kind.name, category = category, title = item.title, year = item.year,
                    overview = item.overview, posterUrl = poster, backdropUrl = backdrop,
                    genres = item.genres.joinToString("|"), language = item.language, country = item.country,
                    castNames = item.cast.joinToString("|"), director = item.director, rating = item.rating,
                    runtimeMinutes = item.runtimeMinutes, tmdbId = item.tmdbId,
                    addedAt = item.addedAtMillis ?: now, updatedAt = now,
                    tmdbCheckedAt = if (enrichAttempted) now else 0,
                )
            )
            added = true
        } else {
            contentId = existing.id
            val genres = (existing.genreList() + item.genres).distinct()
            val merged = existing.copy(
                overview = existing.overview ?: item.overview,
                posterUrl = existing.posterUrl ?: poster,
                backdropUrl = existing.backdropUrl ?: backdrop,
                genres = genres.joinToString("|"),
                language = existing.language ?: item.language,
                country = existing.country ?: item.country,
                castNames = if (existing.castNames.isBlank()) item.cast.joinToString("|") else existing.castNames,
                director = existing.director ?: item.director,
                rating = existing.rating ?: item.rating,
                runtimeMinutes = existing.runtimeMinutes ?: item.runtimeMinutes,
                tmdbId = existing.tmdbId ?: item.tmdbId,
                tmdbCheckedAt = if (enrichAttempted) now else existing.tmdbCheckedAt,
            ).let { it.copy(category = Classifier.categorize(it.genreList(), it.language).name) }
            val metaChanged = merged.copy(tmdbCheckedAt = existing.tmdbCheckedAt) != existing
            if (merged != existing) {
                dao.updateContent(if (metaChanged) merged.copy(updatedAt = now) else merged)
                updated = metaChanged
            }
        }
        item.destinations.filter { UrlValidator.isSafeHttpUrl(it.url) }.forEach {
            dao.upsertDestination(DestinationEntity(
                contentId = contentId, episodeId = 0, sourceId = item.sourceId, externalId = item.externalId,
                url = it.url, kind = it.kind.name, label = it.label, lastSeenAt = syncStart))
        }
        var newEpisodes = 0
        for (ep in item.episodes) {
            val found = dao.findEpisode(contentId, ep.season, ep.episode)
            val epId: Long
            if (found == null) {
                epId = dao.insertEpisode(EpisodeEntity(
                    contentId = contentId, season = ep.season, episode = ep.episode, title = ep.title,
                    overview = ep.overview, runtimeMinutes = ep.runtimeMinutes, stillUrl = safeUrl(ep.stillUrl), airDate = ep.airDate))
                newEpisodes++
            } else {
                epId = found.id
                val m = found.copy(
                    title = found.title ?: ep.title, overview = found.overview ?: ep.overview,
                    runtimeMinutes = found.runtimeMinutes ?: ep.runtimeMinutes,
                    stillUrl = found.stillUrl ?: safeUrl(ep.stillUrl), airDate = found.airDate ?: ep.airDate)
                if (m != found) dao.updateEpisode(m)
            }
            ep.destinations.filter { UrlValidator.isSafeHttpUrl(it.url) }.forEach {
                dao.upsertDestination(DestinationEntity(
                    contentId = contentId, episodeId = epId, sourceId = item.sourceId, externalId = ep.externalId,
                    url = it.url, kind = it.kind.name, label = it.label, lastSeenAt = syncStart))
            }
        }
        UpsertResult(added, updated, newEpisodes)
    }

    suspend fun setWatchlist(contentId: Long, on: Boolean, favourite: Boolean = false) {
        if (on) dao.upsertWatch(WatchlistEntity(contentId, favourite, System.currentTimeMillis())) else dao.removeWatch(contentId)
    }
}
