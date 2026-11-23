package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.config.AppConfig
import com.example.data.model.GpsPoint
import com.example.data.network.ApiClient
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types

/**
 * Antrian titik GPS yang disimpan di HP (SharedPreferences) sampai dikirim sebagai 1 batch.
 * Aman jika offline atau aplikasi ditutup: titik tetap ada dan dikirim saat jaringan kembali.
 * Semua aturan ukuran/umur/backoff dibaca dari AppConfig.
 */
class GpsBatchQueue(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("rkm_gps_queue", Context.MODE_PRIVATE)

    private val adapter: JsonAdapter<List<GpsPoint>> by lazy {
        val type = Types.newParameterizedType(List::class.java, GpsPoint::class.java)
        ApiClient.getMoshi().adapter(type)
    }

    private val lock = Any()
    private var cache: MutableList<GpsPoint>? = null

    // Status pengiriman (di memori; reset saat proses aplikasi dimulai ulang)
    private var failures = 0
    private var nextAllowedAt = 0L
    private var attemptedThisProcess = false

    private fun load(): MutableList<GpsPoint> {
        cache?.let { return it }
        val loaded = try {
            prefs.getString(KEY_POINTS, null)?.let { adapter.fromJson(it) }.orEmpty().toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
        cache = loaded
        return loaded
    }

    private fun save(list: List<GpsPoint>) {
        prefs.edit().putString(KEY_POINTS, adapter.toJson(list)).apply()
    }

    fun size(): Int = synchronized(lock) { load().size }

    fun enqueue(point: GpsPoint) {
        synchronized(lock) {
            val list = load()
            list.add(point)
            while (list.size > AppConfig.GPS_QUEUE_MAX) list.removeAt(0) // buang yang tertua
            save(list)
        }
    }

    /**
     * Perlu mengirim sekarang?
     * - antrian berisi, dan tidak sedang masa tunggu (backoff), dan salah satu:
     *   jumlah titik >= GPS_BATCH_SIZE, titik tertua >= GPS_BATCH_MAX_AGE_MS,
     *   atau ini percobaan pertama sejak aplikasi dibuka (agar dashboard langsung melihat salesman),
     *   atau force.
     */
    fun shouldFlush(now: Long, force: Boolean = false): Boolean = synchronized(lock) {
        val list = load()
        if (list.isEmpty()) return false
        if (now < nextAllowedAt) return false
        force ||
            !attemptedThisProcess ||
            list.size >= AppConfig.GPS_BATCH_SIZE ||
            now - list.first().timestamp >= AppConfig.GPS_BATCH_MAX_AGE_MS
    }

    /** Ambil sampai [max] titik tertua milik salesman yang sama dengan titik tertua (urut waktu). */
    fun peekChunk(max: Int): List<GpsPoint> = synchronized(lock) {
        val list = load()
        val first = list.firstOrNull() ?: return emptyList()
        list.asSequence().filter { it.kodeSalesman == first.kodeSalesman }.take(max).toList()
    }

    fun remove(sent: List<GpsPoint>) {
        synchronized(lock) {
            val list = load()
            list.removeAll(sent.toSet())
            save(list)
        }
    }

    fun markAttempt() = synchronized(lock) { attemptedThisProcess = true }

    fun markSuccess() = synchronized(lock) {
        failures = 0
        nextAllowedAt = 0L
    }

    /** Gagal kirim: tunggu 1, 2, 4, ... menit (maks GPS_BACKOFF_MAX_MS) sebelum mencoba lagi. */
    fun markFailure(now: Long) = synchronized(lock) {
        failures = (failures + 1).coerceAtMost(30)
        val delay = (AppConfig.GPS_BACKOFF_BASE_MS shl (failures - 1).coerceAtMost(20))
            .coerceAtMost(AppConfig.GPS_BACKOFF_MAX_MS)
        nextAllowedAt = now + delay
    }

    companion object {
        private const val KEY_POINTS = "points"
    }
}
