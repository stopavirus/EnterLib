package app.enterlib.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

object Routes {
    const val HOME = "home"; const val MOVIES = "movies"; const val SERIES = "series"
    const val SEARCH = "search"; const val LIBRARY = "library"; const val SETTINGS = "settings"
    const val DETAIL = "detail/{id}"; const val PLAYER = "player/{id}/{ep}"
    fun detail(id: Long) = "detail/$id"
    fun player(id: Long, ep: Long) = "player/$id/$ep"
    val topLevel = setOf(HOME, MOVIES, SERIES, SEARCH, LIBRARY, SETTINGS)
}

/** Shared navigation graph; each app supplies its own chrome (bottom bar vs. TV tab row) around it. */
@Composable
fun EnterLibNavHost(nav: NavHostController, spec: UiSpec, modifier: Modifier = Modifier) {
    val longArg = { n: String -> navArgument(n) { type = NavType.LongType } }
    NavHost(nav, Routes.HOME, modifier) {
        composable(Routes.HOME) {
            HomeScreen(spec, { nav.navigate(Routes.detail(it)) }, { id, ep -> nav.navigate(Routes.player(id, ep)) },
                { nav.navigate(Routes.SETTINGS) })
        }
        composable(Routes.MOVIES) { BrowseScreen(spec, "MOVIE", false, { nav.navigate(Routes.detail(it)) }, {}) }
        composable(Routes.SERIES) { BrowseScreen(spec, "SERIES", false, { nav.navigate(Routes.detail(it)) }, {}) }
        composable(Routes.SEARCH) { BrowseScreen(spec, null, true, { nav.navigate(Routes.detail(it)) }, {}) }
        composable(Routes.LIBRARY) { LibraryScreen(spec) { nav.navigate(Routes.detail(it)) } }
        composable(Routes.SETTINGS) { SettingsScreen(spec, showBackup = !spec.tv) }
        composable(Routes.DETAIL, listOf(longArg("id"))) {
            DetailScreen(it.arguments!!.getLong("id"), spec, { nav.popBackStack() },
                { id, ep -> nav.navigate(Routes.player(id, ep)) }, { id -> nav.navigate(Routes.detail(id)) })
        }
        composable(Routes.PLAYER, listOf(longArg("id"), longArg("ep"))) {
            val id = it.arguments!!.getLong("id")
            PlayerScreen(id, it.arguments!!.getLong("ep"), forceLandscape = !spec.tv,
                onNextEpisode = { next -> nav.navigate(Routes.player(id, next)) { popUpTo(Routes.PLAYER) { inclusive = true } } },
                onExit = { nav.popBackStack() })
        }
    }
}

fun NavHostController.goTop(route: String) = navigate(route) {
    popUpTo(Routes.HOME) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
