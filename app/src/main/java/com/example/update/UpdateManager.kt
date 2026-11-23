package com.example.update

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import com.example.config.AppConfig
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class UpdateManager(
    private val context: Context,
    val updateGuard: UpdateGuard = UpdateGuard(context),
    val repository: UpdateRepository = UpdateRepository(context, updateGuard),
    val downloader: ApkDownloader = ApkDownloader(context, updateGuard),
    val installer: UpdateInstaller = UpdateInstaller(context, updateGuard)
) {

    companion object {
        private const val TAG = "UpdateManager"
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled exception in UpdateManager: ${throwable.message}", throwable)
        updateGuard.recordInternalFailure("Unhandled coroutine exception: ${throwable.message}")
        _state.value = UpdateState.Failed(
            data = (_state.value as? UpdateState.Available)?.data
                ?: (_state.value as? UpdateState.Downloading)?.data
                ?: (_state.value as? UpdateState.Verifying)?.data
                ?: (_state.value as? UpdateState.ReadyToInstall)?.data,
            reason = "Terjadi kesalahan internal pada proses pembaruan.",
            canRetry = true
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + exceptionHandler)
    private val mutex = Mutex()
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val hasCheckedInProcess = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        // Cek flag busy saat aplikasi dibuka
        updateGuard.checkAndRecoverStartupBusyFlag()
        // Bersihkan berkas pembaruan lama yang versinya <= versi terpasang
        cleanupObsoleteApks()
    }

    fun getInstalledVersionCode(): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            1L
        }
    }

    fun getInstalledVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    /**
     * Memeriksa pembaruan maksimal sekali per hari kalender lokal saat aplikasi dibuka.
     * Dipicu sekali per proses aplikasi. Tidak memunculkan UI apa pun jika tidak ada update, server mati, atau fitur terkunci.
     */
    fun checkUpdateOnceDaily() {
        if (!hasCheckedInProcess.compareAndSet(false, true)) {
            Log.d(TAG, "Pemeriksaan update sudah pernah dipicu pada proses ini.")
            return
        }

        scope.launch {
            if (mutex.isLocked) return@launch

            mutex.withLock {
                val installedCode = getInstalledVersionCode()
                if (!updateGuard.isFeatureAvailable(installedCode)) {
                    return@withLock
                }

                if (!repository.shouldCheckToday()) {
                    Log.d(TAG, "Pemeriksaan update sudah dilakukan hari ini.")
                    return@withLock
                }

                // Catat tanggal SEBELUM request dikirim untuk menghindari request berulang jika timeout/gagal
                repository.recordCheckAttemptToday()

                _state.value = UpdateState.Checking
                val updateData = try {
                    repository.checkUpdate(installedCode)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(TAG, "Error checking update: ${e.message}")
                    null
                }

                if (updateData != null) {
                    val metered = isNetworkMetered()
                    _state.value = UpdateState.Available(
                        data = updateData,
                        isMetered = metered,
                        showMeteredWarning = false
                    )
                } else {
                    _state.value = UpdateState.Idle
                }
            }
        }
    }

    @Volatile
    private var lastCheckAt = 0L

    /**
     * Memeriksa pembaruan setiap kali pengguna berhasil login.
     * Satu login berhasil = satu HTTP GET baru ke check.php.
     */
    fun checkUpdateOnLogin() = performCheck()

    /**
     * Dipanggil saat aplikasi dibuka/kembali ke layar (MainActivity.onResume).
     * Memeriksa maksimal sekali per AppConfig.UPDATE_RECHECK_INTERVAL_MS agar versi baru
     * segera terdeteksi tanpa menghabiskan kuota request.
     */
    fun checkUpdateIfDue() {
        val now = System.currentTimeMillis()
        if (lastCheckAt != 0L && now - lastCheckAt < AppConfig.UPDATE_RECHECK_INTERVAL_MS) return
        performCheck()
    }

    private fun performCheck() {
        // Jangan menimpa dialog yang sedang tampil/berjalan (unduh, pasang, gagal, dst.)
        if (_state.value !is UpdateState.Idle) return
        lastCheckAt = System.currentTimeMillis()

        scope.launch {
            mutex.withLock {
                if (_state.value !is UpdateState.Idle) return@withLock
                val installedCode = getInstalledVersionCode()

                _state.value = UpdateState.Checking
                val updateData = try {
                    repository.checkUpdate(installedCode)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(TAG, "Error checking update: ${e.message}")
                    null
                }

                if (updateData != null) {
                    _state.value = UpdateState.Available(
                        data = updateData,
                        isMetered = isNetworkMetered(),
                        showMeteredWarning = false
                    )
                } else {
                    _state.value = UpdateState.Idle
                }
            }
        }
    }

    /**
     * Dipanggil saat pengguna menekan tombol "Update Sekarang".
     */
    fun onUpdateNowClicked() {
        val current = _state.value
        if (current is UpdateState.Available) {
            if (current.isMetered && !current.showMeteredWarning) {
                // Tampilkan peringatan data seluler terlebih dahulu
                _state.value = current.copy(showMeteredWarning = true)
            } else {
                startDownload(current.data)
            }
        } else if (current is UpdateState.Failed && current.data != null) {
            startDownload(current.data)
        }
    }

    /**
     * Dipanggil saat pengguna memilih "Tetap Unduh" pada peringatan data seluler.
     */
    fun onProceedMeteredDownload() {
        val current = _state.value
        if (current is UpdateState.Available) {
            startDownload(current.data)
        }
    }

    /**
     * Dipanggil saat pengguna menekan "Nanti" atau "Tunggu Wi-Fi".
     */
    fun onLaterClicked() {
        // Update wajib: tidak boleh ditunda
        if (_state.value.dataOrNull()?.isMandatory == true) return
        _state.value = UpdateState.Idle
    }

    /**
     * Dipanggil saat pengguna membatalkan proses unduhan.
     * Berkas .part dipertahankan agar bisa dilanjutkan kembali (resume).
     */
    fun onCancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val current = _state.value
        val data = (current as? UpdateState.Downloading)?.data
            ?: (current as? UpdateState.Verifying)?.data
            ?: (current as? UpdateState.Available)?.data

        if (data != null) {
            _state.value = UpdateState.Available(
                data = data,
                isMetered = isNetworkMetered(),
                showMeteredWarning = false
            )
        } else {
            _state.value = UpdateState.Idle
        }
    }

    fun onCloseFailedClicked() {
        // Update wajib: dialog gagal tidak boleh ditutup, hanya "Coba Lagi"
        if (_state.value.dataOrNull()?.isMandatory == true) return
        _state.value = UpdateState.Idle
    }

    private fun startDownload(data: UpdateData) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            if (mutex.isLocked) return@launch

            mutex.withLock {
                _state.value = UpdateState.Downloading(
                    data = data,
                    bytesDownloaded = 0L,
                    totalBytes = data.size,
                    progress = 0f
                )

                try {
                    val apkFile = downloader.downloadApk(data) { downloaded, total, progress ->
                        _state.value = UpdateState.Downloading(
                            data = data,
                            bytesDownloaded = downloaded,
                            totalBytes = total,
                            progress = progress
                        )
                    }

                    _state.value = UpdateState.Verifying(data)
                    installer.validateApk(apkFile, data, getInstalledVersionCode())

                    _state.value = UpdateState.ReadyToInstall(data, apkFile)
                    proceedInstallation(data, apkFile)
                } catch (e: CancellationException) {
                    Log.d(TAG, "Download cancelled by user")
                    // Do not treat user cancellation as internal error
                } catch (e: java.io.IOException) {
                    // Jaringan terputus / timeout / server mati: JANGAN hitung sebagai kegagalan internal!
                    Log.w(TAG, "Network IO error during download: ${e.message}")
                    _state.value = UpdateState.Failed(
                        data = data,
                        reason = "Koneksi terputus saat mengunduh pembaruan. Silakan periksa jaringan dan coba lagi.",
                        canRetry = true
                    )
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Download/validation error: ${e.message}")
                    _state.value = UpdateState.Failed(
                        data = data,
                        reason = e.message ?: "Gagal memproses berkas pembaruan.",
                        canRetry = true
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected internal error during update: ${e.message}", e)
                    updateGuard.recordInternalFailure("Download/Install internal error: ${e.message}")
                    _state.value = UpdateState.Failed(
                        data = data,
                        reason = "Gagal mengunduh pembaruan: ${e.localizedMessage ?: "Kesalahan internal"}",
                        canRetry = true
                    )
                }
            }
        }
    }

    fun proceedInstallation(data: UpdateData, apkFile: File) {
        if (!installer.canInstallPackages()) {
            _state.value = UpdateState.Failed(
                data = data,
                reason = "Aplikasi membutuhkan izin untuk memasang pembaruan. Silakan aktifkan izin di Pengaturan.",
                canRetry = true
            )
            installer.openUnknownAppSourcesSettings()
            return
        }

        try {
            _state.value = UpdateState.Installing(data, apkFile)
            installer.launchInstall(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer: ${e.message}", e)
            updateGuard.recordInternalFailure("Launch install error: ${e.message}")
            _state.value = UpdateState.Failed(
                data = data,
                reason = "Gagal membuka installer: ${e.localizedMessage ?: "Terjadi kesalahan"}",
                canRetry = true
            )
        }
    }

    /**
     * Dipanggil saat Activity resume untuk memeriksa apakah izin install baru saja diberikan oleh pengguna.
     */
    fun checkPermissionAndResumeInstall() {
        val current = _state.value
        if (current is UpdateState.ReadyToInstall) {
            if (installer.canInstallPackages()) {
                proceedInstallation(current.data, current.apkFile)
            }
        } else if (current is UpdateState.Installing && current.data.isMandatory) {
            // Pemasang sistem dibatalkan pengguna lalu kembali ke aplikasi: tampilkan tombol "Pasang"
            // lagi agar dialog wajib tidak macet di status "Membuka pemasang paket...".
            _state.value = UpdateState.ReadyToInstall(current.data, current.apkFile)
        } else if (current is UpdateState.Failed && current.data != null) {
            val cachedApk = downloader.getExistingValidApk(current.data)
            if (cachedApk != null && installer.canInstallPackages()) {
                proceedInstallation(current.data, cachedApk)
            }
        }
    }

    fun isNetworkMetered(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.isActiveNetworkMetered == true
        } catch (_: Exception) {
            false
        }
    }

    private fun cleanupObsoleteApks() {
        try {
            val installedCode = getInstalledVersionCode()
            val updatesDir = downloader.getUpdatesDir()
            downloader.cleanOldVersions(updatesDir, installedCode)
        } catch (_: Exception) {}
    }
}