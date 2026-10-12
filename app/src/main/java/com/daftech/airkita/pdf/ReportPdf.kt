package com.daftech.airkita.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.daftech.airkita.data.Repository
import com.daftech.airkita.util.Fmt
import java.io.File

/**
 * Laporan bulanan PDF (A4) — dibuat 100% di HP via android.graphics.pdf.PdfDocument.
 * Isi: kop AIR KITA, ringkasan, tabel pelanggan, penunggak, arus kas,
 * tanda tangan, footer "apk by DAFTECH".
 */
object ReportPdf {
    private const val PW = 595f
    private const val PH = 842f
    private const val M = 40f

    private class Doc(val doc: PdfDocument) {
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var canvas: Canvas
        var y = 0f

        fun newPage() {
            if (pageNo > 0) {
                footer()
                doc.finishPage(page)
            }
            pageNo++
            val pageInfoBuilder = PdfDocument.PageInfo.Builder(PW.toInt(), PH.toInt(), pageNo)
            val pageInfo: PdfDocument.PageInfo = pageInfoBuilder.build()
            page = doc.startPage(pageInfo)
            canvas = page.canvas
            y = M + 6
        }

        fun finish() {
            footer()
            doc.finishPage(page)
        }

        private fun footer() {
            val p = Paint().apply { textSize = 9f; color = 0xFF64748BL.toInt() }
            canvas.drawLine(M, PH - 44, PW - M, PH - 44, p)
            p.textAlign = Paint.Align.LEFT
            canvas.drawText("apk by DAFTECH", M, PH - 28, p)
            p.textAlign = Paint.Align.RIGHT
            canvas.drawText("Halaman $pageNo", PW - M, PH - 28, p)
        }

        fun ensure(space: Float) {
            if (y + space > PH - 70) newPage()
        }
    }

    private fun paint(size: Float, bold: Boolean, color: Int = 0xFF0F2440L.toInt()): Paint =
        Paint().apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            isAntiAlias = true
        }

    private fun Doc.text(x: Float, s: String, size: Float = 11f, bold: Boolean = false,
                         align: Paint.Align = Paint.Align.LEFT, color: Int = 0xFF0F2440L.toInt()) {
        val p = paint(size, bold, color)
        p.textAlign = align
        canvas.drawText(s, x, y, p)
    }

    private fun Doc.gap(h: Float) { y += h }

    private fun Doc.hline() {
        val p = paint(10f, false, 0xFFE3EAF3L.toInt())
        canvas.drawLine(M, y, PW - M, y, p)
        y += 10
    }

    suspend fun generate(context: Context, repo: Repository, period: String): File {
        val rows = repo.billsWithCustomerOnce(period)
        val (total, paid) = repo.billSums(period)
        val (kasIn, kasOut) = repo.kasSums(period)
        val balance = repo.kasBalance()
        val label = Fmt.periodLabel(period)
        val arrears = rows.filter { !it.bill.paid }

        val doc = PdfDocument()
        val d = Doc(doc)
        d.newPage()

        // ---- Kop ----
        d.text(PW / 2, "AIR KITA", 22f, true, Paint.Align.CENTER, 0xFF0A2E52L.toInt())
        d.gap(24f)
        d.text(PW / 2, "Laporan Bulanan Tagihan Air PAMSIMAS", 12f, true, Paint.Align.CENTER)
        d.gap(16f)
        d.text(PW / 2, label, 12f, false, Paint.Align.CENTER, 0xFF64748BL.toInt())
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
            "Saldo kas saat ini" to Fmt.rp(balance)
        ).forEach { (k, v) ->
            d.ensure(16f)
            d.text(M, k, 11f)
            d.text(PW - M, v, 11f, true, Paint.Align.RIGHT)
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
            d.text(cols[3], "m³", 10f, true)
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
            d.text(cols[5], if (b.paid) "Lunas" else "Belum", 10f, true,
                color = if (b.paid) 0xFF16A34AL.toInt() else 0xFFDC2626L.toInt())
            d.gap(14f)
        }
        d.gap(6f)
        d.hline()

        // ---- Penunggak ----
        d.text(M, "Daftar Penunggak (${arrears.size})", 13f, true)
        d.gap(16f)
        if (arrears.isEmpty()) {
            d.text(M, "Tidak ada tunggakan. Semua lunas.", 11f, false, color = 0xFF16A34AL.toInt())
            d.gap(15f)
        } else {
            arrears.forEach { r ->
                d.ensure(16f)
                d.text(M, "• ${r.customer.name} (${r.customer.meterNo})", 11f)
                d.text(PW - M, Fmt.rp(r.bill.total), 11f, true, Paint.Align.RIGHT)
                d.gap(15f)
            }
        }
        d.gap(6f)
        d.hline()

        // ---- Arus kas ----
        d.text(M, "Arus Kas Bulan $label", 13f, true)
        d.gap(16f)
        listOf(
            "Pemasukan (pembayaran iuran)" to Fmt.rp(kasIn),
            "Pengeluaran operasional" to Fmt.rp(kasOut),
            "Saldo kas saat ini" to Fmt.rp(balance)
        ).forEach { (k, v) ->
            d.ensure(16f)
            d.text(M, k, 11f)
            d.text(PW - M, v, 11f, true, Paint.Align.RIGHT)
            d.gap(15f)
        }
        d.gap(10f)

        // ---- Tanda tangan ----
        d.ensure(90f)
        d.text(M + 60, "Bendahara,", 11f, align = Paint.Align.CENTER)
        d.text(PW - M - 60, "Ketua,", 11f, align = Paint.Align.CENTER)
        d.gap(56f)
        d.text(M + 60, "( ............................ )", 11f, align = Paint.Align.CENTER)
        d.text(PW - M - 60, "( ............................ )", 11f, align = Paint.Align.CENTER)

        d.finish()
        val file = File(context.cacheDir, "Laporan-AIR-KITA-$period.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
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
