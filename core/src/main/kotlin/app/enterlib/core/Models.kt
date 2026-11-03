package app.enterlib.core

enum class MediaKind { MOVIE, SERIES }

enum class Category { GENERAL, DOCUMENTARY, ANIME, ANIMATION, KIDS, SHORT, MUSIC }

enum class DestinationKind { HLS, DASH, FILE, EXTERNAL }

/** A place the user is authorised to watch the title. Never contains credentials. */
data class Destination(val url: String, val kind: DestinationKind, val label: String? = null)

data class EpisodeItem(
    val externalId: String,
    val season: Int,
    val episode: Int,
    val title: String? = null,
    val overview: String? = null,
    val runtimeMinutes: Int? = null,
    val stillUrl: String? = null,
    val airDate: String? = null,
    val destinations: List<Destination> = emptyList(),
)

data class CatalogItem(
    val sourceId: String,
    val externalId: String,
    val kind: MediaKind,
    val title: String,
    val year: Int? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val genres: List<String> = emptyList(),
    val language: String? = null,
    val country: String? = null,
    val cast: List<String> = emptyList(),
    val director: String? = null,
    val rating: Double? = null,
    val runtimeMinutes: Int? = null,
    val tmdbId: Long? = null,
    val addedAtMillis: Long? = null,
    val destinations: List<Destination> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList(),
)

class SourceUnavailableException(message: String) : Exception(message)
