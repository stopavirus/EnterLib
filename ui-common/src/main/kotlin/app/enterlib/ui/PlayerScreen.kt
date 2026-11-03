package app.enterlib.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import app.enterlib.core.DestinationKind
import app.enterlib.data.EnterLibGraph
import app.enterlib.data.PlaybackTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** In-app playback of authorised HLS / DASH / progressive URLs. Subtitle and audio selection use the built-in track menu. */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(contentId: Long, episodeId: Long, forceLandscape: Boolean, onNextEpisode: (Long) -> Unit, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val graph = remember { EnterLibGraph.get(ctx) }
    var target by remember(contentId, episodeId) { mutableStateOf<PlaybackTarget?>(null) }
    LaunchedEffect(contentId, episodeId) { target = graph.playback.resolve(contentId, episodeId) }
    BackHandler { onExit() }

    DisposableEffect(forceLandscape) {
        val act = ctx.findActivity()
        val old = act?.requestedOrientation
        if (forceLandscape) act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = act?.window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (old != null) act?.requestedOrientation = old
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val t = target
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (t) {
            null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is PlaybackTarget.InApp -> Surface0(t, graph, contentId, episodeId, onNextEpisode, onExit)
            else -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No authorised in-app playback destination.", color = Color.White)
                Button(onClick = onExit, modifier = Modifier.padding(top = 12.dp)) { Text("Back") }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun BoxScope.Surface0(
    t: PlaybackTarget.InApp, graph: EnterLibGraph, contentId: Long, episodeId: Long,
    onNextEpisode: (Long) -> Unit, onExit: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember(t.url) { mutableStateOf<String?>(null) }
    val exo = remember(t.url) {
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(graph.headersForUrl(t.url))
        ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
    }

    fun load() {
        scope.launch {
            val p = graph.dao.getProgress(contentId, episodeId)
            val start = if (p != null && !p.completed && p.positionMs > 10_000) p.positionMs else 0L
            val mime = when (t.kind) { DestinationKind.HLS -> MimeTypes.APPLICATION_M3U8; DestinationKind.DASH -> MimeTypes.APPLICATION_MPD; else -> null }
            exo.setMediaItem(MediaItem.Builder().setUri(t.url).apply { mime?.let { setMimeType(it) } }.build(), start)
            error = null
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { error = "Playback error (${e.errorCodeName}). Check your connection or server." }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) scope.launch {
                    graph.playback.saveProgress(contentId, episodeId, exo.duration.coerceAtLeast(0), exo.duration.coerceAtLeast(0), true)
                    val next = graph.playback.nextEpisodeId(contentId, episodeId)
                    if (next != null) onNextEpisode(next) else onExit()
                }
            }
        }
        exo.addListener(listener)
        load()
        onDispose {
            exo.removeListener(listener)
            val pos = exo.currentPosition
            val dur = exo.duration
            exo.release()
            graph.appScope.launch { graph.playback.saveProgress(contentId, episodeId, pos, if (dur > 0) dur else 0, false) }
        }
    }

    LaunchedEffect(exo) {
        while (true) {
            delay(5_000)
            if (exo.isPlaying) graph.playback.saveProgress(contentId, episodeId, exo.currentPosition, exo.duration.coerceAtLeast(0), false)
        }
    }

    AndroidView(
        factory = { c -> PlayerView(c).apply { player = exo; setShowSubtitleButton(true); controllerShowTimeoutMs = 4000; keepScreenOn = true } },
        modifier = Modifier.fillMaxSize(),
    )
    error?.let {
        Column(Modifier.align(Alignment.Center).background(Color(0xCC000000)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(it, color = Color.White)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { load() }) { Text("Retry") }
                OutlinedButton(onClick = onExit) { Text("Back") }
            }
        }
    }
}
