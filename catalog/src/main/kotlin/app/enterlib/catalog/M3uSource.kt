package app.enterlib.catalog

import app.enterlib.core.*

/** User-supplied playlists only. Headers are never attached; playlists must be self-contained URLs. */
class M3uSource(private val urls: List<String>, private val http: Http) : CatalogSource {
    override val id = "m3u"
    override val displayName = "M3U playlist"
    override val baseUrl: String? = null
    override suspend fun fetch(): List<CatalogItem> =
        urls.flatMap { M3uParser.toItems(id, M3uParser.parseEntries(http.get(it))) }
}
