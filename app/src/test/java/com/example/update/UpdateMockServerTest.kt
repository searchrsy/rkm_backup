package com.example.update

import android.content.Context
import com.example.data.local.DraftRepository
import com.example.data.model.DraftLaporan
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateMockServerTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var context: Context
    private lateinit var updateGuard: UpdateGuard
    private lateinit var repository: UpdateRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences(UpdateConfig.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        updateGuard = UpdateGuard(context)
        repository = UpdateRepository(context, updateGuard)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        File(context.cacheDir, "updates").deleteRecursively()
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    // ==========================================
    // 1. PENGUJIAN check.php DENGAN SERVER PALSU
    // ==========================================

    @Test
    fun `test checkUpdate with higher version returns valid UpdateData`() {
        runBlocking {
            val baseUrl = mockWebServer.url("/sales-gps-dashboard/apk/").toString().removeSuffix("/")
            val apkUrl = "$baseUrl/app-release.apk"
            val payload = """
                {
                    "success": true,
                    "updateEnabled": true,
                    "message": "Pembaruan tersedia",
                    "data": {
                        "versionCode": 5,
                        "versionName": "1.1.0",
                        "apkUrl": "$apkUrl",
                        "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        "size": 2560000,
                        "changelog": "Perbaikan GPS dan update sistem",
                        "forceUpdate": false
                    }
                }
            """.trimIndent()

            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(payload))

            val checkUrl = mockWebServer.url("/sales-gps-dashboard/apk/check.php").toString()
            val result = repository.checkUpdate(installedVersionCode = 2L, checkUrl = checkUrl)

            assertNotNull(result)
            assertEquals(5L, result!!.versionCode)
            assertEquals("1.1.0", result.versionName)
            assertEquals(2560000L, result.size)
        }
    }

    @Test
    fun `test checkUpdate with same version returns null`() {
        runBlocking {
            val baseUrl = mockWebServer.url("/sales-gps-dashboard/apk/").toString().removeSuffix("/")
            val payload = """
                {
                    "success": true,
                    "updateEnabled": true,
                    "data": {
                        "versionCode": 3,
                        "versionName": "1.0.2",
                        "apkUrl": "$baseUrl/app-release.apk",
                        "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        "size": 2500000
                    }
                }
            """.trimIndent()

            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(payload))
            val checkUrl = mockWebServer.url("/sales-gps-dashboard/apk/check.php").toString()
            val result = repository.checkUpdate(installedVersionCode = 3L, checkUrl = checkUrl)

            assertNull(result)
        }
    }

    @Test
    fun `test checkUpdate with lower version returns null`() {
        runBlocking {
            val baseUrl = mockWebServer.url("/sales-gps-dashboard/apk/").toString().removeSuffix("/")
            val payload = """
                {
                    "success": true,
                    "updateEnabled": true,
                    "data": {
                        "versionCode": 1,
                        "versionName": "1.0.0",
                        "apkUrl": "$baseUrl/app-release.apk",
                        "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        "size": 2500000
                    }
                }
            """.trimIndent()

            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(payload))
            val checkUrl = mockWebServer.url("/sales-gps-dashboard/apk/check.php").toString()
            val result = repository.checkUpdate(installedVersionCode = 3L, checkUrl = checkUrl)

            assertNull(result)
        }
    }

    @Test
    fun `test checkUpdate with updateEnabled false returns null without crash`() {
        runBlocking {
            val payload = """
                {
                    "success": true,
                    "updateEnabled": false,
                    "message": "Fitur update dinonaktifkan sementara oleh server"
                }
            """.trimIndent()

            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(payload))
            val checkUrl = mockWebServer.url("/sales-gps-dashboard/apk/check.php").toString()
            val result = repository.checkUpdate(installedVersionCode = 1L, checkUrl = checkUrl)

            assertNull(result)
        }
    }

    @Test
    fun `test checkUpdate with success false 404 returns null`() {
        runBlocking {
            val payload = """{"success":false,"message":"File APK tidak ditemukan."}"""
            mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody(payload))

            val checkUrl = mockWebServer.url("/sales-gps-dashboard/apk/check.php").toString()
            val result = repository.checkUpdate(installedVersionCode = 1L, checkUrl = checkUrl)

            assertNull(result)
        }
    }

    @Test
    fun `test checkUpdate with malformed JSON returns null without crash`() {
        runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("<html>502 Bad Gateway</html>"))

            val checkUrl = mockWebServer.url("/sales-gps-dashboard/apk/check.php").toString()
            val result = repository.checkUpdate(installedVersionCode = 1L, checkUrl = checkUrl)

            assertNull(result)
        }
    }

    @Test
    fun `test checkUpdate with missing fields uses defaults or returns null gracefully`() {
        runBlocking {
            val payload = """{"success": true, "updateEnabled": true}"""
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(payload))

            val checkUrl = mockWebServer.url("/sales-gps-dashboard/apk/check.php").toString()
            val result = repository.checkUpdate(installedVersionCode = 1L, checkUrl = checkUrl)

            assertNull(result)
        }
    }

    @Test
    fun `test checkUpdate with zero or negative versionCode returns null`() {
        runBlocking {
            val baseUrl = mockWebServer.url("/sales-gps-dashboard/apk/").toString().removeSuffix("/")
            val payloadZero = """
                {
                    "success": true,
                    "updateEnabled": true,
                    "data": { "versionCode": 0, "apkUrl": "$baseUrl/app.apk", "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "size": 100 }
                }
            """.trimIndent()
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(payloadZero))

            val checkUrl = mockWebServer.url("/sales-gps-dashboard/apk/check.php").toString()
            val resultZero = repository.checkUpdate(installedVersionCode = 0L, checkUrl = checkUrl)
            assertNull(resultZero)

            val payloadNeg = """
                {
                    "success": true,
                    "updateEnabled": true,
                    "data": { "versionCode": -5, "apkUrl": "$baseUrl/app.apk", "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "size": 100 }
                }
            """.trimIndent()
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(payloadNeg))
            val resultNeg = repository.checkUpdate(installedVersionCode = 0L, checkUrl = checkUrl)
            assertNull(resultNeg)
        }
    }

    // ==========================================
    // 2. VALIDASI apkUrl KEAMANAN
    // ==========================================

    @Test
    fun `test apkUrl security validation rejects invalid hosts, non-https, and path traversal`() {
        val expectedHost = UpdateConfig.EXPECTED_HOST

        // Valid
        assertTrue(
            repository.isValidApkUrl(
                "https://$expectedHost/sales-gps-dashboard/apk/apk/app-release.apk",
                expectedHost
            )
        )

        // Non-HTTPS ditolak
        assertFalse(
            repository.isValidApkUrl(
                "http://$expectedHost/sales-gps-dashboard/apk/apk/app-release.apk",
                expectedHost
            )
        )

        // Host tidak cocok ditolak (termasuk subdomain atau host mirip)
        assertFalse(
            repository.isValidApkUrl(
                "https://evil-server.com/sales-gps-dashboard/apk/apk/app-release.apk",
                expectedHost
            )
        )
        assertFalse(
            repository.isValidApkUrl(
                "https://$expectedHost.attacker.com/sales-gps-dashboard/apk/apk/app-release.apk",
                expectedHost
            )
        )

        // Path traversal ditolak
        assertFalse(
            repository.isValidApkUrl(
                "https://$expectedHost/sales-gps-dashboard/apk/../../etc/passwd",
                expectedHost
            )
        )
        assertFalse(
            repository.isValidApkUrl(
                "https://$expectedHost/sales-gps-dashboard/apk/../malicious.apk",
                expectedHost
            )
        )

        // Prefix path salah ditolak
        assertFalse(
            repository.isValidApkUrl(
                "https://$expectedHost/other-path/app-release.apk",
                expectedHost
            )
        )
    }

    // ==========================================
    // 3. PENGUJIAN UNDUHAN DENGAN MOCK SERVER
    // ==========================================

    @Test
    fun `test full successful download with streaming and sha verification`() {
        runBlocking {
            val fileContent = "Ini berkas APK valid SPI Batu 2026 byte array".toByteArray()
            val expectedSha = sha256(fileContent)
            val downloadPath = "/sales-gps-dashboard/apk/apk/app-release.apk"

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", fileContent.size)
                    .setHeader("ETag", "\"etag-12345\"")
                    .setBody(Buffer().write(fileContent))
            )

            val fullApkUrl = mockWebServer.url(downloadPath).toString()
            val downloader = ApkDownloader(
                context = context,
                updateGuard = updateGuard,
                customClient = OkHttpClient.Builder().build()
            )

            val updateData = UpdateData(
                versionCode = 10L,
                versionName = "1.5.0",
                apkUrl = fullApkUrl,
                sha256 = expectedSha,
                size = fileContent.size.toLong()
            )

            var lastProgress = 0f
            val downloadedFile = downloader.downloadApk(updateData) { _, _, progress ->
                lastProgress = progress
            }

            assertTrue(downloadedFile.exists())
            assertEquals(fileContent.size.toLong(), downloadedFile.length())
            assertEquals(1.0f, lastProgress, 0.01f)
            assertEquals(expectedSha, downloader.calculateSha256(downloadedFile))

            downloadedFile.delete()
        }
    }

    @Test
    fun `test download resume with HTTP 206 Partial Content`() {
        runBlocking {
            val totalBytes = "Bagian Satu. Bagian Dua.".toByteArray()
            val part1 = "Bagian Satu. ".toByteArray()
            val part2 = "Bagian Dua.".toByteArray()
            val expectedSha = sha256(totalBytes)

            val downloader = ApkDownloader(
                context = context,
                updateGuard = updateGuard,
                customClient = OkHttpClient.Builder().build()
            )

            // Tulis sebagian ke .part file terlebih dahulu
            val partFile = downloader.getPartApkFile(11L)
            FileOutputStream(partFile).use { it.write(part1) }

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes ${part1.size}-${totalBytes.size - 1}/${totalBytes.size}")
                    .setHeader("Content-Length", part2.size)
                    .setHeader("ETag", "\"etag-resume-1\"")
                    .setBody(Buffer().write(part2))
            )

            val updateData = UpdateData(
                versionCode = 11L,
                versionName = "1.5.1",
                apkUrl = mockWebServer.url("/sales-gps-dashboard/apk/app.apk").toString(),
                sha256 = expectedSha,
                size = totalBytes.size.toLong()
            )

            val completed = downloader.downloadApk(updateData) { _, _, _ -> }
            assertTrue(completed.exists())
            assertEquals(totalBytes.size.toLong(), completed.length())
            assertEquals(expectedSha, downloader.calculateSha256(completed))

            val recordedRequest = mockWebServer.takeRequest()
            assertEquals("bytes=${part1.size}-", recordedRequest.getHeader("Range"))

            completed.delete()
        }
    }

    @Test
    fun `test download restarts from 0 when server returns 200 on Range request`() {
        runBlocking {
            val fullContent = "Konten Baru Penuh Menggantikan Part".toByteArray()
            val oldPart = "konten lama".toByteArray()
            val expectedSha = sha256(fullContent)

            val downloader = ApkDownloader(
                context = context,
                updateGuard = updateGuard,
                customClient = OkHttpClient.Builder().build()
            )

            val partFile = downloader.getPartApkFile(12L)
            FileOutputStream(partFile).use { it.write(oldPart) }

            // Server abaikan Range dan kirim 200 OK dengan seluruh konten
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", fullContent.size)
                    .setBody(Buffer().write(fullContent))
            )

            val updateData = UpdateData(
                versionCode = 12L,
                versionName = "1.5.2",
                apkUrl = mockWebServer.url("/sales-gps-dashboard/apk/app.apk").toString(),
                sha256 = expectedSha,
                size = fullContent.size.toLong()
            )

            val completed = downloader.downloadApk(updateData) { _, _, _ -> }
            assertTrue(completed.exists())
            assertEquals(fullContent.size.toLong(), completed.length())
            assertEquals(expectedSha, downloader.calculateSha256(completed))

            completed.delete()
        }
    }

    @Test
    fun `test download resets part file when ETag changes on server`() {
        runBlocking {
            val fullContent = "File Baru Dengan ETag Baru".toByteArray()
            val oldPart = "file lama part".toByteArray()
            val expectedSha = sha256(fullContent)

            val prefs = context.getSharedPreferences(UpdateConfig.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(UpdateConfig.KEY_SAVED_ETAG, "\"etag-old\"").apply()

            val downloader = ApkDownloader(
                context = context,
                updateGuard = updateGuard,
                customClient = OkHttpClient.Builder().build()
            )

            val partFile = downloader.getPartApkFile(13L)
            FileOutputStream(partFile).use { it.write(oldPart) }

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("ETag", "\"etag-new\"")
                    .setHeader("Content-Length", fullContent.size)
                    .setBody(Buffer().write(fullContent))
            )

            val updateData = UpdateData(
                versionCode = 13L,
                versionName = "1.5.3",
                apkUrl = mockWebServer.url("/sales-gps-dashboard/apk/app.apk").toString(),
                sha256 = expectedSha,
                size = fullContent.size.toLong()
            )

            val completed = downloader.downloadApk(updateData) { _, _, _ -> }
            assertEquals(fullContent.size.toLong(), completed.length())
            assertEquals(expectedSha, downloader.calculateSha256(completed))
            assertEquals("\"etag-new\"", prefs.getString(UpdateConfig.KEY_SAVED_ETAG, null))

            completed.delete()
        }
    }

    @Test
    fun `test download deletes part file on 416 Range Not Satisfiable`() {
        runBlocking {
            val downloader = ApkDownloader(
                context = context,
                updateGuard = updateGuard,
                customClient = OkHttpClient.Builder().build()
            )

            val partFile = downloader.getPartApkFile(14L)
            partFile.writeText("invalid range bytes")

            mockWebServer.enqueue(MockResponse().setResponseCode(416))

            val updateData = UpdateData(
                versionCode = 14L,
                versionName = "1.5.4",
                apkUrl = mockWebServer.url("/sales-gps-dashboard/apk/app.apk").toString(),
                sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                size = 500
            )

            try {
                downloader.downloadApk(updateData) { _, _, _ -> }
                fail("Harus melempar IllegalStateException pada HTTP 416")
            } catch (e: IllegalStateException) {
                assertTrue(e.message?.contains("416") == true)
            }

            assertFalse(partFile.exists())
        }
    }

    @Test
    fun `test download fails and deletes file when size does not match`() {
        runBlocking {
            val content = "Konten hanya 16 byte".toByteArray()
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(content)))

            val downloader = ApkDownloader(
                context = context,
                updateGuard = updateGuard,
                customClient = OkHttpClient.Builder().build()
            )

            val updateData = UpdateData(
                versionCode = 15L,
                versionName = "1.5.5",
                apkUrl = mockWebServer.url("/sales-gps-dashboard/apk/app.apk").toString(),
                sha256 = sha256(content),
                size = 9999L // Diharapkan 9999 byte tetapi dikirim 16 byte
            )

            try {
                downloader.downloadApk(updateData) { _, _, _ -> }
                fail("Harus gagal saat ukuran tidak cocok")
            } catch (e: IllegalStateException) {
                assertTrue(e.message?.contains("Ukuran unduhan tidak sesuai") == true)
            }

            assertFalse(downloader.getCompletedApkFile(15L).exists())
            assertFalse(downloader.getPartApkFile(15L).exists())
        }
    }

    @Test
    fun `test download fails and deletes file when sha256 does not match`() {
        runBlocking {
            val content = "Konten asli byte".toByteArray()
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(content)))

            val downloader = ApkDownloader(
                context = context,
                updateGuard = updateGuard,
                customClient = OkHttpClient.Builder().build()
            )

            val updateData = UpdateData(
                versionCode = 16L,
                versionName = "1.5.6",
                apkUrl = mockWebServer.url("/sales-gps-dashboard/apk/app.apk").toString(),
                sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                size = content.size.toLong()
            )

            try {
                downloader.downloadApk(updateData) { _, _, _ -> }
                fail("Harus gagal saat hash SHA-256 tidak cocok")
            } catch (e: IllegalStateException) {
                assertTrue(e.message?.contains("SHA-256") == true)
            }

            assertFalse(downloader.getCompletedApkFile(16L).exists())
            assertFalse(downloader.getPartApkFile(16L).exists())
        }
    }

    // ==========================================
    // 4. VERIFIKASI DRAFT DAN FOTO TIDAK TERSENTUH
    // ==========================================

    @Test
    fun `test drafts and photos are completely unaffected by all update mock server scenarios`() {
        runBlocking {
            val draftRepo = DraftRepository(context)
            val testDraft = DraftLaporan(
                kodeMember = "MBR_SPI_MOCK_TEST",
                idRkm = 101L,
                hasil = "BERHASIL",
                catatan = "Draft persisten selama uji mock server update",
                photoPath = "/cache/photos/mock_photo.jpg",
                latitude = -7.869,
                longitude = 112.525
            )
            draftRepo.saveDraft(testDraft)

            // Simulasikan serangkaian skenario error server palsu
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            repository.checkUpdate(installedVersionCode = 2L, checkUrl = mockWebServer.url("/check.php").toString())

            mockWebServer.enqueue(MockResponse().setResponseCode(404))
            repository.checkUpdate(installedVersionCode = 2L, checkUrl = mockWebServer.url("/check.php").toString())

            // Pastikan draft masih utuh 100%
            val retrieved = draftRepo.getDraft("MBR_SPI_MOCK_TEST")
            assertNotNull(retrieved)
            assertEquals("Draft persisten selama uji mock server update", retrieved!!.catatan)
            assertEquals("/cache/photos/mock_photo.jpg", retrieved.photoPath)

            draftRepo.removeDraft("MBR_SPI_MOCK_TEST")
        }
    }
}
