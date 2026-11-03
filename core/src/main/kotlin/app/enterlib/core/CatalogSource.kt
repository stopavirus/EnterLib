package app.enterlib.core

/**
 * A pluggable, authorised catalogue source. Implementations must only return content the
 * user is permitted to access (own server, licensed playlist, official API).
 */
interface CatalogSource {
    val id: String
    val displayName: String
    /** Base URL used to attach auth headers to playback/image requests for this source. */
    val baseUrl: String?
    suspend fun fetch(): List<CatalogItem>
    /** Headers needed at play/image time. Kept out of the database on purpose. */
    fun playbackHeaders(): Map<String, String> = emptyMap()
}
