package com.example.ui.components

import android.app.DatePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.config.AppConfig
import com.example.data.model.DraftLaporan
import com.example.data.model.MemberItem
import com.example.ui.theme.BgLightGray
import com.example.ui.theme.BlueSecondary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.LokasiBiru
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RedError
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextPrimary
import com.example.util.LocationHelper
import com.example.util.PhotoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.TimeZone

@Composable
fun LaporanDialog(
    member: MemberItem,
    idRkm: Long?,
    existingDraft: DraftLaporan?,
    isSubmitting: Boolean,
    errorMessage: String?,
    onSaveDraft: (DraftLaporan) -> Unit,
    onSubmit: (DraftLaporan, File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val options = LaporanValidator.ALLOWED_HASIL
    var selectedHasil by remember { mutableStateOf(existingDraft?.hasil ?: "") }
    var selectedOrderHariIni by remember {
        mutableStateOf<String?>(if (existingDraft?.hasil == "BERHASIL") existingDraft.orderHariIni else null)
    }
    var catatan by remember { mutableStateOf(existingDraft?.catatan ?: "") }
    var followUpDate by remember {
        mutableStateOf<String?>(if (existingDraft?.hasil == "TOKO TUTUP") existingDraft.followUp else null)
    }
    var currentLatitude by remember { mutableStateOf(existingDraft?.latitude) }
    var currentLongitude by remember { mutableStateOf(existingDraft?.longitude) }
    var localPhotoPath by remember { mutableStateOf(existingDraft?.photoPath) }
    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    var showHasilError by remember { mutableStateOf(false) }
    var showOrderError by remember { mutableStateOf(false) }
    var showCatatanError by remember { mutableStateOf(false) }
    var showFollowUpError by remember { mutableStateOf(false) }

    val validation = remember(selectedHasil, selectedOrderHariIni, catatan, followUpDate) {
        LaporanValidator.validate(
            hasil = selectedHasil,
            orderHariIni = selectedOrderHariIni,
            catatan = catatan,
            followUp = followUpDate
        )
    }
    val hasilErrorText = if (showHasilError) validation.hasilError else null
    val orderErrorText = if (showOrderError) validation.orderHariIniError else null
    val catatanErrorText = if (showCatatanError) validation.catatanError else null
    val followUpErrorText = if (showFollowUpError) validation.followUpError else null

    val onSelectOption: (String) -> Unit = { option ->
        if (selectedHasil != option) {
            selectedHasil = option
            if (option != "BERHASIL") {
                selectedOrderHariIni = null
            }
            if (option != "TOKO TUTUP") {
                followUpDate = null
            }
            showOrderError = false
            showFollowUpError = false
        }
        showHasilError = false
    }

    // Dialog Pemilih Tanggal Follow Up (zona waktu cabang)
    val openDatePicker = {
        val cal = Calendar.getInstance(TimeZone.getTimeZone(AppConfig.TIMEZONE))
        // Jika sudah ada tanggal terpilih, inisialisasi picker dengan tanggal tersebut
        if (!followUpDate.isNullOrBlank()) {
            try {
                val parts = followUpDate!!.split("-")
                if (parts.size == 3) {
                    cal.set(Calendar.YEAR, parts[0].toInt())
                    cal.set(Calendar.MONTH, parts[1].toInt() - 1)
                    cal.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                }
            } catch (_: Exception) {}
        }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val dpd = DatePickerDialog(
            context,
            { _, y, m, d ->
                followUpDate = "%04d-%02d-%02d".format(y, m + 1, d)
                showFollowUpError = false
            },
            year,
            month,
            day
        )
        dpd.show()
    }

    // Launcher kamera
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val sourceFile = pendingPhotoFile
        if (success && sourceFile != null) {
            scope.launch {
                val compressedPath = withContext(Dispatchers.IO) {
                    if (sourceFile.exists()) {
                        PhotoHelper.compressPhoto(sourceFile, context).absolutePath
                    } else {
                        null
                    }
                }
                if (compressedPath != null) {
                    localPhotoPath = compressedPath
                }
            }
        }
    }

    // Ambil GPS otomatis 1 kali saat form dibuka jika belum ada koordinat
    LaunchedEffect(Unit) {
        if (currentLatitude == null || currentLongitude == null) {
            isFetchingLocation = true
            val loc = LocationHelper.getCurrentLocation(context)
            if (loc != null) {
                currentLatitude = loc.first
                currentLongitude = loc.second
            }
            isFetchingLocation = false
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (catatan.isNotBlank() || localPhotoPath != null || !followUpDate.isNullOrBlank()) {
                showDiscardConfirm = true
            } else {
                onDismiss()
            }
        },
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(12.dp),
        title = {
            Column {
                Text(
                    text = "Laporan Kunjungan",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )
                Text(
                    text = "${member.displayName} (${member.kodeMember})",
                    fontSize = 13.sp,
                    color = BlueSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = BorderGray)

                // Indikator Validasi ID Rencana RKM dari data.rencana.id
                if (idRkm == null || idRkm <= 0) {
                    Surface(
                        color = RedError.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = RedError, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "id_rkm tidak valid. Pastikan ada rencana kunjungan aktif sebelum melapor.",
                                color = RedError,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Error Banner dari Respons Server (jika ada)
                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = RedError.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, RedError.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = RedError, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = errorMessage,
                                color = RedError,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 1. Pilihan Hasil Kunjungan
                Text(
                    text = "Hasil Kunjungan *",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        val isOptionSelected = selectedHasil == option
                        if (option == "BERHASIL") {
                            // Wadah visual menyatu untuk BERHASIL dan sub-pilihannya
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isOptionSelected) BgLightGray else SurfaceWhite)
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isOptionSelected) BlueSecondary.copy(alpha = 0.45f)
                                            else BorderGray.copy(alpha = 0.5f)
                                        ),
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = isOptionSelected,
                                            onClick = { onSelectOption("BERHASIL") }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isOptionSelected,
                                        onClick = { onSelectOption("BERHASIL") },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = NavyPrimary,
                                            unselectedColor = BorderGray
                                        ),
                                        modifier = Modifier.testTag("radio_berhasil")
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "BERHASIL",
                                        fontSize = 13.sp,
                                        color = if (isOptionSelected) NavyPrimary else TextPrimary,
                                        fontWeight = if (isOptionSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }

                                if (isOptionSelected) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                                    ) {
                                        // Garis penghubung vertikal tipis dari radio BERHASIL ke sub-pilihan
                                        Box(
                                            modifier = Modifier
                                                .width(48.dp)
                                                .fillMaxHeight(),
                                            contentAlignment = Alignment.TopCenter
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .padding(bottom = 12.dp)
                                                    .width(1.5.dp)
                                                    .background(BlueSecondary.copy(alpha = 0.5f))
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Sub-pilihan sejajar awal teks "BERHASIL"
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .selectable(
                                                        selected = (selectedOrderHariIni == "0"),
                                                        onClick = {
                                                            selectedOrderHariIni = "0"
                                                            showOrderError = false
                                                        }
                                                    )
                                                    .padding(vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = (selectedOrderHariIni == "0"),
                                                    onClick = {
                                                        selectedOrderHariIni = "0"
                                                        showOrderError = false
                                                    },
                                                    colors = RadioButtonDefaults.colors(
                                                        selectedColor = NavyPrimary,
                                                        unselectedColor = BorderGray
                                                    ),
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .testTag("radio_hanya_dikunjungi")
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Hanya dikunjungi",
                                                    fontSize = 12.sp,
                                                    color = if (selectedOrderHariIni == "0") NavyPrimary else TextPrimary,
                                                    fontWeight = if (selectedOrderHariIni == "0") FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .selectable(
                                                        selected = (selectedOrderHariIni == "1"),
                                                        onClick = {
                                                            selectedOrderHariIni = "1"
                                                            showOrderError = false
                                                        }
                                                    )
                                                    .padding(vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = (selectedOrderHariIni == "1"),
                                                    onClick = {
                                                        selectedOrderHariIni = "1"
                                                        showOrderError = false
                                                    },
                                                    colors = RadioButtonDefaults.colors(
                                                        selectedColor = NavyPrimary,
                                                        unselectedColor = BorderGray
                                                    ),
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .testTag("radio_order_hari_ini")
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Dikunjungi dan Order hari ini (dicek otomatis)",
                                                    fontSize = 12.sp,
                                                    color = if (selectedOrderHariIni == "1") NavyPrimary else TextPrimary,
                                                    fontWeight = if (selectedOrderHariIni == "1") FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            }

                                            if (orderErrorText != null) {
                                                Text(
                                                    text = orderErrorText,
                                                    color = RedError,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier
                                                        .padding(top = 2.dp)
                                                        .testTag("error_order_hari_ini")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isOptionSelected) BgLightGray else SurfaceWhite)
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isOptionSelected) BlueSecondary.copy(alpha = 0.45f)
                                            else BorderGray.copy(alpha = 0.5f)
                                        ),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .selectable(
                                        selected = isOptionSelected,
                                        onClick = { onSelectOption(option) }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isOptionSelected,
                                    onClick = { onSelectOption(option) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = NavyPrimary,
                                        unselectedColor = BorderGray
                                    ),
                                    modifier = Modifier.testTag(
                                        "radio_${option.lowercase().replace(' ', '_')}"
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = option,
                                    fontSize = 13.sp,
                                    color = if (isOptionSelected) NavyPrimary else TextPrimary,
                                    fontWeight = if (isOptionSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    if (hasilErrorText != null) {
                        Text(
                            text = hasilErrorText,
                            color = RedError,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("error_hasil_kunjungan")
                        )
                    }
                }

                // 2. Input Catatan (Wajib untuk semua hasil, maksimal 150 karakter)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Catatan (Wajib) *",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${catatan.length}/150",
                        fontSize = 12.sp,
                        color = if (catatan.length > 150) RedError else TextGray
                    )
                }

                OutlinedTextField(
                    value = catatan,
                    onValueChange = {
                        if (it.length <= 150) {
                            catatan = it
                            if (it.isNotBlank()) {
                                showCatatanError = false
                            }
                        }
                    },
                    isError = catatanErrorText != null,
                    placeholder = { Text("Tulis keterangan kunjungan...", fontSize = 13.sp, color = TextGray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_catatan"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueSecondary,
                        unfocusedBorderColor = BorderGray,
                        errorBorderColor = RedError,
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        errorContainerColor = SurfaceWhite
                    ),
                    shape = RoundedCornerShape(8.dp),
                    minLines = 2,
                    maxLines = 4
                )

                if (catatanErrorText != null) {
                    Text(
                        text = catatanErrorText,
                        color = RedError,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("error_catatan")
                    )
                }

                // 3. Tanggal Follow Up (Hanya tampil dan wajib saat hasil = TOKO TUTUP)
                if (selectedHasil == "TOKO TUTUP") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Tanggal Follow Up (Wajib) *",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tanggal rencana tindak lanjut toko tutup",
                            fontSize = 11.sp,
                            color = TextGray
                        )

                        Surface(
                            color = SurfaceWhite,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (followUpErrorText != null) RedError else BorderGray),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { openDatePicker() }
                                .testTag("field_follow_up")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = BlueSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    if (!followUpDate.isNullOrBlank()) {
                                        Text(
                                            text = followUpDate!!,
                                            fontSize = 13.sp,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else {
                                        Text(
                                            text = "Pilih tanggal (YYYY-MM-DD)",
                                            fontSize = 13.sp,
                                            color = TextGray
                                        )
                                    }
                                }

                                if (!followUpDate.isNullOrBlank()) {
                                    IconButton(
                                        onClick = { followUpDate = null },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .testTag("btn_clear_follow_up")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Hapus Tanggal",
                                            tint = TextGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (followUpErrorText != null) {
                            Text(
                                text = followUpErrorText,
                                color = RedError,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("error_follow_up")
                            )
                        }
                    }
                }

                // 4. Status GPS Lokasi (Wajib diisi)
                Text(
                    text = "Lokasi Kunjungan (GPS) *",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Surface(
                    color = BgLightGray,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (currentLatitude != null && currentLongitude != null) {
                                Text(
                                    text = "Lat: %.5f, Lon: %.5f".format(currentLatitude, currentLongitude),
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            } else if (isFetchingLocation) {
                                Text("Mengambil sinyal GPS...", fontSize = 12.sp, color = LokasiBiru)
                            } else {
                                Text("Koordinat GPS belum tersedia (Wajib)", fontSize = 12.sp, color = RedError)
                            }
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    isFetchingLocation = true
                                    val loc = LocationHelper.getCurrentLocation(context)
                                    if (loc != null) {
                                        currentLatitude = loc.first
                                        currentLongitude = loc.second
                                    } else {
                                        Toast.makeText(context, "Sinyal GPS belum didapatkan. Pastikan izin lokasi aktif.", Toast.LENGTH_SHORT).show()
                                    }
                                    isFetchingLocation = false
                                }
                            },
                            enabled = !isFetchingLocation,
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (isFetchingLocation) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = LokasiBiru)
                            } else {
                                Icon(Icons.Default.MyLocation, contentDescription = "Ambil GPS", tint = LokasiBiru, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // 5. Foto Kunjungan (Wajib diunggah sesuai rkm2.php baris 71)
                Text(
                    text = "Foto Kunjungan (Wajib) *",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                val photoSizeKb = remember(localPhotoPath) {
                    localPhotoPath?.let { path ->
                        val f = File(path)
                        if (f.exists()) f.length() / 1024 else null
                    }
                }

                if (localPhotoPath != null) {
                    if (photoSizeKb != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, BorderGray, RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Foto siap diunggah", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text("$photoSizeKb KB (JPEG)", fontSize = 11.sp, color = TextGray)
                                }
                            }
                            IconButton(onClick = { localPhotoPath = null }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus Foto", tint = RedError, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Backend mewajibkan foto bukti kunjungan.",
                        fontSize = 11.sp,
                        color = TextGray
                    )
                }

                OutlinedButton(
                    onClick = {
                        val (uri, file) = PhotoHelper.createTempImageUri(context)
                        tempPhotoUri = uri
                        pendingPhotoFile = file
                        cameraLauncher.launch(uri)
                    },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (localPhotoPath == null) BlueSecondary else BorderGray),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_take_photo")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (localPhotoPath == null) "Ambil Foto Kamera *" else "Ganti Foto Kamera", color = NavyPrimary, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Tombol Simpan Draft (Selalu bisa disimpan kapan saja tanpa validasi)
                OutlinedButton(
                    onClick = {
                        val draft = DraftLaporan(
                            kodeMember = member.kodeMember,
                            idRkm = idRkm ?: 0L,
                            hasil = selectedHasil,
                            catatan = catatan,
                            photoPath = localPhotoPath,
                            latitude = currentLatitude,
                            longitude = currentLongitude,
                            followUp = if (selectedHasil == "TOKO TUTUP") followUpDate else null,
                            orderHariIni = if (selectedHasil == "BERHASIL") selectedOrderHariIni else "0"
                        )
                        onSaveDraft(draft)
                        Toast.makeText(context, "Draft laporan disimpan.", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderGray),
                    modifier = Modifier.testTag("btn_save_draft")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = TextGray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Draft", color = TextGray, fontSize = 13.sp)
                }

                // Tombol Kirim Laporan dengan validasi inline & presisi backend
                Button(
                    onClick = {
                        if (idRkm == null || idRkm <= 0) {
                            Toast.makeText(context, "id_rkm tidak valid.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        showHasilError = validation.hasilError != null
                        showOrderError = validation.orderHariIniError != null
                        showCatatanError = validation.catatanError != null
                        showFollowUpError = validation.followUpError != null
                        if (!validation.isValid) {
                            return@Button
                        }
                        if (currentLatitude == null || currentLongitude == null) {
                            Toast.makeText(context, "Koordinat GPS wajib diisi.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val lat = currentLatitude!!
                        val lon = currentLongitude!!
                        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
                            Toast.makeText(context, "Koordinat GPS di luar rentang yang sah.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val photoFile = localPhotoPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
                        if (photoFile == null) {
                            Toast.makeText(context, "Foto kunjungan wajib diunggah.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val draft = DraftLaporan(
                            kodeMember = member.kodeMember,
                            idRkm = idRkm,
                            hasil = selectedHasil,
                            catatan = catatan,
                            photoPath = localPhotoPath,
                            latitude = lat,
                            longitude = lon,
                            followUp = if (selectedHasil == "TOKO TUTUP") followUpDate else null,
                            orderHariIni = if (selectedHasil == "BERHASIL") selectedOrderHariIni else "0"
                        )
                        onSubmit(draft, photoFile)
                    },
                    enabled = !isSubmitting && idRkm != null && idRkm > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("btn_submit_laporan")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = SurfaceWhite)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mengirim...")
                    } else {
                        Text("Kirim Laporan")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (catatan.isNotBlank() || localPhotoPath != null || !followUpDate.isNullOrBlank()) {
                        showDiscardConfirm = true
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text("Batal", color = TextGray)
            }
        }
    )

    // Dialog Konfirmasi Keluar dengan data belum disimpan
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Simpan Draft?", color = NavyPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Formulir memiliki data atau foto yang belum dikirim. Simpan sebagai draft agar tidak hilang?", color = TextPrimary) },
            confirmButton = {
                Button(
                    onClick = {
                        val draft = DraftLaporan(
                            kodeMember = member.kodeMember,
                            idRkm = idRkm ?: 0L,
                            hasil = selectedHasil,
                            catatan = catatan,
                            photoPath = localPhotoPath,
                            latitude = currentLatitude,
                            longitude = currentLongitude,
                            followUp = if (selectedHasil == "TOKO TUTUP") followUpDate else null,
                            orderHariIni = if (selectedHasil == "BERHASIL") selectedOrderHariIni else "0"
                        )
                        onSaveDraft(draft)
                        showDiscardConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Simpan Draft")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onDismiss()
                    }
                ) {
                    Text("Buang", color = RedError)
                }
            }
        )
    }
}
