package com.example.ui.components

data class LaporanValidationResult(
    val hasilError: String? = null,
    val orderHariIniError: String? = null,
    val catatanError: String? = null,
    val followUpError: String? = null
) {
    val isValid: Boolean
        get() = hasilError == null && orderHariIniError == null && catatanError == null && followUpError == null

    val firstError: String?
        get() = hasilError ?: orderHariIniError ?: catatanError ?: followUpError
}

object LaporanValidator {
    val ALLOWED_HASIL = listOf("BERHASIL", "TOKO TUTUP", "TIDAK MAU ORDER", "ALASAN LAIN")

    fun validate(
        hasil: String,
        orderHariIni: String?,
        catatan: String,
        followUp: String?
    ): LaporanValidationResult {
        val trimmedHasil = hasil.trim()
        val hasilError = if (trimmedHasil !in ALLOWED_HASIL) {
            "Pilih hasil kunjungan"
        } else {
            null
        }

        val orderHariIniError = if (trimmedHasil == "BERHASIL" && orderHariIni.isNullOrBlank()) {
            "Pilih status order untuk hasil BERHASIL"
        } else {
            null
        }

        val catatanError = when {
            catatan.isBlank() -> "Catatan wajib diisi"
            catatan.length > 150 -> "Catatan maksimal 150 karakter."
            else -> null
        }

        val followUpError = if (trimmedHasil == "TOKO TUTUP" && followUp.isNullOrBlank()) {
            "Tanggal follow up wajib diisi untuk TOKO TUTUP"
        } else {
            null
        }

        return LaporanValidationResult(
            hasilError = hasilError,
            orderHariIniError = orderHariIniError,
            catatanError = catatanError,
            followUpError = followUpError
        )
    }
}
