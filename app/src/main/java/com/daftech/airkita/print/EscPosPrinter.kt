package com.daftech.airkita.print

import android.bluetooth.BluetoothManager
import android.content.Context
import com.daftech.airkita.data.entity.Bill
import com.daftech.airkita.data.entity.Customer
import com.daftech.airkita.engine.TariffSettings
import com.daftech.airkita.util.Fmt
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Cetak struk ke printer thermal 58mm via Bluetooth SPP (RFCOMM).
 * ESC/POS: init, align, teks 32 kolom, feed + cut.
 */
object EscPosPrinter {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val COLS = 32

    fun print(context: Context, mac: String, data: ByteArray): Result<Unit> {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: return Result.failure(Exception("Bluetooth tidak tersedia"))
            if (!adapter.isEnabled) return Result.failure(Exception("Bluetooth mati"))
            try { adapter.cancelDiscovery() } catch (_: SecurityException) { /* abaikan */ }
            val device = try {
                adapter.getRemoteDevice(mac)
            } catch (e: IllegalArgumentException) {
                return Result.failure(Exception("Alamat printer tidak valid"))
            } catch (e: SecurityException) {
                return Result.failure(e)
            }
            val socket = try {
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: SecurityException) {
                return Result.failure(e)
            } catch (e: Exception) {
                return Result.failure(e)
            }
            try {
                socket.connect()
                socket.outputStream.use { it.write(data); it.flush() }
                Thread.sleep(400)
            } finally {
                try { socket.close() } catch (_: Exception) { }
            }
            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(Exception("Izin Bluetooth ditolak"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Bangun byte ESC/POS struk tagihan. */
    fun buildReceipt(b: Bill, c: Customer, t: TariffSettings): ByteArray {
        val out = ByteArrayOutputStream()
        fun raw(vararg bs: Int) = bs.forEach { out.write(it) }
        fun txt(s: String) = out.write(san(s).toByteArray(Charsets.US_ASCII))
        fun nl() = raw(0x0A)
        fun center() = raw(0x1B, 0x61, 0x01)
        fun left() = raw(0x1B, 0x61, 0x00)
        fun bold(on: Boolean) = raw(0x1B, 0x45, if (on) 1 else 0)
        fun big(on: Boolean) = raw(0x1D, 0x21, if (on) 0x11 else 0x00)
        fun row(l: String, r: String) {
            val rr = if (r.length > COLS) r.take(COLS) else r
            var ll = l
            if (ll.length + rr.length > COLS) ll = ll.take(COLS - rr.length)
            txt(ll + " ".repeat(COLS - ll.length - rr.length) + rr); nl()
        }
        fun dash() { txt("-".repeat(COLS)); nl() }

        raw(0x1B, 0x40) // init
        center(); bold(true); big(true); txt("AIR KITA"); nl(); big(false); bold(false)
        txt("Tagihan Air PAMSIMAS"); nl()
        txt(Fmt.periodLabel(b.period)); nl()
        dash()
        left()
        row("Pelanggan", c.name.take(18))
        row("No. meter", "${c.meterNo} ${c.address}".take(20).trim())
        row("Meter awal", "${b.meterStart} m3")
        row("Meter akhir", "${b.meterEnd} m3")
        row("Pemakaian", "${b.usageM3} m3")
        if (b.paid && b.paidAt != null) {
            row("Tgl bayar", Fmt.dateStr(b.paidAt))
        }
        dash()
        row("Blok1 ${b.block1m3}x${t.block1Rate}", Fmt.rp(b.block1m3.toLong() * t.block1Rate).replace("Rp", "Rp"))
        row("Blok2 ${b.block2m3}x${t.block2Rate}", Fmt.rp(b.block2m3.toLong() * t.block2Rate))
        if (b.block3m3 > 0) row("Blok3 ${b.block3m3}x${t.block3Rate}", Fmt.rp(b.block3m3.toLong() * t.block3Rate))
        row("Abonemen", Fmt.rp(b.abonemen))
        if (b.fine > 0) row("Denda", Fmt.rp(b.fine))
        dash()
        bold(true); row("TOTAL", Fmt.rp(b.total)); bold(false)
        nl()
        if (b.paid) { center(); bold(true); txt("** LUNAS **"); nl(); bold(false); left() }
        center(); txt("Terima kasih."); nl(); txt("Bayar tepat waktu ya!"); nl()
        raw(0x1B, 0x64, 0x04) // feed 4
        raw(0x1D, 0x56, 0x00) // cut
        return out.toByteArray()
    }

    /** Versi teks biasa untuk dibagikan via WhatsApp. */
    fun buildTextReceipt(b: Bill, c: Customer, t: TariffSettings): String {
        val sb = StringBuilder()
        sb.appendLine("*AIR KITA*")
        sb.appendLine("Tagihan Air PAMSIMAS • ${Fmt.periodLabel(b.period)}")
        sb.appendLine("--------------------------------")
        sb.appendLine("Pelanggan : ${c.name}")
        sb.appendLine("No. meter : ${c.meterNo} • ${c.address}")
        sb.appendLine("Meter awal: ${b.meterStart} m³")
        sb.appendLine("Meter akhir: ${b.meterEnd} m³")
        sb.appendLine("Pemakaian : ${b.usageM3} m³")
        if (b.paid && b.paidAt != null) {
            sb.appendLine("Tanggal bayar: ${Fmt.dateStr(b.paidAt)}")
        }
        sb.appendLine("--------------------------------")
        sb.appendLine("Blok 1 (${b.block1m3} × ${Fmt.rp(t.block1Rate)}): ${Fmt.rp(b.block1m3.toLong() * t.block1Rate)}")
        sb.appendLine("Blok 2 (${b.block2m3} × ${Fmt.rp(t.block2Rate)}): ${Fmt.rp(b.block2m3.toLong() * t.block2Rate)}")
        if (b.block3m3 > 0) sb.appendLine("Blok 3 (${b.block3m3} × ${Fmt.rp(t.block3Rate)}): ${Fmt.rp(b.block3m3.toLong() * t.block3Rate)}")
        sb.appendLine("Abonemen: ${Fmt.rp(b.abonemen)}")
        if (b.fine > 0) sb.appendLine("Denda: ${Fmt.rp(b.fine)}")
        sb.appendLine("--------------------------------")
        sb.appendLine("*TOTAL: ${Fmt.rp(b.total)}*")
        if (b.paid) sb.appendLine("Status: LUNAS ✅")
        sb.appendLine("Terima kasih 🙏")
        return sb.toString()
    }

    /** Hanya karakter ASCII yang aman untuk printer thermal. */
    private fun san(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when (ch) {
                '³' -> sb.append('3')
                '•', '·' -> sb.append('-')
                '×' -> sb.append('x')
                '−', '–', '—' -> sb.append('-')
                '★' -> sb.append('*')
                '‹', '›', '«', '»' -> {}
                else -> if (ch.code in 32..126) sb.append(ch) else sb.append('?')
            }
        }
        return sb.toString()
    }
}
