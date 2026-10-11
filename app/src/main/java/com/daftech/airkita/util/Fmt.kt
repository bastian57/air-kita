package com.daftech.airkita.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    private val idLocale = Locale("in", "ID")
    private val months = arrayOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )

    fun rp(v: Long): String = "Rp" + NumberFormat.getNumberInstance(idLocale).format(v)

    /** Periode berjalan: "yyyy-MM". */
    fun period(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    /** "2026-10" -> "Oktober 2026". */
    fun periodLabel(period: String): String {
        val p = period.split("-")
        if (p.size != 2) return period
        val m = p[1].toIntOrNull() ?: return period
        if (m !in 1..12) return period
        return "${months[m - 1]} ${p[0]}"
    }

    fun prevPeriod(period: String): String {
        val p = period.split("-")
        var y = p.getOrNull(0)?.toIntOrNull() ?: 2026
        var m = p.getOrNull(1)?.toIntOrNull() ?: 1
        m -= 1
        if (m < 1) { m = 12; y -= 1 }
        return "%04d-%02d".format(y, m)
    }

    fun nextPeriod(period: String): String {
        val p = period.split("-")
        var y = p.getOrNull(0)?.toIntOrNull() ?: 2026
        var m = p.getOrNull(1)?.toIntOrNull() ?: 1
        m += 1
        if (m > 12) { m = 1; y += 1 }
        return "%04d-%02d".format(y, m)
    }

    fun dateStr(ts: Long): String =
        SimpleDateFormat("d MMM yyyy", idLocale).format(Date(ts))

    fun fileStamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}
