package app.enterlib.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.enterlib.catalog.*
import app.enterlib.core.CatalogSource
import app.enterlib.core.UrlValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ServerConfig(val enabled: Boolean = false, val url: String = "", val token: String = "", val userId: String = "")

data class AppSettings(
    val syncHours: Int = 24,
    val theme: String = "system",
    val tmdbKey: String = "",
    val jellyfin: ServerConfig = ServerConfig(),
    val emby: ServerConfig = ServerConfig(),
    val plex: ServerConfig = ServerConfig(),
    val m3uEnabled: Boolean = false,
    val m3uUrls: String = "",
)

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "enterlib_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        context.getSharedPreferences("enterlib_plain", Context.MODE_PRIVATE)
    }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state

    private fun server(p: String) = ServerConfig(
        prefs.getBoolean("$p.enabled", false), prefs.getString("$p.url", "") ?: "",
        prefs.getString("$p.token", "") ?: "", prefs.getString("$p.user", "") ?: "")

    private fun load() = AppSettings(
        syncHours = prefs.getInt("syncHours", 24), theme = prefs.getString("theme", "system") ?: "system",
        tmdbKey = prefs.getString("tmdbKey", "") ?: "",
        jellyfin = server("jellyfin"), emby = server("emby"), plex = server("plex"),
        m3uEnabled = prefs.getBoolean("m3u.enabled", false), m3uUrls = prefs.getString("m3u.urls", "") ?: "")

    fun save(s: AppSettings) {
        val e = prefs.edit()
        e.putInt("syncHours", s.syncHours).putString("theme", s.theme).putString("tmdbKey", s.tmdbKey.trim())
        for ((p, c) in listOf("jellyfin" to s.jellyfin, "emby" to s.emby, "plex" to s.plex)) {
            e.putBoolean("$p.enabled", c.enabled).putString("$p.url", c.url.trim())
                .putString("$p.token", c.token.trim()).putString("$p.user", c.userId.trim())
        }
        e.putBoolean("m3u.enabled", s.m3uEnabled).putString("m3u.urls", s.m3uUrls).apply()
        _state.value = s
    }
}

object SourceFactory {
    fun build(s: AppSettings, http: Http): List<CatalogSource> {
        val l = mutableListOf<CatalogSource>()
        fun ok(c: ServerConfig) = c.enabled && UrlValidator.isSafeHttpUrl(c.url) && c.token.isNotBlank()
        if (ok(s.jellyfin)) l += EmbyJellyfinSource.jellyfin(s.jellyfin.url, s.jellyfin.token, s.jellyfin.userId, http)
        if (ok(s.emby)) l += EmbyJellyfinSource.emby(s.emby.url, s.emby.token, s.emby.userId, http)
        if (ok(s.plex)) l += PlexSource(s.plex.url, s.plex.token, http)
        if (s.m3uEnabled) {
            val urls = s.m3uUrls.lines().map { it.trim() }.filter { UrlValidator.isSafeHttpUrl(it) }
            if (urls.isNotEmpty()) l += M3uSource(urls, http)
        }
        return l
    }
}

object SourceNames {
    fun display(id: String) = when (id) {
        "jellyfin" -> "Jellyfin"; "emby" -> "Emby"; "plex" -> "Plex"; "m3u" -> "M3U playlist"; else -> id
    }
}
