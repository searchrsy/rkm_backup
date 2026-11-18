package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.ui.main.LoginScreen
import com.example.ui.main.MainScreen
import com.example.ui.main.MainViewModel
import com.example.ui.theme.BgLightGray
import com.example.ui.theme.RkmTheme
import com.example.update.UpdateDialog
import com.example.update.UpdateManager
import com.example.util.LocationHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val updateManager: UpdateManager by lazy { UpdateManager(applicationContext) }
    private val viewModel: MainViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(application, updateManager) as T
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateManager.checkPermissionAndResumeInstall()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RkmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgLightGray
                ) {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()

                    // Permintaan Runtime Permission Resmi Android saat Pertama Kali Membuka Aplikasi
                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                        val cameraGranted = permissions[Manifest.permission.CAMERA] == true

                        if (fineGranted || coarseGranted) {
                            lifecycleScope.launch {
                                val loc = LocationHelper.getCurrentLocationDetails(this@MainActivity)
                                if (loc != null) {
                                    viewModel.sendGpsLocation(loc.latitude, loc.longitude, loc.accuracy, loc.speed)
                                }
                            }
                        }

                        if (!fineGranted && !coarseGranted && !cameraGranted) {
                            viewModel.setPermissionBannerMessage("Izin lokasi dan kamera ditolak. Aktifkan di pengaturan agar dapat menggunakan fitur GPS dan foto laporan.")
                        } else if (!fineGranted && !coarseGranted) {
                            viewModel.setPermissionBannerMessage("Izin lokasi belum aktif. Lokasi salesman tidak dapat ditampilkan pada peta.")
                        } else if (!cameraGranted) {
                            viewModel.setPermissionBannerMessage("Izin kamera belum aktif. Pengambilan foto laporan memerlukan izin kamera.")
                        } else {
                            viewModel.setPermissionBannerMessage(null)
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (!viewModel.hasRequestedInitialPermissions) {
                            val fineGranted = ContextCompat.checkSelfPermission(
                                this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            val coarseGranted = ContextCompat.checkSelfPermission(
                                this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            val cameraGranted = ContextCompat.checkSelfPermission(
                                this@MainActivity, Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED

                            if (!fineGranted || !coarseGranted || !cameraGranted) {
                                viewModel.setInitialPermissionsRequested()
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.CAMERA
                                    )
                                )
                            } else {
                                viewModel.setInitialPermissionsRequested()
                            }
                        }
                    }

                    if (state.isLoggedIn) {
                        BackHandler(enabled = state.activeLaporanMember != null || state.selectedMember != null) {
                            if (state.activeLaporanMember != null) {
                                viewModel.openLaporan(null)
                            } else if (state.selectedMember != null) {
                                viewModel.selectMember(null)
                            }
                        }

                        MainScreen(
                            state = state,
                            viewModel = viewModel,
                            onOpenAppSettings = {
                                openAppSettings()
                            }
                        )
                    } else {
                        // Muat label cabang + daftar salesman dari server tiap layar login tampil
                        LaunchedEffect(Unit) { viewModel.loadLoginConfig() }

                        LoginScreen(
                            isLoading = state.loginLoading,
                            errorMessage = state.loginError,
                            cabangLabel = state.cabangLabel,
                            subtitle = state.loginSubtitle,
                            logoUrl = state.logoUrl,
                            salesmen = state.salesmen,
                            configLoading = state.loginConfigLoading,
                            configError = state.loginConfigError,
                            onRetryConfig = { viewModel.loadLoginConfig() },
                            onLogin = { code, pass -> viewModel.login(code, pass) }
                        )
                    }

                    // Dialog popup pembaruan (hanya muncul jika ada update baru yang belum dipasang)
                    UpdateDialogHost(updateManager = updateManager)
                }
            }
        }
    }

    @Composable
    private fun UpdateDialogHost(updateManager: UpdateManager) {
        val updateState by updateManager.state.collectAsStateWithLifecycle()
        UpdateDialog(
            state = updateState,
            installedVersionName = updateManager.getInstalledVersionName(),
            installedVersionCode = updateManager.getInstalledVersionCode(),
            onUpdateNow = updateManager::onUpdateNowClicked,
            onProceedMetered = updateManager::onProceedMeteredDownload,
            onLater = updateManager::onLaterClicked,
            onCancelDownload = updateManager::onCancelDownload,
            onRetry = updateManager::onUpdateNowClicked,
            onCloseFailed = updateManager::onCloseFailedClicked,
            onInstallReady = updateManager::proceedInstallation
        )
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}