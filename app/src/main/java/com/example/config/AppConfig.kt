package com.example.config

import com.example.BuildConfig

/**
 * ============================================================
 *  Aplikasi TIDAK diedit per cabang.
 *  Semua nilai cabang dibaca dari SATU file: cabang.properties
 *  (di root proyek), lalu diteruskan oleh Gradle lewat BuildConfig.
 *
 *  Ganti cabang = edit cabang.properties -> build APK.
 *  (Naikkan CONFIG_VERSION bila SERVER_HOST / APP_PATH berubah.)
 * ============================================================
 */
object AppConfig {

    // ---------- DIBACA DARI cabang.properties ----------
    const val SERVER_SCHEME = "https"
    const val SERVER_HOST = BuildConfig.SERVER_HOST
    const val APP_PATH = BuildConfig.APP_PATH
    const val API_KEY = BuildConfig.API_KEY
    const val CONFIG_VERSION = BuildConfig.CONFIG_VERSION

    const val CABANG_NAMA = BuildConfig.CABANG_NAMA
    const val CABANG_LABEL = BuildConfig.CABANG_LABEL        // cadangan jika server belum menjawab
    const val CABANG_SUBTITLE = BuildConfig.CABANG_SUBTITLE
    const val TIMEZONE = BuildConfig.TIMEZONE
    const val MAP_LAT = BuildConfig.MAP_LAT
    const val MAP_LNG = BuildConfig.MAP_LNG
    const val USER_AGENT = "RKM-${BuildConfig.CABANG_NAMA}-Android/1.0"
    // ---------------------------------------------------

    private const val ORIGIN = "$SERVER_SCHEME://$SERVER_HOST"

    // URL turunan
    const val BASE_URL = "$ORIGIN$APP_PATH/endpoint/"        // rkm_login.php, rkm1.php, dst.
    const val DASHBOARD_API_URL = "$ORIGIN$APP_PATH/api/"    // lokasi_terakhir.php, riwayat_lokasi.php
    const val PATH_GPS = "/gps.php"
    const val GPS_URL = "$ORIGIN$PATH_GPS"                   // kirim GPS
    const val UPDATE_BASE_URL = "$ORIGIN$APP_PATH/apk"       // cek & unduh update APK

    // Nama file endpoint (relatif terhadap BASE_URL / DASHBOARD_API_URL)
    const val PATH_LOGIN = "rkm_login.php"
    const val PATH_LOGIN_CONFIG = "login_config.php"         // daftar salesman + label cabang
    const val PATH_RKM = "rkm1.php"
    const val PATH_LAPORAN = "rkm2.php"
    const val PATH_LOKASI_TERAKHIR = "lokasi_terakhir.php"
    const val PATH_RIWAYAT_LOKASI = "riwayat_lokasi.php"

    // ---------- PENGIRIMAN GPS BATCH (hemat kuota ngrok) ----------
    // Titik GPS tetap diambil tiap GPS_SAMPLE_INTERVAL_MS, tetapi dikirim sekaligus
    // (1 request) setelah terkumpul GPS_BATCH_SIZE titik ATAU titik tertua berumur
    // GPS_BATCH_MAX_AGE_MS. Antrian disimpan di HP sehingga aman jika offline/app ditutup.
    const val GPS_SAMPLE_INTERVAL_MS = 30_000L
    const val GPS_BATCH_SIZE = 10
    const val GPS_BATCH_MAX_AGE_MS = 5 * 60_000L
    const val GPS_FLUSH_CHUNK = 100          // maksimum titik per 1 request
    const val GPS_QUEUE_MAX = 500            // batas antrian offline; titik tertua dibuang
    const val GPS_BACKOFF_BASE_MS = 60_000L  // jeda awal setelah kirim gagal (x2 tiap gagal)
    const val GPS_BACKOFF_MAX_MS = 60 * 60_000L

    // ---------- UPDATE APLIKASI ----------
    // true = setiap versi baru WAJIB dipasang (dialog tidak bisa ditutup).
    const val UPDATE_WAJIB = true
    const val UPDATE_RECHECK_INTERVAL_MS = 30 * 60_000L // cek ulang saat app dibuka kembali

    // ---------- PEMBENTUK URL dari base URL yang tersimpan di HP ----------
    // Satu-satunya tempat yang menurunkan alamat gps / api / endpoint dari host.
    private fun originOf(baseUrl: String): String? = try {
        val uri = java.net.URI(baseUrl)
        val port = if (uri.port != -1) ":${uri.port}" else ""
        if (uri.scheme == null || uri.host == null) null else "${uri.scheme}://${uri.host}$port"
    } catch (_: Exception) {
        null
    }

    fun gpsUrl(baseUrl: String): String =
        originOf(baseUrl)?.let { it + PATH_GPS } ?: GPS_URL

    fun dashboardApiUrl(baseUrl: String): String {
        val origin = originOf(baseUrl) ?: return DASHBOARD_API_URL
        val path = try { java.net.URI(baseUrl).path ?: "" } catch (_: Exception) { "" }
        return if (path.contains(APP_PATH)) "$origin$APP_PATH/api/" else "$origin/api/"
    }

    /** Cadangan jika dashboardApiUrl memberi 404: https://host/api/ */
    fun rootApiUrl(baseUrl: String): String =
        originOf(baseUrl)?.let { "$it/api/" } ?: baseUrl
}