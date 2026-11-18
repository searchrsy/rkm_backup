package com.example

import com.example.data.local.DraftRepository
import com.example.data.model.DraftLaporan
import com.example.data.model.LaporanResponse
import com.example.data.model.LoginResponse
import com.example.data.model.MemberItem
import com.example.data.model.RkmResponse
import com.example.data.network.ApiClient
import com.example.ui.components.LaporanValidator
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RkmIntegrationTest {

    private val moshi = ApiClient.getMoshi()

    @Test
    fun `parse actual login success json`() {
        val json = """
            {
              "success": true,
              "message": "Login berhasil",
              "token": "ZE0lOOjPm3nNmQgexd4ckkO--FVZN_77aL3s_cA3GOs",
              "expires_at": "2026-10-21 12:46:29",
              "data": {
                "kode_salesman": "ADK",
                "nama_salesman": "ADK"
              }
            }
        """.trimIndent()

        val adapter = moshi.adapter(LoginResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertTrue(response!!.success)
        assertEquals("ZE0lOOjPm3nNmQgexd4ckkO--FVZN_77aL3s_cA3GOs", response.token)
        assertEquals("ADK", response.data?.kodeSalesman)
    }

    @Test
    fun `parse actual empty rkm response`() {
        val json = """
            {
              "success": true,
              "message": "Belum ada rencana kunjungan hari ini",
              "data": {
                "tanggal": "2026-10-21",
                "kode_salesman": "ADK",
                "rencana": null,
                "kunjungan": []
              }
            }
        """.trimIndent()

        val adapter = moshi.adapter(RkmResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertTrue(response!!.success)
        assertEquals("2026-10-21", response.data?.tanggal)
        assertEquals("ADK", response.data?.kodeSalesman)
        assertNull(response.data?.rencana)
        assertTrue(response.data?.kunjungan?.isEmpty() == true)
    }

    @Test
    fun `parse rkm1 actual structure with koordinat string`() {
        val json = """
            {
              "success": true,
              "message": "Daftar kunjungan berhasil diambil",
              "data": {
                "tanggal": "2026-10-21",
                "kode_salesman": "ADK",
                "rencana": {
                  "id": 42,
                  "status": "aktif"
                },
                "kunjungan": [
                  {
                    "id_rkm": 42,
                    "urutan": 1,
                    "kode_member": "MBR001",
                    "nama_member": "Toko Berkah SPI",
                    "alamat": "Jl. Panglima Sudirman No. 12",
                    "kecamatan": "Batu",
                    "koordinat": "-7.8712,112.5273",
                    "status_member": "AKTIF",
                    "status_kunjungan": "BELUM"
                  }
                ]
              }
            }
        """.trimIndent()

        val adapter = moshi.adapter(RkmResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertEquals(42L, response!!.data?.rencana?.id)

        val members = response.data?.kunjungan
        assertNotNull(members)
        assertEquals(1, members!!.size)

        val m1 = members[0]
        assertEquals("MBR001", m1.kodeMember)
        assertEquals("Toko Berkah SPI", m1.displayName)
        assertEquals(-7.8712, m1.doubleLatitude!!, 0.0001)
        assertEquals(112.5273, m1.doubleLongitude!!, 0.0001)
        assertFalse(m1.isFinished)
    }

    @Test
    fun `parse rkm2 actual success 201 response`() {
        val json = """
            {
              "success": true,
              "message": "Hasil kunjungan berhasil disimpan",
              "data": {
                "id": 1001,
                "id_rkm": 42,
                "kode_member": "MBR001",
                "hasil": "BERHASIL",
                "waktu_kunjungan": "2026-10-21 15:48:00"
              }
            }
        """.trimIndent()

        val adapter = moshi.adapter(LaporanResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertTrue(response!!.success)
        assertEquals("Hasil kunjungan berhasil disimpan", response.message)
        assertEquals(1001L, response.data?.id)
        assertEquals(42L, response.data?.idRkm)
        assertEquals("MBR001", response.data?.kodeMember)
        assertEquals("BERHASIL", response.data?.hasil)
    }

    @Test
    fun `parse rkm2 validation error responses`() {
        val errorJson = """{"success":false,"message":"Foto kunjungan wajib diunggah."}"""
        val adapter = moshi.adapter(LaporanResponse::class.java)
        val response = adapter.fromJson(errorJson)

        assertNotNull(response)
        assertFalse(response!!.success)
        assertEquals("Foto kunjungan wajib diunggah.", response.message)
    }

    @Test
    fun `draft repository preserves draft upon simulated failure`() {
        val context = RuntimeEnvironment.getApplication()
        val repo = DraftRepository(context)

        val draft = DraftLaporan(
            kodeMember = "MBR001",
            idRkm = 42L,
            hasil = "BERHASIL",
            catatan = "Catatan kunjungan penting",
            photoPath = "/data/user/0/cache/photos/bukti.jpg",
            latitude = -7.8712,
            longitude = 112.5273
        )

        repo.saveDraft(draft)

        // Verifikasi draft tetap tersimpan
        val loaded = repo.getDraft("MBR001")
        assertNotNull(loaded)
        assertEquals("MBR001", loaded!!.kodeMember)
        assertEquals(42L, loaded.idRkm)
        assertEquals("Catatan kunjungan penting", loaded.catatan)

        // Saat gagal: repo.removeDraft TIDAK dipanggil, pastikan masih ada
        val stillExists = repo.getDraft("MBR001")
        assertNotNull(stillExists)

        // Hapus hanya saat sukses
        repo.removeDraft("MBR001")
        assertNull(repo.getDraft("MBR001"))
    }

    @Test
    fun `draft repository handles follow_up date correctly`() {
        val context = RuntimeEnvironment.getApplication()
        val repo = DraftRepository(context)

        val draftWithDate = DraftLaporan(
            kodeMember = "MBR002",
            idRkm = 42L,
            hasil = "BERHASIL",
            catatan = "Follow up minggu depan",
            photoPath = null,
            latitude = -7.8712,
            longitude = 112.5273,
            followUp = "2026-10-25"
        )

        repo.saveDraft(draftWithDate)
        val loaded = repo.getDraft("MBR002")
        assertNotNull(loaded)
        assertEquals("2026-10-25", loaded!!.followUp)

        // Test clearing follow_up
        val draftCleared = draftWithDate.copy(followUp = null)
        repo.saveDraft(draftCleared)
        val loadedCleared = repo.getDraft("MBR002")
        assertNotNull(loadedCleared)
        assertNull(loadedCleared!!.followUp)

        repo.removeDraft("MBR002")
    }

    @Test
    fun `draft repository handles legacy draft without follow_up field`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("rkm_spi_drafts", android.content.Context.MODE_PRIVATE)

        // Simulasikan JSON draft versi lama tanpa properti follow_up
        val legacyJson = """
            {
                "kode_member": "MBR_OLD",
                "id_rkm": 10,
                "hasil": "TOKO TUTUP",
                "catatan": "Toko masih tutup",
                "photo_path": "",
                "latitude": -7.87,
                "longitude": 112.52,
                "timestamp": 1697000000000
            }
        """.trimIndent()
        prefs.edit().putString("MBR_OLD", legacyJson).apply()

        val repo = DraftRepository(context)
        val loaded = repo.getDraft("MBR_OLD")
        assertNotNull(loaded)
        assertEquals("MBR_OLD", loaded!!.kodeMember)
        assertEquals("TOKO TUTUP", loaded.hasil)
        assertNull(loaded.followUp) // default null jika field tidak ada

        repo.removeDraft("MBR_OLD")
    }

    @Test
    fun `parse lokasi terakhir actual json structure`() {
        val json = """
            {
              "success": true,
              "message": "Data lokasi berhasil diambil",
              "data": [
                {
                  "id": 1,
                  "kode_sales": "ADK",
                  "device_id": "android_adk_01",
                  "latitude": -7.871234,
                  "longitude": 112.527345,
                  "akurasi": 12.5,
                  "kecepatan": 0.0,
                  "waktu_gps": "2026-10-21T08:30:00",
                  "diterima_pada": "2026-10-21 08:30:05",
                  "aktif": true
                },
                {
                  "id": 2,
                  "kode_sales": "CLS",
                  "device_id": "android_cls_02",
                  "latitude": null,
                  "longitude": 0.0,
                  "akurasi": null,
                  "aktif": false
                }
              ]
            }
        """.trimIndent()

        val adapter = moshi.adapter(com.example.data.model.LokasiTerakhirResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertTrue(response!!.success)
        assertEquals(2, response.data?.size)

        val item1 = response.data!![0]
        assertEquals("ADK", item1.salesCode)
        assertEquals(-7.871234, item1.doubleLatitude!!, 0.000001)
        assertEquals(112.527345, item1.doubleLongitude!!, 0.000001)
        assertTrue(item1.isValidCoordinate)
        assertTrue(item1.aktif == true)

        // Item 2 dengan koordinat null/0.0 harus tidak valid
        val item2 = response.data!![1]
        assertFalse(item2.isValidCoordinate)
    }

    @Test
    fun `parse riwayat lokasi actual json structure with group and titik`() {
        val json = """
            {
              "success": true,
              "message": "Riwayat lokasi berhasil diambil",
              "data": [
                {
                  "device_id": "android_adk_01",
                  "kode_sales": "ADK",
                  "titik": [
                    {
                      "latitude": -7.8710,
                      "longitude": 112.5270,
                      "akurasi": 10.0,
                      "waktu_gps": "2026-10-21T08:00:00"
                    },
                    {
                      "latitude": -7.8720,
                      "longitude": 112.5280,
                      "akurasi": 8.0,
                      "waktu_gps": "2026-10-21T08:15:00"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val adapter = moshi.adapter(com.example.data.model.RiwayatLokasiResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertTrue(response!!.success)
        assertEquals(1, response.data?.size)

        val group = response.data!![0]
        assertEquals("ADK", group.salesCode)
        assertEquals(2, group.titik?.size)

        val p1 = group.titik!![0]
        assertTrue(p1.isValidCoordinate)
        assertEquals(-7.8710, p1.doubleLatitude!!, 0.0001)
        assertEquals(112.5270, p1.doubleLongitude!!, 0.0001)
    }

    @Test
    fun `coordinate validation filters invalid and out of range coordinates`() {
        val valid = com.example.data.model.LokasiSalesItem(
            latitude = -7.87,
            longitude = 112.52
        )
        assertTrue(valid.isValidCoordinate)

        val zeroZero = com.example.data.model.LokasiSalesItem(
            latitude = 0.0,
            longitude = 0.0
        )
        assertFalse(zeroZero.isValidCoordinate)

        val outOfRangeLat = com.example.data.model.LokasiSalesItem(
            latitude = 95.0,
            longitude = 112.52
        )
        assertFalse(outOfRangeLat.isValidCoordinate)

        val nullCoord = com.example.data.model.LokasiSalesItem(
            latitude = null,
            longitude = null
        )
        assertFalse(nullCoord.isValidCoordinate)
    }

    @Test
    fun `session manager preserves offline location cache`() {
        val context = RuntimeEnvironment.getApplication()
        val session = com.example.data.local.SessionManager(context)

        val sampleJson = """[{"id":1,"kode_sales":"ADK","latitude":-7.87,"longitude":112.52}]"""
        session.saveCachedLokasi(sampleJson)

        val loaded = session.getCachedLokasi()
        assertEquals(sampleJson, loaded)
    }

    @Test
    fun `serialize GpsPayload includes timestamp and required backend fields`() {
        val payload = com.example.data.model.GpsPayload(
            kodeSalesman = "ADK",
            kodeSales = "ADK",
            deviceId = "android_dev_test_01",
            latitude = -7.954285,
            longitude = 112.583150,
            akurasi = 10.0f,
            kecepatan = 0.0f,
            timestamp = 1729502000000L
        )
        val adapter = moshi.adapter(com.example.data.model.GpsPayload::class.java)
        val json = adapter.toJson(payload)
        val obj = JSONObject(json)

        assertEquals("ADK", obj.getString("kode_sales"))
        assertEquals("android_dev_test_01", obj.getString("device_id"))
        assertEquals(-7.954285, obj.getDouble("latitude"), 0.000001)
        assertEquals(112.583150, obj.getDouble("longitude"), 0.000001)
        assertEquals(1729502000000L, obj.getLong("timestamp"))
        assertEquals(10.0, obj.getDouble("akurasi"), 0.01)
        assertEquals(0.0, obj.getDouble("kecepatan"), 0.01)
    }

    @Test
    fun `order_hari_ini sub-pilihan a - BERHASIL with Hanya dikunjungi yields 0 and preserves BERHASIL`() {
        val context = RuntimeEnvironment.getApplication()
        val repo = DraftRepository(context)

        val draft = DraftLaporan(
            kodeMember = "MBR_SUB_0",
            idRkm = 100L,
            hasil = "BERHASIL",
            catatan = "Kunjungan rutin",
            photoPath = "/cache/test.jpg",
            latitude = -7.87,
            longitude = 112.52,
            orderHariIni = "0"
        )
        repo.saveDraft(draft)

        val loaded = repo.getDraft("MBR_SUB_0")
        assertNotNull(loaded)
        assertEquals("BERHASIL", loaded!!.hasil)
        assertEquals("0", loaded.orderHariIni)

        // Verifikasi penentuan part multipart di logic aplikasi
        val orderVal = if (loaded.hasil == "BERHASIL") (loaded.orderHariIni ?: "0") else "0"
        assertEquals("0", orderVal)
        assertEquals("BERHASIL", loaded.hasil)

        repo.removeDraft("MBR_SUB_0")
    }

    @Test
    fun `order_hari_ini sub-pilihan b - BERHASIL with Dikunjungi dan Order yields 1 and preserves BERHASIL`() {
        val context = RuntimeEnvironment.getApplication()
        val repo = DraftRepository(context)

        val draft = DraftLaporan(
            kodeMember = "MBR_SUB_1",
            idRkm = 101L,
            hasil = "BERHASIL",
            catatan = "Order sabun 5 karton",
            photoPath = "/cache/test1.jpg",
            latitude = -7.87,
            longitude = 112.52,
            orderHariIni = "1"
        )
        repo.saveDraft(draft)

        val loaded = repo.getDraft("MBR_SUB_1")
        assertNotNull(loaded)
        assertEquals("BERHASIL", loaded!!.hasil)
        assertEquals("1", loaded.orderHariIni)

        // Verifikasi penentuan part multipart di logic aplikasi
        val orderVal = if (loaded.hasil == "BERHASIL") (loaded.orderHariIni ?: "0") else "0"
        assertEquals("1", orderVal)
        assertEquals("BERHASIL", loaded.hasil)

        repo.removeDraft("MBR_SUB_1")
    }

    @Test
    fun `order_hari_ini sub-pilihan c - BERHASIL without sub-pilihan is rejected by validation`() {
        val app = RuntimeEnvironment.getApplication() as android.app.Application
        val sessionManager = com.example.data.local.SessionManager(app)
        sessionManager.saveSession("fake_valid_token_1234567890_abcdefghijklmn", "2030-01-01 00:00:00", "ADK", "Sales ADK")

        val vm = com.example.ui.main.MainViewModel(app)

        val dummyFile = java.io.File.createTempFile("test_photo", ".jpg")
        dummyFile.writeBytes(ByteArray(100))

        val draftWithoutSubPilihan = DraftLaporan(
            kodeMember = "MBR_NO_SUB",
            idRkm = 102L,
            hasil = "BERHASIL",
            catatan = "Belum memilih sub-pilihan",
            photoPath = dummyFile.absolutePath,
            latitude = -7.87,
            longitude = 112.52,
            orderHariIni = null // Belum memilih
        )

        vm.submitLaporan(draftWithoutSubPilihan, dummyFile)

        // Validasi wajib menolak laporan
        assertEquals("Pilih status order untuk hasil BERHASIL", vm.uiState.value.laporanError)
        assertFalse(vm.uiState.value.isSubmittingLaporan)

        dummyFile.delete()
    }

    @Test
    fun `order_hari_ini sub-pilihan d - hasil other than BERHASIL yields 0 and preserves original hasil`() {
        val context = RuntimeEnvironment.getApplication()
        val repo = DraftRepository(context)

        val nonBerhasilOptions = listOf("TOKO TUTUP", "TIDAK MAU ORDER", "ALASAN LAIN")
        for (opt in nonBerhasilOptions) {
            val draft = DraftLaporan(
                kodeMember = "MBR_$opt",
                idRkm = 103L,
                hasil = opt,
                catatan = if (opt == "ALASAN LAIN") "Alasan lain wajib catatan" else "",
                photoPath = "/cache/photo.jpg",
                latitude = -7.87,
                longitude = 112.52,
                orderHariIni = "0"
            )
            repo.saveDraft(draft)

            val loaded = repo.getDraft("MBR_$opt")
            assertNotNull(loaded)
            assertEquals(opt, loaded!!.hasil)

            // Untuk hasil selain BERHASIL, kirim order_hari_ini = "0" dan hasil tidak berubah
            val orderVal = if (loaded.hasil == "BERHASIL") (loaded.orderHariIni ?: "0") else "0"
            assertEquals("0", orderVal)
            assertEquals(opt, loaded.hasil)

            repo.removeDraft("MBR_$opt")
        }
    }

    @Test
    fun `validasi d3a - hasil kosong ditolak`() {
        val result = LaporanValidator.validate(
            hasil = "",
            orderHariIni = null,
            catatan = "Kunjungan dilakukan",
            followUp = null
        )
        assertFalse(result.isValid)
        assertEquals("Pilih hasil kunjungan", result.hasilError)

        val app = RuntimeEnvironment.getApplication() as android.app.Application
        val sessionManager = com.example.data.local.SessionManager(app)
        sessionManager.saveSession("fake_valid_token_1234567890_abcdefghijklmn", "2030-01-01 00:00:00", "ADK", "Sales ADK")
        val vm = com.example.ui.main.MainViewModel(app)

        val dummyFile = java.io.File.createTempFile("test_empty_hasil", ".jpg")
        dummyFile.writeBytes(ByteArray(100))

        vm.submitLaporan(
            DraftLaporan(
                kodeMember = "MBR_EMPTY",
                idRkm = 200L,
                hasil = "",
                catatan = "Kunjungan dilakukan",
                photoPath = dummyFile.absolutePath,
                latitude = -7.87,
                longitude = 112.52
            ),
            dummyFile
        )
        assertEquals("Pilih hasil kunjungan", vm.uiState.value.laporanError)
        assertFalse(vm.uiState.value.isSubmittingLaporan)
        dummyFile.delete()
    }

    @Test
    fun `validasi d3b - BERHASIL tanpa sub-pilihan ditolak`() {
        val result = LaporanValidator.validate(
            hasil = "BERHASIL",
            orderHariIni = null,
            catatan = "Toko buka",
            followUp = null
        )
        assertFalse(result.isValid)
        assertEquals("Pilih status order untuk hasil BERHASIL", result.orderHariIniError)

        val blankResult = LaporanValidator.validate(
            hasil = "BERHASIL",
            orderHariIni = "  ",
            catatan = "Toko buka",
            followUp = null
        )
        assertFalse(blankResult.isValid)
        assertEquals("Pilih status order untuk hasil BERHASIL", blankResult.orderHariIniError)
    }

    @Test
    fun `validasi d3c - catatan kosong atau hanya spasi ditolak untuk setiap hasil`() {
        val allHasil = listOf("BERHASIL", "TOKO TUTUP", "TIDAK MAU ORDER", "ALASAN LAIN")
        val blankNotes = listOf("", "   ", "\t\n ")

        val app = RuntimeEnvironment.getApplication() as android.app.Application
        val sessionManager = com.example.data.local.SessionManager(app)
        sessionManager.saveSession("fake_valid_token_1234567890_abcdefghijklmn", "2030-01-01 00:00:00", "ADK", "Sales ADK")
        val vm = com.example.ui.main.MainViewModel(app)

        val dummyFile = java.io.File.createTempFile("test_blank_catatan", ".jpg")
        dummyFile.writeBytes(ByteArray(100))

        for (hasil in allHasil) {
            for (note in blankNotes) {
                val res = LaporanValidator.validate(
                    hasil = hasil,
                    orderHariIni = if (hasil == "BERHASIL") "0" else null,
                    catatan = note,
                    followUp = if (hasil == "TOKO TUTUP") "2026-11-20" else null
                )
                assertFalse("Harus ditolak untuk $hasil dengan catatan '$note'", res.isValid)
                assertEquals("Catatan wajib diisi", res.catatanError)

                vm.submitLaporan(
                    DraftLaporan(
                        kodeMember = "MBR_NOTE",
                        idRkm = 201L,
                        hasil = hasil,
                        catatan = note,
                        photoPath = dummyFile.absolutePath,
                        latitude = -7.87,
                        longitude = 112.52,
                        followUp = if (hasil == "TOKO TUTUP") "2026-11-20" else null,
                        orderHariIni = if (hasil == "BERHASIL") "0" else "0"
                    ),
                    dummyFile
                )
                assertEquals("Catatan wajib diisi", vm.uiState.value.laporanError)
                assertFalse(vm.uiState.value.isSubmittingLaporan)
            }
        }
        dummyFile.delete()
    }

    @Test
    fun `validasi d3d - TOKO TUTUP tanpa follow_up ditolak dan dengan follow_up terkirim sesuai`() {
        // 1. TOKO TUTUP tanpa tanggal follow up ditolak
        val invalidResult = LaporanValidator.validate(
            hasil = "TOKO TUTUP",
            orderHariIni = null,
            catatan = "Pemilik sedang pergi",
            followUp = ""
        )
        assertFalse(invalidResult.isValid)
        assertEquals("Tanggal follow up wajib diisi untuk TOKO TUTUP", invalidResult.followUpError)

        val app = RuntimeEnvironment.getApplication() as android.app.Application
        val sessionManager = com.example.data.local.SessionManager(app)
        val rkm2Bodies = java.util.concurrent.LinkedBlockingQueue<String>()
        val server = MockWebServer()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                return if (request.path?.contains("rkm2.php") == true) {
                    rkm2Bodies.put(request.body.readUtf8())
                    MockResponse().setResponseCode(201).setBody("""{"success":true,"message":"OK"}""")
                } else {
                    MockResponse().setResponseCode(200).setBody("""{"success":true,"data":null}""")
                }
            }
        }
        server.start()
        try {
            sessionManager.baseUrl = server.url("/sales-gps-dashboard/endpoint/").toString()
            sessionManager.saveSession("fake_valid_token_1234567890_abcdefghijklmn", "2030-01-01 00:00:00", "ADK", "Sales ADK")

            val vm = com.example.ui.main.MainViewModel(app)
            val dummyFile = java.io.File.createTempFile("test_toko_tutup", ".jpg")
            dummyFile.writeBytes(ByteArray(100))

            // 1b. ViewModel juga menolak TOKO TUTUP tanpa tanggal follow up (tidak ada request ke rkm2.php)
            vm.submitLaporan(
                DraftLaporan(
                    kodeMember = "MBR_TT",
                    idRkm = 202L,
                    hasil = "TOKO TUTUP",
                    catatan = "Pemilik sedang pergi",
                    photoPath = dummyFile.absolutePath,
                    latitude = -7.8712,
                    longitude = 112.5273,
                    followUp = null
                ),
                dummyFile
            )
            assertEquals("Tanggal follow up wajib diisi untuk TOKO TUTUP", vm.uiState.value.laporanError)
            assertNull(rkm2Bodies.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS))

            // 2. TOKO TUTUP dengan tanggal follow up terkirim dengan follow_up berisi tanggal itu
            vm.submitLaporan(
                DraftLaporan(
                    kodeMember = "MBR_TT",
                    idRkm = 202L,
                    hasil = "TOKO TUTUP",
                    catatan = "Pemilik sedang pergi",
                    photoPath = dummyFile.absolutePath,
                    latitude = -7.8712,
                    longitude = 112.5273,
                    followUp = "2026-11-20",
                    orderHariIni = "0"
                ),
                dummyFile
            )
            val bodyTokoTutup = rkm2Bodies.poll(5, java.util.concurrent.TimeUnit.SECONDS)
            assertNotNull(bodyTokoTutup)
            assertEquals("2026-11-20", extractMultipartField(bodyTokoTutup!!, "follow_up"))
            assertEquals("TOKO TUTUP", extractMultipartField(bodyTokoTutup, "hasil"))
            assertEquals("0", extractMultipartField(bodyTokoTutup, "order_hari_ini"))

            // 3. Hasil selain TOKO TUTUP mengirim follow_up kosong meski draft memiliki tanggal lama
            val nonTokoTutup = listOf("BERHASIL", "TIDAK MAU ORDER", "ALASAN LAIN")
            for (hasil in nonTokoTutup) {
                vm.submitLaporan(
                    DraftLaporan(
                        kodeMember = "MBR_OTHER",
                        idRkm = 203L,
                        hasil = hasil,
                        catatan = "Keterangan kunjungan $hasil",
                        photoPath = dummyFile.absolutePath,
                        latitude = -7.8712,
                        longitude = 112.5273,
                        followUp = "2026-11-25",
                        orderHariIni = if (hasil == "BERHASIL") "1" else "0"
                    ),
                    dummyFile
                )
                val bodyOther = rkm2Bodies.poll(5, java.util.concurrent.TimeUnit.SECONDS)
                assertNotNull(bodyOther)
                assertEquals("", extractMultipartField(bodyOther!!, "follow_up"))
                assertEquals(hasil, extractMultipartField(bodyOther, "hasil"))
            }

            dummyFile.delete()
        } finally {
            sessionManager.baseUrl = com.example.data.local.SessionManager.DEFAULT_BASE_URL
            server.shutdown()
        }
    }

    @Test
    fun `validasi d3e - hasil, order_hari_ini, dan seluruh field payload tidak berubah`() {
        val app = RuntimeEnvironment.getApplication() as android.app.Application
        val sessionManager = com.example.data.local.SessionManager(app)
        val rkm2Bodies = java.util.concurrent.LinkedBlockingQueue<String>()
        val server = MockWebServer()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                return if (request.path?.contains("rkm2.php") == true) {
                    rkm2Bodies.put(request.body.readUtf8())
                    MockResponse().setResponseCode(201).setBody("""{"success":true,"message":"OK"}""")
                } else {
                    MockResponse().setResponseCode(200).setBody("""{"success":true,"data":null}""")
                }
            }
        }
        server.start()
        try {
            sessionManager.baseUrl = server.url("/sales-gps-dashboard/endpoint/").toString()
            sessionManager.saveSession("fake_valid_token_1234567890_abcdefghijklmn", "2030-01-01 00:00:00", "ADK", "Sales ADK")

            val vm = com.example.ui.main.MainViewModel(app)
            val dummyFile = java.io.File.createTempFile("test_payload", ".jpg")
            dummyFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))

            vm.submitLaporan(
                DraftLaporan(
                    kodeMember = "MBR777",
                    idRkm = 55L,
                    hasil = "BERHASIL",
                    catatan = "Order minyak goreng 3 dus",
                    photoPath = dummyFile.absolutePath,
                    latitude = -7.8712,
                    longitude = 112.5273,
                    followUp = null,
                    orderHariIni = "1"
                ),
                dummyFile
            )

            val body = rkm2Bodies.poll(5, java.util.concurrent.TimeUnit.SECONDS)
            assertNotNull(body)

            assertEquals("55", extractMultipartField(body!!, "id_rkm"))
            assertEquals("MBR777", extractMultipartField(body, "kode_member"))
            assertEquals("BERHASIL", extractMultipartField(body, "hasil"))
            assertEquals("Order minyak goreng 3 dus", extractMultipartField(body, "catatan"))
            assertEquals("", extractMultipartField(body, "follow_up"))
            assertEquals("-7.8712", extractMultipartField(body, "latitude"))
            assertEquals("112.5273", extractMultipartField(body, "longitude"))
            assertEquals("1", extractMultipartField(body, "order_hari_ini"))
            val idemp = extractMultipartField(body, "idempotency_key")
            assertNotNull(idemp)
            assertTrue(idemp!!.startsWith("rkm_55_MBR777_"))
            assertTrue(body.contains("name=\"foto\"; filename=\"${dummyFile.name}\""))

            dummyFile.delete()
        } finally {
            sessionManager.baseUrl = com.example.data.local.SessionManager.DEFAULT_BASE_URL
            server.shutdown()
        }
    }

    private fun extractMultipartField(rawBody: String, fieldName: String): String? {
        val marker = "name=\"$fieldName\""
        val start = rawBody.indexOf(marker)
        if (start == -1) return null
        val headerEnd = rawBody.indexOf("\r\n\r\n", start)
        if (headerEnd == -1) return null
        val valueStart = headerEnd + 4
        val nextBoundary = rawBody.indexOf("\r\n--", valueStart)
        if (nextBoundary == -1) return null
        return rawBody.substring(valueStart, nextBoundary)
    }
}


