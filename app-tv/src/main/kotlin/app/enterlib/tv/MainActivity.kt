package app.enterlib.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.enterlib.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EnterLibTheme("dark") { TvRoot() } }
    }
}

@Composable
private fun TvTab(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Text(
        label, style = MaterialTheme.typography.titleMedium,
        color = if (selected || focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
            .background(
                when { focused -> MaterialTheme.colorScheme.primary; selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.surface }, shape,
            )
            .then(if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.secondary, shape) else Modifier)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    )
}

@Composable
private fun TvRoot() {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val tabs = listOf(
        Routes.HOME to "Home", Routes.MOVIES to "Movies", Routes.SERIES to "TV & Series",
        Routes.SEARCH to "Search", Routes.LIBRARY to "My List", Routes.SETTINGS to "Settings",
    )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            if (route in Routes.topLevel) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("EnterLib", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 24.dp))
                    tabs.forEach { (r, l) -> TvTab(l, route == r) { nav.goTop(r) } }
                }
            }
            EnterLibNavHost(nav, UiSpec(tv = true), Modifier.weight(1f))
        }
    }
}
