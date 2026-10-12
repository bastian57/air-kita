package com.daftech.airkita.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftech.airkita.license.LicenseManager
import com.daftech.airkita.license.LicenseResult
import com.daftech.airkita.ui.theme.AirBlue
import com.daftech.airkita.ui.theme.AppBg
import com.daftech.airkita.ui.theme.CardBg
import com.daftech.airkita.ui.theme.Green
import com.daftech.airkita.ui.theme.Muted
import com.daftech.airkita.ui.theme.Navy
import com.daftech.airkita.ui.theme.Red

/**
 * Layar aktivasi lisensi — tampil saat aplikasi belum diaktivasi.
 * Pengguna memasukkan kode lisensi dari DAFTECH yang terikat ke Device ID HP ini.
 */
@Composable
fun ActivationScreen(onActivated: () -> Unit) {
    val ctx = LocalContext.current
    val deviceId = remember { LicenseManager.getDeviceId(ctx) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(AppBg)) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Navy, AirBlue)))
                .padding(horizontal = 22.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("💧", fontSize = 44.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Aktivasi AIR KITA",
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            Text(
                "Masukkan kode lisensi dari DAFTECH",
                color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        Column(
            Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Device ID
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Device ID HP ini",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        deviceId,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Device ID", deviceId))
                            Toast.makeText(ctx, "Device ID disalin", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("📋 Salin Device ID", fontSize = 13.sp) }
                    Text(
                        "Kirim Device ID ini ke DAFTECH via WhatsApp untuk mendapatkan kode lisensi.",
                        fontSize = 11.sp, color = Muted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Kode lisensi
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Kode Lisensi",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("AK1-...", fontSize = 13.sp) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = false,
                        minLines = 2
                    )
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "⚠️ $error",
                            fontSize = 12.sp, color = Red, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (code.isBlank()) {
                                error = "Masukkan kode lisensi dulu"
                                return@Button
                            }
                            busy = true
                            val result = LicenseManager.validateLicense(code, deviceId)
                            busy = false
                            when (result) {
                                is LicenseResult.Valid -> {
                                    val prefs = ctx.getSharedPreferences(
                                        LicenseManager.PREFS_NAME, Context.MODE_PRIVATE
                                    )
                                    LicenseManager.saveLicense(
                                        prefs, code, result.buyerName, result.expiry
                                    )
                                    Toast.makeText(
                                        ctx,
                                        "Aktif! Selamat datang, ${result.buyerName}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onActivated()
                                }
                                is LicenseResult.Invalid -> {
                                    error = result.reason
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green)
                    ) {
                        Text(
                            if (busy) "Memeriksa..." else "🔓 Aktifkan",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "apk by DAFTECH",
                    fontSize = 11.sp, color = Muted
                )
            }
        }
    }
}
