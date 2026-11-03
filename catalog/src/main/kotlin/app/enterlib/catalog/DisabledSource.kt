package app.enterlib.catalog

import app.enterlib.core.*

/** Placeholder for sources whose permitted integration method has not been verified. Never fetches. */
class DisabledSource(override val id: String, override val displayName: String, val reason: String) : CatalogSource {
    override val baseUrl: String? = null
    override suspend fun fetch(): List<CatalogItem> = throw SourceUnavailableException(reason)
}

object KnownDisabledSources {
    const val REASON = "Disabled: no official API, licence or terms permitting integration has been verified. " +
        "No scraping or stream extraction is implemented."
    val all = listOf(
        DisabledSource("redflix", "Redflix (redflix.club)", REASON),
        DisabledSource("pvrplay", "PVRplay (pvrplay.site)", REASON),
    )
}
