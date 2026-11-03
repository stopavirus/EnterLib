package app.enterlib.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.enterlib.catalog.KnownDisabledSources
import app.enterlib.data.*
import java.text.DateFormat
import java.util.Date

@Composable
private fun Field(label: String, value: String, secret: Boolean = false, single: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = single, modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}

@Composable
private fun ServerBlock(title: String, c: ServerConfig, hint: String, onChange: (ServerConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(c.enabled, { onChange(c.copy(enabled = it)) }); Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        if (c.enabled) {
            Field("Server URL ($hint)", c.url) { onChange(c.copy(url = it)) }
            Field("API key / token", c.token, secret = true) { onChange(c.copy(token = it)) }
            if (title != "Plex") Field("User ID (optional)", c.userId) { onChange(c.copy(userId = it)) }
        }
    }
}

@Composable
fun SettingsScreen(spec: UiSpec, showBackup: Boolean) {
    val vm = graphViewModel { SettingsViewModel(it) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var d by remember(ui.settings) { mutableStateOf(ui.settings) }
    val cr = LocalContext.current.contentResolver
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u -> u?.let { vm.exportTo(cr, it) } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u?.let { vm.importFrom(cr, it) } }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(spec.pad), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }
        item {
            Text("Automatic sync", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(0 to "Manual only", 6 to "Every 6 h", 12 to "Every 12 h", 24 to "Every 24 h")) { (h, l) ->
                    FilterChip(d.syncHours == h, { d = d.copy(syncHours = h) }, { Text(l) })
                }
            }
            Text("Background sync is scheduled by Android and may be delayed by battery optimisation; it is not instant.",
                style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("system" to "System", "dark" to "Dark", "light" to "Light")) { (k, l) ->
                    FilterChip(d.theme == k, { d = d.copy(theme = k) }, { Text(l) })
                }
            }
        }
        item { Text("Sources", style = MaterialTheme.typography.titleLarge) }
        item { ServerBlock("Jellyfin", d.jellyfin, "http://host:8096") { d = d.copy(jellyfin = it) } }
        item { ServerBlock("Emby", d.emby, "http://host:8096") { d = d.copy(emby = it) } }
        item { ServerBlock("Plex", d.plex, "http://host:32400") { d = d.copy(plex = it) } }
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(d.m3uEnabled, { d = d.copy(m3uEnabled = it) }); Spacer(Modifier.width(8.dp))
                Text("M3U / HLS playlists", style = MaterialTheme.typography.titleMedium)
            }
            if (d.m3uEnabled) Field("Playlist URLs (one per line; only content you have rights to)", d.m3uUrls, single = false) { d = d.copy(m3uUrls = it) }
        }
        item { Field("TMDB API key (optional metadata; read access token also works)", d.tmdbKey, secret = true) { d = d.copy(tmdbKey = it) } }
        item {
            Text("Unavailable sources", style = MaterialTheme.typography.titleMedium)
            KnownDisabledSources.all.forEach {
                Text("${it.displayName}: ${it.reason}", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.save(d) }) { Text("Save") }
                OutlinedButton(onClick = { vm.save(d); vm.syncNow() }, enabled = !syncing) { Text(if (syncing) "Syncing…" else "Save & sync now") }
            }
            if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            message?.let { Text(it, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium) }
        }
        if (showBackup) item {
            Text("Backup (watchlist & progress)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { export.launch("enterlib-backup.json") }) { Text("Export") }
                OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import") }
            }
        }
        item { Text("Sync history", style = MaterialTheme.typography.titleMedium) }
        if (ui.history.isEmpty()) item { Text("No syncs yet.") }
        items(ui.history, key = { it.id }) { h ->
            Text(
                "${fmt.format(Date(h.finishedAt))} · ${SourceNames.display(h.sourceId)} · " +
                    (if (h.success) "ok +${h.added} new, ${h.updated} updated, ${h.newEpisodes} episodes, ${h.removed} removed" else "failed") +
                    (h.error?.let { " — $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Text(
                "This product uses the TMDB API but is not endorsed or certified by TMDB. EnterLib contains no advertising SDKs, " +
                    "no analytics and no tracking. Data stays on this device.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
