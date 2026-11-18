package com.example.update

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig

class UpdateGuard(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(UpdateConfig.PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "UpdateGuard"
    }

    /**
     * Memeriksa apakah fitur update diizinkan berjalan.
     * Menggabungkan Kunci Build (Layer 1) dan Kunci Otomatis di Perangkat (Layer 3).
     */
    fun isFeatureAvailable(currentVersionCode: Long): Boolean {
        // Layer 1: Kunci build
        if (!BuildConfig.UPDATE_FEATURE_ENABLED) {
            return false
        }

        // Periksa apakah ada kunci otomatis yang aktif
        val isLocked = prefs.getBoolean(UpdateConfig.KEY_LOCKED, false)
        if (isLocked) {
            val lockedAt = prefs.getLong(UpdateConfig.KEY_LOCKED_AT_VERSION, -1L)
            if (currentVersionCode > lockedAt && lockedAt != -1L) {
                // Kunci dibuka otomatis jika versi aplikasi telah berubah (pengguna install manual)
                unlock(currentVersionCode)
                return true
            }
            Log.w(TAG, "Update feature is locked due to prior consecutive internal failures.")
            return false
        }

        return true
    }

    /**
     * Cek status update_busy saat inisialisasi aplikasi.
     * Jika update_busy masih menyala saat startup, berarti proses instalasi/verifikasi sebelumnya terhenti tak wajar.
     */
    fun checkAndRecoverStartupBusyFlag() {
        val wasBusy = prefs.getBoolean(UpdateConfig.KEY_UPDATE_BUSY, false)
        if (wasBusy) {
            prefs.edit().putBoolean(UpdateConfig.KEY_UPDATE_BUSY, false).apply()
            Log.i(TAG, "Penanda update_busy dari sesi sebelumnya dibersihkan saat startup tanpa mengunci fitur.")
        }
    }

    fun setBusy(busy: Boolean) {
        prefs.edit().putBoolean(UpdateConfig.KEY_UPDATE_BUSY, busy).apply()
    }

    fun recordInternalFailure(reason: String) {
        val currentFailures = prefs.getInt(UpdateConfig.KEY_CONSECUTIVE_FAILURES, 0) + 1
        val editor = prefs.edit().putInt(UpdateConfig.KEY_CONSECUTIVE_FAILURES, currentFailures)

        Log.e(TAG, "Internal update failure recorded ($currentFailures/${UpdateConfig.MAX_CONSECUTIVE_FAILURES}): $reason")

        if (currentFailures >= UpdateConfig.MAX_CONSECUTIVE_FAILURES) {
            editor.putBoolean(UpdateConfig.KEY_LOCKED, true)
            val currentCode = getInstalledVersionCode()
            editor.putLong(UpdateConfig.KEY_LOCKED_AT_VERSION, currentCode)
            Log.e(TAG, "Circuit breaker activated. Update feature is now LOCKED at versionCode $currentCode.")
        }
        editor.apply()
    }

    fun resetFailures() {
        if (prefs.getInt(UpdateConfig.KEY_CONSECUTIVE_FAILURES, 0) > 0) {
            prefs.edit().putInt(UpdateConfig.KEY_CONSECUTIVE_FAILURES, 0).apply()
        }
    }

    private fun unlock(newVersionCode: Long) {
        prefs.edit()
            .putBoolean(UpdateConfig.KEY_LOCKED, false)
            .putInt(UpdateConfig.KEY_CONSECUTIVE_FAILURES, 0)
            .putLong(UpdateConfig.KEY_LOCKED_AT_VERSION, -1L)
            .apply()
        Log.i(TAG, "Update circuit breaker unlocked automatically for new versionCode $newVersionCode.")
    }

    private fun getInstalledVersionCode(): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            0L
        }
    }
}
