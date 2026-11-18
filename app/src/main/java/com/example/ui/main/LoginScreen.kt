package com.example.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.SalesmanOption
import com.example.ui.theme.RedError
import kotlinx.coroutines.delay

// Palet khusus layar login (lokal, tidak memengaruhi tema aplikasi lain)
private val LoginBg = Color.White
private val LoginNavy = Color(0xFF1F3A5F)
private val LoginField = Color(0xFFF1F4F9)
private val LoginTextPrimary = Color(0xFF1B2430)
private val LoginTextSoft = Color(0xFF4A5668)
private val LoginTextMuted = Color(0xFF7A869A)
private val LoginFooter = Color(0xFF9AA5B8)

@Composable
fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    cabangLabel: String,
    subtitle: String,
    salesmen: List<SalesmanOption>,
    configLoading: Boolean,
    configError: String?,
    onRetryConfig: () -> Unit,
    onLogin: (String, String) -> Unit,
    logoUrl: String = "",
    modifier: Modifier = Modifier
) {
    // Daftar kode diambil dari server (hanya user aktif). Jika pilihan tidak ada lagi, pakai yang pertama.
    val codes = salesmen.map { it.kodeSalesman }
    var pickedCode by remember { mutableStateOf("") }
    val selectedCode = if (pickedCode in codes) pickedCode else codes.firstOrNull().orEmpty()
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    // Logo dianggap selesai bila tidak ada logo dari server, atau sudah berhasil/gagal dimuat.
    var logoDone by remember(logoUrl) { mutableStateOf(logoUrl.isBlank()) }
    // Batas tunggu logo 8 detik supaya layar tidak menggantung saat jaringan lambat.
    LaunchedEffect(logoUrl) {
        if (logoUrl.isNotBlank()) {
            delay(8000)
            logoDone = true
        }
    }
    // Halaman login menampilkan loading sampai config server (label, daftar salesman) dan logo siap.
    val pageLoading = configLoading || !logoDone

    // Animasi masuk: berjalan setelah loading selesai (hanya saat layar pertama kali dibuka).
    // rememberSaveable membuat animasi tidak diulang ketika layar diputar (rotasi).
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pageLoading) { if (!pageLoading) started = true }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = LoginBg
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        // Kolom luar: bisa di-scroll (layar pendek / landscape) dan menengahkan form di layar lebar
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Kolom form: lebar maksimal 400dp agar tetap rapi di tablet
            Column(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header rata kiri: logo + label cabang, lalu judul sejajar dengan sisi kiri logo
                Entrance(started = started, delayMillis = 0, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Logo dari server (logo_url). Jika kosong / gagal dimuat,
                            // pakai logo bawaan (res/drawable/ic_rkm_logo.png)
                            if (logoUrl.isBlank()) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_rkm_logo),
                                    contentDescription = "Logo SPI",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(width = 140.dp, height = 56.dp)
                                )
                            } else {
                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = "Logo SPI",
                                    contentScale = ContentScale.Fit,
                                    placeholder = painterResource(id = R.drawable.ic_rkm_logo),
                                    error = painterResource(id = R.drawable.ic_rkm_logo),
                                    onSuccess = { logoDone = true },
                                    onError = { logoDone = true },
                                    modifier = Modifier.size(width = 140.dp, height = 56.dp)
                                )
                            }
                            if (cabangLabel.isNotBlank()) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = cabangLabel,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontStyle = FontStyle.Italic,
                                    color = LoginTextMuted
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = LoginNavy
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Error Banner
                if (!errorMessage.isNullOrBlank()) {
                    Entrance(started = started, delayMillis = 150, modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            color = RedError.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, RedError.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage,
                                color = RedError,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Kode Salesman Pilihan Cepat
                Entrance(started = started, delayMillis = 200, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (salesmen.isEmpty()) {
                            // Belum ada data dari server (loading / gagal)
                            if (configLoading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = LoginNavy
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Memuat daftar salesman...", fontSize = 12.sp, color = LoginTextMuted)
                                }
                            } else {
                                Text(
                                    text = configError ?: "Daftar salesman belum tersedia.",
                                    fontSize = 12.sp,
                                    color = RedError,
                                    textAlign = TextAlign.Center
                                )
                                TextButton(onClick = onRetryConfig) {
                                    Text("Muat ulang", color = LoginNavy, fontSize = 12.sp)
                                }
                            }
                        } else {
                            salesmen.chunked(4).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        val code = item.kodeSalesman
                                        FilterChip(
                                            selected = (selectedCode == code),
                                            onClick = { pickedCode = code },
                                            label = {
                                                Text(
                                                    text = code,
                                                    fontSize = 14.sp,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = LoginField,
                                                labelColor = LoginTextSoft,
                                                selectedContainerColor = LoginNavy,
                                                selectedLabelColor = Color.White
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                borderColor = Color.Transparent,
                                                selectedBorderColor = LoginNavy,
                                                enabled = true,
                                                selected = (selectedCode == code)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                        )
                                    }
                                    // Isi sisa kolom agar lebar chip tetap sama
                                    if (rowItems.size < 4) {
                                        Spacer(modifier = Modifier.weight((4 - rowItems.size).toFloat()))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input Password
                Entrance(started = started, delayMillis = 300, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password", fontSize = 14.sp) },
                        textStyle = TextStyle(fontSize = 15.sp),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = LoginTextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Sembunyikan password" else "Lihat password",
                                    tint = LoginTextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LoginNavy,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = LoginField,
                            unfocusedContainerColor = LoginField,
                            focusedTextColor = LoginTextPrimary,
                            unfocusedTextColor = LoginTextPrimary,
                            cursorColor = LoginNavy,
                            focusedLabelColor = LoginNavy,
                            unfocusedLabelColor = LoginTextMuted
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .testTag("input_password"),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            onLogin(selectedCode, password)
                        })
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Tombol Login
                Entrance(started = started, delayMillis = 400, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onLogin(selectedCode, password)
                        },
                        enabled = !isLoading && selectedCode.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LoginNavy,
                            contentColor = Color.White,
                            disabledContainerColor = LoginNavy.copy(alpha = 0.4f),
                            disabledContentColor = Color.White.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("btn_login")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Memproses...", fontSize = 17.sp)
                        } else {
                            Text("Masuk", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Entrance(started = started, delayMillis = 500, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "RKM SPI",
                        fontSize = 12.sp,
                        color = LoginFooter,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Layar loading: menutupi form sampai semua data siap (form tetap dibuat di belakang
        // supaya logo bisa mulai dimuat). Menyerap sentuhan agar form tidak bisa ditekan.
        if (pageLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LoginBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = LoginNavy
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Memuat...", fontSize = 13.sp, color = LoginTextMuted)
                }
            }
        }
      }
    }
}

/**
 * Animasi masuk: konten memudar (fade in) sambil naik dari bawah.
 * Ruang layout sudah tersedia sejak awal, jadi elemen lain tidak bergeser saat animasi berjalan.
 * delayMillis dipakai untuk membuat efek muncul berurutan (stagger).
 */
@Composable
private fun Entrance(
    started: Boolean,
    delayMillis: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(
            durationMillis = 600,
            delayMillis = delayMillis,
            easing = FastOutSlowInEasing
        ),
        label = "loginEntrance"
    )
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 24.dp.toPx()
        }
    ) {
        content()
    }
}