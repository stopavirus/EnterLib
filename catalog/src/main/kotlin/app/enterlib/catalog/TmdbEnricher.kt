package app.enterlib.catalog

import app.enterlib.core.*

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder
import java.util.Locale

/**
 * Fills missing metadata from TMDB. Metadata only: TMDB provides no playback.
 * Results are accepted only when the title matches exactly after normalisation, so nothing is guessed.
 * "This product uses the TMDB API but is not endorsed or certified by TMDB."
 */
class TmdbEnricher(
    private val apiKey: String,
    private val http: Http,
    private val apiBase: String = "https://api.themoviedb.org/3",
) {
    private val bearer = apiKey.length > 40
    private fun headers() = if (bearer) mapOf("Authorization" to "Bearer $apiKey") else emptyMap()
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun url(path: String, params: Map<String, String> = emptyMap()): String {
        val all = if (bearer) params else params + ("api_key" to apiKey)
        return "$apiBase$path?" + all.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
    }

    suspend fun enrich(item: CatalogItem): CatalogItem {
        val movie = item.kind == MediaKind.MOVIE
        val type = if (movie) "movie" else "tv"
        var id = item.tmdbId
        if (id == null) {
            val params = mutableMapOf("query" to item.title)
            item.year?.let { params[if (movie) "year" else "first_air_date_year"] = it.toString() }
            val wanted = Matching.normalizeTitle(item.title)
            id = parse(http.get(url("/search/$type", params), headers())).arr("results").mapNotNull { it.obj() }
                .firstOrNull { r ->
                    listOf(r.str("title"), r.str("name"), r.str("original_title"), r.str("original_name"))
                        .any { it != null && Matching.normalizeTitle(it) == wanted }
                }?.long("id")
        }
        if (id == null) return item
        val d = parse(http.get(url("/$type/$id", mapOf("append_to_response" to "credits")), headers()))
        val credits = d.sub("credits")
        val genres = d.arr("genres").mapNotNull { it.obj()?.str("name") }
        val language = d.str("original_language")?.let {
            Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH).takeIf { n -> n.isNotBlank() && n != it }
        }
        val country = if (movie) d.arr("production_countries").firstOrNull()?.obj()?.str("name")
        else d.arr("origin_country").firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?.let { Locale("", it).getDisplayCountry(Locale.ENGLISH) }
        val runtime = if (movie) d.int("runtime") else d.arr("episode_run_time").firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }
        val director = if (movie) credits?.arr("crew").orEmpty().mapNotNull { it.obj() }.firstOrNull { it.str("job") == "Director" }?.str("name")
        else d.arr("created_by").firstOrNull()?.obj()?.str("name")
        val cast = credits?.arr("cast").orEmpty().mapNotNull { it.obj()?.str("name") }.take(10)
        fun img(k: String, size: String) = d.str(k)?.let { "https://image.tmdb.org/t/p/$size$it" }
        return item.copy(
            tmdbId = id,
            overview = item.overview ?: d.str("overview")?.ifBlank { null },
            posterUrl = item.posterUrl ?: img("poster_path", "w500"),
            backdropUrl = item.backdropUrl ?: img("backdrop_path", "w1280"),
            genres = (item.genres + genres).distinct(),
            language = item.language ?: language,
            country = item.country ?: country?.ifBlank { null },
            cast = item.cast.ifEmpty { cast },
            director = item.director ?: director,
            rating = item.rating ?: d.dbl("vote_average")?.takeIf { it > 0 },
            runtimeMinutes = item.runtimeMinutes ?: runtime?.takeIf { it > 0 },
            year = item.year ?: (d.str("release_date") ?: d.str("first_air_date"))?.take(4)?.toIntOrNull(),
        )
    }
}
