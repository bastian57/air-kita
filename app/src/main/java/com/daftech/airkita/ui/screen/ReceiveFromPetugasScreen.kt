package com.daftech.airkita.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.daftech.airkita.data.Repository
import com.daftech.airkita.data.entity.Bill
import com.daftech.airkita.engine.TariffEngine
import com.daftech.airkita.qr.QrCodec
import com.daftech.airkita.qr.QrPacket
import com.daftech.airkita.ui.components.AirBottomBar
import com.daftech.airkita.ui.components.GradientHeader
import com.daftech.airkita.ui.components.SectionTitle
import com.daftech.airkita.ui.nav.Routes
import com.daftech.airkita.ui.theme.AirBlue
import com.daftech.airkita.ui.theme.AppBg
import com.daftech.airkita.ui.theme.CardBg
import com.daftech.airkita.ui.theme.Cyan
import com.daftech.airkita.ui.theme.Green
import com.daftech.airkita.ui.theme.Muted
import com.daftech.airkita.ui.theme.Red
import com.daftech.airkita.util.Fmt
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ReadingPreview(
    val customerId: Long,
    val name: String,
    val meterStart: Int,
    val meterEnd: Int
)

/**
 * Terima hasil pencatatan dari APK Petugas via scan QR (offline).
 * Kumpulkan semua paket QR, gabungkan, pratinjau, lalu buat tagihan.
 */
@Composable
fun ReceiveFromPetugasScreen(repo: Repository, nav: NavController) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPerm = granted }
    LaunchedEffect(Unit) {
        if (!hasPerm) permLauncher.launch(Manifest.permission.CAMERA)
    }

    var packets by remember { mutableStateOf<Map<Int, QrPacket>>(emptyMap()) }
    var wrongType by remember { mutableStateOf(false) }

    val options = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    }
    val scanner = remember { BarcodeScanning.getClient(options) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var providerRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            providerRef?.unbindAll()
            cameraExecutor.shutdown()
            scanner.close()
        }
    }

    // Gabungkan paket -> JSON -> daftar bacaan
    val combinedJson = remember(packets) { QrCodec.combine(packets.values) }
    val readings = remember(combinedJson) {
        combinedJson?.let { runCatching { QrCodec.parseReadings(it) }.getOrNull() }
            ?: emptyList()
    }
    val totalParts = packets.values.firstOrNull()?.total ?: 0

    // Lookup nama pelanggan untuk pratinjau
    var preview by remember { mutableStateOf<List<ReadingPreview>>(emptyList()) }
    LaunchedEffect(readings) {
        if (readings.isEmpty()) {
            preview = emptyList()
        } else {
            preview = withContext(Dispatchers.IO) {
                readings.mapNotNull { r ->
                    val c = repo.getCustomer(r.customerId) ?: return@mapNotNull null
                    ReadingPreview(r.customerId, c.name, c.lastMeter, r.meterEnd)
                }
            }
        }
    }

    var busy by remember { mutableStateOf(false) }

    fun makeBills() {
        if (busy || readings.isEmpty()) return
        busy = true
        scope.launch(Dispatchers.IO) {
            val tariff = repo.getTariff()
            val period = Fmt.period()
            var count = 0
            for (r in readings) {
                val customer = repo.getCustomer(r.customerId) ?: continue
                if (r.meterEnd < customer.lastMeter) continue
                val calc = TariffEngine.calculate(customer.lastMeter, r.meterEnd, tariff)
                repo.saveBill(
                    Bill(
                        customerId = customer.id,
                        period = period,
                        meterStart = customer.lastMeter,
                        meterEnd = r.meterEnd,
                        usageM3 = calc.usage,
                        block1m3 = calc.b1,
                        block2m3 = calc.b2,
                        block3m3 = calc.b3,
                        abonemen = calc.abonemen,
                        fine = calc.fine,
                        total = calc.total
                    )
                )
                repo.updateCustomerLastMeter(customer.id, r.meterEnd)
                count++
            }
            withContext(Dispatchers.Main) {
                busy = false
                Toast.makeText(ctx, "$count tagihan dibuat.", Toast.LENGTH_LONG).show()
                nav.popBackStack()
            }
        }
    }

    Scaffold(
        containerColor = AppBg,
        bottomBar = { AirBottomBar(nav, Routes.HOME) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            GradientHeader("Terima dari Petugas", "Scan QR hasil pencatatan", slim = true)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (hasPerm) {
                    AndroidView(
                        factory = { c ->
                            val pv = PreviewView(c)
                            val future = ProcessCameraProvider.getInstance(c)
                            future.addListener({
                                val provider = future.get()
                                providerRef = provider
                                val previewUse = Preview.Builder().build()
                                previewUse.setSurfaceProvider(pv.surfaceProvider)
                                var lastTs = 0L
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                analysis.setAnalyzer(cameraExecutor) { proxy ->
                                    val now = SystemClock.elapsedRealtime()
                                    val media = proxy.image
                                    if (now - lastTs > 700 && media != null) {
                                        lastTs = now
                                        val image = InputImage.fromMediaImage(
                                            media, proxy.imageInfo.rotationDegrees
                                        )
                                        scanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                val raw = barcodes.firstOrNull()?.rawValue
                                                if (raw != null) {
                                                    val pkt = QrCodec.parsePacket(raw)
                                                    if (pkt != null) {
                                                        if (pkt.type != "R") {
                                                            wrongType = true
                                                        } else if (!packets.containsKey(pkt.part)) {
                                                            packets = packets + (pkt.part to pkt)
                                                        }
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener { proxy.close() }
                                    } else {
                                        proxy.close()
                                    }
                                }
                                provider.unbindAll()
                                try {
                                    provider.bindToLifecycle(
                                        lifecycle,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        previewUse,
                                        analysis
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(c, "Kamera gagal dibuka.", Toast.LENGTH_SHORT).show()
                                }
                            }, ContextCompat.getMainExecutor(c))
                            pv
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(240.dp)
                                .border(3.dp, Cyan, RoundedCornerShape(16.dp))
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Izin kamera diperlukan untuk scan QR.",
                            color = Muted, fontSize = 13.sp,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    when {
                        wrongType -> {
                            Text(
                                "QR ini bukan hasil pencatatan petugas.",
                                color = Red,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { wrongType = false; packets = emptyMap() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Ulangi") }
                        }
                        readings.isEmpty() -> {
                            SectionTitle("Pindaian")
                            Text(
                                if (totalParts > 0)
                                    "Terkumpul ${packets.size} dari $totalParts QR — pindai sisanya."
                                else
                                    "Arahkan kamera ke QR di HP petugas.",
                                fontSize = 13.sp, color = Muted
                            )
                            if (packets.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { packets = emptyMap() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Ulangi dari awal") }
                            }
                        }
                        else -> {
                            SectionTitle("Pratinjau (${preview.size})")
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            ) {
                                items(preview) { p ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 5.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(p.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Text(
                                            "${p.meterStart} → ${p.meterEnd}",
                                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            color = Green
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { makeBills() },
                                enabled = !busy && preview.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AirBlue)
                            ) {
                                Text(
                                    if (busy) "Membuat…" else "Buat Tagihan (${preview.size})",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
