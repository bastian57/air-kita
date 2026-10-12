package com.daftech.airkita.ui.screen

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.daftech.airkita.data.Repository
import com.daftech.airkita.qr.CustomerPayload
import com.daftech.airkita.qr.QrBitmap
import com.daftech.airkita.qr.QrCodec
import com.daftech.airkita.ui.components.AirBottomBar
import com.daftech.airkita.ui.components.GradientHeader
import com.daftech.airkita.ui.components.SectionTitle
import com.daftech.airkita.ui.nav.Routes
import com.daftech.airkita.ui.theme.AppBg
import com.daftech.airkita.ui.theme.CardBg
import com.daftech.airkita.ui.theme.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bagikan daftar pelanggan ke APK Petugas via QR (offline).
 * Satu QR per ~2000 karakter base64; pindai berurutan di HP petugas.
 */
@Composable
fun ShareToPetugasScreen(repo: Repository, nav: NavController) {
    val ctx = LocalContext.current
    val customers by repo.customers().collectAsState(initial = emptyList())

    val qrStrings = remember(customers) {
        if (customers.isEmpty()) emptyList()
        else QrCodec.encodeCustomers(
            customers.map {
                CustomerPayload(it.id, it.name, it.meterNo, it.address, it.lastMeter)
            }
        )
    }
    var idx by remember { mutableStateOf(0) }
    LaunchedEffect(qrStrings.size) { idx = 0 }
    val safeIdx = idx.coerceIn(0, (qrStrings.size - 1).coerceAtLeast(0))

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(qrStrings, safeIdx) {
        bitmap = if (qrStrings.isEmpty()) null
        else withContext(Dispatchers.Default) { QrBitmap.generate(qrStrings[safeIdx], 800) }
    }
    LaunchedEffect(customers) {
        if (customers.isEmpty()) {
            Toast.makeText(ctx, "Belum ada pelanggan.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = AppBg,
        bottomBar = { AirBottomBar(nav, Routes.HOME) }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
        ) {
            GradientHeader("Bagikan ke Petugas", "Scan di HP petugas", slim = true)
            Column(Modifier.padding(14.dp)) {
                SectionTitle("QR Daftar Pelanggan")
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "${customers.size} pelanggan • ${qrStrings.size} QR",
                            fontSize = 13.sp, color = Muted
                        )
                        Spacer(Modifier.height(10.dp))
                        val bmp = bitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "QR ${safeIdx + 1}",
                                modifier = Modifier
                                    .size(280.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Text(
                                "Menyiapkan QR…",
                                fontSize = 13.sp, color = Muted,
                                modifier = Modifier.padding(vertical = 120.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (qrStrings.size > 1) {
                            Text(
                                "QR ${safeIdx + 1} dari ${qrStrings.size}",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { idx = (safeIdx - 1).coerceAtLeast(0) },
                                    enabled = safeIdx > 0,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("‹ Sebelumnya") }
                                OutlinedButton(
                                    onClick = { idx = (safeIdx + 1).coerceAtMost(qrStrings.size - 1) },
                                    enabled = safeIdx < qrStrings.size - 1,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Berikutnya ›") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Minta petugas memindai semua QR berurutan di APK Petugas.",
                            fontSize = 11.sp, color = Muted, textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
