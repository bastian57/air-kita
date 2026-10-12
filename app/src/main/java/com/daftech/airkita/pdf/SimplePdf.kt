package com.daftech.airkita.pdf

/**
 * Penulis PDF minimal murni Kotlin (tanpa android.graphics.pdf / android.print).
 * Mendukung: teks (Helvetica / Helvetica-Bold), garis horizontal, multi-halaman A4.
 * Output: ByteArray PDF 1.4 valid.
 */
object SimplePdf {
    const val PW = 595f
    const val PH = 842f

    data class Text(
        val x: Float, val y: Float, val size: Float,
        val str: String, val bold: Boolean = false,
        val align: Int = 0 // 0=kiri, 1=tengah, 2=kanan
    )
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    class Page {
        val texts = mutableListOf<Text>()
        val lines = mutableListOf<Line>()
        fun text(x: Float, y: Float, s: String, size: Float = 11f, bold: Boolean = false, align: Int = 0) {
            texts.add(Text(x, y, size, s, bold, align))
        }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            lines.add(Line(x1, y1, x2, y2))
        }
    }

    private fun esc(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '(' -> append("\\(")
                ')' -> append("\\)")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> if (c.code in 32..126) append(c) else append("?")
            }
        }
    }

    fun generate(pages: List<Page>): ByteArray {
        val out = mutableListOf<Byte>()
        fun ascii(s: String) { out.addAll(s.toByteArray(Charsets.US_ASCII).toList()) }

        val offsets = mutableListOf<Int>()
        fun obj(n: Int, body: String) {
            offsets.add(out.size)
            ascii("$n 0 obj\n$body\nendobj\n")
        }
        fun streamObj(n: Int, data: String) {
            offsets.add(out.size)
            val bytes = data.toByteArray(Charsets.US_ASCII)
            ascii("$n 0 obj\n<< /Length ${bytes.size} >>\nstream\n")
            out.addAll(bytes.toList())
            ascii("\nendstream\nendobj\n")
        }

        ascii("%PDF-1.4\n")

        val pageCount = pages.size
        // 1: Catalog, 2: Pages, 3: Helvetica, 4: Helvetica-Bold
        // Page objects: 5, 7, 9... ; Content streams: 6, 8, 10...
        val kids = (0 until pageCount).map { i -> "${5 + i * 2} 0 R" }.joinToString(" ")
        obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
        obj(2, "<< /Type /Pages /Kids [$kids] /Count $pageCount >>")
        obj(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        obj(4, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")

        pages.forEachIndexed { i, page ->
            val pageObj = 5 + i * 2
            val streamObjNum = 6 + i * 2
            obj(pageObj, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PW $PH] " +
                "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents $streamObjNum 0 R >>")

            val sb = StringBuilder()
            // Garis
            for (ln in page.lines) {
                sb.append("${ln.x1} ${PH - ln.y1} m ${ln.x2} ${PH - ln.y2} l S\n")
            }
            // Teks
            for (t in page.texts) {
                val font = if (t.bold) "/F2" else "/F1"
                // Estimasi lebar untuk alignment tengah/kanan (0.5 * size per karakter, kasar)
                val approxW = t.str.length * t.size * 0.5f
                val x = when (t.align) {
                    1 -> t.x - approxW / 2
                    2 -> t.x - approxW
                    else -> t.x
                }
                val yPdf = PH - t.y
                sb.append("BT $font ${t.size} Tf 1 0 0 1 $x $yPdf Tm (${esc(t.str)}) Tj ET\n")
            }
            streamObj(streamObjNum, sb.toString())
        }

        val xrefPos = out.size
        ascii("xref\n0 ${offsets.size + 1}\n")
        ascii("0000000000 65535 f \n")
        for (off in offsets) {
            ascii(String.format("%010d 00000 n \n", off))
        }
        ascii("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\n")
        ascii("startxref\n$xrefPos\n%%EOF\n")
        return out.toByteArray()
    }
}
