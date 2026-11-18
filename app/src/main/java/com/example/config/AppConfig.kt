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
}