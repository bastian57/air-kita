package com.daftech.airkita.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.daftech.airkita.data.Repository
import com.daftech.airkita.ui.components.AirBottomBar
import com.daftech.airkita.ui.components.AvatarCircle
import com.daftech.airkita.ui.components.GradientHeader
import com.daftech.airkita.ui.components.StatusBadge
import com.daftech.airkita.ui.nav.Routes
import com.daftech.airkita.ui.theme.AirBlue
import com.daftech.airkita.ui.theme.AppBg
import com.daftech.airkita.ui.theme.CardBg
import com.daftech.airkita.ui.theme.Muted
import com.daftech.airkita.ui.theme.Navy
import com.daftech.airkita.util.Fmt

@Composable
fun BillsScreen(repo: Repository, nav: NavController) {
    var period by remember { mutableStateOf(Fmt.period()) }
    var filter by remember { mutableStateOf(0) } // 0 semua, 1 belum lunas, 2 lunas
    val rows by repo.billsWithCustomer(period).collectAsState(initial = emptyList())

    val total = rows.sumOf { it.bill.total }
    val paidSum = rows.filter { it.bill.paid }.sumOf { it.bill.total }
    val progress = if (total > 0) paidSum.toFloat() / total else 0f
    val unpaidCount = rows.count { !it.bill.paid }

    val shown = when (filter) {
        1 -> rows.filter { !it.bill.paid }
        2 -> rows.filter { it.bill.paid }
        else -> rows
    }

    Scaffold(
        containerColor = AppBg,
        bottomBar = { AirBottomBar(nav, Routes.BILLS) }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                GradientHeader("Tagihan", "‹ ${Fmt.periodLabel(period)} ›", slim = true)
            }
            item {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("‹", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AirBlue,
                            modifier = Modifier.clickable { period = Fmt.prevPeriod(period) }.padding(8.dp))
                        Spacer(Modifier.weight(1f))
                        Text(Fmt.periodLabel(period), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        Text("›", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AirBlue,
                            modifier = Modifier.clickable { period = Fmt.nextPeriod(period) }.padding(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text("Terkumpul bulan ini", fontSize = 11.sp, color = Muted)
                                Spacer(Modifier.weight(1f))
                                Text("${(progress * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AirBlue)
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)),
                                color = AirBlue,
                                trackColor = Color(0xFFE7EEF5)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("${Fmt.rp(paidSum)} dari ${Fmt.rp(total)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row {
                        FilterChip("Semua", filter == 0) { filter = 0 }
                        FilterChip("Belum lunas ($unpaidCount)", filter == 1) { filter = 1 }
                        FilterChip("Lunas", filter == 2) { filter = 2 }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        "Belum ada tagihan periode ini.",
                        modifier = Modifier.padding(16.dp),
                        color = Muted, fontSize = 13.sp
                    )
                }
            }
            items(shown, key = { it.bill.id }) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                        .clickable { nav.navigate(Routes.billDetail(row.bill.id)) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarCircle(row.customer.name)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(row.customer.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${row.bill.usageM3} m³ • No. ${row.customer.meterNo}", fontSize = 11.sp, color = Muted)
                        }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Fmt.rp(row.bill.total), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            StatusBadge(if (row.bill.paid) "Lunas" else "Belum lunas", if (row.bill.paid) "green" else "amber")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Navy else CardBg,
        modifier = Modifier.padding(end = 8.dp).clickable { onClick() },
        shadowElevation = 2.dp
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else Muted
        )
    }
}
