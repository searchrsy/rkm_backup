package com.example.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.config.AppConfig
import com.example.data.model.MemberItem
import com.example.ui.components.LaporanDialog
import com.example.ui.components.MemberCard
import com.example.ui.components.MemberDetailDialog
import com.example.ui.components.OsmMapView
import com.example.ui.theme.BgLightGray
import com.example.ui.theme.BlueSecondary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RedError
import com.example.ui.theme.StatusBelumHijau
import com.example.ui.theme.StatusSelesaiAbu
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextPrimary
import com.example.util.LocationHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenAppSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showDetailDialog by remember { mutableStateOf(false) }
    var mapFocusTrigger by remember { mutableIntStateOf(0) }

    // Launcher Izin Lokasi
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            scope.launch {
                val loc = LocationHelper.getCurrentLocationDetails(context)
                if (loc != null) {
                    viewModel.sendGpsLocation(loc.latitude, loc.longitude, loc.accuracy, loc.speed)
                    Toast.makeText(context, "Lokasi salesman terdeteksi.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Sinyal GPS belum didapatkan.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Izin lokasi tidak diberikan. Peta tetap dapat digunakan.", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestLocationWithCheck() {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            scope.launch {
                val loc = LocationHelper.getCurrentLocationDetails(context)
                if (loc != null) {
                    viewModel.sendGpsLocation(loc.latitude, loc.longitude, loc.accuracy, loc.speed)
                    Toast.makeText(context, "Lokasi salesman terdeteksi.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Sinyal GPS belum didapatkan.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    val onMapMemberSelected = remember(viewModel) {
        { member: MemberItem ->
            viewModel.selectMember(member)
            showDetailDialog = true
        }
    }

    val onMemberCardClick: (MemberItem) -> Unit = remember(viewModel, context) {
        { member ->
            val lat = member.doubleLatitude
            val lon = member.doubleLongitude
            val hasValidCoord = lat != null && lon != null &&
                lat != 0.0 && lon != 0.0 &&
                lat in -90.0..90.0 && lon in -180.0..180.0
            if (!hasValidCoord) {
                Toast.makeText(context, "Koordinat member tidak tersedia", Toast.LENGTH_SHORT).show()
            }
            viewModel.selectMember(member)
            mapFocusTrigger++
        }
    }

    val onMemberDetailClick = remember(viewModel) {
        { member: MemberItem ->
            viewModel.selectMember(member)
            showDetailDialog = true
        }
    }

    // Pelacakan GPS berkala di latar belakang saat salesman aktif
    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            requestLocationWithCheck()
            while (true) {
                kotlinx.coroutines.delay(com.example.config.AppConfig.GPS_SAMPLE_INTERVAL_MS)
                val fineGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val coarseGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (fineGranted || coarseGranted) {
                    val loc = LocationHelper.getCurrentLocationDetails(context)
                    if (loc != null) {
                        viewModel.sendGpsLocation(loc.latitude, loc.longitude, loc.accuracy, loc.speed)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            // A. Header Ringkas (Tetap terlihat saat daftar di-scroll)
            Surface(
                color = NavyPrimary,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (state.cabangLabel.isNotBlank()) "RKM SPI ${state.cabangLabel}" else "RKM SPI",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = SurfaceWhite
                            )
                            Text(
                                text = "Salesman: ${state.namaSalesman.ifBlank { state.kodeSalesman }}",
                                fontSize = 12.sp,
                                color = SurfaceWhite.copy(alpha = 0.85f)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Tombol Refresh Manual
                            IconButton(
                                onClick = { viewModel.loadRkm() },
                                enabled = !state.rkmLoading,
                                modifier = Modifier.size(36.dp).testTag("btn_refresh")
                            ) {
                                if (state.rkmLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = SurfaceWhite
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Muat Ulang RKM",
                                        tint = SurfaceWhite
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Tombol Logout dengan Konfirmasi
                            IconButton(
                                onClick = { viewModel.openLogoutConfirm(true) },
                                modifier = Modifier.size(36.dp).testTag("btn_logout")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Keluar",
                                    tint = SurfaceWhite
                                )
                            }
                        }
                    }

                    // Baris Tanggal Kunjungan & Info Rencana
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tanggal: ${state.tanggal ?: "Hari Ini"}",
                            fontSize = 11.sp,
                            color = SurfaceWhite.copy(alpha = 0.75f)
                        )
                        if (state.idRkm != null) {
                            Text(
                                text = "ID Rencana: #${state.idRkm}",
                                fontSize = 11.sp,
                                color = SurfaceWhite.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        containerColor = SurfaceWhite,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Banner Pemberitahuan Status Permission jika ada
            if (!state.permissionBannerMessage.isNullOrBlank()) {
                Surface(
                    color = BgLightGray,
                    shape = RoundedCornerShape(0.dp),
                    border = BorderStroke(1.dp, BorderGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = BlueSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.permissionBannerMessage,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onOpenAppSettings,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Pengaturan", fontSize = 11.sp, color = NavyPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Banner Error Jaringan / API jika ada
            if (!state.rkmError.isNullOrBlank()) {
                Surface(
                    color = RedError.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = RedError, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.rkmError,
                            color = RedError,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { viewModel.loadRkm() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Coba Lagi", fontSize = 11.sp, color = NavyPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // B. Peta Kotak (osmdroid) - 35-40% tinggi layar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .aspectRatio(1.25f) // Rasio mendekati 4:3
            ) {
                OsmMapView(
                    members = state.members,
                    salesmanLocation = state.salesmanLocation,
                    selectedMember = state.selectedMember,
                    onMemberSelected = onMapMemberSelected,
                    onRequestLocation = { requestLocationWithCheck() },
                    lastKnownLocations = state.lastKnownLocations,
                    historyTracks = state.historyTracks,
                    focusTrigger = mapFocusTrigger,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // C. Header Daftar Member & Ringkasan Status
            val totalMember = state.members.size
            val finishedCount = remember(state.members) { state.members.count { it.isFinished } }

            Surface(
                color = BgLightGray,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Daftar Member",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = NavyPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "$totalMember Member",
                                color = NavyPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Selesai:",
                            fontSize = 12.sp,
                            color = TextGray
                        )
                        Text(
                            text = "$finishedCount / $totalMember",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (finishedCount == totalMember && totalMember > 0) StatusBelumHijau else BlueSecondary
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderGray, thickness = 1.dp)

            // C. Daftar Member Scroll Vertikal
            MemberListSection(
                members = state.members,
                selectedMemberKode = state.selectedMember?.kodeMember,
                rkmLoading = state.rkmLoading,
                rkmError = state.rkmError,
                onMemberClick = onMemberCardClick,
                onMemberDetailClick = onMemberDetailClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }

    // Dialog Popup Detail Member
    if (showDetailDialog && state.selectedMember != null) {
        MemberDetailDialog(
            member = state.selectedMember,
            onDismiss = { showDetailDialog = false },
            onOpenLaporan = { member ->
                viewModel.openLaporan(member)
            }
        )
    }

    // Dialog Formulir Laporan Kunjungan
    if (state.activeLaporanMember != null) {
        val draft = remember(state.activeLaporanMember.kodeMember) {
            viewModel.getDraft(state.activeLaporanMember.kodeMember)
        }
        LaporanDialog(
            member = state.activeLaporanMember,
            idRkm = state.idRkm,
            existingDraft = draft,
            isSubmitting = state.isSubmittingLaporan,
            errorMessage = state.laporanError,
            onSaveDraft = { newDraft ->
                viewModel.saveDraft(newDraft)
            },
            onSubmit = { reportDraft, photoFile ->
                viewModel.submitLaporan(reportDraft, photoFile)
            },
            onDismiss = {
                viewModel.openLaporan(null)
            }
        )
    }

    // Dialog Konfirmasi Logout
    if (state.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.openLogoutConfirm(false) },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(12.dp),
            title = {
                Text("Konfirmasi Keluar", color = NavyPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin keluar dari akun salesman? Draft laporan kunjungan Anda akan tetap tersimpan aman di perangkat.",
                    color = TextPrimary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = RedError),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("btn_confirm_logout")
                ) {
                    Text("Keluar", color = SurfaceWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.openLogoutConfirm(false) }) {
                    Text("Batal", color = TextGray)
                }
            }
        )
    }
}

@Composable
private fun MemberListSection(
    members: List<MemberItem>,
    selectedMemberKode: String?,
    rkmLoading: Boolean,
    rkmError: String?,
    onMemberClick: (MemberItem) -> Unit,
    onMemberDetailClick: (MemberItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (rkmLoading && members.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = NavyPrimary, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Memuat daftar rencana kunjungan...", fontSize = 13.sp, color = TextGray)
            }
        }
    } else if (members.isEmpty() && rkmError == null) {
        // Sah: Backend memastikan tidak ada rencana kunjungan
        Box(
            modifier = modifier.padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = TextGray,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Belum ada rencana kunjungan untuk hari ini",
                    fontSize = 14.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Hubungi supervisor atau admin SPI ${AppConfig.CABANG_NAMA} jika rencana kunjungan belum diterbitkan.",
                    fontSize = 12.sp,
                    color = TextGray,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = members,
                key = { it.kodeMember },
                contentType = { "member_card" }
            ) { member ->
                val itemClick = remember(member, onMemberClick) { { onMemberClick(member) } }
                val itemDetailClick = remember(member, onMemberDetailClick) { { onMemberDetailClick(member) } }
                MemberCard(
                    member = member,
                    isSelected = (member.kodeMember == selectedMemberKode),
                    onClick = itemClick,
                    onDetailClick = itemDetailClick
                )
            }
        }
    }
}