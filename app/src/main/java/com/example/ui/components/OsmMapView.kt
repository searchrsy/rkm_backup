package com.example.ui.components

import android.content.Context
import com.example.config.AppConfig
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.LokasiSalesItem
import com.example.data.model.MemberItem
import com.example.data.model.RiwayatGroupItem
import com.example.ui.theme.BgLightGray
import com.example.ui.theme.BorderGray
import com.example.ui.theme.LokasiBiru
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.StatusBelumHijau
import com.example.ui.theme.StatusSelesaiAbu
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.TextGray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

private class MapOverlayState(
    val historyFolder: FolderOverlay = FolderOverlay(),
    val memberFolder: FolderOverlay = FolderOverlay(),
    val serverFolder: FolderOverlay = FolderOverlay(),
    val salesmanFolder: FolderOverlay = FolderOverlay()
) {
    var lastHistoryTracks: List<RiwayatGroupItem>? = null
    var lastMembers: List<MemberItem>? = null
    var lastSelectedKode: String? = null
    var lastKnownLocations: List<LokasiSalesItem>? = null
    var lastSalesmanLocation: Pair<Double, Double>? = null
    var salesmanMarker: Marker? = null
}

@Composable
fun OsmMapView(
    members: List<MemberItem>,
    salesmanLocation: Pair<Double, Double>?,
    selectedMember: MemberItem?,
    onMemberSelected: (MemberItem) -> Unit,
    onRequestLocation: () -> Unit,
    lastKnownLocations: List<LokasiSalesItem> = emptyList(),
    historyTracks: List<RiwayatGroupItem> = emptyList(),
    focusTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestOnMemberSelected = rememberUpdatedState(onMemberSelected)
    val overlayState = remember { MapOverlayState() }

    // Titik pusat default peta diambil dari cabang.properties (MAP_LAT, MAP_LNG)
    val defaultCenter = remember { GeoPoint(AppConfig.MAP_LAT, AppConfig.MAP_LNG) }
    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = AppConfig.USER_AGENT
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
            tileFileSystemCacheMaxBytes = 100L * 1024L * 1024L
            tileFileSystemCacheTrimBytes = 80L * 1024L * 1024L
            tileDownloadThreads = 4.toShort()
            tileFileSystemThreads = 4.toShort()
            cacheMapTileCount = 24.toShort()
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            controller.setCenter(defaultCenter)
            overlays.add(overlayState.historyFolder)
            overlays.add(overlayState.memberFolder)
            overlays.add(overlayState.serverFolder)
            overlays.add(overlayState.salesmanFolder)
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Fokuskan kamera ke member terpilih saat member dipilih atau kartu diketuk ulang
    LaunchedEffect(selectedMember?.kodeMember, focusTrigger) {
        selectedMember?.let { sel ->
            val sLat = sel.doubleLatitude
            val sLon = sel.doubleLongitude
            if (sLat != null && sLon != null && sLat != 0.0 && sLon != 0.0 && sLat in -90.0..90.0 && sLon in -180.0..180.0) {
                mapView.controller.animateTo(GeoPoint(sLat, sLon))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                var hasOverlayChanges = false

                // 1. Update garis riwayat perjalanan (Polyline) hanya jika data riwayat berubah
                val historyChanged = overlayState.lastHistoryTracks == null ||
                    (overlayState.lastHistoryTracks !== historyTracks && overlayState.lastHistoryTracks != historyTracks)
                if (historyChanged) {
                    overlayState.historyFolder.items.clear()
                    for (group in historyTracks) {
                        val points = group.titik.orEmpty().filter { it.isValidCoordinate }
                        if (points.size >= 2) {
                            val polyline = Polyline(map).apply {
                                val geoList = points.mapNotNull { pt ->
                                    val lat = pt.doubleLatitude
                                    val lon = pt.doubleLongitude
                                    if (lat != null && lon != null) GeoPoint(lat, lon) else null
                                }
                                setPoints(geoList)
                                outlinePaint.color = 0xFFF57C00.toInt() // Oranye Riwayat
                                outlinePaint.strokeWidth = 6f
                                outlinePaint.isAntiAlias = true
                            }
                            overlayState.historyFolder.add(polyline)
                        }
                    }
                    overlayState.lastHistoryTracks = historyTracks
                    hasOverlayChanges = true
                }

                // 2. Update marker member hanya jika daftar member atau pilihan berubah
                val currentSelectedKode = selectedMember?.kodeMember
                val membersChanged = overlayState.lastMembers == null ||
                    (overlayState.lastMembers !== members && overlayState.lastMembers != members)
                val selectionChanged = overlayState.lastSelectedKode != currentSelectedKode

                if (membersChanged) {
                    overlayState.memberFolder.items.clear()
                    for (member in members) {
                        val lat = member.doubleLatitude
                        val lon = member.doubleLongitude
                        if (lat != null && lon != null && lat != 0.0 && lon != 0.0) {
                            val geoPoint = GeoPoint(lat, lon)
                            val marker = Marker(map).apply {
                                position = geoPoint
                                title = member.displayName
                                snippet = member.kodeMember
                                relatedObject = member.isFinished
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = createMarkerDrawable(
                                    color = if (member.isFinished) 0xFF7A8491.toInt() else 0xFF2E7D32.toInt(),
                                    isSelected = member.kodeMember == currentSelectedKode
                                )
                                setOnMarkerClickListener { _, _ ->
                                    latestOnMemberSelected.value(member)
                                    true
                                }
                            }
                            overlayState.memberFolder.add(marker)
                        }
                    }
                    overlayState.lastMembers = members
                    overlayState.lastSelectedKode = currentSelectedKode
                    hasOverlayChanges = true
                } else if (selectionChanged) {
                    val prevKode = overlayState.lastSelectedKode
                    for (item in overlayState.memberFolder.items) {
                        if (item is Marker && (item.snippet == prevKode || item.snippet == currentSelectedKode)) {
                            val isFinished = item.relatedObject as? Boolean ?: false
                            item.icon = createMarkerDrawable(
                                color = if (isFinished) 0xFF7A8491.toInt() else 0xFF2E7D32.toInt(),
                                isSelected = item.snippet == currentSelectedKode
                            )
                        }
                    }
                    overlayState.lastSelectedKode = currentSelectedKode
                    hasOverlayChanges = true
                }

                // 3. Update marker lokasi terakhir server hanya jika data server berubah
                val serverChanged = overlayState.lastKnownLocations == null ||
                    (overlayState.lastKnownLocations !== lastKnownLocations && overlayState.lastKnownLocations != lastKnownLocations)
                if (serverChanged) {
                    overlayState.serverFolder.items.clear()
                    for (locItem in lastKnownLocations) {
                        if (locItem.isValidCoordinate) {
                            val lat = locItem.doubleLatitude
                            val lon = locItem.doubleLongitude
                            if (lat != null && lon != null) {
                                val geoPoint = GeoPoint(lat, lon)
                                val waktu = locItem.waktuGps ?: locItem.diterimaPada ?: "-"
                                val akurasi = if (locItem.akurasi != null) " | Akurasi: ${locItem.akurasi}m" else ""
                                val marker = Marker(map).apply {
                                    position = geoPoint
                                    title = "Lokasi Terakhir: ${locItem.salesCode}"
                                    snippet = "Waktu: $waktu$akurasi (Server)"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    icon = createServerSalesMarkerDrawable(locItem.aktif == true)
                                }
                                overlayState.serverFolder.add(marker)
                            }
                        }
                    }
                    overlayState.lastKnownLocations = lastKnownLocations
                    hasOverlayChanges = true
                }

                // 4. Update marker lokasi real-time salesman secara in-place
                if (overlayState.lastSalesmanLocation != salesmanLocation) {
                    if (salesmanLocation == null) {
                        overlayState.salesmanFolder.items.clear()
                        overlayState.salesmanMarker = null
                    } else {
                        val salesPoint = GeoPoint(salesmanLocation.first, salesmanLocation.second)
                        val existingMarker = overlayState.salesmanMarker
                        if (existingMarker == null) {
                            val createdMarker = Marker(map).apply {
                                position = salesPoint
                                title = "Lokasi Saya (Real-time)"
                                snippet = "GPS Perangkat Aktif"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = createSalesmanMarkerDrawable()
                            }
                            overlayState.salesmanFolder.add(createdMarker)
                            overlayState.salesmanMarker = createdMarker
                        } else {
                            existingMarker.position = salesPoint
                        }
                    }
                    overlayState.lastSalesmanLocation = salesmanLocation
                    hasOverlayChanges = true
                }

                if (hasOverlayChanges) {
                    map.invalidate()
                }
            }
        )

        // Overlay Kontrol Peta (Lokasi Saya, Pusatkan Member)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = SurfaceWhite,
                shadowElevation = 3.dp,
                modifier = Modifier.size(40.dp)
            ) {
                IconButton(onClick = onRequestLocation) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Lokasi Saya",
                        tint = LokasiBiru,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = SurfaceWhite,
                shadowElevation = 3.dp,
                modifier = Modifier.size(40.dp)
            ) {
                IconButton(onClick = {
                    val validPoints = mutableListOf<GeoPoint>()
                    members.forEach { m ->
                        val lat = m.doubleLatitude
                        val lon = m.doubleLongitude
                        if (lat != null && lon != null && lat != 0.0 && lon != 0.0) {
                            validPoints.add(GeoPoint(lat, lon))
                        }
                    }
                    lastKnownLocations.forEach { l ->
                        val lat = l.doubleLatitude
                        val lon = l.doubleLongitude
                        if (lat != null && lon != null) {
                            validPoints.add(GeoPoint(lat, lon))
                        }
                    }
                    if (salesmanLocation != null) {
                        validPoints.add(GeoPoint(salesmanLocation.first, salesmanLocation.second))
                    }
                    if (validPoints.isNotEmpty()) {
                        val box = BoundingBox.fromGeoPoints(validPoints)
                        mapView.zoomToBoundingBox(box, true, 50)
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.CropFree,
                        contentDescription = "Pusatkan Member",
                        tint = NavyPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Legenda Peta Ringkas di Pojok Kiri Atas
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite.copy(alpha = 0.92f)),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(StatusBelumHijau, CircleShape))
                    Text("Belum", fontSize = 10.sp, color = TextGray)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(StatusSelesaiAbu, CircleShape))
                    Text("Selesai", fontSize = 10.sp, color = TextGray)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(LokasiBiru, CircleShape))
                    Text("Live", fontSize = 10.sp, color = TextGray)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(androidx.compose.ui.graphics.Color(0xFFE65100), CircleShape))
                    Text("Server", fontSize = 10.sp, color = TextGray)
                }
            }
        }
    }
}

private val sharedOvalShape = OvalShape()

private fun createMarkerDrawable(color: Int, isSelected: Boolean): ShapeDrawable {
    val size = if (isSelected) 36 else 28
    val shape = ShapeDrawable(sharedOvalShape).apply {
        intrinsicWidth = size
        intrinsicHeight = size
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
    }
    return shape
}

private fun createSalesmanMarkerDrawable(): ShapeDrawable {
    val shape = ShapeDrawable(sharedOvalShape).apply {
        intrinsicWidth = 32
        intrinsicHeight = 32
        paint.color = 0xFF1976D2.toInt() // Biru Lokasi Live
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
    }
    return shape
}

private fun createServerSalesMarkerDrawable(isActive: Boolean): ShapeDrawable {
    val shape = ShapeDrawable(sharedOvalShape).apply {
        intrinsicWidth = 30
        intrinsicHeight = 30
        paint.color = if (isActive) 0xFF00897B.toInt() else 0xFFE65100.toInt() // Hijau Tua jika aktif, Oranye jika riwayat server
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
    }
    return shape
}
