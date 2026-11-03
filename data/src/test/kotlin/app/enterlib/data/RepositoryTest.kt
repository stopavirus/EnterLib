package app.enterlib.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.enterlib.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    private lateinit var db: EnterLibDatabase
    private lateinit var repo: ContentRepository
    private lateinit var dao: LibraryDao

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, EnterLibDatabase::class.java).allowMainThreadQueries().build()
        repo = ContentRepository(db); dao = db.dao()
    }
    @After fun tearDown() = db.close()

    private fun movie(src: String, ext: String, url: String, cast: List<String> = emptyList()) = CatalogItem(
        src, ext, MediaKind.MOVIE, "The Film", 2020, genres = listOf("Drama"), cast = cast,
        destinations = listOf(Destination(url, DestinationKind.FILE)))

    @Test fun sameTitleFromTwoSourcesIsOneCardWithTwoDestinations() = runBlocking {
        repo.upsert(movie("jellyfin", "1", "https://a.example/v1"), 1)
        val r = repo.upsert(movie("plex", "9", "https://b.example/v2", listOf("Actor One")), 1)
        assertFalse(r.added)
        val all = dao.observeAvailable().first()
        assertEquals(1, all.size)
        assertEquals(2, dao.getDestinations(all[0].id, 0).size)
        assertEquals(listOf("Actor One"), all[0].castList())
    }

    @Test fun seriesEpisodesStaySeparatePerSeason() = runBlocking {
        fun ep(s: Int, e: Int) = EpisodeItem("x$s$e", s, e, "T$s$e",
            destinations = listOf(Destination("https://a.example/$s/$e", DestinationKind.FILE)))
        val show = CatalogItem("jellyfin", "s", MediaKind.SERIES, "Show", 2019,
            episodes = listOf(ep(1, 1), ep(1, 2), ep(2, 1)))
        val r = repo.upsert(show, 1)
        assertEquals(3, r.newEpisodes)
        assertEquals(0, repo.upsert(show, 2).newEpisodes)
        val id = dao.observeAvailable().first().single().id
        assertEquals(listOf(1 to 1, 1 to 2, 2 to 1), dao.getEpisodes(id).map { it.season to it.episode })
    }

    @Test fun unsafeUrlsAreNeverStored() = runBlocking {
        repo.upsert(movie("m3u", "1", "javascript:alert(1)"), 1)
        val c = dao.findByKey(Matching.matchKey(MediaKind.MOVIE, "The Film", 2020))!!
        assertTrue(dao.getDestinations(c.id, 0).isEmpty())
    }

    @Test fun pruneMarksUnavailableAndRestoreMakesAvailable() = runBlocking {
        repo.upsert(movie("jellyfin", "1", "https://a.example/v1"), 100)
        dao.pruneDestinations("jellyfin", 200); dao.markUnavailable()
        assertTrue(dao.observeAvailable().first().isEmpty())
        repo.upsert(movie("jellyfin", "1", "https://a.example/v1"), 300); dao.markAvailable()
        assertEquals(1, dao.observeAvailable().first().size)
    }

    @Test fun searchCoversTitleActorEpisodeAndYear() = runBlocking {
        repo.upsert(movie("jellyfin", "1", "https://a.example/v1", listOf("Zed Actor")), 1)
        repo.upsert(CatalogItem("jellyfin", "s", MediaKind.SERIES, "Show", 2018, episodes = listOf(
            EpisodeItem("e", 1, 1, "Unique Episode Name", destinations = listOf(Destination("https://a.example/e", DestinationKind.FILE))))), 1)
        assertEquals(1, dao.search("%Zed%").first().size)
        assertEquals("Show", dao.search("%Unique Episode%").first().single().title)
        assertEquals("Show", dao.search("%2018%").first().single().title)
        assertTrue(dao.search("%nothing-here%").first().isEmpty())
    }

    @Test fun watchlistProgressAndBackupRoundTrip() = runBlocking {
        repo.upsert(movie("jellyfin", "1", "https://a.example/v1"), 1)
        val id = dao.observeAvailable().first().single().id
        repo.setWatchlist(id, true, favourite = true)
        assertEquals(1, dao.observeWatchlist().first().size)
        PlaybackService(dao).saveProgress(id, 0, 60_000, 600_000, false)
        assertEquals(1, dao.observeContinueWatching().first().size)
        val json = BackupManager(dao).export()
        repo.setWatchlist(id, false); dao.clearHistory()
        assertTrue(dao.observeWatchlist().first().isEmpty())
        assertEquals(2, BackupManager(dao).import(json))
        assertEquals(1, dao.observeWatchlist().first().size)
        assertEquals(60_000, dao.getProgress(id, 0)!!.positionMs)
    }

    @Test fun finishedPlaybackLeavesContinueWatching() = runBlocking {
        repo.upsert(movie("jellyfin", "1", "https://a.example/v1"), 1)
        val id = dao.observeAvailable().first().single().id
        PlaybackService(dao).saveProgress(id, 0, 590_000, 600_000, false)
        assertTrue(dao.observeContinueWatching().first().isEmpty())
    }
}
