package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MemberItem
import com.example.ui.theme.BgLightGray
import com.example.ui.theme.BlueSecondary
import com.example.ui.theme.BorderGray
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.StatusBelumHijau
import com.example.ui.theme.StatusSelesaiAbu
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextPrimary
import com.example.util.MapsHelper

@Composable
fun MemberCard(
    member: MemberItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDetailClick: () -> Unit = onClick,
    modifier: Modifier = Modifier
) {
    val statusColor = if (member.isFinished) StatusSelesaiAbu else StatusBelumHijau
    val statusLabel = if (member.isFinished) "Selesai" else "Belum"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) BgLightGray else SurfaceWhite
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isSelected) BlueSecondary else BorderGray),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("member_card_${member.kodeMember}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indikator Status Warna
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(statusColor, CircleShape)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = member.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Surface(
                        color = statusColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Kode: ${member.kodeMember}",
                    fontSize = 12.sp,
                    color = BlueSecondary,
                    fontWeight = FontWeight.Medium
                )

                val subInfo = remember(member.kecamatan, member.alamat) {
                    buildString {
                        if (!member.kecamatan.isNullOrBlank()) append("Kec. ${member.kecamatan}")
                        if (!member.alamat.isNullOrBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(member.alamat)
                        }
                    }
                }

                if (subInfo.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subInfo,
                        fontSize = 12.sp,
                        color = TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onDetailClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(BgLightGray)
                    .testTag("btn_detail_${member.kodeMember}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Buka detail member",
                    tint = NavyPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun MemberDetailDialog(
    member: MemberItem,
    onDismiss: () -> Unit,
    onOpenLaporan: (MemberItem) -> Unit
) {
    val context = LocalContext.current
    val lat = remember(member) { member.doubleLatitude }
    val lon = remember(member) { member.doubleLongitude }
    val hasValidCoord = lat != null && lon != null && lat != 0.0 && lon != 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(12.dp),
        title = {
            Column {
                Text(
                    text = member.displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )
                Text(
                    text = "Kode Member: ${member.kodeMember}",
                    fontSize = 13.sp,
                    color = BlueSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = BorderGray)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Status Kunjungan:", fontSize = 13.sp, color = TextGray)
                    Surface(
                        color = (if (member.isFinished) StatusSelesaiAbu else StatusBelumHijau).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (member.isFinished) "Selesai (Terkonfirmasi)" else "Belum Dikunjungi",
                            color = if (member.isFinished) StatusSelesaiAbu else StatusBelumHijau,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (!member.alamat.isNullOrBlank()) {
                    Text("Alamat:", fontSize = 12.sp, color = TextGray, fontWeight = FontWeight.Medium)
                    Text(member.alamat, fontSize = 13.sp, color = TextPrimary)
                }

                if (!member.kecamatan.isNullOrBlank()) {
                    Text("Kecamatan: ${member.kecamatan}", fontSize = 13.sp, color = TextPrimary)
                }

                Text("Koordinat:", fontSize = 12.sp, color = TextGray, fontWeight = FontWeight.Medium)
                if (hasValidCoord) {
                    Text("$lat, $lon", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Koordinat tidak tersedia", fontSize = 13.sp, color = TextGray)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Tombol Aksi Navigasi selebar baris
                OutlinedButton(
                    onClick = {
                        if (hasValidCoord) {
                            MapsHelper.openGoogleMapsNavigation(context, lat!!, lon!!)
                        }
                    },
                    enabled = hasValidCoord,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (hasValidCoord) BlueSecondary else BorderGray.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_navigate_gmaps")
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp), tint = BlueSecondary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Navigasi", fontSize = 13.sp, color = BlueSecondary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onOpenLaporan(member)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.testTag("btn_buat_laporan")
            ) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (member.isFinished) "Kirim Ulang Laporan" else "Buat Laporan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = TextGray)
            }
        }
    )
}
