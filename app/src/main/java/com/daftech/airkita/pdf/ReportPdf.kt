package com.daftech.airkita.pdf

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.daftech.airkita.data.PrefKeys
import com.daftech.airkita.data.Repository
import com.daftech.airkita.util.Fmt
import java.io.File

/**
 * Laporan bulanan PDF (A4) — dibuat 100% di HP via SimplePdf (murni Kotlin).
 * Isi: kop AIR KITA, ringkasan, tabel pelanggan, penunggak, arus kas,
 * tanda tangan, footer "apk by DAFTECH".
 */
object ReportPdf {
    private const val PW = SimplePdf.PW
    private const val PH = SimplePdf.PH
    private const val M = 40f

    private class Doc {
        val pages = mutableListOf<SimplePdf.Page>()
        var page: SimplePdf.Page = SimplePdf.Page()
        var y = 0f
        var pageNo = 0

        init { newPage() }

        fun newPage() {
            if (pageNo > 0) {
                footer()
                pages.add(page)
            }
            pageNo++
            page = SimplePdf.Page()
            y = M + 6
        }

        fun finish() {
            footer()
            pages.add(page)
        }

        private fun footer() {
            page.line(M, PH - 44, PW - M, PH - 44)
            page.text(M, PH - 28, "apk by DAFTECH", 9f)
            page.text(PW - M, PH - 28, "Halaman $pageNo", 9f, align = 2)
        }

        fun ensure(space: Float) {
            if (y + space > PH - 70) newPage()
        }

        fun text(x: Float, s: String, size: Float = 11f, bold: Boolean = false, align: Int = 0) {
            page.text(x, y, s, size, bold, align)
        }
        fun gap(h: Float) { y += h }
        fun hline() {
            page.line(M, y, PW - M, y)
            y += 10
        }
    }

    suspend fun generate(context: Context, repo: Repository, period: String): File {
        val rows = repo.billsWithCustomerOnce(period)
        val (total, paid) = repo.billSums(period)
        val (kasIn, kasOut) = repo.kasSums(period)
        val balance = repo.kasBalance()
        val opening = repo.getSetting(PrefKeys.KAS_OPENING, "0").toLongOrNull() ?: 0L
        val label = Fmt.periodLabel(period)
        val arrears = rows.filter { !it.bill.paid }

        val d = Doc()

        // ---- Kop ----
        d.text(PW / 2, "AIR KITA", 22f, true, 1)
        d.gap(24f)
        d.text(PW / 2, "Laporan Bulanan Tagihan Air PAMSIMAS", 12f, true, 1)
        d.gap(16f)
        d.text(PW / 2, label, 12f, align = 1)
        d.gap(10f)
        d.hline()

        // ---- Ringkasan ----
        d.text(M, "Ringkasan", 13f, true)
        d.gap(16f)
        val pct = if (total > 0) (paid * 100 / total).toInt() else 0
        listOf(
            "Total tagihan diterbitkan" to Fmt.rp(total),
            "Terkumpul ($pct%)" to Fmt.rp(paid),
            "Tunggakan" to Fmt.rp(total - paid),
            "Pemasukan kas bulan ini" to Fmt.rp(kasIn),
            "Pengeluaran kas bulan ini" to Fmt.rp(kasOut),
            "Saldo akhir kas (total)" to Fmt.rp(balance)
        ).forEach { (k, v) ->
            d.ensure(16f)
            d.text(M, k, 11f)
            d.text(PW - M, v, 11f, true, 2)
            d.gap(15f)
        }
        d.gap(6f)
        d.hline()

        // ---- Tabel pelanggan ----
        d.text(M, "Daftar Pelanggan (${rows.size})", 13f, true)
        d.gap(18f)
        val cols = floatArrayOf(M, M + 26, M + 170, M + 268, M + 330, M + 420)
        fun header() {
            d.text(cols[0], "No", 10f, true)
            d.text(cols[1], "Nama", 10f, true)
            d.text(cols[2], "Meter", 10f, true)
            d.text(cols[3], "m3", 10f, true)
            d.text(cols[4], "Tagihan", 10f, true)
            d.text(cols[5], "Status", 10f, true)
            d.gap(14f)
        }
        header()
        rows.forEachIndexed { i, r ->
            d.ensure(16f)
            val b = r.bill
            d.text(cols[0], "${i + 1}", 10f)
            d.text(cols[1], r.customer.name.take(24), 10f)
            d.text(cols[2], "${b.meterStart}-${b.meterEnd}", 10f)
            d.text(cols[3], "${b.usageM3}", 10f)
            d.text(cols[4], Fmt.rp(b.total), 10f)
            d.text(cols[5], if (b.paid) "Lunas" else "Belum", 10f, true)
            d.gap(14f)
        }
        d.gap(6f)
        d.hline()

        // ---- Penunggak ----
        d.text(M, "Daftar Penunggak (${arrears.size})", 13f, true)
        d.gap(16f)
        if (arrears.isEmpty()) {
            d.text(M, "Tidak ada tunggakan. Semua lunas.", 11f)
            d.gap(15f)
        } else {
            arrears.forEach { r ->
                d.ensure(16f)
                d.text(M, "- ${r.customer.name} (${r.customer.meterNo})", 11f)
                d.text(PW - M, Fmt.rp(r.bill.total), 11f, true, 2)
                d.gap(15f)
            }
        }
        d.gap(6f)
        d.hline()

        // ---- Arus kas ----
        d.text(M, "Arus Kas Bulan $label", 13f, true)
        d.gap(16f)
        listOf(
            "Saldo awal kas" to Fmt.rp(opening),
            "Pemasukan (pembayaran iuran)" to Fmt.rp(kasIn),
            "Pengeluaran operasional" to Fmt.rp(kasOut),
            "Saldo akhir kas (total)" to Fmt.rp(balance)
        ).forEach { (k, v) ->
            d.ensure(16f)
            d.text(M, k, 11f)
            d.text(PW - M, v, 11f, true, 2)
            d.gap(15f)
        }
        d.gap(10f)

        // ---- Tanda tangan ----
        d.ensure(90f)
        d.text(M + 60, "Bendahara,", 11f, align = 1)
        d.text(PW - M - 60, "Ketua,", 11f, align = 1)
        d.gap(56f)
        d.text(M + 60, "( ............................ )", 11f, align = 1)
        d.text(PW - M - 60, "( ............................ )", 11f, align = 1)

        d.finish()
        val bytes = SimplePdf.generate(d.pages)
        val file = File(context.cacheDir, "Laporan-AIR-KITA-$period.pdf")
        file.writeBytes(bytes)
        return file
    }

    /** Salin file (PDF/JSON) ke folder Download. API 29+ via MediaStore, di bawah itu via file langsung. */
    fun saveToDownloads(context: Context, file: File): Uri? {
        val name = file.name
        val mime = when {
            name.endsWith(".pdf", true) -> "application/pdf"
            name.endsWith(".json", true) -> "application/json"
            else -> "application/octet-stream"
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val dest = File(dir, name)
                file.copyTo(dest, overwrite = true)
                Uri.fromFile(dest)
            }
        } catch (_: Exception) {
            null
        }
    }
}
