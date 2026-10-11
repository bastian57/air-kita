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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.daftech.airkita.data.Repository
import com.daftech.airkita.data.entity.Customer
import com.daftech.airkita.ui.components.GradientHeader
import com.daftech.airkita.ui.theme.AirBlue
import com.daftech.airkita.ui.theme.AppBg
import com.daftech.airkita.ui.theme.CardBg
import com.daftech.airkita.ui.theme.Cyan
import com.daftech.airkita.ui.theme.Muted
import com.daftech.airkita.util.Fmt
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/**
 * Scan angka meter pakai kamera. OCR on-device (ML Kit), gratis, offline.
 * Hasil digit bisa dikoreksi manual sebelum dipakai.
 */
@Composable
fun MeterScanScreen(repo: Repository, nav: NavController, customerId: Long) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
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

    var customer by remember { mutableStateOf<Customer?>(null) }
    LaunchedEffect(customerId) { customer = repo.getCustomer(customerId) }

    var digits by remember { mutableStateOf("") }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var providerRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            providerRef?.unbindAll()
            cameraExecutor.shutdown()
        }
    }

    Scaffold(containerColor = AppBg) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            GradientHeader(
                "Scan Meter",
                customer?.let { "${it.name} • No. ${it.meterNo}" } ?: "Memuat…",
                slim = true
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (hasPerm) {
                    AndroidView(
                        factory = { c ->
                            val pv = PreviewView(c)
                            val future = ProcessCameraProvider.getInstance(c)
                            future.addListener({
                                val provider = future.get()
                                providerRef = provider
                                val preview = Preview.Builder().build()
                                preview.setSurfaceProvider(pv.surfaceProvider)
                                var lastTs = 0L
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                analysis.setAnalyzer(cameraExecutor) { proxy ->
                                    val now = SystemClock.elapsedRealtime()
                                    val media = proxy.image
                                    if (now - lastTs > 900 && media != null) {
                                        lastTs = now
                                        val image = InputImage.fromMediaImage(
                                            media, proxy.imageInfo.rotationDegrees
                                        )
                                        recognizer.process(image)
                                            .addOnSuccessListener { result ->
                                                val d = result.text.filter { ch -> ch.isDigit() }
                                                if (d.length >= 3) digits = d.take(7)
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
                                        preview,
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
                                .size(260.dp, 110.dp)
                                .border(3.dp, Cyan, RoundedCornerShape(16.dp))
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Izin kamera diperlukan untuk scan meter.",
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
                    Text("Hasil scan — bisa dikoreksi manual", fontSize = 11.sp, color = Muted)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = digits,
                        onValueChange = { digits = it.filter { ch -> ch.isDigit() }.take(7) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Arahkan kamera ke angka meter…") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { nav.popBackStack() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Batal") }
                        Button(
                            onClick = {
                                nav.previousBackStackEntry?.savedStateHandle
                                    ?.set("scan_result", digits)
                                nav.popBackStack()
                            },
                            enabled = digits.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AirBlue)
                        ) { Text("Pakai Hasil Ini", fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "OCR berjalan di HP, tanpa internet & tanpa server.",
                        fontSize = 10.sp, color = Muted
                    )
                }
            }
        }
    }
}
