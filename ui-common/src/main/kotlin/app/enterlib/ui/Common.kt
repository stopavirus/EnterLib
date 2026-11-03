package app.enterlib.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.enterlib.core.UrlValidator
import app.enterlib.data.*
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** Sizing differences between the phone/tablet and TV layouts. */
class UiSpec(val tv: Boolean) {
    val cardW = if (tv) 170.dp else 118.dp
    val cardH = cardW * 1.5f
    val pad = if (tv) 48.dp else 16.dp
    val heroH = if (tv) 380.dp else 300.dp
    val still = if (tv) 200.dp else 130.dp
}

@Composable
inline fun <reified T : ViewModel> graphViewModel(key: String? = null, crossinline build: (EnterLibGraph) -> T): T {
    val ctx = LocalContext.current.applicationContext
    return viewModel(key = key, factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = build(EnterLibGraph.get(ctx)) as V
    })
}

fun openExternal(ctx: Context, url: String) {
    if (!UrlValidator.isSafeHttpUrl(url)) { Toast.makeText(ctx, "Blocked an unsafe link", Toast.LENGTH_SHORT).show(); return }
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "No app can open this link", Toast.LENGTH_SHORT).show()
    }
}

/** Resolves an authorised destination, then either plays in-app or opens the external viewing link. */
@Composable
fun rememberPlayAction(onInApp: (Long, Long) -> Unit): (Long, Long) -> Unit {
    val ctx = LocalContext.current
    val graph = remember { EnterLibGraph.get(ctx) }
    val scope = rememberCoroutineScope()
    return { cid, eid ->
        scope.launch {
            when (val t = graph.playback.resolve(cid, eid)) {
                is PlaybackTarget.InApp -> onInApp(cid, eid)
                is PlaybackTarget.External -> openExternal(ctx, t.url)
                PlaybackTarget.None -> Toast.makeText(ctx, "No authorised playback destination", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

val categoryLabels = mapOf(
    "DOCUMENTARY" to "Documentaries", "ANIME" to "Anime", "ANIMATION" to "Animation",
    "KIDS" to "Kids & Family", "SHORT" to "Short Films", "MUSIC" to "Concerts & Music",
)

@Composable
fun PosterCard(item: ContentEntity, spec: UiSpec, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "focusScale")
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier.width(spec.cardW).scale(scale).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick),
    ) {
        Box(
            Modifier.size(spec.cardW, spec.cardH).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
        ) {
            if (item.posterUrl != null) {
                AsyncImage(item.posterUrl, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(item.title, Modifier.align(Alignment.Center).padding(8.dp), style = MaterialTheme.typography.labelLarge)
            }
            if (item.isNew()) {
                Text(
                    "NEW", Modifier.align(Alignment.TopStart).padding(6.dp)
                        .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.Black,
                )
            }
        }
        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp))
        Text(
            listOfNotNull(item.year?.toString(), if (item.isSeries()) "Series" else "Movie").joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ContentRow(title: String, items: List<ContentEntity>, spec: UiSpec, onOpen: (Long) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, Modifier.padding(horizontal = spec.pad, vertical = 6.dp), style = MaterialTheme.typography.titleLarge)
        LazyRow(contentPadding = PaddingValues(horizontal = spec.pad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.id }) { PosterCard(it, spec, { onOpen(it.id) }) }
        }
    }
}

@Composable
fun EmptyLibrary(onSettings: () -> Unit, onSync: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your library is empty", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Add a source you are authorised to use (Jellyfin, Plex, Emby or an M3U playlist) in Settings, then sync. " +
                "EnterLib never shows placeholder titles.",
            Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSettings) { Text("Open Settings") }
            OutlinedButton(onClick = onSync) { Text("Sync now") }
        }
    }
}
