package com.pxr.cymatic.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.LocalOwnersProvider
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
internal fun InstantMusicNavHost(
    startDestination: String,
    routes: Map<String, @Composable (NavBackStackEntry) -> Unit>,
    modifier: Modifier = Modifier,
) {
    val nav = LocalNavController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
    nav.setViewModelStore(viewModelStoreOwner.viewModelStore)
    val graph = remember(nav, startDestination, routes.keys) {
        nav.createGraph(startDestination) {
            routes.keys.forEach { route -> composable(route) {} }
        }
    }
    nav.graph = graph
    val navigator = remember(nav) {
        nav.navigatorProvider.getNavigator<ComposeNavigator>("composable")
    }
    val backStack by navigator.backStack.collectAsState()
    val visibleEntries by nav.visibleEntries.collectAsState()
    val savedState = rememberSaveableStateHolder()
    val entry = backStack.lastOrNull()?.let { nav.currentBackStackEntry ?: it }

    DisposableEffect(nav, lifecycleOwner) {
        nav.setLifecycleOwner(lifecycleOwner)
        onDispose {
            nav.visibleEntries.value.forEach(navigator::onTransitionComplete)
        }
    }

    Box(modifier) {
        if (entry != null) {
            key(entry.id) {
                entry.LocalOwnersProvider(savedState) {
                    routes[entry.destination.route]?.invoke(entry)
                }
            }
        }
    }

    SideEffect {
        visibleEntries.forEach(navigator::onTransitionComplete)
    }
}
