package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val speed: Float? = null
)

object LocationHelper {

    suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        val details = getCurrentLocationDetails(context) ?: return null
        return Pair(details.latitude, details.longitude)
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationDetails(context: Context): DeviceLocation? {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        // Tentukan provider berdasarkan izin yang diberikan (Presisi vs Perkiraan)
        val providers = mutableListOf<String>()
        if (hasFine) {
            providers.add(LocationManager.GPS_PROVIDER)
        }
        if (hasCoarse || hasFine) {
            providers.add(LocationManager.NETWORK_PROVIDER)
            providers.add(LocationManager.PASSIVE_PROVIDER)
        }

        val bestLocation: Location? = withContext(Dispatchers.IO) {
            var best: Location? = null
            for (provider in providers) {
                try {
                    if (locationManager.isProviderEnabled(provider)) {
                        val loc = locationManager.getLastKnownLocation(provider)
                        if (loc != null) {
                            val age = System.currentTimeMillis() - loc.time
                            if (age < 120_000) {
                                if (best == null || loc.accuracy < best.accuracy) {
                                    best = loc
                                }
                            }
                        }
                    }
                } catch (_: SecurityException) {
                } catch (_: Exception) {
                }
            }
            best
        }

        if (bestLocation != null) {
            return DeviceLocation(
                latitude = bestLocation.latitude,
                longitude = bestLocation.longitude,
                accuracy = if (bestLocation.hasAccuracy()) bestLocation.accuracy else null,
                speed = if (bestLocation.hasSpeed()) bestLocation.speed else null
            )
        }

        // Minta single update lokasi dengan batas timeout 12 detik
        return suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            var resumed = false

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!resumed) {
                        resumed = true
                        try {
                            locationManager.removeUpdates(this)
                        } catch (_: Exception) {}
                        continuation.resume(
                            DeviceLocation(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracy = if (location.hasAccuracy()) location.accuracy else null,
                                speed = if (location.hasSpeed()) location.speed else null
                            )
                        )
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            val timeoutRunnable = Runnable {
                if (!resumed) {
                    resumed = true
                    try {
                        locationManager.removeUpdates(listener)
                    } catch (_: Exception) {}
                    continuation.resume(null)
                }
            }
            handler.postDelayed(timeoutRunnable, 12_000)

            continuation.invokeOnCancellation {
                handler.removeCallbacks(timeoutRunnable)
                try {
                    locationManager.removeUpdates(listener)
                } catch (_: Exception) {}
            }

            var requested = false
            for (provider in providers) {
                try {
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                        requested = true
                    }
                } catch (_: SecurityException) {
                } catch (_: Exception) {
                }
            }

            if (!requested && !resumed) {
                handler.removeCallbacks(timeoutRunnable)
                resumed = true
                continuation.resume(null)
            }
        }
    }
}
