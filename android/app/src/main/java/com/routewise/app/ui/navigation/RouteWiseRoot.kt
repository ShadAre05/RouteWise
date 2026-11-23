package com.routewise.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.routewise.app.AppContainer
import com.routewise.app.ui.auth.AuthScreen
import com.routewise.app.ui.auth.AuthViewModel
import com.routewise.app.ui.budget.BudgetScreen
import com.routewise.app.ui.budget.BudgetViewModel
import com.routewise.app.ui.factoryOf
import com.routewise.app.ui.history.HistoryScreen
import com.routewise.app.ui.history.HistoryViewModel
import com.routewise.app.ui.history.TripDetailScreen
import com.routewise.app.ui.history.TripDetailViewModel
import com.routewise.app.ui.plan.PlanScreen
import com.routewise.app.ui.plan.PlanViewModel
import com.routewise.app.ui.profile.ProfileScreen
import com.routewise.app.ui.profile.ProfileViewModel
import com.routewise.app.ui.details.FuelTrendScreen
import com.routewise.app.ui.details.FuelTrendViewModel
import com.routewise.app.ui.details.LodgingScreen
import com.routewise.app.ui.details.LodgingViewModel
import com.routewise.app.ui.details.RefuelPlanScreen
import com.routewise.app.ui.details.RouteDetailsScreen
import com.routewise.app.ui.details.RouteDetailsViewModel
import com.routewise.app.ui.vehicles.VehiclesScreen
import com.routewise.app.ui.vehicles.VehiclesViewModel

object Routes {
    const val PLAN = "plan"
    const val VEHICLES = "vehicles"
    const val HISTORY = "history"
    const val PROFILE = "profile"
    const val BUDGET = "budget"
    const val TRIP = "trip/{id}"
    const val DETAILS = "details"
    const val REFUEL = "refuel"
    const val TREND = "trend"
    const val LODGING = "lodging"
    fun trip(id: String) = "trip/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab(Routes.PLAN, "Plan", Icons.Default.Place),
    Tab(Routes.HISTORY, "History", Icons.Default.DateRange),
    Tab(Routes.VEHICLES, "Vehicles", Icons.Default.Build),
    Tab(Routes.PROFILE, "Profile", Icons.Default.Person),
)

/**
 * The root of the UI. It shows the login screen while there is no session and the tabbed app otherwise, so a
 * 401 that the API client could not repair (session cleared) leads back to login from any screen. Messages of
 * all view models arrive here and are shown in one snackbar.
 */
@Composable
fun RouteWiseRoot(container: AppContainer) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(container) {
        container.notifier.events.collect { snackbar.showSnackbar(it) }
    }
    val tokens by container.session.tokens.collectAsState()
    val expired by container.session.expired.collectAsState()
    LaunchedEffect(tokens == null) {
        if (tokens == null) {
            container.currentCalculation.value = null
            container.tripCache.clear()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (tokens == null) {
                val vm: AuthViewModel = viewModel(factory = factoryOf { AuthViewModel(container.auth, container.errors) })
                AuthScreen(vm, sessionExpired = expired)
            } else {
                MainApp(container)
            }
        }
    }
}

@Composable
private fun MainApp(container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isTab = route == null || Tabs.any { it.route == route }

    Column(Modifier.fillMaxSize()) {
        if (!isTab) {
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = { nav.popBackStack() }, modifier = Modifier.testTag("back")) { Text("← Back") }
            }
        }
        Column(Modifier.weight(1f)) {
            NavHost(nav, startDestination = Routes.PLAN) {
                composable(Routes.PLAN) {
                    val vm: PlanViewModel = viewModel(factory = factoryOf {
                        PlanViewModel(container.vehicles, container.calculator, container.errors, container.currentCalculation)
                    })
                    PlanScreen(vm, onCalculated = { nav.navigate(Routes.BUDGET) }, onAddCar = { switchTab(nav, Routes.VEHICLES) })
                }
                composable(Routes.BUDGET) {
                    val vm: BudgetViewModel = viewModel(factory = factoryOf {
                        BudgetViewModel(container.calculator, container.errors, container.notifier, container.currentCalculation)
                    })
                    BudgetScreen(
                        vm,
                        onPlan = { nav.popBackStack() },
                        onDetails = { nav.navigate(Routes.DETAILS) },
                        onLodging = { nav.navigate(Routes.LODGING) },
                    )
                }
                composable(Routes.DETAILS) {
                    val vm: RouteDetailsViewModel = viewModel(factory = factoryOf {
                        RouteDetailsViewModel(container.currentCalculation, container.reference, container.errors)
                    })
                    RouteDetailsScreen(vm, onRefuel = { nav.navigate(Routes.REFUEL) }, onTrend = { nav.navigate(Routes.TREND) })
                }
                composable(Routes.REFUEL) {
                    val calculation by container.currentCalculation.collectAsState()
                    RefuelPlanScreen(calculation, onAddFuelLevel = { nav.popBackStack(Routes.PLAN, false) })
                }
                composable(Routes.TREND) {
                    val vm: FuelTrendViewModel = viewModel(factory = factoryOf {
                        FuelTrendViewModel(container.currentCalculation, container.reference, container.errors)
                    })
                    FuelTrendScreen(vm)
                }
                composable(Routes.LODGING) {
                    val vm: LodgingViewModel = viewModel(factory = factoryOf {
                        LodgingViewModel(container.calculator, container.currentCalculation, container.errors, container.notifier)
                    })
                    LodgingScreen(vm)
                }
                composable(Routes.VEHICLES) {
                    val vm: VehiclesViewModel = viewModel(factory = factoryOf {
                        VehiclesViewModel(container.vehicles, container.errors, container.notifier)
                    })
                    VehiclesScreen(vm, onFirstCarSaved = { switchTab(nav, Routes.PLAN) })
                }
                composable(Routes.HISTORY) {
                    val vm: HistoryViewModel = viewModel(factory = factoryOf {
                        HistoryViewModel(container.trips, container.errors, container.notifier)
                    })
                    HistoryScreen(vm, onOpen = { nav.navigate(Routes.trip(it)) }, onPlan = { switchTab(nav, Routes.PLAN) })
                }
                composable(Routes.TRIP) { backStack ->
                    val id = backStack.arguments?.getString("id").orEmpty()
                    val vm: TripDetailViewModel = viewModel(factory = factoryOf {
                        TripDetailViewModel(id, container.trips, container.errors, container.notifier)
                    })
                    TripDetailScreen(vm, onDeleted = { nav.popBackStack() })
                }
                composable(Routes.PROFILE) {
                    val vm: ProfileViewModel = viewModel(factory = factoryOf {
                        ProfileViewModel(container.auth, container.errors, container.notifier)
                    })
                    ProfileScreen(vm)
                }
            }
        }
        if (isTab) {
            NavigationBar {
                Tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = { switchTab(nav, tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("tab_${tab.route}"),
                    )
                }
            }
        }
    }
}

private fun switchTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
