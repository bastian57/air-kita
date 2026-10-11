package com.daftech.airkita.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.daftech.airkita.data.entity.KasTransaction
import com.daftech.airkita.ui.components.AirBottomBar
import com.daftech.airkita.ui.components.GradientHeader
import com.daftech.airkita.ui.components.SectionTitle
import com.daftech.airkita.ui.components.StatCard
import com.daftech.airkita.ui.nav.Routes
import com.daftech.airkita.ui.theme.AirBlue
import com.daftech.airkita.ui.theme.AppBg
import com.daftech.airkita.ui.theme.CardBg
import com.daftech.airkita.ui.theme.Green
import com.daftech.airkita.ui.theme.GreenSoft
import com.daftech.airkita.ui.theme.Ink
import com.daftech.airkita.ui.theme.Muted
import com.daftech.airkita.ui.theme.Red
import com.daftech.airkita.ui.theme.RedSoft
import com.daftech.airkita.util.Fmt
import kotlinx.coroutines.launch

@Composable
fun KasScreen(repo: Repository, nav: NavController) {
    val period = remember { Fmt.period() }
    val scope = rememberCoroutineScope()
    val txns by repo.kasForPeriod(period).collectAsState(initial = emptyList())
    var balance by remember { mutableStateOf(0L) }
    var sumIn by remember { mutableStateOf(0L) }
    var sumOut by remember { mutableStateOf(0L) }
    LaunchedEffect(period) {
        balance = repo.kasBalance()
        val (i, o) = repo.kasSums(period)
        sumIn = i; sumOut = o
    }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = AppBg,
        bottomBar = { AirBottomBar(nav, Routes.KAS) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = AirBlue,
                contentColor = Color.White
            ) { Icon(Icons.Filled.Add, contentDescription = "Tambah pengeluaran") }
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                GradientHeader("Kas AIR KITA", Fmt.periodLabel(period), slim = true)
            }
            item {
                Column(Modifier.padding(14.dp)) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Brush.linearGradient(listOf(com.daftech.airkita.ui.theme.Navy, AirBlue)))
                                .padding(16.dp)
                        ) {
                            Text("Saldo kas saat ini", color = Color.White.copy(0.85f), fontSize = 12.sp)
                            Text(Fmt.rp(balance), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Diperbarui otomatis setiap pembayaran", color = Color.White.copy(0.7f), fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Pemasukan bln ini", Fmt.rp(sumIn), Green, Modifier.weight(1f))
                        StatCard("Pengeluaran bln ini", Fmt.rp(sumOut), Red, Modifier.weight(1f))
                    }
                    SectionTitle("Transaksi terakhir")
                }
            }
            items(txns, key = { it.id }) { t ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(36.dp)
                                .background(if (t.kind == "IN") GreenSoft else RedSoft, RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (t.kind == "IN") Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                contentDescription = null,
                                tint = if (t.kind == "IN") Green else Red,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(t.note.ifBlank { t.category }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "${Fmt.dateStr(t.date)} • ${t.category}${if (t.auto) " • otomatis" else ""}",
                                fontSize = 11.sp, color = Muted
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            (if (t.kind == "IN") "+" else "−") + Fmt.rp(t.amount),
                            fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                            color = if (t.kind == "IN") Green else Red
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAdd) {
        var note by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Operasional") }
        var amountStr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Catat Pengeluaran", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("Keterangan (mis. Beli klorin)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = category, onValueChange = { category = it },
                        label = { Text("Kategori") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it.filter { ch -> ch.isDigit() }.take(12) },
                        label = { Text("Nominal (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountStr.toLongOrNull() ?: 0L
                        if (note.isNotBlank() && amt > 0) {
                            scope.launch {
                                repo.addKas(
                                    KasTransaction(
                                        date = System.currentTimeMillis(),
                                        kind = "OUT", category = category.ifBlank { "Lainnya" },
                                        note = note, amount = amt, auto = false
                                    )
                                )
                                balance = repo.kasBalance()
                                val (i, o) = repo.kasSums(period)
                                sumIn = i; sumOut = o
                            }
                            showAdd = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AirBlue)
                ) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Batal") }
            }
        )
    }
}
