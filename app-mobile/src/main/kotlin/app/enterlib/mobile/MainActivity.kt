package app.enterlib.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.enterlib.data.EnterLibGraph
import app.enterlib.ui.*

private data class Tab(val route: String, val label: String, val icon: ImageVector)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = EnterLibGraph.get(this)
        setContent {
            val settings by graph.settings.state.collectAsStateWithLifecycle()
            EnterLibTheme(settings.theme) { MobileRoot() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(
        Tab(Routes.HOME, "Home", Icons.Filled.Home), Tab(Routes.MOVIES, "Movies", Icons.Filled.PlayArrow),
        Tab(Routes.SERIES, "Series", Icons.Filled.List), Tab(Routes.SEARCH, "Search", Icons.Filled.Search),
        Tab(Routes.LIBRARY, "Library", Icons.Filled.Favorite),
    )
    val top = route in Routes.topLevel
    val fullscreen = route == Routes.PLAYER
    Scaffold(
        topBar = {
            if (top) CenterAlignedTopAppBar(
                title = { Text("EnterLib") },
                actions = { IconButton({ nav.goTop(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, "Settings") } },
            )
        },
        bottomBar = {
            if (top) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(route == t.route, { nav.goTop(t.route) }, { Icon(t.icon, t.label) }, label = { Text(t.label) })
                }
            }
        },
    ) { pad ->
        EnterLibNavHost(nav, UiSpec(tv = false), Modifier.padding(if (fullscreen) androidx.compose.foundation.layout.PaddingValues() else pad))
    }
}
