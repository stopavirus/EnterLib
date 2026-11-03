package app.enterlib.catalog

import app.enterlib.core.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

class CatalogTests {
    private fun server(routes: Map<String, String>): MockWebServer {
        val s = MockWebServer()
        s.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val hit = routes.entries.firstOrNull { path.startsWith(it.key) }
                return if (hit != null) MockResponse().setBody(hit.value) else MockResponse().setResponseCode(404)
            }
        }
        s.start()
        return s
    }

    @Test fun jellyfinMapsMoviesAndSeriesEpisodes() = runBlocking {
        val s = server(mapOf(
            "/Shows/s1/Episodes" to """{"Items":[{"Id":"e1","Name":"Pilot","ParentIndexNumber":1,"IndexNumber":1},
                {"Id":"e2","Name":"Two","ParentIndexNumber":1,"IndexNumber":2}]}""",
            "/Items" to """{"TotalRecordCount":2,"Items":[
                {"Id":"m1","Name":"Film","Type":"Movie","ProductionYear":2020,"Genres":["Drama"],
                 "ProviderIds":{"Tmdb":"55"},"ImageTags":{"Primary":"x"},
                 "People":[{"Name":"A","Type":"Actor"},{"Name":"D","Type":"Director"}]},
                {"Id":"s1","Name":"Show","Type":"Series","ProductionYear":2019}]}""",
        ))
        val src = EmbyJellyfinSource.jellyfin(s.url("/").toString(), "tok", "", Http())
        val items = src.fetch()
        assertEquals(2, items.size)
        val movie = items.first { it.kind == MediaKind.MOVIE }
        assertEquals(55L, movie.tmdbId)
        assertEquals("D", movie.director)
        assertEquals(listOf("A"), movie.cast)
        assertTrue(movie.destinations.single().url.contains("/Videos/m1/stream"))
        assertFalse(movie.destinations.single().url.contains("tok"))
        val show = items.first { it.kind == MediaKind.SERIES }
        assertEquals(2, show.episodes.size)
        assertEquals("tok", s.takeRequest().getHeader("X-Emby-Token"))
        s.shutdown()
    }

    @Test fun plexMapsLibrary() = runBlocking {
        val s = server(mapOf(
            "/library/sections/1/all" to """{"MediaContainer":{"totalSize":1,"Metadata":[{"ratingKey":"10","title":"Plex Film","year":2001,
                "Genre":[{"tag":"Action"}],"Guid":[{"id":"tmdb://77"}],"Media":[{"Part":[{"key":"/library/parts/1/file.mkv"}]}]}]}}""",
            "/library/sections" to """{"MediaContainer":{"Directory":[{"key":"1","type":"movie"},{"key":"2","type":"artist"}]}}""",
        ))
        val items = PlexSource(s.url("/").toString(), "pt", Http()).fetch()
        assertEquals(1, items.size)
        assertEquals(77L, items[0].tmdbId)
        assertTrue(items[0].destinations.single().url.endsWith("/library/parts/1/file.mkv"))
        s.shutdown()
    }

    @Test fun tmdbFillsMissingOnlyAndRejectsMismatchedTitles() = runBlocking {
        val s = server(mapOf(
            "/search/movie" to """{"results":[{"id":1,"title":"Other Thing"},{"id":2,"title":"Real Film"}]}""",
            "/movie/2" to """{"overview":"Plot","poster_path":"/p.jpg","genres":[{"name":"Drama"}],"original_language":"ja",
                "vote_average":7.5,"runtime":100,"release_date":"2020-05-01",
                "credits":{"cast":[{"name":"X"}],"crew":[{"job":"Director","name":"Y"}]}}""",
        ))
        val e = TmdbEnricher("key", Http(), s.url("/").toString().trimEnd('/'))
        val base = CatalogItem("jellyfin", "1", MediaKind.MOVIE, "Real Film", overview = "Mine")
        val r = e.enrich(base)
        assertEquals(2L, r.tmdbId)
        assertEquals("Mine", r.overview)
        assertEquals("https://image.tmdb.org/t/p/w500/p.jpg", r.posterUrl)
        assertEquals("Japanese", r.language)
        assertEquals("Y", r.director)
        val none = e.enrich(base.copy(title = "Unknown"))
        assertNull(none.tmdbId)
        s.shutdown()
    }

    @Test fun disabledSourcesNeverFetch() {
        for (d in KnownDisabledSources.all) {
            try { runBlocking { d.fetch() }; fail() } catch (e: SourceUnavailableException) { assertTrue(e.message!!.startsWith("Disabled")) }
        }
        assertEquals(setOf("redflix", "pvrplay"), KnownDisabledSources.all.map { it.id }.toSet())
    }

    @Test fun httpErrorsSurface() {
        val s = server(emptyMap())
        try { runBlocking { Http().get(s.url("/x").toString()) }; fail() } catch (e: HttpStatusException) { assertEquals(404, e.code) }
        s.shutdown()
    }
}
