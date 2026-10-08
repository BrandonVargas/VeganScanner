package dev.brandonvargas.veganscanner.android.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.brandonvargas.veganscanner.R
import dev.brandonvargas.veganscanner.android.feature.history.HistoryScreen
import dev.brandonvargas.veganscanner.android.feature.result.ResultScreen
import dev.brandonvargas.veganscanner.android.feature.scanner.ScannerScreen

private enum class TopLevelDestination(
    val route: NavKey,
    val icon: ImageVector,
    @StringRes val label: Int,
) {
    Scan(ScanRoute, Icons.Rounded.QrCodeScanner, R.string.tab_scan),
    History(HistoryRoute, Icons.Rounded.History, R.string.tab_history),
}

/** Single back stack: Scan is the root, History sits on top of it, results are pushed above either. */
@Composable
fun VeganScannerApp(
    modifier: Modifier = Modifier,
    deepLinkBarcode: String? = null,
    onDeepLinkConsume: () -> Unit = {},
) {
    val backStack = rememberNavBackStack(ScanRoute)
    val currentOnDeepLinkConsume by rememberUpdatedState(onDeepLinkConsume)
    LaunchedEffect(deepLinkBarcode) {
        if (deepLinkBarcode != null) {
            backStack.add(ResultRoute(deepLinkBarcode))
            currentOnDeepLinkConsume()
        }
    }
    val current = backStack.lastOrNull()
    val showBottomBar = TopLevelDestination.entries.any { it.route == current }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = current == destination.route,
                            onClick = { backStack.selectTopLevel(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Only the bottom bar's space is reserved; each screen handles the top insets itself.
        val bottomBarPadding = PaddingValues(bottom = padding.calculateBottomPadding())
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.padding(bottomBarPadding).consumeWindowInsets(bottomBarPadding),
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider =
                entryProvider {
                    entry<ScanRoute> {
                        ScannerScreen(onOpenResult = { backStack.add(ResultRoute(it)) })
                    }
                    entry<HistoryRoute> {
                        HistoryScreen(onOpenResult = { backStack.add(ResultRoute(it)) })
                    }
                    entry<ResultRoute> { route ->
                        ResultScreen(barcode = route.barcode, onBack = { backStack.removeLastOrNull() })
                    }
                },
        )
    }
}

private fun NavBackStack<NavKey>.selectTopLevel(route: NavKey) {
    if (lastOrNull() == route) return
    clear()
    add(ScanRoute)
    if (route != ScanRoute) add(route)
}
