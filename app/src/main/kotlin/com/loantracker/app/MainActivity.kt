package com.loantracker.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.loantracker.app.services.NotificationService
import com.loantracker.app.services.ThemeMode
import com.loantracker.app.services.ThemeService
import com.loantracker.app.ui.AddLoanScreen
import com.loantracker.app.ui.AddPaymentScreen
import com.loantracker.app.ui.CelebrationDialog
import com.loantracker.app.ui.DashboardScreen
import com.loantracker.app.ui.LoanDetailScreen
import com.loantracker.app.ui.LoanHistoryScreen
import com.loantracker.app.ui.LoanViewModel
import com.loantracker.app.ui.loanTrackerColorScheme
import com.loantracker.app.ui.MpesaImportScreen
import com.loantracker.app.ui.SettingsScreen
import com.loantracker.app.ui.SmsScanScreen

private val DefaultLightScrim = 0xE6FFFFFF.toInt()
private val DefaultDarkScrim = 0x801B1B1B.toInt()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationService.createChannels(this)
        setContent {
            val context = LocalContext.current
            val themeMode by remember { ThemeService.observe(context) }
                .collectAsState(initial = ThemeMode.SYSTEM)
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Fix A: drive edge-to-edge from the app's theme (not the system theme).
            // Without this, forced-light on a dark system gives invisible status bar icons.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT
                    ) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        DefaultLightScrim, DefaultDarkScrim
                    ) { dark }
                )
                onDispose {}
            }

            LoanTrackerTheme(dark) { AppNav() }
        }
    }
}

@Composable
private fun LoanTrackerTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = loanTrackerColorScheme(dark)
    MaterialTheme(colorScheme = colors) {
        Surface(color = MaterialTheme.colorScheme.background) { content() }
    }
}

private data class NavItem(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
)

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val vm: LoanViewModel = viewModel(factory = LoanViewModel.factory(context))

    val celebration by vm.celebration.collectAsStateWithLifecycle()
    celebration?.let { CelebrationDialog(it) { vm.dismissCelebration() } }

    val navItems = listOf(
        NavItem("dashboard", "Dashboard", { Icon(Icons.Filled.Dashboard, contentDescription = null) }),
        NavItem("history", "History", { Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null) }),
        NavItem("settings", "Settings", { Icon(Icons.Filled.Settings, contentDescription = null) })
    )

    val currentBackStack by nav.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    val showBottomBar = currentRoute in navItems.map { it.route }

    // Fix B: outer Scaffold zeroes contentWindowInsets so system bars are not
    // applied here. The NavigationBar handles the bottom inset itself. Screen
    // Scaffolds own the top (status bar) inset; tab screens exclude the bottom
    // to avoid doubling with the NavigationBar.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            icon = item.icon,
                            label = { Text(item.label) },
                            selected = currentRoute == item.route,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding)
        ) {
            // Tab routes (with bottom bar) — no back arrow
            composable("dashboard") {
                DashboardScreen(
                    vm = vm,
                    onAddLoan = { nav.navigate("addLoan") },
                    onOpenLoan = { id -> nav.navigate("loanDetail/$id") }
                )
            }
            composable("history") {
                LoanHistoryScreen(
                    vm = vm,
                    onOpenLoan = { id -> nav.navigate("loanDetail/$id") },
                    onAddLoan = { nav.navigate("addLoan") }
                )
            }
            composable("settings") {
                SettingsScreen(vm = vm)
            }

            // Full-screen routes (no bottom bar) — keep back arrow
            composable("addLoan") {
                AddLoanScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable("loanDetail/{loanId}") { entry ->
                val loanId = entry.arguments?.getString("loanId").orEmpty()
                LoanDetailScreen(
                    loanId = loanId,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onAddPayment = { id -> nav.navigate("addPayment/$id") },
                    onMpesaImport = { id -> nav.navigate("mpesaImport/$id") },
                    onSmsScan = { id -> nav.navigate("smsScan/$id") }
                )
            }
            composable("addPayment/{loanId}") { entry ->
                val loanId = entry.arguments?.getString("loanId").orEmpty()
                AddPaymentScreen(loanId = loanId, vm = vm, onBack = { nav.popBackStack() })
            }
            composable("mpesaImport/{loanId}") { entry ->
                val loanId = entry.arguments?.getString("loanId").orEmpty()
                MpesaImportScreen(loanId = loanId, vm = vm, onBack = { nav.popBackStack() })
            }
            composable("smsScan/{loanId}") { entry ->
                val loanId = entry.arguments?.getString("loanId").orEmpty()
                SmsScanScreen(loanId = loanId, vm = vm, onBack = { nav.popBackStack() })
            }
        }
    }
}
