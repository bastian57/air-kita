package com.daftech.airkita.backup

import android.content.Context
import com.daftech.airkita.BuildConfig
import com.daftech.airkita.data.Repository
import com.daftech.airkita.pdf.ReportPdf
import com.daftech.airkita.util.Fmt
import com.google.auth.oauth2.ServiceAccountCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Backup ke Google Drive via Drive REST API v3.
 *
 * Kredensial service account dibaca dari assets/service_account.json yang
 * di-inject workflow CI dari secret GOOGLE_SERVICE_ACCOUNT_JSON.
 * Jika file masih placeholder / folder ID kosong -> kembalikan PLACEHOLDER
 * agar UI memakai fallback lokal (simpan ke Download + share sheet).
 *
 * TIDAK ada secret/token/email yang di-hardcode di repo ini.
 */
object DriveBackup {
    /** Penanda agar pemanggil memakai fallback lokal. */
    const val PLACEHOLDER = "PLACEHOLDER"

    private const val PLACEHOLDER_MARK = "diganti workflow"
    private const val SCOPE = "https://www.googleapis.com/auth/drive.file"

    data class Result(val ok: Boolean, val message: String)

    suspend fun backup(context: Context, repo: Repository, period: String): Result =
        withContext(Dispatchers.IO) {
            val folderId = BuildConfig.DRIVE_FOLDER_ID
            val saText = try {
                context.assets.open("service_account.json").bufferedReader().readText()
            } catch (_: Exception) {
                ""
            }
            if (saText.isBlank() || saText.contains(PLACEHOLDER_MARK) || folderId.isBlank()) {
                return@withContext Result(false, PLACEHOLDER)
            }
            try {
                val creds = ServiceAccountCredentials.fromStream(saText.byteInputStream())
                    .createScoped(listOf(SCOPE))
                creds.refreshIfExpired()
                val token = creds.accessToken?.tokenValue
                    ?: return@withContext Result(false, "Gagal mendapatkan token Drive.")
                val stamp = Fmt.fileStamp()

                val json = repo.exportJson()
                upload(
                    token, folderId,
                    "airkita-backup-$stamp.json", "application/json",
                    json.toByteArray(Charsets.UTF_8)
                )
                val pdf = ReportPdf.generate(context, repo, period)
                upload(token, folderId, pdf.name, "application/pdf", pdf.readBytes())

                Result(true, "Backup terkirim ke Google Drive.")
            } catch (e: Exception) {
                Result(false, "Backup gagal: ${e.message}")
            }
        }

    private fun upload(token: String, folderId: String, name: String, mime: String, bytes: ByteArray) {
        val boundary = "AIRKITA${System.currentTimeMillis()}"
        val conn = (URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30000
            readTimeout = 60000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        }
        val meta = "{\"name\":\"$name\",\"parents\":[\"$folderId\"],\"mimeType\":\"$mime\"}"
        DataOutputStream(conn.outputStream).use { out ->
            out.writeBytes("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            out.write(meta.toByteArray(Charsets.UTF_8))
            out.writeBytes("\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n")
            out.write(bytes)
            out.writeBytes("\r\n--$boundary--\r\n")
            out.flush()
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { "" }
            throw RuntimeException("Drive HTTP $code $err")
        }
        conn.disconnect()
    }
}
