package app.enterlib.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM content WHERE matchKey = :key LIMIT 1") suspend fun findByKey(key: String): ContentEntity?
    @Insert suspend fun insertContent(c: ContentEntity): Long
    @Update suspend fun updateContent(c: ContentEntity)
    @Query("SELECT * FROM episodes WHERE contentId = :cid AND season = :s AND episode = :e LIMIT 1")
    suspend fun findEpisode(cid: Long, s: Int, e: Int): EpisodeEntity?
    @Insert suspend fun insertEpisode(e: EpisodeEntity): Long
    @Update suspend fun updateEpisode(e: EpisodeEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDestination(d: DestinationEntity)
    @Query("DELETE FROM destinations WHERE sourceId = :sid AND lastSeenAt < :before")
    suspend fun pruneDestinations(sid: String, before: Long): Int
    @Query("UPDATE content SET available = 0 WHERE available = 1 AND id NOT IN (SELECT contentId FROM destinations)")
    suspend fun markUnavailable(): Int
    @Query("UPDATE content SET available = 1 WHERE available = 0 AND id IN (SELECT contentId FROM destinations)")
    suspend fun markAvailable(): Int

    @Query("SELECT * FROM content WHERE available = 1 ORDER BY addedAt DESC") fun observeAvailable(): Flow<List<ContentEntity>>
    @Query("SELECT * FROM content WHERE id = :id") fun observeContent(id: Long): Flow<ContentEntity?>
    @Query("SELECT * FROM content WHERE id = :id") suspend fun getContent(id: Long): ContentEntity?
    @Query("SELECT * FROM content WHERE matchKey = :key") suspend fun getByKey(key: String): ContentEntity?
    @Query("SELECT * FROM episodes WHERE contentId = :id ORDER BY season, episode") fun observeEpisodes(id: Long): Flow<List<EpisodeEntity>>
    @Query("SELECT * FROM episodes WHERE contentId = :id ORDER BY season, episode") suspend fun getEpisodes(id: Long): List<EpisodeEntity>
    @Query("SELECT * FROM destinations WHERE contentId = :cid AND episodeId = :eid") suspend fun getDestinations(cid: Long, eid: Long): List<DestinationEntity>
    @Query("SELECT * FROM destinations WHERE contentId = :cid") fun observeDestinations(cid: Long): Flow<List<DestinationEntity>>

    @Query(
        "SELECT * FROM content WHERE available = 1 AND (title LIKE :q OR castNames LIKE :q OR director LIKE :q OR genres LIKE :q " +
            "OR language LIKE :q OR country LIKE :q OR CAST(year AS TEXT) LIKE :q OR kind LIKE :q OR category LIKE :q " +
            "OR id IN (SELECT contentId FROM episodes WHERE title LIKE :q)) ORDER BY title"
    )
    fun search(q: String): Flow<List<ContentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertWatch(w: WatchlistEntity)
    @Query("DELETE FROM watchlist WHERE contentId = :id") suspend fun removeWatch(id: Long)
    @Query("SELECT * FROM watchlist WHERE contentId = :id") fun observeWatchEntry(id: Long): Flow<WatchlistEntity?>
    @Query("SELECT * FROM watchlist WHERE contentId = :id") suspend fun getWatchEntry(id: Long): WatchlistEntity?
    @Query("SELECT c.* FROM content c JOIN watchlist w ON w.contentId = c.id WHERE c.available = 1 ORDER BY w.addedAt DESC")
    fun observeWatchlist(): Flow<List<ContentEntity>>
    @Query("SELECT c.* FROM content c JOIN watchlist w ON w.contentId = c.id WHERE c.available = 1 AND w.favourite = 1 ORDER BY w.addedAt DESC")
    fun observeFavourites(): Flow<List<ContentEntity>>
    @Query("SELECT * FROM watchlist") suspend fun allWatch(): List<WatchlistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertProgress(p: ProgressEntity)
    @Query("SELECT * FROM progress WHERE contentId = :cid AND episodeId = :eid") suspend fun getProgress(cid: Long, eid: Long): ProgressEntity?
    @Query("SELECT * FROM progress WHERE contentId = :cid") fun observeProgress(cid: Long): Flow<List<ProgressEntity>>
    @Query("SELECT * FROM progress") suspend fun allProgress(): List<ProgressEntity>
    @Query(
        "SELECT c.* FROM content c JOIN (SELECT contentId, MAX(updatedAt) u FROM progress WHERE completed = 0 AND positionMs > 10000 " +
            "GROUP BY contentId) p ON p.contentId = c.id WHERE c.available = 1 ORDER BY p.u DESC LIMIT 20"
    )
    fun observeContinueWatching(): Flow<List<ContentEntity>>
    @Query(
        "SELECT c.* FROM content c JOIN (SELECT contentId, MAX(updatedAt) u FROM progress GROUP BY contentId) p " +
            "ON p.contentId = c.id WHERE c.available = 1 ORDER BY p.u DESC LIMIT 50"
    )
    fun observeRecentlyViewed(): Flow<List<ContentEntity>>
    @Query("DELETE FROM progress") suspend fun clearHistory()

    @Insert suspend fun insertHistory(h: SyncHistoryEntity)
    @Query("SELECT * FROM sync_history ORDER BY finishedAt DESC LIMIT :limit") fun observeHistory(limit: Int): Flow<List<SyncHistoryEntity>>
}
