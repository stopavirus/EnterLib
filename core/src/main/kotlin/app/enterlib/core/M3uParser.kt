package app.enterlib.core

/** Parses user-supplied M3U/M3U8 playlists into catalogue items. No scraping, no guessing metadata. */
object M3uParser {
    data class Entry(val title: String, val url: String, val logo: String?, val group: String?)

    private val attr = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")
    private val episode = Regex("^(.*?)[\\s._-]+[Ss](\\d{1,2})[\\s._-]*[Ee](\\d{1,3})\\b[\\s._-]*(.*)$")

    fun parseEntries(text: String): List<Entry> {
        val out = mutableListOf<Entry>()
        var pending: Entry? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                val title = line.substringAfterLast(',', "").trim()
                pending = Entry(title, "", attrs["tvg-logo"], attrs["group-title"]?.takeIf { it.isNotBlank() })
            } else if (!line.startsWith("#")) {
                val p = pending
                if (p != null && p.title.isNotBlank() && UrlValidator.isSafeHttpUrl(line)) out += p.copy(url = line)
                pending = null
            }
        }
        return out
    }

    fun kindOf(url: String): DestinationKind {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".m3u8") -> DestinationKind.HLS
            path.endsWith(".mpd") -> DestinationKind.DASH
            else -> DestinationKind.FILE
        }
    }

    fun toItems(sourceId: String, entries: List<Entry>): List<CatalogItem> {
        val movies = mutableListOf<CatalogItem>()
        val series = LinkedHashMap<String, MutableList<Pair<Entry, Triple<String, Int, Int>>>>()
        for (e in entries) {
            val m = episode.matchEntire(e.title)
            if (m != null) {
                val name = m.groupValues[1].trim()
                series.getOrPut(name) { mutableListOf() } +=
                    e to Triple(m.groupValues[4].trim(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            } else {
                val (title, year) = Matching.splitTitleYear(e.title)
                movies += CatalogItem(
                    sourceId = sourceId, externalId = e.url, kind = MediaKind.MOVIE, title = title, year = year,
                    posterUrl = e.logo?.takeIf { UrlValidator.isSafeHttpUrl(it) },
                    genres = listOfNotNull(e.group),
                    destinations = listOf(Destination(e.url, kindOf(e.url))),
                )
            }
        }
        val shows = series.map { (name, eps) ->
            val episodes = eps.groupBy { it.second.second to it.second.third }.map { (k, list) ->
                val first = list.first()
                EpisodeItem(
                    externalId = first.first.url, season = k.first, episode = k.second,
                    title = first.second.first.ifBlank { null },
                    destinations = list.map { Destination(it.first.url, kindOf(it.first.url)) }.distinct(),
                )
            }
            val head = eps.first().first
            CatalogItem(
                sourceId = sourceId, externalId = "m3u-series:" + Matching.normalizeTitle(name), kind = MediaKind.SERIES,
                title = name, posterUrl = head.logo?.takeIf { UrlValidator.isSafeHttpUrl(it) },
                genres = listOfNotNull(head.group), episodes = episodes,
            )
        }
        return movies + shows
    }
}
