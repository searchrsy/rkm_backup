package com.example.data.model

import androidx.compose.runtime.Immutable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "kode_salesman") val kodeSalesman: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String?,
    @Json(name = "token") val token: String?,
    @Json(name = "expires_at") val expiresAt: String?,
    @Json(name = "data") val data: SalesmanInfo?
)

@JsonClass(generateAdapter = true)
data class LoginConfigResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "cabang_label") val cabangLabel: String?,
    @Json(name = "subtitle") val subtitle: String?,
    @Json(name = "logo_url") val logoUrl: String? = null,
    @Json(name = "salesmen") val salesmen: List<SalesmanOption>?
)

@JsonClass(generateAdapter = true)
data class SalesmanOption(
    @Json(name = "kode_salesman") val kodeSalesman: String,
    @Json(name = "nama_salesman") val namaSalesman: String?
)

@JsonClass(generateAdapter = true)
data class SalesmanInfo(
    @Json(name = "kode_salesman") val kodeSalesman: String,
    @Json(name = "nama_salesman") val namaSalesman: String?
)

@JsonClass(generateAdapter = true)
data class RkmResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: RkmPayload?
)

@JsonClass(generateAdapter = true)
data class RkmPayload(
    @Json(name = "tanggal") val tanggal: String?,
    @Json(name = "kode_salesman") val kodeSalesman: String?,
    @Json(name = "rencana") val rencana: RencanaData?,
    @Json(name = "kunjungan") val kunjungan: List<MemberItem>?
)

@JsonClass(generateAdapter = true)
data class RencanaData(
    @Json(name = "id") val id: Long?,
    @Json(name = "status") val status: String? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class MemberItem(
    @Json(name = "kode_member") val kodeMember: String,
    @Json(name = "nama_member") val namaMember: String? = null,
    @Json(name = "nama") val nama: String? = null,
    @Json(name = "alamat") val alamat: String? = null,
    @Json(name = "kecamatan") val kecamatan: String? = null,
    @Json(name = "koordinat") val koordinat: String? = null,
    @Json(name = "latitude") val latitude: Any? = null,
    @Json(name = "longitude") val longitude: Any? = null,
    @Json(name = "id_rkm") val idRkm: Long? = null,
    @Json(name = "urutan") val urutan: Int? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "status_kunjungan") val statusKunjungan: String? = null,
    @Json(name = "status_member") val statusMember: String? = null
) {
    val displayName: String
        get() = namaMember?.ifBlank { null } ?: nama?.ifBlank { null } ?: kodeMember

    val doubleLatitude: Double?
        get() {
            val direct = parseCoordinate(latitude)
            if (direct != null && direct != 0.0) return direct
            return parseCoordFromPair(0)
        }

    val doubleLongitude: Double?
        get() {
            val direct = parseCoordinate(longitude)
            if (direct != null && direct != 0.0) return direct
            return parseCoordFromPair(1)
        }

    val isFinished: Boolean
        get() {
            val s = (status ?: statusKunjungan ?: "").uppercase()
            return s == "SELESAI" || s == "BERHASIL" || s == "SUDAH"
        }

    private fun parseCoordFromPair(index: Int): Double? {
        val raw = koordinat?.trim() ?: return null
        if (!raw.contains(",")) return null
        val parts = raw.split(",")
        if (parts.size >= 2) {
            return parts[index].trim().toDoubleOrNull()
        }
        return null
    }

    private fun parseCoordinate(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
    }
}

@JsonClass(generateAdapter = true)
data class LaporanResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: LaporanData? = null
)

@JsonClass(generateAdapter = true)
data class LaporanData(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "id_rkm") val idRkm: Long? = null,
    @Json(name = "kode_member") val kodeMember: String? = null,
    @Json(name = "hasil") val hasil: String? = null,
    @Json(name = "waktu_kunjungan") val waktuKunjungan: String? = null
)

@Immutable
data class DraftLaporan(
    val kodeMember: String,
    val idRkm: Long,
    val hasil: String,
    val catatan: String,
    val photoPath: String?,
    val latitude: Double?,
    val longitude: Double?,
    val followUp: String? = null,
    val orderHariIni: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class GpsPayload(
    @Json(name = "kode_salesman") val kodeSalesman: String,
    @Json(name = "kode_sales") val kodeSales: String? = null,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "akurasi") val akurasi: Float? = null,
    @Json(name = "kecepatan") val kecepatan: Float? = null,
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @Json(name = "waktu") val waktu: String? = null,
    @Json(name = "waktu_gps") val waktuGps: String? = null
)

@JsonClass(generateAdapter = true)
data class GpsResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class LokasiTerakhirResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: List<LokasiSalesItem>? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class LokasiSalesItem(
    @Json(name = "id") val id: Any? = null,
    @Json(name = "kode_sales") val kodeSales: String? = null,
    @Json(name = "kode_salesman") val kodeSalesman: String? = null,
    @Json(name = "device_id") val deviceId: String? = null,
    @Json(name = "latitude") val latitude: Any? = null,
    @Json(name = "longitude") val longitude: Any? = null,
    @Json(name = "akurasi") val akurasi: Any? = null,
    @Json(name = "kecepatan") val kecepatan: Any? = null,
    @Json(name = "waktu_gps") val waktuGps: String? = null,
    @Json(name = "diterima_pada") val diterimaPada: String? = null,
    @Json(name = "aktif") val aktif: Boolean? = null
) {
    val salesCode: String
        get() = kodeSales?.ifBlank { null } ?: kodeSalesman?.ifBlank { null } ?: "Sales"

    val doubleLatitude: Double?
        get() {
            val v = parseCoord(latitude)
            return if (v != null && v >= -90.0 && v <= 90.0) v else null
        }

    val doubleLongitude: Double?
        get() {
            val v = parseCoord(longitude)
            return if (v != null && v >= -180.0 && v <= 180.0) v else null
        }

    val isValidCoordinate: Boolean
        get() {
            val lat = doubleLatitude
            val lon = doubleLongitude
            return lat != null && lon != null && (lat != 0.0 || lon != 0.0)
        }

    private fun parseCoord(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
    }
}

@JsonClass(generateAdapter = true)
data class RiwayatLokasiResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: List<RiwayatGroupItem>? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class RiwayatGroupItem(
    @Json(name = "device_id") val deviceId: Any? = null,
    @Json(name = "kode_sales") val kodeSales: String? = null,
    @Json(name = "kode_salesman") val kodeSalesman: String? = null,
    @Json(name = "titik") val titik: List<TitikLokasiItem>? = null
) {
    val salesCode: String
        get() = kodeSales?.ifBlank { null } ?: kodeSalesman?.ifBlank { null } ?: "Sales"
}

@Immutable
@JsonClass(generateAdapter = true)
data class TitikLokasiItem(
    @Json(name = "latitude") val latitude: Any? = null,
    @Json(name = "longitude") val longitude: Any? = null,
    @Json(name = "akurasi") val akurasi: Any? = null,
    @Json(name = "kecepatan") val kecepatan: Any? = null,
    @Json(name = "waktu_gps") val waktuGps: String? = null,
    @Json(name = "diterima_pada") val diterimaPada: String? = null
) {
    val doubleLatitude: Double?
        get() {
            val v = parseCoord(latitude)
            return if (v != null && v >= -90.0 && v <= 90.0) v else null
        }

    val doubleLongitude: Double?
        get() {
            val v = parseCoord(longitude)
            return if (v != null && v >= -180.0 && v <= 180.0) v else null
        }

    val isValidCoordinate: Boolean
        get() {
            val lat = doubleLatitude
            val lon = doubleLongitude
            return lat != null && lon != null && (lat != 0.0 || lon != 0.0)
        }

    private fun parseCoord(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
    }
}