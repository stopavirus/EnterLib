package app.enterlib.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [ContentEntity::class, EpisodeEntity::class, DestinationEntity::class, WatchlistEntity::class,
        ProgressEntity::class, SyncHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class EnterLibDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao
}

/** Add Migration objects here for every schema bump; destructive migration is intentionally not enabled. */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
