package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object MapsHelper {

    fun openGoogleMapsNavigation(context: Context, latitude: Double, longitude: Double) {
        val uriStr = "https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude&travelmode=driving"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
            setPackage("com.google.android.apps.maps")
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Jika aplikasi Google Maps tidak terpasang, buka lewat browser
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr))
                context.startActivity(browserIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Tidak dapat membuka peta navigasi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
