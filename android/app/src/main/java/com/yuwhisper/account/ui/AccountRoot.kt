package com.yuwhisper.account.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yuwhisper.account.capture.AutoBookkeepingPrefs
import com.yuwhisper.account.data.LedgerRepository
import com.yuwhisper.account.ui.category.CategoryScreen
import com.yuwhisper.account.ui.ledger.LedgerScreen
import com.yuwhisper.account.ui.manual.ManualEntryScreen
import com.yuwhisper.account.ui.onboarding.PermissionOnboardingScreen
import com.yuwhisper.account.ui.settings.SettingsScreen
import com.yuwhisper.account.ui.settings.WatchAppsScreen
import com.yuwhisper.account.ui.stats.StatsScreen

private object Routes {
    const val LEDGER = "ledger"
    const val STATS = "stats"
    const val CATEGORY = "category"
    const val SETTINGS = "settings"
    const val MANUAL = "manual"
    const val WATCH_APPS = "watch_apps"
    const val PERMISSION_ONBOARDING = "permission_onboarding"
}

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun AccountRoot(repository: LedgerRepository) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val vm: LedgerViewModel = viewModel(factory = LedgerViewModel.factory(repository))
    val days by vm.ledgerDays.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val stats by vm.monthStats.collectAsStateWithLifecycle()
    val watchApps by vm.watchApps.collectAsStateWithLifecycle()
    var onboardingAutoShown by remember { mutableStateOf(false) }

    val tabs = listOf(
        TabItem(Routes.LEDGER, "\u6d41\u6c34", Icons.AutoMirrored.Filled.List),
        TabItem(Routes.STATS, "\u7edf\u8ba1", Icons.Filled.BarChart),
        TabItem(Routes.CATEGORY, "\u5206\u7c7b", Icons.Filled.Category),
        TabItem(Routes.SETTINGS, "\u8bbe\u7f6e", Icons.Filled.Settings),
    )
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    fun openPermissionOnboarding() {
        navController.navigate(Routes.PERMISSION_ONBOARDING) {
            launchSingleTop = true
        }
    }

    LaunchedEffect(Unit) {
        if (!onboardingAutoShown && AutoBookkeepingPrefs.shouldShowPermissionOnboarding(context)) {
            onboardingAutoShown = true
            openPermissionOnboarding()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LEDGER,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LEDGER) {
                LedgerScreen(
                    days = days,
                    onAddClick = { navController.navigate(Routes.MANUAL) },
                )
            }
            composable(Routes.STATS) {
                StatsScreen(stats = stats)
            }
            composable(Routes.CATEGORY) {
                CategoryScreen(
                    categories = categories,
                    onUpsert = { localId, name, sortOrder, onDone, onError ->
                        vm.upsertCategory(localId, name, sortOrder, onDone, onError)
                    },
                    onDelete = { localId, onError ->
                        vm.deleteCategory(localId, onError)
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onExportCsv = { file, onDone, onError ->
                        vm.exportCsv(file, onDone, onError)
                    },
                    onOpenWatchApps = { navController.navigate(Routes.WATCH_APPS) },
                    onOpenPermissionOnboarding = { openPermissionOnboarding() },
                )
            }
            composable(Routes.WATCH_APPS) {
                WatchAppsScreen(
                    apps = watchApps,
                    onToggle = { packageName, enabled ->
                        vm.setWatchAppEnabled(packageName, enabled)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PERMISSION_ONBOARDING) {
                PermissionOnboardingScreen(
                    onFinished = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.LEDGER) {
                                launchSingleTop = true
                            }
                        }
                    },
                    showBack = true,
                )
            }
            composable(Routes.MANUAL) {
                ManualEntryScreen(
                    categories = categories,
                    onBack = { navController.popBackStack() },
                    onSave = { amountCents, type, categoryLocalId, merchant, note, occurredAt, onDone, onError ->
                        vm.addManual(
                            amountCents = amountCents,
                            type = type,
                            categoryLocalId = categoryLocalId,
                            merchant = merchant,
                            note = note,
                            occurredAt = occurredAt,
                            onDone = onDone,
                            onError = onError,
                        )
                    },
                )
            }
        }
    }
}
