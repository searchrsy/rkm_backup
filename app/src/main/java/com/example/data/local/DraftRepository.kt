package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.DraftLaporan
import org.json.JSONObject

class DraftRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("rkm_spi_drafts", Context.MODE_PRIVATE)

    fun saveDraft(draft: DraftLaporan) {
        val json = JSONObject().apply {
            put("kode_member", draft.kodeMember)
            put("id_rkm", draft.idRkm)
            put("hasil", draft.hasil)
            put("catatan", draft.catatan)
            put("photo_path", draft.photoPath ?: "")
            put("latitude", draft.latitude ?: JSONObject.NULL)
            put("longitude", draft.longitude ?: JSONObject.NULL)
            put("follow_up", draft.followUp ?: "")
            put("order_hari_ini", draft.orderHariIni ?: "")
            put("timestamp", draft.timestamp)
        }
        prefs.edit().putString(draft.kodeMember, json.toString()).apply()
    }

    fun getDraft(kodeMember: String): DraftLaporan? {
        val str = prefs.getString(kodeMember, null) ?: return null
        return try {
            val json = JSONObject(str)
            DraftLaporan(
                kodeMember = json.getString("kode_member"),
                idRkm = json.getLong("id_rkm"),
                hasil = json.getString("hasil"),
                catatan = json.getString("catatan"),
                photoPath = json.optString("photo_path").ifBlank { null },
                latitude = if (json.isNull("latitude")) null else json.getDouble("latitude"),
                longitude = if (json.isNull("longitude")) null else json.getDouble("longitude"),
                followUp = json.optString("follow_up").ifBlank { null },
                orderHariIni = json.optString("order_hari_ini").ifBlank { null },
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            null
        }
    }

    fun removeDraft(kodeMember: String) {
        prefs.edit().remove(kodeMember).apply()
    }

    fun getAllDrafts(): List<DraftLaporan> {
        val result = mutableListOf<DraftLaporan>()
        val all = prefs.all
        for ((_, value) in all) {
            if (value is String) {
                try {
                    val json = JSONObject(value)
                    result.add(
                        DraftLaporan(
                            kodeMember = json.getString("kode_member"),
                            idRkm = json.getLong("id_rkm"),
                            hasil = json.getString("hasil"),
                            catatan = json.getString("catatan"),
                            photoPath = json.optString("photo_path").ifBlank { null },
                            latitude = if (json.isNull("latitude")) null else json.getDouble("latitude"),
                            longitude = if (json.isNull("longitude")) null else json.getDouble("longitude"),
                            followUp = json.optString("follow_up").ifBlank { null },
                            orderHariIni = json.optString("order_hari_ini").ifBlank { null },
                            timestamp = json.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                } catch (_: Exception) {
                }
            }
        }
        return result
    }
}
