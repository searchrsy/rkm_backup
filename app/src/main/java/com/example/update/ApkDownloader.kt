package com.example.update

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class ApkDownloader(
    private val context: Context,
    private val updateGuard: UpdateGuard,
    private val customClient: OkHttpClient? = null
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(UpdateConfig.PREFS_NAME, Context.MODE_PRIVATE)

    private val httpClient: OkHttpClient = customClient ?: OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    companion object {
        private const val TAG = "ApkDownloader"
    }

    fun getUpdatesDir(): File {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getCompletedApkFile(versionCode: Long): File {
        return File(getUpdatesDir(), "app-$versionCode.apk")
    }

    fun getPartApkFile(versionCode: Long): File {
        return File(getUpdatesDir(), "app-$versionCode.apk.part")
    }

    /**
     * Memeriksa apakah file APK lengkap untuk versi ini sudah ada dan valid di cache.
     */
    fun getExistingValidApk(data: UpdateData): File? {
        val completed = getCompletedApkFile(data.versionCode)
        if (completed.exists() && completed.length() == data.size) {
            val sha = calculateSha256(completed)
            if (sha.equals(data.sha256, ignoreCase = true)) {
                Log.i(TAG, "Existing valid APK found in cache for versionCode ${data.versionCode}")
                return completed
            } else {
                completed.delete()
            }
        }
        return null
    }

    /**
     * Mengunduh APK dengan streaming buffer 64KB, resume support (HTTP 206),
     * verifikasi ukuran dan SHA-256.
     */
    suspend fun downloadApk(
        data: UpdateData,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, progress: Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val existing = getExistingValidApk(data)
        if (existing != null) {
            onProgress(data.size, data.size, 1.0f)
            return@withContext existing
        }

        // 1. Cek ruang penyimpanan (minimal 1.5x ukuran file)
        val requiredSpace = (data.size * 1.5).toLong()
        val usableSpace = context.cacheDir.usableSpace
        if (usableSpace < requiredSpace) {
            throw IllegalStateException("Ruang penyimpanan tidak mencukupi untuk mengunduh update.")
        }

        val updateDir = getUpdatesDir()
        cleanOldVersions(updateDir, data.versionCode)

        val partFile = getPartApkFile(data.versionCode)
        val completeFile = getCompletedApkFile(data.versionCode)

        var existingBytes = if (partFile.exists()) partFile.length() else 0L
        val savedEtag = prefs.getString(UpdateConfig.KEY_SAVED_ETAG, null)

        // Jika ukuran part file melebihi total size, buang part file
        if (existingBytes >= data.size && data.size > 0) {
            partFile.delete()
            existingBytes = 0L
        }

        val requestBuilder = Request.Builder()
            .url(data.apkUrl ?: UpdateConfig.APK_DOWNLOAD_URL)
            .addHeader("ngrok-skip-browser-warning", "true")
            .addHeader("Accept-Encoding", "identity")

        if (existingBytes > 0L) {
            requestBuilder.addHeader("Range", "bytes=$existingBytes-")
            if (!savedEtag.isNullOrBlank()) {
                requestBuilder.addHeader("If-Range", savedEtag)
            }
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        response.use { resp ->
            val code = resp.code
            val isPartial = code == 206
            val isOk = code == 200

            if (code == 416) {
                // Range tidak valid, hapus part dan unduh ulang
                partFile.delete()
                throw IllegalStateException("Rentang unduhan tidak valid (416). Silakan coba lagi.")
            }

            if (!isPartial && !isOk) {
                throw IllegalStateException("Server mengembalikan HTTP $code saat mengunduh APK.")
            }

            // Simpan ETag terbaru jika disediakan
            val newEtag = resp.header("ETag")
            if (!newEtag.isNullOrBlank()) {
                if (savedEtag != null && savedEtag != newEtag && existingBytes > 0L) {
                    partFile.delete()
                    existingBytes = 0L
                }
                prefs.edit().putString(UpdateConfig.KEY_SAVED_ETAG, newEtag).apply()
            }

            val body = resp.body ?: throw IllegalStateException("Respons server tidak memiliki body.")
            val inputStream = body.byteStream()

            val append = isPartial && existingBytes > 0L
            if (!append && partFile.exists()) {
                partFile.delete()
                existingBytes = 0L
            }

            val outputStream = FileOutputStream(partFile, append)
            outputStream.use { out ->
                val buffer = ByteArray(UpdateConfig.BUFFER_SIZE)
                var bytesRead: Int
                var currentDownloaded = existingBytes
                val totalBytes = data.size

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    coroutineContext.ensureActive()
                    out.write(buffer, 0, bytesRead)
                    currentDownloaded += bytesRead

                    val progress = if (totalBytes > 0) {
                        (currentDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    onProgress(currentDownloaded, totalBytes, progress)
                }
                out.flush()
            }
        }

        // Verifikasi ukuran hasil unduhan
        if (data.size > 0 && partFile.length() != data.size) {
            val actualLen = partFile.length()
            partFile.delete()
            throw IllegalStateException("Ukuran unduhan tidak sesuai (diterima $actualLen, diharapkan ${data.size}).")
        }

        // Verifikasi SHA-256
        val calculatedSha = calculateSha256(partFile)
        if (!calculatedSha.equals(data.sha256?.trim(), ignoreCase = true)) {
            partFile.delete()
            throw IllegalStateException("Verifikasi keutuhan berkas (SHA-256) gagal. Berkas dibuang.")
        }

        // Ganti nama dari .part ke .apk lengkap
        if (completeFile.exists()) {
            completeFile.delete()
        }
        if (!partFile.renameTo(completeFile)) {
            // Jika rename gagal, salin manual
            partFile.copyTo(completeFile, overwrite = true)
            partFile.delete()
        }

        Log.i(TAG, "Download and verification complete: ${completeFile.absolutePath}")
        return@withContext completeFile
    }

    fun cleanOldVersions(updateDir: File, currentTargetVersion: Long) {
        try {
            updateDir.listFiles()?.forEach { file ->
                val name = file.name
                if (name.startsWith("app-") && (name.endsWith(".apk") || name.endsWith(".apk.part"))) {
                    val versionStr = name.substringAfter("app-").substringBefore(".apk")
                    val vCode = versionStr.toLongOrNull()
                    if (vCode != null && vCode != currentTargetVersion) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error cleaning old version files: ${e.message}")
        }
    }

    fun cleanupAllUpdates() {
        try {
            getUpdatesDir().deleteRecursively()
        } catch (_: Exception) {}
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(UpdateConfig.BUFFER_SIZE)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
