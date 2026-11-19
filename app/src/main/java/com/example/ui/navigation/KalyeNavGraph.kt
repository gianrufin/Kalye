package com.example.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.KalyeViewModel
import com.example.ui.screens.OfflineMapsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ShareCardScreen
import com.example.ui.screens.TrackScreen
import com.example.ui.screens.TripDetailScreen
import com.example.ui.screens.TripsScreen
import com.example.ui.screens.VehicleGarageScreen
import kotlinx.serialization.Serializable

@Serializable
data object TrackRoute

@Serializable
data object TripsRoute

@Serializable
data object OfflineMapsRoute

@Serializable
data object SettingsRoute

@Serializable
data object GarageRoute

@Serializable
data class TripDetailRoute(val tripId: String)

@Serializable
data class ShareCardRoute(val tripId: String)

private data class TopTab(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun KalyeMainScaffold(
    viewModel: KalyeViewModel,
    onSignedOut: () -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()

    LaunchedEffect(syncMessage) {
        val msg = syncMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSyncMessage()
    }

    val topTabs = remember {
        listOf(
            TopTab(TrackRoute, "Track", Icons.Rounded.Explore, Icons.Outlined.Explore, "nav_track"),
            TopTab(TripsRoute, "Trips", Icons.Rounded.History, Icons.Outlined.History, "nav_trips"),
            TopTab(OfflineMapsRoute, "Offline", Icons.Rounded.OfflinePin, Icons.Outlined.OfflinePin, "nav_offline"),
            TopTab(SettingsRoute, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings")
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val isTopLevel = currentDest?.hierarchy?.any { dest ->
        dest.hasRoute<TrackRoute>() ||
            dest.hasRoute<TripsRoute>() ||
            dest.hasRoute<OfflineMapsRoute>() ||
            dest.hasRoute<SettingsRoute>()
    } == true

    val navigateToTopTab: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (isTopLevel && !useRail) {
                    androidx.compose.foundation.layout.Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            topTabs.forEach { tab ->
                                val selected = currentDest?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTopTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize()) {
                if (isTopLevel && useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topTabs.forEach { tab ->
                            val selected = currentDest?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                val tabFadeIn = fadeIn(animationSpec = tween(190, easing = FastOutSlowInEasing))
                val tabFadeOut = fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing))
                val pushIn = slideInHorizontally(animationSpec = tween(240, easing = FastOutSlowInEasing)) { it / 5 } +
                    fadeIn(animationSpec = tween(220))
                val popOut = slideOutHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it / 5 } +
                    fadeOut(animationSpec = tween(180))

                NavHost(
                    navController = navController,
                    startDestination = TrackRoute,
                    enterTransition = { tabFadeIn },
                    exitTransition = { tabFadeOut },
                    popEnterTransition = { tabFadeIn },
                    popExitTransition = { tabFadeOut },
                    modifier = Modifier.weight(1f)
                ) {
                    composable<TrackRoute> {
                        TrackScreen(
                            viewModel = viewModel,
                            bottomBarPadding = if (useRail) PaddingValues(0.dp) else innerPadding,
                            onRideFinished = { tripId ->
                                navController.navigate(TripDetailRoute(tripId))
                            },
                            onOpenGarage = {
                                navController.navigate(GarageRoute)
                            }
                        )
                    }
                    composable<TripsRoute> {
                        TripsScreen(
                            viewModel = viewModel,
                            bottomBarPadding = if (useRail) PaddingValues(0.dp) else innerPadding,
                            onTripSelected = { tripId ->
                                navController.navigate(TripDetailRoute(tripId))
                            },
                            onStartTrackRequested = {
                                navigateToTopTab(TrackRoute)
                            }
                        )
                    }
                    composable<OfflineMapsRoute> {
                        OfflineMapsScreen(
                            viewModel = viewModel,
                            bottomBarPadding = if (useRail) PaddingValues(0.dp) else innerPadding
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            viewModel = viewModel,
                            bottomBarPadding = if (useRail) PaddingValues(0.dp) else innerPadding,
                            onSignedOut = onSignedOut,
                            onOpenGarage = {
                                navController.navigate(GarageRoute)
                            }
                        )
                    }
                    composable<GarageRoute>(
                        enterTransition = { pushIn },
                        popExitTransition = { popOut }
                    ) {
                        VehicleGarageScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onStartDriveWithRig = {
                                navController.popBackStack()
                                navigateToTopTab(TrackRoute)
                            }
                        )
                    }
                    composable<TripDetailRoute>(
                        enterTransition = { pushIn },
                        popExitTransition = { popOut }
                    ) { entry ->
                        val route = entry.toRoute<TripDetailRoute>()
                        TripDetailScreen(
                            tripId = route.tripId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenShareCard = { tripId ->
                                navController.navigate(ShareCardRoute(tripId))
                            }
                        )
                    }
                    composable<ShareCardRoute>(
                        enterTransition = { pushIn },
                        popExitTransition = { popOut }
                    ) { entry ->
                        val route = entry.toRoute<ShareCardRoute>()
                        ShareCardScreen(
                            tripId = route.tripId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
