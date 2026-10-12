package com.daftech.airkita.qr

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Format QR transfer offline AIR KITA Petugas.
 *
 * Satu paket: "AKP1|<type>|<part>/<total>|<base64url>"
 *  - type "C" = daftar pelanggan (admin -> petugas)
 *  - type "R" = hasil pencatatan (petugas -> admin)
 * Data JSON di-base64 (URL_SAFE, NO_WRAP) lalu dipotong ~2000 karakter per QR.
 */
data class CustomerPayload(
    val id: Long,
    val name: String,
    val meterNo: String,
    val address: String,
    val lastMeter: Int
)

data class ReadingPayload(
    val customerId: Long,
    val meterEnd: Int,
    val recordedAt: Long
)

data class QrPacket(
    val type: String,
    val part: Int,
    val total: Int,
    val data: String
)

object QrCodec {
    private const val PREFIX = "AKP1"
    private const val CHUNK = 2000
    private const val TYPE_CUSTOMERS = "C"
    private const val TYPE_READINGS = "R"

    fun encodeCustomers(list: List<CustomerPayload>): List<String> {
        val arr = JSONArray()
        for (c in list) {
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("meterNo", c.meterNo)
                    .put("address", c.address)
                    .put("lastMeter", c.lastMeter)
            )
        }
        return split(TYPE_CUSTOMERS, arr.toString())
    }

    fun encodeReadings(list: List<ReadingPayload>): List<String> {
        val arr = JSONArray()
        for (r in list) {
            arr.put(
                JSONObject()
                    .put("customerId", r.customerId)
                    .put("meterEnd", r.meterEnd)
                    .put("recordedAt", r.recordedAt)
            )
        }
        return split(TYPE_READINGS, arr.toString())
    }

    private fun split(type: String, json: String): List<String> {
        val b64 = Base64.encodeToString(
            json.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP or Base64.URL_SAFE
        )
        val chunks = b64.chunked(CHUNK)
        return chunks.mapIndexed { i, c -> "$PREFIX|$type|${i + 1}/${chunks.size}|$c" }
    }

    /** Parse satu hasil scan QR menjadi paket; null bila bukan paket AIR KITA. */
    fun parsePacket(raw: String): QrPacket? {
        val parts = raw.split("|", limit = 4)
        if (parts.size != 4 || parts[0] != PREFIX) return null
        val type = parts[1]
        if (type != TYPE_CUSTOMERS && type != TYPE_READINGS) return null
        val pt = parts[2].split("/")
        if (pt.size != 2) return null
        val part = pt[0].toIntOrNull() ?: return null
        val total = pt[1].toIntOrNull() ?: return null
        if (part < 1 || total < 1 || part > total) return null
        return QrPacket(type, part, total, parts[3])
    }

    /**
     * Gabungkan paket-paket menjadi string JSON.
     * Mengembalikan null bila paket belum lengkap atau tipe campur aduk.
     */
    fun combine(packets: Collection<QrPacket>): String? {
        if (packets.isEmpty()) return null
        val type = packets.first().type
        val total = packets.first().total
        if (packets.any { it.type != type || it.total != total }) return null
        if (packets.size < total) return null
        val joined = packets.sortedBy { it.part }.joinToString("") { it.data }
        return try {
            String(
                Base64.decode(joined, Base64.NO_WRAP or Base64.URL_SAFE),
                Charsets.UTF_8
            )
        } catch (_: Exception) {
            null
        }
    }

    fun parseCustomers(json: String): List<CustomerPayload> {
        val out = mutableListOf<CustomerPayload>()
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                CustomerPayload(
                    id = o.getLong("id"),
                    name = o.optString("name", ""),
                    meterNo = o.optString("meterNo", ""),
                    address = o.optString("address", ""),
                    lastMeter = o.optInt("lastMeter", 0)
                )
            )
        }
        return out
    }

    fun parseReadings(json: String): List<ReadingPayload> {
        val out = mutableListOf<ReadingPayload>()
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                ReadingPayload(
                    customerId = o.getLong("customerId"),
                    meterEnd = o.getInt("meterEnd"),
                    recordedAt = o.optLong("recordedAt", System.currentTimeMillis())
                )
            )
        }
        return out
    }
}
