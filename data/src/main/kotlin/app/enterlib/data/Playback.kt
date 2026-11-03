package app.enterlib.data

import app.enterlib.core.DestinationKind

sealed class PlaybackTarget {
    data class InApp(val url: String, val kind: DestinationKind, val sourceId: String) : PlaybackTarget()
    data class External(val url: String) : PlaybackTarget()
    object None : PlaybackTarget()
}

class PlaybackService(private val dao: LibraryDao) {
    suspend fun resolve(contentId: Long, episodeId: Long): PlaybackTarget {
        val d = dao.getDestinations(contentId, episodeId)
        d.firstOrNull { it.kind == "HLS" || it.kind == "DASH" || it.kind == "FILE" }?.let {
            return PlaybackTarget.InApp(it.url, DestinationKind.valueOf(it.kind), it.sourceId)
        }
        return d.firstOrNull { it.kind == "EXTERNAL" }?.let { PlaybackTarget.External(it.url) } ?: PlaybackTarget.None
    }

    suspend fun nextEpisodeId(contentId: Long, currentEpisodeId: Long): Long? {
        val eps = dao.getEpisodes(contentId)
        val idx = eps.indexOfFirst { it.id == currentEpisodeId }
        if (idx < 0) return null
        return eps.drop(idx + 1).firstOrNull { dao.getDestinations(contentId, it.id).isNotEmpty() }?.id
    }

    suspend fun saveProgress(contentId: Long, episodeId: Long, positionMs: Long, durationMs: Long, completed: Boolean) {
        val done = completed || (durationMs > 0 && positionMs >= durationMs * 95 / 100)
        dao.upsertProgress(ProgressEntity(contentId, episodeId, positionMs.coerceAtLeast(0), durationMs.coerceAtLeast(0),
            System.currentTimeMillis(), done))
    }
}
