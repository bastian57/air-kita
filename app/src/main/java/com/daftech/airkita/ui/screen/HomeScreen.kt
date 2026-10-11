package com.daftech.airkita.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.daftech.airkita.data.BillWithCustomer
import com.daftech.airkita.data.PrefKeys
import com.daftech.airkita.data.Repository
import com.daftech.airkita.ui.components.AirBottomBar
import com.daftech.airkita.ui.components.AvatarCircle
import com.daftech.airkita.ui.components.GradientHeader
import com.daftech.airkita.ui.components.SectionTitle
import com.daftech.airkita.ui.components.StatCard
import com.daftech.airkita.ui.components.StatusBadge
import com.daftech.airkita.ui.nav.Routes
import com.daftech.airkita.ui.theme.AirBlue
import com.daftech.airkita.ui.theme.AppBg
import com.daftech.airkita.ui.theme.CardBg
import com.daftech.airkita.ui.theme.CyanSoft
import com.daftech.airkita.ui.theme.Green
import com.daftech.airkita.ui.theme.GreenSoft
import com.daftech.airkita.ui.theme.Ink
import com.daftech.airkita.ui.theme.Red
import com.daftech.airkita.util.Fmt

@Composable
fun HomeScreen(repo: Repository, nav: NavController) {
    val period = remember { Fmt.period() }

    var setupChecked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val done = repo.getSetting(PrefKeys.SETUP_DONE, "0") == "1"
        if (!done) {
            nav.navigate(Routes.SETUP_WIZARD) { popUpTo(Routes.HOME) { inclusive = true } }
        } else setupChecked = true
    }

    val bills by repo.billsWithCustomer(period).collectAsState(initial = emptyList())
    val customerCount by repo.countActiveCustomers().collectAsState(initial = 0)
    var arrears by remember { mutableStateOf<List<BillWithCustomer>>(emptyList()) }
    LaunchedEffect(period) { arrears = repo.unpaidBills(5) }

    val total = bills.sumOf { it.bill.total }
    val paid = bills.filter { it.bill.paid }.sumOf { it.bill.total }
    val unpaid = total - paid

    Scaffold(
        containerColor = AppBg,
        bottomBar = { AirBottomBar(nav, Routes.HOME) }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            item {
                GradientHeader(
                    title = "AIR KITA",
                    subtitle = "Admin Tagihan Air • ${Fmt.periodLabel(period)}",
                    trailing = {
                        IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Pengaturan", tint = Color.White)
                        }
                    }
                )
            }
            item {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Tagihan bulan ini", Fmt.rp(total), AirBlue, Modifier.weight(1f))
                        StatCard("Terkumpul", Fmt.rp(paid), Green, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Tunggakan", Fmt.rp(unpaid), Red, Modifier.weight(1f))
                        StatCard("Pelanggan", "$customerCount aktif", Ink, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickAction("Catat Meter", Icons.Filled.Speed, CyanSoft, AirBlue, Modifier.weight(1f)) {
                            nav.navigate(Routes.METER_MANUAL)
                        }
                        QuickAction("Laporan PDF", Icons.Filled.Description, Color(0xFFEDE9FE), Color(0xFF7C3AED), Modifier.weight(1f)) {
                            nav.navigate(Routes.REPORT)
                        }
                        QuickAction("Kas", Icons.Filled.Wallet, GreenSoft, Green, Modifier.weight(1f)) {
                            nav.navigate(Routes.KAS)
                        }
                    }
                    SectionTitle("Perlu perhatian", "Lihat semua") { nav.navigate(Routes.BILLS) }
                }
            }
            if (arrears.isEmpty()) {
                item {
                    Text(
                        "Semua tagihan lunas. Mantap!",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 13.sp, color = Green, fontWeight = FontWeight.Bold
                    )
                }
            } else {
                items(arrears.take(3)) { row ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .clickable { nav.navigate(Routes.billDetail(row.bill.id)) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarCircle(row.customer.name)
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(row.customer.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "${Fmt.periodLabel(row.bill.period)} • ${row.bill.usageM3} m³",
                                    fontSize = 11.sp, color = com.daftech.airkita.ui.theme.Muted
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Fmt.rp(row.bill.total), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                StatusBadge("Belum lunas", "red")
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = bg),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.padding(9.dp).size(20.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
