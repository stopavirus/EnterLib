package app.enterlib.data

import androidx.room.*

@Entity(tableName = "content", indices = [Index(value = ["matchKey"], unique = true)])
data class ContentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchKey: String,
    val kind: String,
    val category: String,
    val title: String,
    val year: Int?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val genres: String,
    val language: String?,
    val country: String?,
    val castNames: String,
    val director: String?,
    val rating: Double?,
    val runtimeMinutes: Int?,
    val tmdbId: Long?,
    val addedAt: Long,
    val updatedAt: Long,
    val tmdbCheckedAt: Long = 0,
    val available: Boolean = true,
) {
    fun genreList() = genres.split('|').filter { it.isNotBlank() }
    fun castList() = castNames.split('|').filter { it.isNotBlank() }
    fun isSeries() = kind == "SERIES"
    fun isNew(now: Long = System.currentTimeMillis()) = now - addedAt < 14L * 24 * 3600 * 1000
}

@Entity(tableName = "episodes", indices = [Index(value = ["contentId", "season", "episode"], unique = true)])
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: Long,
    val season: Int,
    val episode: Int,
    val title: String?,
    val overview: String?,
    val runtimeMinutes: Int?,
    val stillUrl: String?,
    val airDate: String?,
)

/** episodeId = 0 means the destination belongs to the title itself (movies). */
@Entity(
    tableName = "destinations",
    indices = [Index(value = ["contentId", "episodeId", "sourceId", "url"], unique = true), Index("sourceId")],
)
data class DestinationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: Long,
    val episodeId: Long = 0,
    val sourceId: String,
    val externalId: String,
    val url: String,
    val kind: String,
    val label: String?,
    val lastSeenAt: Long,
)

@Entity(tableName = "watchlist")
data class WatchlistEntity(@PrimaryKey val contentId: Long, val favourite: Boolean, val addedAt: Long)

@Entity(tableName = "progress", primaryKeys = ["contentId", "episodeId"])
data class ProgressEntity(
    val contentId: Long,
    val episodeId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val completed: Boolean,
)

@Entity(tableName = "sync_history")
data class SyncHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val startedAt: Long,
    val finishedAt: Long,
    val success: Boolean,
    val added: Int,
    val updated: Int,
    val newEpisodes: Int,
    val removed: Int,
    val error: String?,
)
