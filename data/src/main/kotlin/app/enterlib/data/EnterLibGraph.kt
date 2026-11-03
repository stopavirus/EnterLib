package app.enterlib.data

import android.content.Context
import androidx.room.Room
import app.enterlib.catalog.Http
import app.enterlib.catalog.TmdbEnricher
import app.enterlib.core.CatalogSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Minimal service locator shared by both apps (no DI framework, fewer moving parts). */
class EnterLibGraph private constructor(val appContext: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db: EnterLibDatabase = Room.databaseBuilder(appContext, EnterLibDatabase::class.java, "enterlib.db")
        .addMigrations(*ALL_MIGRATIONS).build()
    val dao = db.dao()
    val settings = SettingsStore(appContext)
    val repo = ContentRepository(db)
    val playback = PlaybackService(dao)
    val backup = BackupManager(dao)
    private val http = Http()

    private var cacheKey: AppSettings? = null
    private var cache: List<CatalogSource> = emptyList()
    @Synchronized fun activeSources(): List<CatalogSource> {
        val s = settings.state.value
        if (s != cacheKey) { cache = SourceFactory.build(s, http); cacheKey = s }
        return cache
    }

    val sync = SyncEngine(repo, dao, { activeSources() }, {
        settings.state.value.tmdbKey.takeIf { it.isNotBlank() }?.let { TmdbEnricher(it, http) }
    })

    /** Auth headers for playback and artwork requests that target a configured server. */
    fun headersForUrl(url: String): Map<String, String> =
        activeSources().firstOrNull { it.baseUrl != null && url.startsWith(it.baseUrl!!) }?.playbackHeaders() ?: emptyMap()

    companion object {
        @Volatile private var inst: EnterLibGraph? = null
        fun get(ctx: Context): EnterLibGraph = inst ?: synchronized(this) {
            inst ?: EnterLibGraph(ctx.applicationContext).also { inst = it }
        }
    }
}
