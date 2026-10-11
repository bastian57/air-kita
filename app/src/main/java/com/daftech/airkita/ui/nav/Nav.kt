package com.daftech.airkita.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.daftech.airkita.data.Repository
import com.daftech.airkita.ui.screen.BillDetailScreen
import com.daftech.airkita.ui.screen.BillsScreen
import com.daftech.airkita.ui.screen.HomeScreen
import com.daftech.airkita.ui.screen.KasScreen
import com.daftech.airkita.ui.screen.MeterManualScreen
import com.daftech.airkita.ui.screen.MeterScanScreen
import com.daftech.airkita.ui.screen.ReportScreen
import com.daftech.airkita.ui.screen.SettingsScreen
import com.daftech.airkita.ui.screen.SetupWizardScreen

object Routes {
    const val HOME = "home"
    const val METER_MANUAL = "meter_manual"
    const val METER_SCAN = "meter_scan/{customerId}"
    const val BILLS = "bills"
    const val BILL_DETAIL = "bill_detail/{billId}"
    const val KAS = "kas"
    const val REPORT = "report"
    const val SETTINGS = "settings"
    const val SETUP_WIZARD = "setup_wizard"

    fun meterScan(customerId: Long) = "meter_scan/$customerId"
    fun billDetail(billId: Long) = "bill_detail/$billId"
}

@Composable
fun AirKitaNav(repo: Repository) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(repo, nav) }
        composable(Routes.METER_MANUAL) { MeterManualScreen(repo, nav) }
        composable(
            Routes.METER_SCAN,
            arguments = listOf(navArgument("customerId") { type = NavType.LongType })
        ) { backStack ->
            MeterScanScreen(repo, nav, backStack.arguments?.getLong("customerId") ?: 0L)
        }
        composable(Routes.BILLS) { BillsScreen(repo, nav) }
        composable(
            Routes.BILL_DETAIL,
            arguments = listOf(navArgument("billId") { type = NavType.LongType })
        ) { backStack ->
            BillDetailScreen(repo, nav, backStack.arguments?.getLong("billId") ?: 0L)
        }
        composable(Routes.KAS) { KasScreen(repo, nav) }
        composable(Routes.REPORT) { ReportScreen(repo, nav) }
        composable(Routes.SETTINGS) { SettingsScreen(repo, nav) }
        composable(Routes.SETUP_WIZARD) { SetupWizardScreen(repo, nav) }
    }
}
