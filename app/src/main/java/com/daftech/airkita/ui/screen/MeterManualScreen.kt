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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.daftech.airkita.data.Repository
import com.daftech.airkita.data.entity.Bill
import com.daftech.airkita.data.entity.Customer
import com.daftech.airkita.engine.TariffEngine
import com.daftech.airkita.ui.components.AirBottomBar
import com.daftech.airkita.ui.components.AvatarCircle
import com.daftech.airkita.ui.components.GradientHeader
import com.daftech.airkita.ui.components.StatusBadge
import com.daftech.airkita.ui.nav.Routes
import com.daftech.airkita.ui.theme.AirBlue
import com.daftech.airkita.ui.theme.AppBg
import com.daftech.airkita.ui.theme.CardBg
import com.daftech.airkita.ui.theme.Cyan
import com.daftech.airkita.ui.theme.Muted
import com.daftech.airkita.ui.theme.Navy
import com.daftech.airkita.ui.theme.Red
import com.daftech.airkita.ui.theme.Teal
import com.daftech.airkita.util.Fmt
import kotlinx.coroutines.launch

@Composable
fun MeterManualScreen(repo: Repository, nav: NavController) {
    val period = remember { Fmt.period() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val customers by repo.searchCustomers(query).collectAsState(initial = emptyList())
    val tariff by repo.tariffFlow().collectAsState(initial = com.daftech.airkita.engine.TariffSettings())

    var selected by remember { mutableStateOf<Customer?>(null) }
    var meterEndStr by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Hasil scan kamera dari MeterScanScreen
    val savedHandle = nav.currentBackStackEntry?.savedStateHandle
    val scanFlow = remember { savedHandle?.getStateFlow("scan_result", "") }
    val scanVal by (scanFlow?.collectAsState() ?: remember { mutableStateOf("") })
    LaunchedEffect(scanVal) {
        if (!scanVal.isNullOrBlank()) {
            meterEndStr = scanVal!!
            savedHandle?.set("scan_result", "")
        }
    }

    val meterStart = selected?.lastMeter ?: 0
    val meterEnd = meterEndStr.toIntOrNull()
    val calc = if (selected != null && meterEnd != null)
        TariffEngine.calculate(meterStart, meterEnd, tariff) else null
    val invalid = meterEnd != null && meterEnd < meterStart

    Scaffold(
        containerColor = AppBg,
        bottomBar = { AirBottomBar(nav, Routes.METER_MANUAL) }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad)
        ) {
            item {
                GradientHeader("Catat Meter", "Periode ${Fmt.periodLabel(period)}", slim = true)
            }
            item {
                Column(Modifier.padding(14.dp)) {
                    // Toggle manual / scan
                    Row(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val c = selected
                                if (c != null) nav.navigate(Routes.meterScan(c.id))
                                else error = "Pilih pelanggan dulu sebelum scan."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Scan Kamera")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; if (selected != null && it != selected!!.name) selected = null },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Cari nama / no. meter…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
            if (selected == null) {
                items(customers.take(8)) { c ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                            .clickable { selected = c; query = c.name; error = null },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            AvatarCircle(c.name)
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(c.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("No. ${c.meterNo} • ${c.address}", fontSize = 11.sp, color = Muted)
                            }
                            Spacer(Modifier.weight(1f))
                            StatusBadge("Meter: ${c.lastMeter}", "blue")
                        }
                    }
                }
            } else {
                val c = selected!!
                item {
                    Column(Modifier.padding(horizontal = 14.dp)) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AvatarCircle(c.name)
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text(c.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("No. ${c.meterNo} • ${c.address}", fontSize = 11.sp, color = Muted)
                                }
                                Spacer(Modifier.weight(1f))
                                Text("Ganti", color = AirBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { selected = null; query = ""; meterEndStr = "" })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Meter awal (${Fmt.periodLabel(Fmt.prevPeriod(period))})", fontSize = 11.sp, color = Muted)
                        Text("${c.lastMeter} m³", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = meterEndStr,
                            onValueChange = { meterEndStr = it.filter { ch -> ch.isDigit() }.take(7); error = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Meter akhir — ketik angka sekarang") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            isError = invalid
                        )
                        if (invalid) Text("Meter akhir tidak boleh lebih kecil dari meter awal.", color = Red, fontSize = 12.sp)
                        if (error != null) Text(error!!, color = Red, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        if (calc != null && !invalid) {
                            CalcCard(calc, tariff)
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        val id = repo.saveBill(
                                            Bill(
                                                customerId = c.id, period = period,
                                                meterStart = meterStart, meterEnd = meterEnd!!,
                                                usageM3 = calc.usage, block1m3 = calc.b1,
                                                block2m3 = calc.b2, block3m3 = calc.b3,
                                                abonemen = calc.abonemen, fine = calc.fine,
                                                total = calc.total
                                            )
                                        )
                                        repo.updateCustomerLastMeter(c.id, meterEnd!!)
                                        nav.navigate(Routes.billDetail(id)) {
                                            popUpTo(Routes.METER_MANUAL) { inclusive = true }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AirBlue)
                            ) {
                                Text("Simpan Tagihan", modifier = Modifier.padding(vertical = 6.dp), fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CalcCard(calc: com.daftech.airkita.engine.BillCalc, tariff: com.daftech.airkita.engine.TariffSettings) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Navy, AirBlue)))
                .padding(14.dp)
        ) {
            CalcRow("Pemakaian", "${calc.usage} m³", bold = true)
            CalcRow("Blok 1 — ${calc.b1} m³ × ${Fmt.rp(tariff.block1Rate)}", Fmt.rp(calc.amount1))
            CalcRow("Blok 2 — ${calc.b2} m³ × ${Fmt.rp(tariff.block2Rate)}", Fmt.rp(calc.amount2))
            if (calc.b3 > 0) CalcRow("Blok 3 — ${calc.b3} m³ × ${Fmt.rp(tariff.block3Rate)}", Fmt.rp(calc.amount3))
            CalcRow("Abonemen", Fmt.rp(calc.abonemen))
            if (calc.fine > 0) CalcRow("Denda", Fmt.rp(calc.fine))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Total tagihan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(Fmt.rp(calc.total), color = Teal, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun CalcRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 12.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}
