package com.example.update

import android.content.Context
import com.example.data.local.DraftRepository
import com.example.data.model.DraftLaporan
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
class UpdateFeatureTest {

    private lateinit var context: Context
    private lateinit var updateGuard: UpdateGuard
    private lateinit var repository: UpdateRepository
    private lateinit var downloader: ApkDownloader
    private lateinit var installer: UpdateInstaller
    private lateinit var updateManager: UpdateManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences(UpdateConfig.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        updateGuard = UpdateGuard(context)
        repository = UpdateRepository(context, updateGuard)
        downloader = ApkDownloader(context, updateGuard)
        installer = UpdateInstaller(context, updateGuard)
        updateManager = UpdateManager(context, updateGuard, repository, downloader, installer)
    }

    @Test
    fun `test state machine starts at Idle`() {
        assertEquals(UpdateState.Idle, updateManager.state.value)
    }

    @Test
    fun `test daily check date comparison prevents duplicate checks on same day`() {
        assertTrue(repository.shouldCheckToday())

        // Catat hari ini
        repository.recordCheckAttemptToday()
        assertFalse(repository.shouldCheckToday())

        // Jika jam perangkat dimundurkan atau tanggal diubah, pastikan tidak terblokir selamanya
        val prefs = context.getSharedPreferences(UpdateConfig.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(UpdateConfig.KEY_LAST_CHECK_DATE, "2025-01-01").apply()
        assertTrue(repository.shouldCheckToday())
    }

    @Test
    fun `test circuit breaker locks after 2 consecutive internal failures`() {
        val currentVersion = updateManager.getInstalledVersionCode()
        assertTrue(updateGuard.isFeatureAvailable(currentVersion))

        // Kegagalan pertama
        updateGuard.recordInternalFailure("NPE di logic")
        assertTrue(updateGuard.isFeatureAvailable(currentVersion))

        // Kegagalan kedua -> harus terkunci
        updateGuard.recordInternalFailure("IllegalStateException")
        assertFalse(updateGuard.isFeatureAvailable(currentVersion))

        // Buka otomatis jika versionCode berubah
        val higherVersion = currentVersion + 1
        assertTrue(updateGuard.isFeatureAvailable(higherVersion))
    }

    @Test
    fun `test circuit breaker startup busy flag recovery does not lock or count as internal failure`() {
        val currentVersion = updateManager.getInstalledVersionCode()
        val prefs = context.getSharedPreferences(UpdateConfig.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(UpdateConfig.KEY_UPDATE_BUSY, true).apply()

        updateGuard.checkAndRecoverStartupBusyFlag()
        assertFalse(prefs.getBoolean(UpdateConfig.KEY_UPDATE_BUSY, false))
        assertEquals(0, prefs.getInt(UpdateConfig.KEY_CONSECUTIVE_FAILURES, 0))
    }

    @Test
    fun `test SHA256 calculation and verification`() {
        val testFile = File(context.cacheDir, "test_file.bin")
        val content = "Update APK Test Content SPI Batu 2026".toByteArray()
        FileOutputStream(testFile).use { it.write(content) }

        val calculated = downloader.calculateSha256(testFile)

        val digest = MessageDigest.getInstance("SHA-256")
        val expected = digest.digest(content).joinToString("") { "%02x".format(it) }

        assertEquals(expected, calculated)
        testFile.delete()
    }

    @Test
    fun `test existing valid apk file is detected without downloading again`() {
        val content = "Sample APK File Content".toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        val expectedSha = digest.digest(content).joinToString("") { "%02x".format(it) }

        val testApk = downloader.getCompletedApkFile(99L)
        FileOutputStream(testApk).use { it.write(content) }

        val data = UpdateData(
            versionCode = 99L,
            versionName = "2.0.0",
            apkUrl = "https://numbing-brigade-glacier.ngrok-free.dev/sales-gps-dashboard/apk/apk/app-release.apk",
            sha256 = expectedSha,
            size = content.size.toLong()
        )

        val existing = downloader.getExistingValidApk(data)
        assertNotNull(existing)
        assertEquals(testApk.absolutePath, existing!!.absolutePath)

        testApk.delete()
    }

    @Test
    fun `test old version apks are cleaned up properly`() {
        val updateDir = downloader.getUpdatesDir()
        val oldApk = File(updateDir, "app-1.apk")
        val oldPart = File(updateDir, "app-1.apk.part")
        val newApk = File(updateDir, "app-2.apk")

        oldApk.writeText("old")
        oldPart.writeText("old part")
        newApk.writeText("new")

        downloader.cleanOldVersions(updateDir, 2L)

        assertFalse(oldApk.exists())
        assertFalse(oldPart.parseDotPartExists())
        assertTrue(newApk.exists())

        newApk.delete()
    }

    private fun File.parseDotPartExists(): Boolean {
        return File(parentFile, "$name.part").exists() || exists()
    }

    @Test
    fun `test update feature does not touch or clear drafts and photos`() {
        val draftRepo = DraftRepository(context)
        val draft = DraftLaporan(
            kodeMember = "MBR_UPDATE_TEST",
            idRkm = 99L,
            hasil = "BERHASIL",
            catatan = "Draft aman saat update",
            photoPath = "/cache/photos/test.jpg",
            latitude = -7.87,
            longitude = 112.52
        )
        draftRepo.saveDraft(draft)

        // Simulasikan alur update (cek, pembatalan, failure)
        updateManager.onLaterClicked()
        updateManager.onCancelDownload()
        updateGuard.recordInternalFailure("Simulated internal error")

        val loaded = draftRepo.getDraft("MBR_UPDATE_TEST")
        assertNotNull(loaded)
        assertEquals("Draft aman saat update", loaded!!.catatan)

        draftRepo.removeDraft("MBR_UPDATE_TEST")
    }

    @Test
    fun `test update dialog state transitions`() {
        val sampleData = UpdateData(
            versionCode = 5L,
            versionName = "1.2.0",
            apkUrl = "https://numbing-brigade-glacier.ngrok-free.dev/sales-gps-dashboard/apk/apk/app-release.apk",
            sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            size = 5000000L,
            changelog = "Perbaikan GPS dan pembaruan stabil"
        )

        // 1. Available state
        val available = UpdateState.Available(sampleData, isMetered = false)
        assertEquals(5000000L, available.data.size)
        assertEquals("4.8 MB", available.data.sizeInMbFormatted)

        // 2. Later transitions to Idle
        updateManager.onLaterClicked()
        assertEquals(UpdateState.Idle, updateManager.state.value)

        // 3. Failed close transitions to Idle
        updateManager.onCloseFailedClicked()
        assertEquals(UpdateState.Idle, updateManager.state.value)
    }
}
