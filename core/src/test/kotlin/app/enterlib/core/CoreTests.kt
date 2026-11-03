package app.enterlib.core

import org.junit.Assert.*
import org.junit.Test

class CoreTests {
    @Test fun normalizeIgnoresCaseAccentsArticles() {
        assertEquals("amelie", Matching.normalizeTitle("The Amélie"))
        assertEquals("fast and furious", Matching.normalizeTitle("Fast & Furious"))
    }

    @Test fun sameTitleYearGivesSameKey() {
        assertEquals(
            Matching.matchKey(MediaKind.MOVIE, "The Matrix", 1999),
            Matching.matchKey(MediaKind.MOVIE, "matrix", 1999),
        )
        assertNotEquals(
            Matching.matchKey(MediaKind.MOVIE, "Dune", 1984),
            Matching.matchKey(MediaKind.MOVIE, "Dune", 2021),
        )
        assertNotEquals(
            Matching.matchKey(MediaKind.MOVIE, "Fargo", 1996),
            Matching.matchKey(MediaKind.SERIES, "Fargo", 1996),
        )
    }

    @Test fun splitTitleYear() {
        assertEquals("Heat" to 1995, Matching.splitTitleYear("Heat (1995)"))
        assertEquals("Heat" to null, Matching.splitTitleYear("Heat"))
    }

    @Test fun classifier() {
        assertEquals(Category.DOCUMENTARY, Classifier.categorize(listOf("Documentary"), null))
        assertEquals(Category.ANIME, Classifier.categorize(listOf("Animation"), "Japanese"))
        assertEquals(Category.ANIMATION, Classifier.categorize(listOf("Animation"), "English"))
        assertEquals(Category.KIDS, Classifier.categorize(listOf("Animation", "Family"), "English"))
        assertEquals(Category.GENERAL, Classifier.categorize(listOf("Drama"), null))
    }

    @Test fun urlValidation() {
        assertTrue(UrlValidator.isSafeHttpUrl("https://example.com/a.m3u8"))
        assertTrue(UrlValidator.isSafeHttpUrl("http://192.168.1.5:8096"))
        assertFalse(UrlValidator.isSafeHttpUrl("javascript:alert(1)"))
        assertFalse(UrlValidator.isSafeHttpUrl("file:///etc/passwd"))
        assertFalse(UrlValidator.isSafeHttpUrl("https://user:pw@example.com"))
        assertFalse(UrlValidator.isSafeHttpUrl("not a url"))
        assertFalse(UrlValidator.isSafeHttpUrl(null))
    }

    private val playlist = """
        #EXTM3U
        #EXTINF:-1 tvg-logo="https://img.example/a.jpg" group-title="Drama",Some Movie (2020)
        https://cdn.example/movie.m3u8
        #EXTINF:-1 group-title="Comedy",Show Name S01E02 The Second
        https://cdn.example/s1e2.mp4
        #EXTINF:-1,Show Name S01E01 Pilot
        https://cdn.example/s1e1.mp4
        #EXTINF:-1,Bad Entry
        javascript:alert(1)
    """.trimIndent()

    @Test fun m3uParsesMoviesAndSeries() {
        val entries = M3uParser.parseEntries(playlist)
        assertEquals(3, entries.size)
        val items = M3uParser.toItems("m3u", entries)
        val movie = items.single { it.kind == MediaKind.MOVIE }
        assertEquals("Some Movie", movie.title)
        assertEquals(2020, movie.year)
        assertEquals(DestinationKind.HLS, movie.destinations.single().kind)
        val show = items.single { it.kind == MediaKind.SERIES }
        assertEquals("Show Name", show.title)
        assertEquals(2, show.episodes.size)
        assertEquals(setOf(1, 2), show.episodes.map { it.episode }.toSet())
    }

    @Test fun m3uDoesNotInventContent() {
        assertTrue(M3uParser.toItems("m3u", M3uParser.parseEntries("")).isEmpty())
        assertTrue(M3uParser.toItems("m3u", M3uParser.parseEntries("#EXTM3U\n#EXTINF:-1,NoUrl\n")).isEmpty())
    }
}
