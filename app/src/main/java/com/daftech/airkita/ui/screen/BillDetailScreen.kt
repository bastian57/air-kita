package com.daftech.airkita.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.daftech.airkita.data.PrefKeys
import com.daftech.airkita.data.Repository
import com.daftech.airkita.data.entity.Bill
import com.daftech.airkita.data.entity.Customer
import com.daftech.airkita.engine.TariffSettings
import com.daftech.airkita.print.EscPosPrinter
import com.daftech.airkita.ui.components.AirBottomBar
import com.daftech.airkita.ui.components.GradientHeader
import com.daftech.airkita.ui.nav.Routes
import com.daftech.airkita.ui.theme.AirBlue
import com.daftech.airkita.ui.theme.AppBg
import com.daftech.airkita.ui.theme.CardBg
import com.daftech.airkita.ui.theme.Green
import com.daftech.airkita.ui.theme.MonoReceipt
import com.daftech.airkita.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BillDetailScreen(repo: Repository, nav: NavController, billId: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var bill by remember { mutableStateOf<Bill?>(null) }
    var customer by remember { mutableStateOf<Customer?>(null) }
    var tariff by remember { mutableStateOf(TariffSettings()) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(billId) {
        val b = repo.getBill(billId)
        bill = b
        customer = b?.let { repo.getCustomer(it.customerId) }
        tariff = repo.getTariff()
    }

    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(ctx, "Izin Bluetooth diperlukan untuk cetak.", Toast.LENGTH_SHORT).show()
    }

    fun hasBtPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        containerColor = AppBg,
        bottomBar = { AirBottomBar(nav, Routes.BILLS) }
    ) { pad ->
        val b = bill
        val c = customer
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
        ) {
            GradientHeader(
                "Struk Tagihan",
                if (b != null && c != null) "${c.name} • ${Fmt.periodLabel(b.period)}" else "Memuat…",
                slim = true
            )
            if (b == null || c == null) {
                Text("Memuat…", Modifier.padding(16.dp))
            } else {
                Column(Modifier.padding(14.dp)) {
                    ReceiptCard(b, c, tariff)
                    Spacer(Modifier.height(10.dp))
                    if (!b.paid) {
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    repo.markBillPaid(b.id, Fmt.periodLabel(b.period))
                                    bill = repo.getBill(b.id)
                                    busy = false
                                    Toast.makeText(ctx, "Tagihan ditandai lunas.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Green)
                        ) {
                            Text("Tandai Lunas", Modifier.padding(vertical = 6.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            if (!hasBtPermission()) {
                                if (Build.VERSION.SDK_INT >= 31) btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                return@OutlinedButton
                            }
                            scope.launch(Dispatchers.IO) {
                                val mac = repo.getSetting(PrefKeys.PRINTER_MAC)
                                if (mac.isBlank()) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(ctx, "Pilih printer dulu di Pengaturan.", Toast.LENGTH_LONG).show()
                                    }
                                    return@launch
                                }
                                val res = EscPosPrinter.print(ctx, mac, EscPosPrinter.buildReceipt(b, c, tariff))
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        ctx,
                                        if (res.isSuccess) "Struk terkirim ke printer." else "Gagal cetak: ${res.exceptionOrNull()?.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cetak Struk (Thermal)", color = AirBlue, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 6.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            val text = EscPosPrinter.buildTextReceipt(b, c, tariff)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                                setPackage("com.whatsapp")
                            }
                            try {
                                ctx.startActivity(send)
                            } catch (_: Exception) {
                                val chooser = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                ctx.startActivity(Intent.createChooser(chooser, "Bagikan struk"))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Bagikan ke WhatsApp", color = Green, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 6.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ReceiptCard(b: Bill, c: Customer, t: TariffSettings) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("AIR KITA", style = MonoReceipt, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text("Tagihan Air PAMSIMAS • ${Fmt.periodLabel(b.period)}", style = MonoReceipt,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 10.sp)
            Dashed()
            RRow("Pelanggan", c.name)
            RRow("No. meter", "${c.meterNo} • ${c.address}")
            RRow("Meter awal", "${b.meterStart} m³")
            RRow("Meter akhir", "${b.meterEnd} m³")
            RRow("Pemakaian", "${b.usageM3} m³")
            Dashed()
            RRow("Blok 1 (${b.block1m3}×${Fmt.rp(t.block1Rate)})", Fmt.rp(b.block1m3.toLong() * t.block1Rate))
            RRow("Blok 2 (${b.block2m3}×${Fmt.rp(t.block2Rate)})", Fmt.rp(b.block2m3.toLong() * t.block2Rate))
            if (b.block3m3 > 0) RRow("Blok 3 (${b.block3m3}×${Fmt.rp(t.block3Rate)})", Fmt.rp(b.block3m3.toLong() * t.block3Rate))
            RRow("Abonemen", Fmt.rp(b.abonemen))
            if (b.fine > 0) RRow("Denda", Fmt.rp(b.fine))
            Dashed()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("TOTAL", style = MonoReceipt, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text(Fmt.rp(b.total), style = MonoReceipt, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            if (b.paid) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "★ LUNAS ★",
                    color = Green, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Terima kasih • bayar tepat waktu ya!",
                style = MonoReceipt, fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RRow(left: String, right: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(left, style = MonoReceipt, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text(right, style = MonoReceipt, fontSize = 11.sp)
    }
}

@Composable
private fun Dashed() {
    Text(
        "- - - - - - - - - - - - - - - - - - - -",
        style = MonoReceipt, fontSize = 10.sp, color = com.daftech.airkita.ui.theme.Muted,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        textAlign = TextAlign.Center
    )
}
