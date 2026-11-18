package com.example.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.BgLightGray
import com.example.ui.theme.BorderGray
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RedError
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextPrimary

@Composable
fun UpdateDialog(
    state: UpdateState,
    installedVersionName: String,
    installedVersionCode: Long,
    onUpdateNow: () -> Unit,
    onProceedMetered: () -> Unit,
    onLater: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetry: () -> Unit,
    onCloseFailed: () -> Unit,
    onInstallReady: (UpdateData, java.io.File) -> Unit
) {
    if (state is UpdateState.Idle || state is UpdateState.Checking) {
        return
    }

    BackHandler(enabled = true) {
        when (state) {
            is UpdateState.Downloading -> onCancelDownload()
            is UpdateState.Available -> onLater()
            is UpdateState.Failed -> onCloseFailed()
            else -> onLater()
        }
    }

    Dialog(
        onDismissRequest = {
            if (state is UpdateState.Downloading) {
                onCancelDownload()
            } else {
                onLater()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = state !is UpdateState.Downloading && state !is UpdateState.Verifying
        )
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceWhite,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                .testTag("update_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                when (state) {
                    is UpdateState.Available -> {
                        AvailableContent(
                            data = state.data,
                            installedVersionName = installedVersionName,
                            installedVersionCode = installedVersionCode,
                            showMeteredWarning = state.showMeteredWarning,
                            onUpdateNow = onUpdateNow,
                            onProceedMetered = onProceedMetered,
                            onLater = onLater
                        )
                    }
                    is UpdateState.Downloading -> {
                        DownloadingContent(
                            data = state.data,
                            bytesDownloaded = state.bytesDownloaded,
                            totalBytes = state.totalBytes,
                            progress = state.progress,
                            onCancel = onCancelDownload
                        )
                    }
                    is UpdateState.Verifying -> {
                        VerifyingContent()
                    }
                    is UpdateState.ReadyToInstall -> {
                        ReadyToInstallContent(
                            data = state.data,
                            onInstall = { onInstallReady(state.data, state.apkFile) }
                        )
                    }
                    is UpdateState.Installing -> {
                        InstallingContent()
                    }
                    is UpdateState.Failed -> {
                        FailedContent(
                            data = state.data,
                            reason = state.reason,
                            canRetry = state.canRetry,
                            onRetry = onRetry,
                            onClose = onCloseFailed
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun AvailableContent(
    data: UpdateData,
    installedVersionName: String,
    installedVersionCode: Long,
    showMeteredWarning: Boolean,
    onUpdateNow: () -> Unit,
    onProceedMetered: () -> Unit,
    onLater: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = null,
            tint = NavyPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Pembaruan Tersedia",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = NavyPrimary
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Informasi Versi dan Ukuran
    Surface(
        color = BgLightGray,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderGray),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Versi Terpasang:", fontSize = 12.sp, color = TextGray)
                Text("$installedVersionName (v$installedVersionCode)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Versi Baru:", fontSize = 12.sp, color = TextGray)
                Text("${data.versionName ?: "-"} (v${data.versionCode})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ukuran Berkas:", fontSize = 12.sp, color = TextGray)
                Text(data.sizeInMbFormatted, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            }
        }
    }

    if (data.forceUpdate) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavyPrimary.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Pembaruan ini sangat disarankan untuk kelancaran sinkronisasi.",
                fontSize = 11.sp,
                color = NavyPrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }

    // Catatan Perubahan (Changelog)
    if (!data.changelog.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text("Catatan Pembaruan:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp)
                .verticalScroll(rememberScrollState())
                .background(BgLightGray, RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            Text(
                text = data.changelog,
                fontSize = 11.sp,
                color = TextPrimary,
                lineHeight = 16.sp
            )
        }
    }

    // Peringatan Data Seluler (jika aktif)
    if (showMeteredWarning) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            color = RedError.copy(alpha = 0.08f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, RedError.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = RedError, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Anda memakai data seluler. Ukuran unduhan ${data.sizeInMbFormatted}. Sebaiknya gunakan Wi-Fi.",
                    fontSize = 11.sp,
                    color = RedError,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onLater) {
                Text("Tunggu Wi-Fi", color = TextGray, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onProceedMetered,
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
            ) {
                Text("Tetap Unduh", color = SurfaceWhite, fontSize = 13.sp)
            }
        }
    } else {
        Spacer(modifier = Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onLater, modifier = Modifier.testTag("btn_update_later")) {
                Text("Nanti", color = TextGray, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onUpdateNow,
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                modifier = Modifier.testTag("btn_update_now")
            ) {
                Text("Perbarui", color = SurfaceWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DownloadingContent(
    data: UpdateData,
    bytesDownloaded: Long,
    totalBytes: Long,
    progress: Float,
    onCancel: () -> Unit
) {
    Text(
        text = "Mengunduh Pembaruan...",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = NavyPrimary
    )
    Spacer(modifier = Modifier.height(12.dp))

    LinearProgressIndicator(
        progress = { progress },
        color = NavyPrimary,
        trackColor = BgLightGray,
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
    )

    Spacer(modifier = Modifier.height(8.dp))
    val percent = (progress * 100).toInt()
    val downloadedMb = String.format(java.util.Locale.US, "%.1f", bytesDownloaded.toDouble() / (1024.0 * 1024.0))
    val totalMb = data.sizeInMbFormatted

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$percent%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
        Text("$downloadedMb MB / $totalMb", fontSize = 12.sp, color = TextGray)
    }

    Spacer(modifier = Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.testTag("btn_cancel_download")) {
            Text("Batal", color = TextGray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun VerifyingContent() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = NavyPrimary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Memverifikasi berkas APK...",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

@Composable
private fun ReadyToInstallContent(
    data: UpdateData,
    onInstall: () -> Unit
) {
    Text(
        text = "Unduhan Selesai",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = NavyPrimary
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Berkas APK v${data.versionName} berhasil diunduh dan diverifikasi. Tekan Pasang untuk melanjutkan.",
        fontSize = 12.sp,
        color = TextPrimary
    )
    Spacer(modifier = Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(
            onClick = onInstall,
            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
            modifier = Modifier.testTag("btn_install_ready")
        ) {
            Text("Pasang", color = SurfaceWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InstallingContent() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = NavyPrimary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Membuka pemasang paket...",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

@Composable
private fun FailedContent(
    data: UpdateData?,
    reason: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = RedError, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Pembaruan Gagal",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = RedError
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = reason,
        fontSize = 12.sp,
        color = TextPrimary,
        lineHeight = 17.sp
    )
    Spacer(modifier = Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClose, modifier = Modifier.testTag("btn_close_failed")) {
            Text("Tutup", color = TextGray, fontSize = 13.sp)
        }
        if (canRetry && data != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                modifier = Modifier.testTag("btn_retry_update")
            ) {
                Text("Coba Lagi", color = SurfaceWhite, fontSize = 13.sp)
            }
        }
    }
}
