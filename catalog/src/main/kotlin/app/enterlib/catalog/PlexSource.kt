package app.enterlib.catalog

import app.enterlib.core.*
import kotlinx.serialization.json.JsonObject

class PlexSource(serverUrl: String, private val token: String, private val http: Http) : CatalogSource {
    override val id = "plex"
    override val displayName = "Plex"
    private val root = serverUrl.trim().trimEnd('/')
    override val baseUrl: String = root
    override fun playbackHeaders() = mapOf("X-Plex-Token" to token)
    private val headers get() = mapOf("X-Plex-Token" to token, "Accept" to "application/json")

    override suspend fun fetch(): List<CatalogItem> {
        val sections = parse(http.get("$root/library/sections", headers)).sub("MediaContainer")?.arr("Directory") ?: return emptyList()
        val out = mutableListOf<CatalogItem>()
        for (s in sections.mapNotNull { it.obj() }) {
            val key = s.str("key") ?: continue
            val kind = when (s.str("type")) { "movie" -> MediaKind.MOVIE; "show" -> MediaKind.SERIES; else -> continue }
            var start = 0
            while (true) {
                val h = headers + mapOf("X-Plex-Container-Start" to "$start", "X-Plex-Container-Size" to "200")
                val mc = parse(http.get("$root/library/sections/$key/all?includeGuids=1", h)).sub("MediaContainer") ?: break
                val md = mc.arr("Metadata").mapNotNull { it.obj() }
                md.forEach { out += toItem(it, kind) }
                start += md.size
                if (md.isEmpty() || start >= (mc.int("totalSize") ?: 0)) break
            }
        }
        return out
    }

    private fun tags(o: JsonObject, k: String) = o.arr(k).mapNotNull { it.obj()?.str("tag") }
    private fun partDest(o: JsonObject): List<Destination> {
        val key = o.arr("Media").firstOrNull()?.obj()?.arr("Part")?.firstOrNull()?.obj()?.str("key") ?: return emptyList()
        return listOf(Destination("$root$key", DestinationKind.FILE, displayName))
    }

    private suspend fun toItem(o: JsonObject, kind: MediaKind): CatalogItem {
        val rk = o.str("ratingKey") ?: error("missing ratingKey")
        val tmdb = o.arr("Guid").mapNotNull { it.obj()?.str("id") }.firstOrNull { it.startsWith("tmdb://") }
            ?.removePrefix("tmdb://")?.toLongOrNull()
        return CatalogItem(
            sourceId = id, externalId = rk, kind = kind, title = o.str("title") ?: "Untitled",
            year = o.int("year"), overview = o.str("summary")?.ifBlank { null },
            posterUrl = o.str("thumb")?.let { "$root$it" }, backdropUrl = o.str("art")?.let { "$root$it" },
            genres = tags(o, "Genre"), country = tags(o, "Country").firstOrNull(),
            cast = tags(o, "Role").take(12), director = tags(o, "Director").firstOrNull(),
            rating = o.dbl("rating"), runtimeMinutes = o.long("duration")?.let { (it / 60_000L).toInt() }?.takeIf { it > 0 },
            tmdbId = tmdb, addedAtMillis = o.long("addedAt")?.let { it * 1000 },
            destinations = if (kind == MediaKind.MOVIE) partDest(o) else emptyList(),
            episodes = if (kind == MediaKind.SERIES) fetchEpisodes(rk) else emptyList(),
        )
    }

    private suspend fun fetchEpisodes(ratingKey: String): List<EpisodeItem> =
        parse(http.get("$root/library/metadata/$ratingKey/allLeaves", headers)).sub("MediaContainer")
            ?.arr("Metadata").orEmpty().mapNotNull { it.obj() }.mapNotNull { e ->
                EpisodeItem(
                    externalId = e.str("ratingKey") ?: return@mapNotNull null,
                    season = e.int("parentIndex") ?: return@mapNotNull null,
                    episode = e.int("index") ?: return@mapNotNull null,
                    title = e.str("title"), overview = e.str("summary")?.ifBlank { null },
                    runtimeMinutes = e.long("duration")?.let { (it / 60_000L).toInt() }?.takeIf { it > 0 },
                    stillUrl = e.str("thumb")?.let { "$root$it" }, airDate = e.str("originallyAvailableAt"),
                    destinations = partDest(e),
                )
            }
}
