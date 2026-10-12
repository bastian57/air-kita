package com.daftech.airkita.license

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Sistem lisensi offline AIR KITA.
 *
 * Format kode lisensi: AK1-<base64url(data)>-<base64url(signature)>
 *   data      = "deviceId|buyerName|expiryEpoch" (expiryEpoch 0 = permanen)
 *   signature = RSA-SHA256(data) dengan private key milik DAFTECH
 *
 * Private key TIDAK ada di repo ini — hanya public key untuk verifikasi.
 */
sealed class LicenseResult {
    data class Valid(val buyerName: String, val expiry: Long) : LicenseResult()
    data class Invalid(val reason: String) : LicenseResult()
}

object LicenseManager {
    /** Public key RSA-2048 (X.509 DER, base64). Sama di semua APK AIR KITA. */
    const val PUBLIC_KEY_B64 =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzeFooO9gvvlsZ029Za/f" +
        "/psVxYGMMY4KhPeMt1qUKwZ/BRWk0rKljlaNoR4WZUkbVhN60m2HRS2sbF/ewo6S" +
        "OxaZ/aEEzqlgOfIPul4KpCzRoMk2ZBIJ63+oNQR9Dp9sDCxhjhBRej/SJoS3ILgG" +
        "q0RbZNJSy1YBVj1dvmw6cPQImiQr+v2uusnyDwcMSpxZRxNlP8C2GBkX5yrRpTFr" +
        "UXUsa5LQtvKEm4VA/2IDRpaealHzqAVLOSAQcAAiiEb0p4ZRxJYW+EtAoEXC/yDo" +
        "pjIAkMq0aLDKgWDGC/KBcKJ/A4e26b5O9CjyNnf0n3q1IJvbZGdFozOCi2PCc6zU" +
        "zQIDAQAB"

    const val PREFS_NAME = "airkita_license"
    private const val KEY_ACTIVATED = "activated"
    private const val KEY_LICENSE = "license_key"
    private const val KEY_BUYER = "buyer"
    private const val KEY_EXPIRY = "expiry"

    /** ID unik perangkat (ANDROID_ID). Dipakai untuk mengikat lisensi ke 1 HP. */
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    }

    /** True jika app sudah diaktivasi dan lisensi belum kedaluwarsa. */
    fun isActivated(prefs: SharedPreferences): Boolean {
        if (!prefs.getBoolean(KEY_ACTIVATED, false)) return false
        val expiry = prefs.getLong(KEY_EXPIRY, 0L)
        return expiry == 0L || expiry > System.currentTimeMillis()
    }

    /** Validasi kode lisensi terhadap deviceId HP ini. */
    fun validateLicense(rawKey: String, deviceId: String): LicenseResult {
        val key = rawKey.trim()
        if (!key.startsWith("AK1-")) {
            return LicenseResult.Invalid("Format kode salah (harus diawali AK1-)")
        }
        val rest = key.removePrefix("AK1-")
        val lastDash = rest.lastIndexOf('-')
        if (lastDash <= 0) {
            return LicenseResult.Invalid("Format kode salah")
        }
        val dataB64 = rest.substring(0, lastDash)
        val sigB64 = rest.substring(lastDash + 1)
        if (dataB64.isBlank() || sigB64.isBlank()) {
            return LicenseResult.Invalid("Format kode salah")
        }

        val dataBytes: ByteArray
        val sigBytes: ByteArray
        try {
            dataBytes = b64UrlDecode(dataB64)
            sigBytes = b64UrlDecode(sigB64)
        } catch (_: Exception) {
            return LicenseResult.Invalid("Kode tidak valid (encoding rusak)")
        }

        // Verifikasi signature RSA-SHA256
        val ok = try {
            val pubKey = KeyFactory.getInstance("RSA").generatePublic(
                X509EncodedKeySpec(Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT))
            )
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(pubKey)
            sig.update(dataBytes)
            sig.verify(sigBytes)
        } catch (_: Exception) {
            return LicenseResult.Invalid("Kode tidak valid (verifikasi gagal)")
        }
        if (!ok) return LicenseResult.Invalid("Kode tidak valid (signature salah)")

        // Parse data: deviceId|buyerName|expiryEpoch
        val dataStr = try {
            String(dataBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            return LicenseResult.Invalid("Kode tidak valid")
        }
        val fields = dataStr.split('|')
        if (fields.size != 3) return LicenseResult.Invalid("Kode tidak valid")

        val licDevice = fields[0]
        val buyer = fields[1]
        val expiry = fields[2].toLongOrNull()
            ?: return LicenseResult.Invalid("Kode tidak valid")

        if (licDevice != deviceId) {
            return LicenseResult.Invalid("Kode ini untuk perangkat lain")
        }
        if (expiry != 0L && expiry <= System.currentTimeMillis()) {
            return LicenseResult.Invalid("Lisensi sudah kedaluwarsa")
        }
        return LicenseResult.Valid(buyerName = buyer, expiry = expiry)
    }

    /** Simpan lisensi yang valid ke SharedPreferences. */
    fun saveLicense(prefs: SharedPreferences, key: String, buyerName: String, expiry: Long) {
        prefs.edit()
            .putBoolean(KEY_ACTIVATED, true)
            .putString(KEY_LICENSE, key.trim())
            .putString(KEY_BUYER, buyerName)
            .putLong(KEY_EXPIRY, expiry)
            .apply()
    }

    /** base64url tanpa padding → bytes (tambah padding bila perlu). */
    private fun b64UrlDecode(s: String): ByteArray {
        var str = s.trim()
        val pad = (4 - str.length % 4) % 4
        repeat(pad) { str += "=" }
        return Base64.decode(str, Base64.URL_SAFE)
    }
}
