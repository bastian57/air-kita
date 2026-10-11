package com.daftech.airkita.ui.screen

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.daftech.airkita.data.PrefKeys
import com.daftech.airkita.data.Repository
import com.daftech.airkita.data.entity.Bill
import com.daftech.airkita.data.entity.Customer
import com.daftech.airkita.engine.TariffSettings
import com.daftech.airkita.ui.components.GradientHeader
import com.daftech.airkita.ui.theme.AirBlue
import com.daftech.airkita.ui.theme.AppBg
import com.daftech.airkita.ui.theme.CardBg
import com.daftech.airkita.ui.theme.Muted
import com.daftech.airkita.util.Fmt
import kotlinx.coroutines.launch

private data class DraftCustomer(
    var name: String = "",
    var meterNo: String = "",
    var address: String = "",
    var lastMeter: Int = 0
)

private data class DraftArrear(val customerIdx: Int, val amount: Long)

/**
 * Wizard migrasi awal (metode cut-off): tarif → pelanggan+meter terakhir →
 * tunggakan → saldo kas awal. Dijalankan sekali saat pertama buka aplikasi.
 */
@Composable
fun SetupWizardScreen(repo: Repository, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) }
    val titles = listOf("Tarif Air", "Daftar Pelanggan", "Tunggakan", "Saldo Kas Awal")

    // Step 0
    var fB1L by remember { mutableStateOf("10") }
    var fB1R by remember { mutableStateOf("2000") }
    var fB2L by remember { mutableStateOf("20") }
    var fB2R by remember { mutableStateOf("2500") }
    var fB3R by remember { mutableStateOf("3000") }
    var fAbo by remember { mutableStateOf("5000") }
    var fFine by remember { mutableStateOf("2") }

    // Step 1
    val drafts = remember { mutableStateListOf<DraftCustomer>() }
    var dName by remember { mutableStateOf("") }
    var dMeterNo by remember { mutableStateOf("") }
    var dAddr by remember { mutableStateOf("") }
    var dLast by remember { mutableStateOf("") }

    // Step 2
    val arrears = remember { mutableStateListOf<DraftArrear>() }
    var selIdx by remember { mutableStateOf(-1) }
    var arrearAmt by remember { mutableStateOf("") }

    // Step 3
    var kasOpening by remember { mutableStateOf("") }

    fun finish() {
        scope.launch {
            repo.saveTariff(
                TariffSettings(
                    fB1L.toIntOrNull() ?: 10, fB1R.toLongOrNull() ?: 2000,
                    fB2L.toIntOrNull() ?: 20, fB2R.toLongOrNull() ?: 2500,
                    fB3R.toLongOrNull() ?: 3000, fAbo.toLongOrNull() ?: 5000,
                    fFine.toDoubleOrNull() ?: 2.0
                )
            )
            val ids = drafts.map { d ->
                repo.addCustomer(Customer(name = d.name, meterNo = d.meterNo, address = d.address, lastMeter = d.lastMeter))
            }
            val prev = Fmt.prevPeriod(Fmt.period())
            arrears.forEach { a ->
                val cid = ids.getOrNull(a.customerIdx) ?: return@forEach
                repo.saveBill(
                    Bill(
                        customerId = cid, period = prev,
                        meterStart = 0, meterEnd = 0, usageM3 = 0,
                        block1m3 = 0, block2m3 = 0, block3m3 = 0,
                        abonemen = 0, fine = 0, total = a.amount, paid = false
                    )
                )
            }
            repo.setSetting(PrefKeys.KAS_OPENING, (kasOpening.toLongOrNull() ?: 0L).toString())
            repo.setSetting(PrefKeys.SETUP_DONE, "1")
            Toast.makeText(ctx, "Setup selesai. Selamat datang di AIR KITA!", Toast.LENGTH_LONG).show()
            nav.navigate(com.daftech.airkita.ui.nav.Routes.HOME) {
                popUpTo(com.daftech.airkita.ui.nav.Routes.SETUP_WIZARD) { inclusive = true }
            }
        }
    }

    Scaffold(containerColor = AppBg) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            GradientHeader("Setup Awal", "Langkah ${step + 1} dari 4: ${titles[step]}", slim = true)
            LinearProgressIndicator(
                progress = { (step + 1) / 4f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                color = AirBlue
            )
            LazyColumn(Modifier.weight(1f)) {
                item {
                    Column(Modifier.padding(14.dp)) {
                        when (step) {
                            0 -> {
                                Text("Sesuaikan dengan kesepakatan warga yang berlaku.", fontSize = 12.sp, color = Muted)
                                Spacer(Modifier.height(8.dp))
                                WizNum("Blok 1 — batas (m³)", fB1L) { fB1L = it }
                                WizNum("Blok 1 — tarif (Rp/m³)", fB1R) { fB1R = it }
                                WizNum("Blok 2 — batas kumulatif (m³)", fB2L) { fB2L = it }
                                WizNum("Blok 2 — tarif (Rp/m³)", fB2R) { fB2R = it }
                                WizNum("Blok 3 — tarif (Rp/m³)", fB3R) { fB3R = it }
                                WizNum("Abonemen / bulan (Rp)", fAbo) { fAbo = it }
                                WizNum("Denda telat (% / bulan)", fFine) { fFine = it }
                            }
                            1 -> {
                                Text("Masukkan pelanggan + angka meter TERAKHIR dari buku.", fontSize = 12.sp, color = Muted)
                                Spacer(Modifier.height(8.dp))
                                WizText("Nama", dName) { dName = it }
                                WizText("No. meter", dMeterNo) { dMeterNo = it }
                                WizText("Alamat / RT", dAddr) { dAddr = it }
                                WizNum("Meter terakhir (m³)", dLast) { dLast = it }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        if (dName.isNotBlank() && dMeterNo.isNotBlank()) {
                                            drafts.add(DraftCustomer(dName.trim(), dMeterNo.trim(), dAddr.trim(), dLast.toIntOrNull() ?: 0))
                                            dName = ""; dMeterNo = ""; dAddr = ""; dLast = ""
                                        } else {
                                            Toast.makeText(ctx, "Isi nama & no. meter.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Tambah Pelanggan") }
                            }
                            2 -> {
                                Text("Siapa yang masih punya tunggakan? (boleh kosong)", fontSize = 12.sp, color = Muted)
                                Spacer(Modifier.height(8.dp))
                                if (drafts.isEmpty()) {
                                    Text("Belum ada pelanggan di langkah 2.", fontSize = 12.sp, color = Muted)
                                } else {
                                    drafts.forEachIndexed { i, d ->
                                        Row(
                                            Modifier.fillMaxWidth().clickable { selIdx = i }.padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = selIdx == i, onClick = { selIdx = i })
                                            Text(d.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    WizNum("Nominal tunggakan (Rp)", arrearAmt) { arrearAmt = it }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val amt = arrearAmt.toLongOrNull() ?: 0L
                                            if (selIdx >= 0 && amt > 0) {
                                                arrears.add(DraftArrear(selIdx, amt))
                                                arrearAmt = ""
                                            } else {
                                                Toast.makeText(ctx, "Pilih pelanggan & isi nominal.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("Tambah Tunggakan") }
                                }
                            }
                            3 -> {
                                Text("Jumlah uang kas PAMSIMAS saat ini menurut buku.", fontSize = 12.sp, color = Muted)
                                Spacer(Modifier.height(8.dp))
                                WizNum("Saldo kas awal (Rp)", kasOpening) { kasOpening = it }
                                Spacer(Modifier.height(8.dp))
                                Text("Ringkasan: ${drafts.size} pelanggan, ${arrears.size} tunggakan.",
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (step == 1 && drafts.isNotEmpty()) {
                    itemsIndexed(drafts) { i, d ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}. ${d.name} • No. ${d.meterNo} • ${d.lastMeter} m³",
                                    fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text("Hapus", color = com.daftech.airkita.ui.theme.Red, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { drafts.removeAt(i) })
                            }
                        }
                    }
                }
                if (step == 2 && arrears.isNotEmpty()) {
                    itemsIndexed(arrears) { i, a ->
                        val nm = drafts.getOrNull(a.customerIdx)?.name ?: "?"
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("$nm — ${Fmt.rp(a.amount)}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text("Hapus", color = com.daftech.airkita.ui.theme.Red, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { arrears.removeAt(i) })
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)) { Text("Kembali") }
                    Spacer(Modifier.padding(4.dp))
                }
                Button(
                    onClick = { if (step < 3) step++ else finish() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AirBlue)
                ) {
                    Text(if (step < 3) "Lanjut" else "Selesai", fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun WizText(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        singleLine = true, shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun WizNum(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' }.take(12)) },
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        singleLine = true, shape = RoundedCornerShape(12.dp)
    )
}
