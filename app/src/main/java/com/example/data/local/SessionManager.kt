package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.config.AppConfig
import com.example.data.model.LoginConfigResponse
import com.example.data.network.ApiClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("rkm_spi_session", Context.MODE_PRIVATE)

    init {
        // Jika CONFIG_VERSION di AppConfig dinaikkan, buang alamat server lama yang tersimpan
        // di HP supaya aplikasi memakai alamat terbaru dari AppConfig.
        if (prefs.getInt(KEY_CONFIG_VERSION, 0) != AppConfig.CONFIG_VERSION) {
            prefs.edit()
                .remove(KEY_BASE_URL)
                .putInt(KEY_CONFIG_VERSION, AppConfig.CONFIG_VERSION)
                .apply()
        }
    }

    companion object {
        // Semua nilai default diambil dari AppConfig.kt (jangan isi manual di sini)
        const val DEFAULT_BASE_URL = AppConfig.BASE_URL
        private const val KEY_CONFIG_VERSION = "config_version"
        private const val KEY_LOGIN_CONFIG = "cached_login_config"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_SALES_CODE = "kode_salesman"
        private const val KEY_SALES_NAME = "nama_salesman"
        private const val KEY_PERM_REQUESTED = "initial_permissions_requested"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_CACHED_LOKASI = "cached_lokasi_terakhir"
        private const val KEY_CACHED_RIWAYAT = "cached_riwayat_lokasi"
        const val DEFAULT_GPS_API_KEY = AppConfig.API_KEY
    }

    var hasRequestedInitialPermissions: Boolean
        get() = prefs.getBoolean(KEY_PERM_REQUESTED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERM_REQUESTED, value).apply()

    var baseUrl: String
        get() {
            val url = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
            return if (url.endsWith("/")) url else "$url/"
        }
        set(value) {
            val clean = if (value.trim().endsWith("/")) value.trim() else "${value.trim()}/"
            prefs.edit().putString(KEY_BASE_URL, clean).apply()
        }

    /** API key selalu dari AppConfig.API_KEY. */
    val gpsApiKey: String
        get() = AppConfig.API_KEY

    val deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id.isNullOrBlank()) {
                val code = kodeSalesman ?: "device"
                id = "${code}_" + java.util.UUID.randomUUID().toString().substring(0, 8)
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }

    /** URL endpoint GPS (gps.php). Semua penurunan URL ada di AppConfig. */
    val gpsEndpointUrl: String
        get() = AppConfig.gpsUrl(baseUrl)

    /** URL base API dashboard (lokasi_terakhir.php dan riwayat_lokasi.php). */
    val dashboardApiBaseUrl: String
        get() = AppConfig.dashboardApiUrl(baseUrl)

    /** Cadangan jika dashboardApiBaseUrl memberi 404: https://host/api/ */
    val rootApiBaseUrl: String
        get() = AppConfig.rootApiUrl(baseUrl)

    // Cache konfigurasi login (label cabang + daftar salesman) agar tetap tampil saat offline
    private val loginConfigAdapter by lazy {
        ApiClient.getMoshi().adapter(LoginConfigResponse::class.java)
    }

    fun saveCachedLoginConfig(config: LoginConfigResponse) {
        prefs.edit().putString(KEY_LOGIN_CONFIG, loginConfigAdapter.toJson(config)).apply()
    }

    fun getCachedLoginConfig(): LoginConfigResponse? {
        val json = prefs.getString(KEY_LOGIN_CONFIG, null) ?: return null
        return try { loginConfigAdapter.fromJson(json) } catch (_: Exception) { null }
    }

    fun saveCachedLokasi(json: String) {
        prefs.edit().putString(KEY_CACHED_LOKASI, json).apply()
    }

    fun getCachedLokasi(): String? {
        return prefs.getString(KEY_CACHED_LOKASI, null)
    }

    fun saveCachedRiwayat(json: String) {
        prefs.edit().putString(KEY_CACHED_RIWAYAT, json).apply()
    }

    fun getCachedRiwayat(): String? {
        return prefs.getString(KEY_CACHED_RIWAYAT, null)
    }

    fun saveSession(token: String, expiresAt: String?, kodeSalesman: String, namaSalesman: String?) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EXPIRES_AT, expiresAt)
            .putString(KEY_SALES_CODE, kodeSalesman)
            .putString(KEY_SALES_NAME, namaSalesman ?: kodeSalesman)
            .apply()
    }

    val token: String?
        get() = prefs.getString(KEY_TOKEN, null)

    val kodeSalesman: String?
        get() = prefs.getString(KEY_SALES_CODE, null)

    val namaSalesman: String?
        get() = prefs.getString(KEY_SALES_NAME, null)

    val expiresAt: String?
        get() = prefs.getString(KEY_EXPIRES_AT, null)

    val isLoggedIn: Boolean
        get() {
            val currentToken = token
            if (currentToken.isNullOrBlank()) return false
            if (isTokenExpired()) return false
            return true
        }

    fun isTokenExpired(): Boolean {
        val exp = expiresAt ?: return false
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = format.parse(exp)
            if (date != null) {
                System.currentTimeMillis() >= date.time
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Logout hanya menghapus kredensial autentikasi sesi.
     * Tidak menghapus draft kunjungan maupun cache offline lokasi.
     */
    fun clearSession() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_SALES_CODE)
            .remove(KEY_SALES_NAME)
            .apply()
    }
}