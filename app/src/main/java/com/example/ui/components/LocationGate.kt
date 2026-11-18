package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Status kesiapan lokasi perangkat. */
data class LocationStatus(val permissionGranted: Boolean, val serviceEnabled: Boolean) {
    val ready: Boolean get() = permissionGranted && serviceEnabled
}

fun readLocationStatus(context: Context): LocationStatus {
    val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    val enabled = lm != null && LocationManagerCompat.isLocationEnabled(lm)
    return LocationStatus(granted, enabled)
}

/**
 * Cegatan lokasi: selama izin lokasi belum diberikan atau GPS/layanan lokasi perangkat mati,
 * [content] (layar login maupun layar utama) tidak ditampilkan dan diganti layar penghalang.
 * Status dicek ulang setiap aplikasi kembali ke layar (ON_RESUME) dan saat penyedia lokasi
 * diubah dari panel notifikasi, sehingga mematikan GPS di tengah pemakaian langsung memblokir.
 */
@Composable
fun LocationGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember { mutableStateOf(readLocationStatus(context)) }
    var askedOnce by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = readLocationStatus(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                status = readLocationStatus(context)
            }
        }
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        askedOnce = true
        status = readLocationStatus(context)
    }

    if (status.ready) {
        content()
        return
    }

    val activity = context as? Activity
    val permanentlyDenied = askedOnce && activity != null &&
        !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.ACCESS_FINE_LOCATION
        )

    val title: String
    val message: String
    val buttonText: String
    val onClick: () -> Unit
    if (!status.permissionGranted) {
        title = "Izin Lokasi Diperlukan"
        message = "Aplikasi ini wajib menggunakan lokasi. Izinkan akses lokasi agar bisa masuk dan memakai aplikasi."
        if (permanentlyDenied) {
            buttonText = "Buka Pengaturan Aplikasi"
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            }
        } else {
            buttonText = "Izinkan Lokasi"
            onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    } else {
        title = "Lokasi (GPS) Mati"
        message = "Nyalakan lokasi/GPS perangkat Anda untuk melanjutkan. Aplikasi tidak dapat digunakan selama lokasi mati."
        buttonText = "Nyalakan Lokasi"
        onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(32.dp)
            .testTag("location_gate"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.LocationOff,
            contentDescription = null,
            tint = Color(0xFF1F3A5F),
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B2430),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = Color(0xFF4A5668),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("location_gate_button")) {
            Text(buttonText)
        }
    }
}
