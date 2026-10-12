package com.daftech.airkita.ui.screen

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.daftech.airkita.backup.DriveBackup
import com.daftech.airkita.data.PrefKeys
import com.daftech.airkita.data.Repository
import com.daftech.airkita.engine.TariffSettings
import com.daftech.airkita.pdf.ReportPdf
import com.daftech.airkita.ui.components.AirBottomBar
import com.daftech.airkita.ui.components.GradientHeader
import com.daftech.airkita.ui.components.SectionTitle
import com.daftech.airkita.ui.nav.Routes
import com.daftech.airkita.ui.theme.AirBlue
import com.daftech.airkita.ui.theme.AppBg
import com.daftech.airkita.ui.theme.CardBg
import com.daftech.airkita.ui.theme.Muted
import com.daftech.airkita.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(repo: Repository, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    // ---- Tarif ----
    var fB1L by remember { mutableStateOf("10") }
    var fB1R by remember { mutableStateOf("2000") }
    var fB2L by remember { mutableStateOf("20") }
    var fB2R by remember { mutableStateOf("2500") }
    var fB3R by remember { mutableStateOf("3000") }
    var fAbo by remember { mutableStateOf("5000") }
    var fFine by remember { mutableStateOf("2") }
    LaunchedEffect(Unit) {
        val t = repo.getTariff()
        fB1L = t.block1Limit.toString(); fB1R = t.block1Rate.toString()
        fB2L = t.block2Limit.toString(); fB2R = t.block2Rate.toString()
        fB3R = t.block3Rate.toString(); fAbo = t.abonemen.toString()
        fFine = t.finePercent.toString()
    }

    // ---- Printer ----
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var savedMac by remember { mutableStateOf("") }
    var savedName by remember { mutableStateOf("") }
    var discovered by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) devices = bondedDevices(ctx) }
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res.values.all { it }) {
            discovered = emptyList()
            scanning = true
            startBtDiscovery(ctx, onDone = { scanning = false })
        } else {
            Toast.makeText(ctx, "Izin Bluetooth ditolak.", Toast.LENGTH_SHORT).show()
        }
    }
    // Receiver hasil pindaian
    androidx.compose.runtime.DisposableEffect(Unit) {
        val recv = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        @Suppress("DEPRECATION")
                        val d: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (d != null &&
                            discovered.none { it.address == d.address } &&
                            devices.none { it.address == d.address }) {
                            discovered = discovered + d
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> scanning = false
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ctx.registerReceiver(recv, filter)
        onDispose {
            try { ctx.unregisterReceiver(recv) } catch (_: Exception) {}
            try {
                val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                if (bm?.adapter?.isDiscovering == true) bm.adapter.cancelDiscovery()
            } catch (_: SecurityException) {}
        }
    }
    LaunchedEffect(Unit) {
        savedMac = repo.getSetting(PrefKeys.PRINTER_MAC)
        savedName = repo.getSetting(PrefKeys.PRINTER_NAME)
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            devices = bondedDevices(ctx)
        }
    }

    // ---- WA ----
    var waNumber by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { waNumber = repo.getSetting(PrefKeys.WA_NUMBER) }

    // ---- Tambah pelanggan baru ----
    var showAddCustomer by remember { mutableStateOf(false) }
    var cName by remember { mutableStateOf("") }
    var cMeterNo by remember { mutableStateOf("") }
    var cAddress by remember { mutableStateOf("") }
    var cLastMeter by remember { mutableStateOf("") }

    // ---- Restore ----
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                    repo.importJson(text)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Restore berhasil.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Restore gagal: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun doBackup() {
        scope.launch(Dispatchers.IO) {
            busy = true
            val res = DriveBackup.backup(ctx, repo, Fmt.period())
            withContext(Dispatchers.Main) {
                busy = false
                if (res.ok) {
                    Toast.makeText(ctx, res.message, Toast.LENGTH_LONG).show()
                } else if (res.message == DriveBackup.PLACEHOLDER) {
                    // Fallback: simpan PDF + JSON ke Download, bagikan via share sheet
                    scope.launch(Dispatchers.IO) {
                        try {
                            val json = repo.exportJson()
                            val jf = File(ctx.cacheDir, "airkita-backup-${Fmt.fileStamp()}.json")
                            jf.writeText(json)
                            val pdf = ReportPdf.generate(ctx, repo, Fmt.period())
                            ReportPdf.saveToDownloads(ctx, jf)
                            ReportPdf.saveToDownloads(ctx, pdf)
                            val uris = arrayListOf(
                                FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", jf),
                                FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", pdf)
                            )
                            withContext(Dispatchers.Main) {
                                val share = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                    putExtra(Intent.EXTRA_TEXT, "Backup AIR KITA — ${Fmt.fileStamp()}")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ctx.startActivity(Intent.createChooser(share, "Kirim backup (tersimpan juga di Download)"))
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(ctx, "Backup gagal: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(ctx, res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = AppBg,
        bottomBar = { AirBottomBar(nav, Routes.HOME) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            GradientHeader("Pengaturan", "AIR KITA • v1.0.0", slim = true)
            Column(Modifier.padding(14.dp)) {
                SectionTitle("Perubahan Tarif/m³")
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        TariffField("Blok 1 — batas (m³)", fB1L) { fB1L = it }
                        TariffField("Blok 1 — tarif (Rp/m³)", fB1R) { fB1R = it }
                        TariffField("Blok 2 — batas kumulatif (m³)", fB2L) { fB2L = it }
                        TariffField("Blok 2 — tarif (Rp/m³)", fB2R) { fB2R = it }
                        TariffField("Blok 3 — tarif (Rp/m³)", fB3R) { fB3R = it }
                        TariffField("Abonemen / bulan (Rp)", fAbo) { fAbo = it }
                        TariffField("Denda telat (% / bulan)", fFine) { fFine = it }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    repo.saveTariff(
                                        TariffSettings(
                                            block1Limit = fB1L.toIntOrNull() ?: 10,
                                            block1Rate = fB1R.toLongOrNull() ?: 2000,
                                            block2Limit = fB2L.toIntOrNull() ?: 20,
                                            block2Rate = fB2R.toLongOrNull() ?: 2500,
                                            block3Rate = fB3R.toLongOrNull() ?: 3000,
                                            abonemen = fAbo.toLongOrNull() ?: 5000,
                                            finePercent = fFine.toDoubleOrNull() ?: 2.0
                                        )
                                    )
                                    Toast.makeText(ctx, "Tarif tersimpan.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AirBlue)
                        ) { Text("Simpan Tarif", fontWeight = FontWeight.Bold) }
                    }
                }

                SectionTitle("Pelanggan")
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Tambah pelanggan baru tanpa membuka Setup Awal.",
                            fontSize = 12.sp, color = Muted,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Button(
                            onClick = {
                                cName = ""; cMeterNo = ""; cAddress = ""; cLastMeter = ""
                                showAddCustomer = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AirBlue)
                        ) { Text("Tambah Pelanggan Baru", fontWeight = FontWeight.Bold) }
                    }
                }

                SectionTitle("Printer thermal")
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        if (devices.isEmpty()) {
                            Text("Tidak ada perangkat Bluetooth yang di-pairing. Pairing dulu di Pengaturan HP.",
                                fontSize = 12.sp, color = Muted)
                        } else {
                            devices.forEach { d ->
                                val name = try { d.name ?: d.address } catch (_: SecurityException) { d.address }
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        scope.launch {
                                            repo.setSetting(PrefKeys.PRINTER_MAC, d.address)
                                            repo.setSetting(PrefKeys.PRINTER_NAME, name)
                                            savedMac = d.address; savedName = name
                                            Toast.makeText(ctx, "Printer dipilih: $name", Toast.LENGTH_SHORT).show()
                                        }
                                    }.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = d.address == savedMac, onClick = null)
                                    Text(name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        if (discovered.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Hasil pindaian:", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp))
                            discovered.forEach { d ->
                                val name = try { d.name ?: d.address } catch (_: SecurityException) { d.address }
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        scope.launch {
                                            repo.setSetting(PrefKeys.PRINTER_MAC, d.address)
                                            repo.setSetting(PrefKeys.PRINTER_NAME, name)
                                            savedMac = d.address; savedName = name
                                            Toast.makeText(ctx, "Printer dipilih: $name", Toast.LENGTH_SHORT).show()
                                        }
                                    }.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = d.address == savedMac, onClick = null)
                                    Column(Modifier.weight(1f)) {
                                        Text(name, fontSize = 13.sp)
                                        Text(d.address, fontSize = 11.sp, color = Muted)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 31) {
                                    val perms = arrayOf(
                                        Manifest.permission.BLUETOOTH_SCAN,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    )
                                    val missing = perms.filter {
                                        ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
                                    }
                                    if (missing.isEmpty()) {
                                        discovered = emptyList()
                                        scanning = true
                                        startBtDiscovery(ctx, onDone = { scanning = false })
                                    } else {
                                        scanLauncher.launch(missing.toTypedArray())
                                    }
                                } else {
                                    discovered = emptyList()
                                    scanning = true
                                    startBtDiscovery(ctx, onDone = { scanning = false })
                                }
                            },
                            enabled = !scanning,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(if (scanning) "Memindai…" else "Pindai Perangkat Bluetooth Baru") }
                        if (savedMac.isNotBlank()) {
                            Text("Terpilih: $savedName ($savedMac)", fontSize = 11.sp, color = Muted,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                SectionTitle("Data & backup")
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = waNumber,
                            onValueChange = { waNumber = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Nomor WA saya (tujuan backup)") },
                            placeholder = { Text("08…") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    repo.setSetting(PrefKeys.WA_NUMBER, waNumber)
                                    doBackup()
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AirBlue)
                        ) { Text("Backup Sekarang (ke Drive)", fontWeight = FontWeight.Bold) }
                        Text(
                            "Backup berisi PDF arsip + file data (bisa di-restore). Tanpa kredensial Drive, backup disimpan ke Download & dibagikan manual.",
                            fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 6.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { restoreLauncher.launch("application/json") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Restore dari File JSON") }
                        OutlinedButton(
                            onClick = { nav.navigate(Routes.SETUP_WIZARD) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Setup Awal / Migrasi Buku") }
                    }
                }

                SectionTitle("Petugas Lapangan")
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Kirim daftar pelanggan ke HP petugas via QR, terima hasil pencatatan via QR.",
                            fontSize = 12.sp, color = Muted,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Button(
                            onClick = { nav.navigate(Routes.SHARE_PETUGAS) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AirBlue)
                        ) { Text("Bagikan ke Petugas (QR)", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { nav.navigate(Routes.RECEIVE_PETUGAS) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Terima dari Petugas (Scan QR)") }
                    }
                }

                SectionTitle("Tentang")
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = com.daftech.airkita.ui.theme.Navy),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("AIR KITA", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Text("Admin Tagihan Air PAMSIMAS • v1.0.0",
                            color = Color.White.copy(0.75f), fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "apk by DAFTECH",
                            color = Color.White.copy(0.85f), fontSize = 11.sp,
                            letterSpacing = 1.5.sp, textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    // ---- Dialog tambah pelanggan baru ----
    if (showAddCustomer) {
        AlertDialog(
            onDismissRequest = { showAddCustomer = false },
            title = { Text("Tambah Pelanggan Baru", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = cName,
                        onValueChange = { cName = it },
                        label = { Text("Nama pelanggan") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = cMeterNo,
                        onValueChange = { cMeterNo = it },
                        label = { Text("No. meter") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = cAddress,
                        onValueChange = { cAddress = it },
                        label = { Text("Alamat") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = cLastMeter,
                        onValueChange = { cLastMeter = it.filter { ch -> ch.isDigit() }.take(9) },
                        label = { Text("Meter awal (m³)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (cName.isBlank() || cMeterNo.isBlank()) {
                            Toast.makeText(ctx, "Nama dan No. meter wajib diisi.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch(Dispatchers.IO) {
                            repo.addCustomer(
                                com.daftech.airkita.data.entity.Customer(
                                    name = cName.trim(),
                                    meterNo = cMeterNo.trim(),
                                    address = cAddress.trim(),
                                    lastMeter = cLastMeter.toIntOrNull() ?: 0
                                )
                            )
                            withContext(Dispatchers.Main) {
                                showAddCustomer = false
                                Toast.makeText(ctx, "Pelanggan ditambahkan.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AirBlue)
                ) { Text("Simpan") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddCustomer = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun TariffField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' }.take(12)) },
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

private fun bondedDevices(ctx: Context): List<BluetoothDevice> {
    return try {
        val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bm?.adapter?.bondedDevices?.toList() ?: emptyList()
    } catch (_: SecurityException) {
        emptyList()
    }
}

/** Mulai Bluetooth discovery (pindai perangkat baru di sekitar).
 *  Hasil ditemukan via BroadcastReceiver yang didaftarkan di DisposableEffect. */
private fun startBtDiscovery(ctx: Context, onDone: () -> Unit) {
    try {
        val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter ?: run { onDone(); return }
        if (!adapter.isEnabled) { onDone(); return }
        try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
        } catch (_: SecurityException) {}
        if (!adapter.startDiscovery()) onDone()
    } catch (_: SecurityException) {
        onDone()
    }
}
