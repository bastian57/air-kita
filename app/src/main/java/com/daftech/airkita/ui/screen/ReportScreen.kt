package com.daftech.airkita.ui.screen

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.daftech.airkita.data.Repository
import com.daftech.airkita.pdf.ReportPdf
import com.daftech.airkita.ui.components.AirBottomBar
import com.daftech.airkita.ui.components.GradientHeader
import com.daftech.airkita.ui.components.SectionTitle
import com.daftech.airkita.ui.components.StatusBadge
import com.daftech.airkita.ui.nav.Routes
import com.daftech.airkita.ui.theme.AirBlue
import com.daftech.airkita.ui.theme.AppBg
import com.daftech.airkita.ui.theme.CardBg
import com.daftech.airkita.ui.theme.Green
import com.daftech.airkita.ui.theme.Muted
import com.daftech.airkita.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ReportScreen(repo: Repository, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf(Fmt.period()) }
    var total by remember { mutableStateOf(0L) }
    var paid by remember { mutableStateOf(0L) }
    var balance by remember { mutableStateOf(0L) }
    var custCount by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(period) {
        val (t, p) = repo.billSums(period)
        total = t; paid = p
        balance = repo.kasBalance()
        custCount = repo.billsWithCustomerOnce(period).size
    }

    fun makePdf(): File? = try {
        ReportPdf.generate(ctx, repo, period)
    } catch (e: Exception) {
        null
    }

    Scaffold(
        containerColor = AppBg,
        bottomBar = { AirBottomBar(nav, Routes.HOME) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            GradientHeader("Laporan Bulanan", "‹ ${Fmt.periodLabel(period)} ›", slim = true)
            Column(Modifier.padding(14.dp)) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Ringkasan ${Fmt.periodLabel(period)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        SumRow("Total tagihan", Fmt.rp(total))
                        SumRow("Terkumpul", Fmt.rp(paid))
                        SumRow("Tunggakan", Fmt.rp(total - paid))
                        SumRow("Saldo kas", Fmt.rp(balance))
                        SumRow("Pelanggan ditagih", "$custCount")
                    }
                }
                SectionTitle("Isi PDF laporan")
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(6.dp)) {
                        listOf(
                            "Ringkasan bulan",
                            "Tabel pelanggan (pemakaian & tagihan)",
                            "Daftar penunggak",
                            "Arus kas + pengeluaran",
                            "Tanda tangan bendahara & ketua"
                        ).forEach { item ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(item, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                StatusBadge("OK", "green")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            busy = true
                            val file = makePdf()
                            val saved = file?.let { ReportPdf.saveToDownloads(ctx, it) }
                            busy = false
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    ctx,
                                    if (saved != null) "PDF tersimpan di Download." else "Gagal membuat PDF.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AirBlue)
                ) {
                    Text("Buat PDF Laporan", Modifier.padding(vertical = 6.dp), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            busy = true
                            val file = makePdf()
                            busy = false
                            withContext(Dispatchers.Main) {
                                if (file == null) {
                                    Toast.makeText(ctx, "Gagal membuat PDF.", Toast.LENGTH_LONG).show()
                                } else {
                                    val uri = FileProvider.getUriForFile(
                                        ctx, ctx.packageName + ".fileprovider", file
                                    )
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_TEXT, "Laporan bulanan AIR KITA — ${Fmt.periodLabel(period)}")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    ctx.startActivity(Intent.createChooser(share, "Bagikan ke Grup WA Warga"))
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Bagikan ke Grup WA Warga", color = Green, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "PDF tersimpan di Download • kop AIR KITA + \"apk by DAFTECH\" di footer.",
                    fontSize = 11.sp, color = Muted
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SumRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
