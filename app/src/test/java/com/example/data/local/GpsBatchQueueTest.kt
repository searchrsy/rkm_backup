package com.example.data.local

import android.content.Context
import com.example.config.AppConfig
import com.example.data.model.GpsPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GpsBatchQueueTest {

    private lateinit var context: Context
    private lateinit var queue: GpsBatchQueue

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("rkm_gps_queue", Context.MODE_PRIVATE).edit().clear().commit()
        queue = GpsBatchQueue(context)
    }

    private fun point(i: Int, t: Long, kode: String = "ADK") = GpsPoint(
        kodeSalesman = kode,
        latitude = -7.87 + i * 0.0001,
        longitude = 112.52,
        timestamp = t,
        waktuGps = "2026-11-23T08:00:%02d".format(i % 60)
    )

    @Test
    fun `first flush of a process is immediate then waits for batch size`() {
        val now = 1_000_000L
        assertFalse(queue.shouldFlush(now))            // antrian kosong
        queue.enqueue(point(0, now))
        assertTrue(queue.shouldFlush(now))             // percobaan pertama sejak proses mulai
        queue.markAttempt()
        queue.markSuccess()
        queue.remove(queue.peekChunk(100))

        queue.enqueue(point(1, now))
        assertFalse(queue.shouldFlush(now + 1_000))    // belum cukup titik / belum cukup umur
        for (i in 2..AppConfig.GPS_BATCH_SIZE) queue.enqueue(point(i, now + i))
        assertTrue(queue.shouldFlush(now + 1_000))     // jumlah titik tercapai
    }

    @Test
    fun `old points trigger flush and failure applies backoff`() {
        val now = 10_000_000L
        queue.markAttempt()
        queue.enqueue(point(0, now))
        assertFalse(queue.shouldFlush(now + 1_000))
        assertTrue(queue.shouldFlush(now + AppConfig.GPS_BATCH_MAX_AGE_MS))

        queue.markFailure(now + AppConfig.GPS_BATCH_MAX_AGE_MS)
        assertFalse(queue.shouldFlush(now + AppConfig.GPS_BATCH_MAX_AGE_MS + 1_000)) // masa tunggu
        assertTrue(queue.shouldFlush(now + AppConfig.GPS_BATCH_MAX_AGE_MS + AppConfig.GPS_BACKOFF_BASE_MS))
        assertFalse(queue.shouldFlush(now + 1_000))
    }

    @Test
    fun `queue is persisted bounded and chunks by salesman`() {
        queue.markAttempt()
        for (i in 0 until AppConfig.GPS_QUEUE_MAX + 20) queue.enqueue(point(i, i.toLong()))
        assertEquals(AppConfig.GPS_QUEUE_MAX, queue.size())

        // instance baru membaca data yang sama dari penyimpanan
        val reloaded = GpsBatchQueue(context)
        assertEquals(AppConfig.GPS_QUEUE_MAX, reloaded.size())
        assertEquals(20L, reloaded.peekChunk(1).first().timestamp) // 20 titik tertua terbuang

        val chunk = reloaded.peekChunk(AppConfig.GPS_FLUSH_CHUNK)
        assertEquals(AppConfig.GPS_FLUSH_CHUNK, chunk.size)
        reloaded.remove(chunk)
        assertEquals(AppConfig.GPS_QUEUE_MAX - AppConfig.GPS_FLUSH_CHUNK, reloaded.size())
    }

    @Test
    fun `chunk only contains points of the same salesman`() {
        queue.enqueue(point(0, 1L, "ADK"))
        queue.enqueue(point(1, 2L, "AFS"))
        queue.enqueue(point(2, 3L, "ADK"))
        val chunk = queue.peekChunk(100)
        assertEquals(listOf(1L, 3L), chunk.map { it.timestamp })
    }
}
