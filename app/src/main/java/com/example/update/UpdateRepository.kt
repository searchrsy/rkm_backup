package com.example.update

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class UpdateRepository(
    private val context: Context,
    private val updateGuard: UpdateGuard
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(UpdateConfig.PREFS_NAME, Context.MODE_PRIVATE)

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(UpdateConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(UpdateConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(UpdateConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    companion object {
        private const val TAG = "UpdateRepository"
    }

    /**
     * Memeriksa apakah pemeriksaan versi diizinkan hari ini.
     * Menggunakan aturan "tidak sama dengan hari ini" (bukan lebih kecil)
     * agar perubahan jam perangkat tidak memblokir pemeriksaan selamanya.
     */
    fun shouldCheckToday(): Boolean {
        val today = getTodayDateString()
        val lastCheck = prefs.getString(UpdateConfig.KEY_LAST_CHECK_DATE, null)
        return lastCheck == null || lastCheck != today
    }

    /**
     * Mencatat tanggal pemeriksaan sebelum request dikirim.
     */
    fun recordCheckAttemptToday() {
        val today = getTodayDateString()
        prefs.edit().putString(UpdateConfig.KEY_LAST_CHECK_DATE, today).apply()
    }

    /**
     * Mengambil dan memvalidasi informasi pembaruan dari server.
     * Mengembalikan UpdateData jika update valid tersedia, atau null jika tidak ada/gagal.
     */
    suspend fun checkUpdate(
        installedVersionCode: Long,
        checkUrl: String = UpdateConfig.CHECK_URL
    ): UpdateData? = withContext(Dispatchers.IO) {
        if (!updateGuard.isFeatureAvailable(installedVersionCode)) {
            return@withContext null
        }

        try {
            val request = Request.Builder()
                .url(checkUrl)
                .addHeader("ngrok-skip-browser-warning", "true")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    Log.d(TAG, "Server check returned HTTP ${resp.code}")
                    return@withContext null
                }

                val bodyString = resp.body?.string() ?: return@withContext null
                val adapter = moshi.adapter(UpdateCheckResponse::class.java)
                val checkResponse = try {
                    adapter.fromJson(bodyString)
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to parse check JSON: ${e.message}")
                    return@withContext null
                }

                if (checkResponse == null || !checkResponse.success) {
                    Log.d(TAG, "Server check indicated no success or empty payload")
                    return@withContext null
                }

                // Layer 2: Kunci server (updateEnabled == false)
                if (!checkResponse.updateEnabled) {
                    Log.d(TAG, "Update feature is disabled by server (updateEnabled: false)")
                    return@withContext null
                }

                val data = checkResponse.data ?: return@withContext null

                // Validasi versionCode
                if (data.versionCode <= 0L || data.versionCode <= installedVersionCode) {
                    Log.d(TAG, "No newer version available (server: ${data.versionCode}, installed: $installedVersionCode)")
                    return@withContext null
                }

                // Validasi URL APK (harus HTTPS, host sama, path diawali /sales-gps-dashboard/apk/)
                val expectedHost = try {
                    Uri.parse(checkUrl).host ?: UpdateConfig.EXPECTED_HOST
                } catch (_: Exception) {
                    UpdateConfig.EXPECTED_HOST
                }
                val apkUrl = data.apkUrl
                if (!isValidApkUrl(apkUrl, expectedHost)) {
                    Log.w(TAG, "Invalid apkUrl rejected: $apkUrl")
                    return@withContext null
                }

                // Validasi SHA256 hex 64 karakter
                val sha = data.sha256?.trim()
                if (sha.isNullOrEmpty() || sha.length != 64 || !sha.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                    Log.w(TAG, "Invalid SHA256 hash in update data: $sha")
                    return@withContext null
                }

                // Reset hitungan error jika pengecekan berhasil
                updateGuard.resetFailures()
                return@withContext data
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Masalah jaringan, timeout, atau DNS bukan internal failure
            Log.d(TAG, "Network error during checkUpdate: ${e.message}")
            return@withContext null
        }
    }

    fun isValidApkUrl(urlStr: String?, expectedHost: String = UpdateConfig.EXPECTED_HOST): Boolean {
        if (urlStr.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(urlStr)
            val isLoopback = expectedHost == "localhost" || expectedHost == "127.0.0.1"
            val isHttps = uri.scheme.equals("https", ignoreCase = true) || (isLoopback && uri.scheme.equals("http", ignoreCase = true))
            val hostMatches = uri.host.equals(expectedHost, ignoreCase = true)
            val path = uri.path ?: ""
            val noTraversal = !path.contains("..") && !path.contains("/.")
            val pathMatches = path.startsWith(UpdateConfig.EXPECTED_PATH_PREFIX)
            isHttps && hostMatches && pathMatches && noTraversal
        } catch (_: Exception) {
            false
        }
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
