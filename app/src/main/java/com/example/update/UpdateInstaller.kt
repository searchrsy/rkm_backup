package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.util.Arrays

class UpdateInstaller(
    private val context: Context,
    private val updateGuard: UpdateGuard
) {

    companion object {
        private const val TAG = "UpdateInstaller"
    }

    /**
     * Memvalidasi APK yang diunduh terhadap integritas paket dan kesesuaian sertifikat penandatangan.
     */
    fun validateApk(apkFile: File, expectedData: UpdateData, installedVersionCode: Long) {
        if (!apkFile.exists() || apkFile.length() <= 0) {
            throw IllegalStateException("Berkas APK tidak ditemukan.")
        }

        if (expectedData.size > 0 && apkFile.length() != expectedData.size) {
            apkFile.delete()
            throw IllegalStateException("Ukuran berkas APK tidak sesuai.")
        }

        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val archiveInfo: PackageInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: run {
                apkFile.delete()
                throw IllegalStateException("Gagal membaca struktur berkas APK.")
            }

        // 1. Validasi package name
        if (archiveInfo.packageName != context.packageName) {
            apkFile.delete()
            throw IllegalStateException("Package name APK (${archiveInfo.packageName}) tidak cocok dengan aplikasi (${context.packageName}).")
        }

        // 2. Validasi version code
        val apkVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.versionCode.toLong()
        }

        if (apkVersionCode <= installedVersionCode) {
            apkFile.delete()
            throw IllegalStateException("Versi APK yang diunduh ($apkVersionCode) tidak lebih baru dari versi terpasang ($installedVersionCode).")
        }

        // 3. Validasi sertifikat tanda tangan (signature matching)
        val installedPkgInfo: PackageInfo = pm.getPackageInfo(context.packageName, flags)
        val signaturesMatch = verifySignaturesMatch(archiveInfo, installedPkgInfo)
        if (!signaturesMatch) {
            apkFile.delete()
            throw IllegalStateException("Sertifikat penandatangan APK tidak cocok dengan aplikasi yang terpasang.")
        }

        Log.i(TAG, "APK validation passed successfully for versionCode $apkVersionCode")
    }

    private fun verifySignaturesMatch(archiveInfo: PackageInfo, installedInfo: PackageInfo): Boolean {
        return try {
            fun hashBytes(bytes: ByteArray): String {
                val md = MessageDigest.getInstance("SHA-256")
                return md.digest(bytes).joinToString("") { "%02x".format(it) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val archiveSigners = archiveInfo.signingInfo?.apkContentsSigners
                val installedSigners = installedInfo.signingInfo?.apkContentsSigners
                if (archiveSigners.isNullOrEmpty() || installedSigners.isNullOrEmpty()) {
                    false
                } else {
                    val archiveHashes = archiveSigners.map { hashBytes(it.toByteArray()) }.toSet()
                    val installedHashes = installedSigners.map { hashBytes(it.toByteArray()) }.toSet()
                    archiveHashes == installedHashes
                }
            } else {
                @Suppress("DEPRECATION")
                val archiveSigs = archiveInfo.signatures
                @Suppress("DEPRECATION")
                val installedSigs = installedInfo.signatures
                if (archiveSigs.isNullOrEmpty() || installedSigs.isNullOrEmpty()) {
                    false
                } else {
                    val archiveHashes = archiveSigs.map { hashBytes(it.toByteArray()) }.toSet()
                    val installedHashes = installedSigs.map { hashBytes(it.toByteArray()) }.toSet()
                    archiveHashes == installedHashes
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing signatures: ${e.message}")
            false
        }
    }

    /**
     * Memeriksa apakah izin memasang aplikasi dari sumber ini sudah diizinkan.
     */
    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Membuka pengaturan izin instalasi aplikasi tidak dikenal.
     */
    fun openUnknownAppSourcesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Meluncurkan intent instalasi paket APK resmi Android via FileProvider.
     */
    fun launchInstall(apkFile: File) {
        if (!apkFile.exists()) {
            throw IllegalStateException("File APK untuk dipasang tidak ditemukan.")
        }

        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            updateGuard.setBusy(true)
            context.startActivity(installIntent)
        } finally {
            updateGuard.setBusy(false)
        }
    }
}
