package com.example.config

/**
 * ============================================================
 *  SATU-SATUNYA TEMPAT untuk mengubah server & API key.
 *  Semua URL lain di aplikasi diturunkan dari file ini.
 * ============================================================
 *
 * Kalau alamat server berubah (misalnya domain ngrok baru):
 *   1. Ubah SERVER_HOST di bawah.
 *   2. Naikkan CONFIG_VERSION (+1) supaya HP yang sudah terpasang
 *      ikut memakai alamat baru setelah update APK.
 *   3. Build APK baru.
 */
object AppConfig {

    // ---------- UBAH DI SINI ----------
    const val SERVER_SCHEME = "https"
    const val SERVER_HOST = "numbing-brigade-glacier.ngrok-free.dev"
    const val APP_PATH = "/sales-gps-dashboard"      // folder aplikasi di server
    const val API_KEY = "akuseorangkapiten"          // harus sama dengan di server (PHP)
    const val CONFIG_VERSION = 1                     // naikkan jika SERVER_HOST/APP_PATH diganti
    // ----------------------------------

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