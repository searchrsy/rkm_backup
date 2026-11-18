package com.example.update

import com.example.config.AppConfig

object UpdateConfig {
    // Alamat server diatur di AppConfig.kt
    const val UPDATE_BASE_URL = AppConfig.UPDATE_BASE_URL
    const val CHECK_URL = "$UPDATE_BASE_URL/check.php"
    const val APK_DOWNLOAD_URL = "$UPDATE_BASE_URL/apk/app-release.apk"
    const val EXPECTED_HOST = AppConfig.SERVER_HOST
    const val EXPECTED_PATH_PREFIX = "${AppConfig.APP_PATH}/apk/"
    const val PREFS_NAME = "update_prefs"

    const val KEY_LAST_CHECK_DATE = "last_check_date"
    const val KEY_CONSECUTIVE_FAILURES = "consecutive_failures"
    const val KEY_LOCKED = "locked"
    const val KEY_LOCKED_AT_VERSION = "locked_at_version"
    const val KEY_UPDATE_BUSY = "update_busy"
    const val KEY_SAVED_ETAG = "saved_etag"
    const val KEY_TARGET_VERSION = "target_version"

    const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer
    const val TIMEOUT_SECONDS = 15L
    const val MAX_CONSECUTIVE_FAILURES = 2
}