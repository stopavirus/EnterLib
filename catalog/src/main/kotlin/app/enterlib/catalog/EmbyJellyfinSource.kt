package app.enterlib.catalog

import app.enterlib.core.*
import java.time.Instant
import kotlinx.serialization.json.contentOrNull

/** Jellyfin and Emby share a near-identical REST API; the only differences are the path prefix and labels. */
class EmbyJellyfinSource(
    override val id: String,
    override val displayName: String,
    serverUrl: String,
    private val token: String,
    private val userId: String,
    private val apiPrefix: String,
    private val http: Http,
) : CatalogSource {
    private val root = serverUrl.trim().trimEnd('/')
    override val baseUrl: String = root
    override fun playbackHeaders() = mapOf("X-Emby-Token" to token)

    companion object {
        fun jellyfin(url: String, token: String, userId: String, http: Http) =
            EmbyJellyfinSource("jellyfin", "Jellyfin", url, token, userId, "", http)
        fun emby(url: String, token: String, userId: String, http: Http) =
            EmbyJellyfinSource("emby", "Emby", url, token, userId, "/emby", http)
    }

    private val itemsPath get() = if (userId.isBlank()) "/Items" else "/Users/$userId/Items"
    private val userQuery get() = if (userId.isBlank()) "" else "&UserId=$userId"

    override suspend fun fetch(): List<CatalogItem> {
        val out = mutableListOf<CatalogItem>()
        var start = 0
        while (true) {
            val url = "$root$apiPrefix$itemsPath?Recursive=true&IncludeItemTypes=Movie,Series" +
                "&Fields=Overview,Genres,People,ProviderIds,DateCreated,ProductionLocations" +
                "&SortBy=DateCreated&SortOrder=Descending&StartIndex=$start&Limit=200"
            val o = parse(http.get(url, playbackHeaders()))
            val arr = o.arr("Items")
            for (el in arr) el.obj()?.let { out += toItem(it) }
            start += arr.size
            if (arr.isEmpty() || start >= (o.int("TotalRecordCount") ?: 0)) break
        }
        return out
    }

    private suspend fun toItem(o: kotlinx.serialization.json.JsonObject): CatalogItem {
        val itemId = o.str("Id") ?: error("missing id")
        val isSeries = o.str("Type") == "Series"
        val people = o.arr("People").mapNotNull { it.obj() }
        val posterTag = o.sub("ImageTags")?.str("Primary")
        val hasBackdrop = o.arr("BackdropImageTags").isNotEmpty()
        val base = "$root$apiPrefix/Items/$itemId"
        return CatalogItem(
            sourceId = id, externalId = itemId,
            kind = if (isSeries) MediaKind.SERIES else MediaKind.MOVIE,
            title = o.str("Name") ?: "Untitled",
            year = o.int("ProductionYear"),
            overview = o.str("Overview")?.ifBlank { null },
            posterUrl = if (posterTag != null) "$base/Images/Primary?maxHeight=720" else null,
            backdropUrl = if (hasBackdrop) "$base/Images/Backdrop/0?maxWidth=1280" else null,
            genres = o.arr("Genres").mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull },
            country = o.arr("ProductionLocations").firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull },
            cast = people.filter { it.str("Type") == "Actor" }.mapNotNull { it.str("Name") }.take(12),
            director = people.firstOrNull { it.str("Type") == "Director" }?.str("Name"),
            rating = o.dbl("CommunityRating"),
            runtimeMinutes = o.long("RunTimeTicks")?.let { (it / 600_000_000L).toInt() }?.takeIf { it > 0 },
            tmdbId = o.sub("ProviderIds")?.str("Tmdb")?.toLongOrNull(),
            addedAtMillis = o.str("DateCreated")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
            destinations = if (isSeries) emptyList() else listOf(stream(itemId)),
            episodes = if (isSeries) fetchEpisodes(itemId) else emptyList(),
        )
    }

    private fun stream(itemId: String) =
        Destination("$root$apiPrefix/Videos/$itemId/stream?static=true&MediaSourceId=$itemId", DestinationKind.FILE, displayName)

    // A failure here intentionally fails the whole sync so nothing gets pruned on a transient error.
    private suspend fun fetchEpisodes(seriesId: String): List<EpisodeItem> {
        val url = "$root$apiPrefix/Shows/$seriesId/Episodes?Fields=Overview,DateCreated$userQuery"
        return parse(http.get(url, playbackHeaders())).arr("Items").mapNotNull { it.obj() }.mapNotNull { e ->
            val eid = e.str("Id") ?: return@mapNotNull null
            val season = e.int("ParentIndexNumber") ?: return@mapNotNull null
            val number = e.int("IndexNumber") ?: return@mapNotNull null
            EpisodeItem(
                externalId = eid, season = season, episode = number,
                title = e.str("Name"), overview = e.str("Overview")?.ifBlank { null },
                runtimeMinutes = e.long("RunTimeTicks")?.let { (it / 600_000_000L).toInt() }?.takeIf { it > 0 },
                stillUrl = if (e.sub("ImageTags")?.str("Primary") != null) "$root$apiPrefix/Items/$eid/Images/Primary?maxWidth=480" else null,
                airDate = e.str("PremiereDate")?.take(10),
                destinations = listOf(stream(eid)),
            )
        }
    }
}
