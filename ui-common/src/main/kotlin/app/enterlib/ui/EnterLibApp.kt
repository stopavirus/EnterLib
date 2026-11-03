package app.enterlib.ui

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import app.enterlib.data.EnterLibGraph
import app.enterlib.data.SyncScheduler
import okhttp3.OkHttpClient

open class EnterLibApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        val g = EnterLibGraph.get(this)
        SyncScheduler.apply(this, g.settings.state.value.syncHours)
    }

    /** Adds per-server auth headers to artwork requests without persisting tokens in the database. */
    override fun newImageLoader(): ImageLoader {
        val g = EnterLibGraph.get(this)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            val h = g.headersForUrl(req.url.toString())
            chain.proceed(if (h.isEmpty()) req else req.newBuilder().apply { h.forEach { (k, v) -> header(k, v) } }.build())
        }.build()
        return ImageLoader.Builder(this).okHttpClient(client).crossfade(true).build()
    }
}
