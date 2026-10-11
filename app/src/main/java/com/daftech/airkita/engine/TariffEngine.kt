package com.daftech.airkita.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Konfigurasi tarif bertingkat. block2Limit = batas kumulatif blok 2 (mis. 20). */
data class TariffSettings(
    val block1Limit: Int = 10,
    val block1Rate: Long = 2000,
    val block2Limit: Int = 20,
    val block2Rate: Long = 2500,
    val block3Rate: Long = 3000,
    val abonemen: Long = 5000,
    val finePercent: Double = 2.0
)

data class BillCalc(
    val usage: Int,
    val b1: Int, val b2: Int, val b3: Int,
    val amount1: Long, val amount2: Long, val amount3: Long,
    val abonemen: Long,
    val fine: Long,
    val total: Long
)

/**
 * Engine perhitungan tagihan — pure function, unit-testable.
 * total = (blok1 × tarif1) + (blok2 × tarif2) + (blok3 × tarif3) + abonemen + denda.
 */
object TariffEngine {
    fun calculate(meterStart: Int, meterEnd: Int, t: TariffSettings, unpaidMonths: Int = 0): BillCalc {
        val usage = max(0, meterEnd - meterStart)
        val b1 = min(usage, t.block1Limit)
        val b2 = min(max(0, usage - t.block1Limit), max(0, t.block2Limit - t.block1Limit))
        val b3 = max(0, usage - t.block2Limit)
        val a1 = b1.toLong() * t.block1Rate
        val a2 = b2.toLong() * t.block2Rate
        val a3 = b3.toLong() * t.block3Rate
        val base = a1 + a2 + a3 + t.abonemen
        val fine = (base * t.finePercent / 100.0 * max(0, unpaidMonths)).roundToLong()
        return BillCalc(usage, b1, b2, b3, a1, a2, a3, t.abonemen, fine, base + fine)
    }
}
