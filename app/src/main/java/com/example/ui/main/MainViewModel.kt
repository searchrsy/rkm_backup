package com.example.ui.main

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.config.AppConfig
import com.example.data.local.DraftRepository
import com.example.data.local.SessionManager
import com.example.data.model.DraftLaporan
import com.example.data.model.GpsPayload
import com.example.data.model.LoginConfigResponse
import com.example.data.model.LoginRequest
import com.example.data.model.LokasiSalesItem
import com.example.data.model.MemberItem
import com.example.data.model.RiwayatGroupItem
import com.example.data.model.SalesmanOption
import com.example.data.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable
data class MainUiState(
    val isLoggedIn: Boolean = false,
    val kodeSalesman: String = "",
    val namaSalesman: String = "",
    val token: String? = null,
    val baseUrl: String = "",
    val loginLoading: Boolean = false,
    val loginError: String? = null,
    val cabangLabel: String = "",
    val loginSubtitle: String = "",
    val salesmen: List<SalesmanOption> = emptyList(),
    val loginConfigLoading: Boolean = false,
    val loginConfigError: String? = null,
    val rkmLoading: Boolean = false,
    val rkmError: String? = null,
    val tanggal: String? = null,
    val idRkm: Long? = null,
    val members: List<MemberItem> = emptyList(),
    val selectedMember: MemberItem? = null,
    val salesmanLocation: Pair<Double, Double>? = null,
    val lastKnownLocations: List<LokasiSalesItem> = emptyList(),
    val historyTracks: List<RiwayatGroupItem> = emptyList(),
    val isGpsSending: Boolean = false,
    val activeLaporanMember: MemberItem? = null,
    val isSubmittingLaporan: Boolean = false,
    val laporanError: String? = null,
    val showServerConfig: Boolean = false,
    val showLogoutConfirm: Boolean = false,
    val permissionBannerMessage: String? = null
)

class MainViewModel(
    application: Application,
    private val updateManager: com.example.update.UpdateManager = com.example.update.UpdateManager(application)
) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)
    private val draftRepository = DraftRepository(application)

    val hasRequestedInitialPermissions: Boolean
        get() = sessionManager.hasRequestedInitialPermissions

    fun setInitialPermissionsRequested() {
        sessionManager.hasRequestedInitialPermissions = true
    }

    private val isFetchingLokasi = java.util.concurrent.atomic.AtomicBoolean(false)
    private val isFetchingRiwayat = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _uiState = MutableStateFlow(
        MainUiState(
            cabangLabel = sessionManager.getCachedLoginConfig()?.cabangLabel.orEmpty(),
            loginSubtitle = sessionManager.getCachedLoginConfig()?.subtitle.orEmpty(),
            salesmen = sessionManager.getCachedLoginConfig()?.salesmen.orEmpty(),
            isLoggedIn = sessionManager.isLoggedIn,
            kodeSalesman = sessionManager.kodeSalesman ?: "",
            namaSalesman = sessionManager.namaSalesman ?: "",
            token = sessionManager.token,
            baseUrl = sessionManager.baseUrl
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            restoreCachedLocations()
        }
        if (sessionManager.isLoggedIn) {
            loadRkm()
            fetchLokasiTerakhir()
            fetchRiwayatLokasi()
        }
    }

    /** Ambil label cabang + daftar salesman aktif dari server (login_config.php). */
    fun loadLoginConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(loginConfigLoading = true, loginConfigError = null) }
            try {
                val api = withContext(Dispatchers.IO) { ApiClient.createService(sessionManager) }
                val response = api.getLoginConfig(AppConfig.API_KEY)
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    sessionManager.saveCachedLoginConfig(body)
                    _uiState.update {
                        it.copy(
                            cabangLabel = body.cabangLabel.orEmpty(),
                            loginSubtitle = body.subtitle.orEmpty(),
                            salesmen = body.salesmen.orEmpty().filter { s -> s.kodeSalesman.isNotBlank() },
                            loginConfigLoading = false,
                            loginConfigError = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            loginConfigLoading = false,
                            loginConfigError = "Gagal memuat daftar salesman (HTTP ${response.code()})."
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loginConfigLoading = false,
                        loginConfigError = "Koneksi gagal: ${e.localizedMessage ?: "periksa jaringan/server"}"
                    )
                }
            }
        }
    }

    fun updateBaseUrl(newUrl: String) {
        sessionManager.baseUrl = newUrl
        _uiState.update { it.copy(baseUrl = sessionManager.baseUrl) }
    }

    fun openServerConfig(show: Boolean) {
        _uiState.update { it.copy(showServerConfig = show) }
    }

    fun openLogoutConfirm(show: Boolean) {
        _uiState.update { it.copy(showLogoutConfirm = show) }
    }

    fun setPermissionBannerMessage(msg: String?) {
        _uiState.update { it.copy(permissionBannerMessage = msg) }
    }

    fun login(kodeSalesman: String, pass: String) {
        val cleanCode = kodeSalesman.trim().uppercase()
        val cleanPass = pass.trim()

        if (cleanCode.isBlank() || cleanPass.isBlank()) {
            _uiState.update { it.copy(loginError = "Kode MR dan password tidak boleh kosong.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loginLoading = true, loginError = null) }
            try {
                val api = ApiClient.createService(sessionManager)
                val response = api.login(LoginRequest(cleanCode, cleanPass))

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success && !body.token.isNullOrBlank()) {
                        val salesName = body.data?.namaSalesman ?: cleanCode
                        sessionManager.saveSession(
                            token = body.token,
                            expiresAt = body.expiresAt,
                            kodeSalesman = cleanCode,
                            namaSalesman = salesName
                        )
                        _uiState.update {
                            it.copy(
                                isLoggedIn = true,
                                token = body.token,
                                kodeSalesman = cleanCode,
                                namaSalesman = salesName,
                                loginLoading = false,
                                loginError = null
                            )
                        }
                        loadRkm()
                        fetchLokasiTerakhir()
                        fetchRiwayatLokasi()
                        updateManager.checkUpdateOnLogin()
                    } else {
                        val msg = body?.message ?: "Login gagal. Periksa kode dan password."
                        _uiState.update { it.copy(loginLoading = false, loginError = msg) }
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val msg = parseErrorMessage(errorBody, "Gagal login (HTTP ${response.code()})")
                    _uiState.update { it.copy(loginLoading = false, loginError = msg) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loginLoading = false,
                        loginError = "Koneksi gagal: ${e.localizedMessage ?: "Periksa jaringan/server"}"
                    )
                }
            }
        }
    }

    private fun restoreCachedLocations() {
        try {
            val cachedLokasiJson = sessionManager.getCachedLokasi()
            if (!cachedLokasiJson.isNullOrBlank()) {
                val list = ApiClient.lokasiListAdapter.fromJson(cachedLokasiJson)
                if (!list.isNullOrEmpty()) {
                    _uiState.update { current ->
                        if (current.lastKnownLocations.isEmpty()) {
                            current.copy(lastKnownLocations = list)
                        } else current
                    }
                }
            }

            val cachedRiwayatJson = sessionManager.getCachedRiwayat()
            if (!cachedRiwayatJson.isNullOrBlank()) {
                val groups = ApiClient.riwayatGroupListAdapter.fromJson(cachedRiwayatJson)
                if (!groups.isNullOrEmpty()) {
                    _uiState.update { current ->
                        if (current.historyTracks.isEmpty()) {
                            current.copy(historyTracks = groups)
                        } else current
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RKM_GPS", "Gagal memulihkan cache lokasi offline: ${e.message}")
        }
    }

    fun fetchLokasiTerakhir() {
        if (!isFetchingLokasi.compareAndSet(false, true)) {
            if (BuildConfig.DEBUG) {
                Log.d("RKM_GPS", "Pengecekan lokasi terakhir masih berjalan, request baru dilewati untuk mencegah penumpukan.")
            }
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val api = ApiClient.createService(sessionManager)
                    val primaryUrl = sessionManager.dashboardApiBaseUrl + AppConfig.PATH_LOKASI_TERAKHIR
                    val token = sessionManager.token
                    val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else null
                    val apiKey = sessionManager.gpsApiKey
                    val salesCode = sessionManager.kodeSalesman

                    var response = api.getLokasiTerakhir(primaryUrl, authHeader, apiKey, salesCode)
                    if (!response.isSuccessful && response.code() == 404) {
                        val fallbackUrl = sessionManager.rootApiBaseUrl + AppConfig.PATH_LOKASI_TERAKHIR
                        if (fallbackUrl != primaryUrl) {
                            response = api.getLokasiTerakhir(fallbackUrl, authHeader, apiKey, salesCode)
                        }
                    }

                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null && body.success) {
                            val validList = body.data.orEmpty().filter { it.isValidCoordinate }
                            if (validList != _uiState.value.lastKnownLocations) {
                                // Simpan ke SharedPreferences agar tetap muncul saat offline
                                try {
                                    sessionManager.saveCachedLokasi(ApiClient.lokasiListAdapter.toJson(validList))
                                } catch (_: Exception) {}

                                _uiState.update { current ->
                                    current.copy(lastKnownLocations = validList)
                                }
                            }
                            if (BuildConfig.DEBUG) {
                                Log.d("RKM_GPS", "Lokasi terakhir sukses diparsing: ${validList.size} marker")
                            }
                        } else {
                            Log.w("RKM_GPS", "Lokasi terakhir gagal diproses: ${body?.message}")
                        }
                    } else if (response.code() == 401) {
                        Log.w("RKM_GPS", "Endpoint lokasi_terakhir.php mengembalikan HTTP 401 Unauthorized. Mempertahankan cache lokasi offline tanpa logout/menghapus sesi.")
                    } else {
                        Log.w("RKM_GPS", "HTTP error lokasi_terakhir.php: ${response.code()}")
                        // Aturan: JANGAN menghapus marker dan data lama jika request gagal
                    }
                }
            } catch (e: Exception) {
                Log.e("RKM_GPS", "Koneksi error lokasi_terakhir: ${e.message}")
                // Aturan: JANGAN menghapus marker dan data lama jika offline/koneksi terputus
            } finally {
                isFetchingLokasi.set(false)
            }
        }
    }

    fun fetchRiwayatLokasi(dateStr: String? = null) {
        if (!isFetchingRiwayat.compareAndSet(false, true)) {
            if (BuildConfig.DEBUG) {
                Log.d("RKM_GPS", "Pengecekan riwayat lokasi masih berjalan, request baru dilewati untuk mencegah penumpukan.")
            }
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val api = ApiClient.createService(sessionManager)
                    val primaryUrl = sessionManager.dashboardApiBaseUrl + AppConfig.PATH_RIWAYAT_LOKASI
                    val token = sessionManager.token
                    val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else null
                    val apiKey = sessionManager.gpsApiKey
                    val filterDate = dateStr ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val salesCode = sessionManager.kodeSalesman
                    val devId = sessionManager.deviceId

                    var response = api.getRiwayatLokasi(primaryUrl, authHeader, apiKey, dari = filterDate, kodeSalesman = salesCode, deviceId = devId)
                    if (!response.isSuccessful && response.code() == 404) {
                        val fallbackUrl = sessionManager.rootApiBaseUrl + AppConfig.PATH_RIWAYAT_LOKASI
                        if (fallbackUrl != primaryUrl) {
                            response = api.getRiwayatLokasi(fallbackUrl, authHeader, apiKey, dari = filterDate, kodeSalesman = salesCode, deviceId = devId)
                        }
                    }

                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null && body.success) {
                            val validGroups = body.data.orEmpty().mapNotNull { g ->
                                val validPoints = g.titik.orEmpty().filter { it.isValidCoordinate }
                                if (validPoints.isNotEmpty()) g.copy(titik = validPoints) else null
                            }
                            if (validGroups != _uiState.value.historyTracks) {
                                // Simpan ke cache lokal offline
                                try {
                                    sessionManager.saveCachedRiwayat(ApiClient.riwayatGroupListAdapter.toJson(validGroups))
                                } catch (_: Exception) {}

                                _uiState.update { current ->
                                    current.copy(historyTracks = validGroups)
                                }
                            }
                            if (BuildConfig.DEBUG) {
                                Log.d("RKM_GPS", "Riwayat lokasi sukses diparsing: ${validGroups.size} rute")
                            }
                        } else {
                            Log.w("RKM_GPS", "Riwayat lokasi gagal: ${body?.message}")
                        }
                    } else if (response.code() == 401) {
                        Log.w("RKM_GPS", "Endpoint riwayat_lokasi.php mengembalikan HTTP 401 Unauthorized. Mempertahankan cache riwayat offline tanpa logout/menghapus sesi.")
                    } else {
                        Log.w("RKM_GPS", "HTTP error riwayat_lokasi.php: ${response.code()}")
                        // Aturan: JANGAN menghapus data riwayat lama saat request gagal
                    }
                }
            } catch (e: Exception) {
                Log.e("RKM_GPS", "Koneksi error riwayat_lokasi: ${e.message}")
                // Aturan: JANGAN menghapus data riwayat lama saat offline
            } finally {
                isFetchingRiwayat.set(false)
            }
        }
    }

    fun sendGpsLocation(lat: Double, lon: Double, accuracy: Float? = null, speed: Float? = null) {
        // Validasi latitude & longitude: null/rentang sah
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0 || (lat == 0.0 && lon == 0.0)) {
            Log.w("RKM_GPS", "Koordinat GPS tidak valid: lat=$lat, lon=$lon")
            return
        }

        // Tampilkan lokasi real-time secara instan pada peta
        val newPoint = Pair(lat, lon)
        if (_uiState.value.salesmanLocation != newPoint) {
            _uiState.update { it.copy(salesmanLocation = newPoint) }
        }

        val actualSalesCode = (sessionManager.kodeSalesman ?: _uiState.value.kodeSalesman).trim().uppercase()
        if (actualSalesCode.isBlank()) {
            Log.w("RKM_GPS", "Kode salesman kosong (belum login). Pengiriman GPS dibatalkan.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGpsSending = true) }
            try {
                withContext(Dispatchers.IO) {
                    val currentTimestamp = System.currentTimeMillis()
                    val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date(currentTimestamp))
                    val payload = GpsPayload(
                        kodeSalesman = actualSalesCode,
                        kodeSales = actualSalesCode,
                        deviceId = sessionManager.deviceId,
                        latitude = lat,
                        longitude = lon,
                        akurasi = accuracy,
                        kecepatan = speed,
                        timestamp = currentTimestamp,
                        waktu = nowIso,
                        waktuGps = nowIso
                    )

                    val api = ApiClient.createService(sessionManager)
                    val gpsUrl = sessionManager.gpsEndpointUrl
                    val apiKey = sessionManager.gpsApiKey

                    val response = api.sendGps(gpsUrl, apiKey, payload)
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body?.success == true) {
                            if (BuildConfig.DEBUG) {
                                Log.d("RKM_GPS", "Server menerima GPS: ${body.message}")
                            }
                        } else {
                            Log.e(
                                "RKM_GPS",
                                "Server menolak GPS: HTTP ${response.code()}, body=${body?.message}"
                            )
                        }
                    } else {
                        val errString = response.errorBody()?.string() ?: ""
                        Log.e("RKM_GPS", "Kirim GPS gagal HTTP ${response.code()}: $errString")
                    }
                }
            } catch (e: Exception) {
                Log.e("RKM_GPS", "Kirim GPS error koneksi: ${e.message}")
            } finally {
                _uiState.update { it.copy(isGpsSending = false) }
                // Perbarui lokasi terakhir dan riwayat dari server setelah pengiriman
                fetchLokasiTerakhir()
                fetchRiwayatLokasi()
            }
        }
    }

    fun loadRkm() {
        val token = sessionManager.token
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(isLoggedIn = false, rkmError = "Sesi telah berakhir.") }
            return
        }

        // Juga muat lokasi terakhir dan riwayat saat memuat RKM
        fetchLokasiTerakhir()
        fetchRiwayatLokasi()

        viewModelScope.launch {
            _uiState.update { it.copy(rkmLoading = true, rkmError = null) }
            try {
                withContext(Dispatchers.IO) {
                    val api = ApiClient.createService(sessionManager)
                    val response = api.getRkm("Bearer $token")

                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null && body.success) {
                            val payload = body.data
                            val idRkmActual = payload?.rencana?.id
                            val rawKunjungan = payload?.kunjungan ?: emptyList()
                            val newCodes = rawKunjungan.map { it.kodeMember }.toSet()

                            _uiState.update { current ->
                                // rkm1.php memfilter member yang sudah tersimpan di rencana_rkm.
                                // Pertahankan member yang sudah selesai dikunjungi di sesi ini agar salesman tetap melihat riwayat.
                                val currentFinished = current.members.filter { it.isFinished }
                                val preserved = currentFinished.filter { it.kodeMember !in newCodes }
                                val merged = (rawKunjungan + preserved).sortedBy { it.urutan ?: 999 }

                                current.copy(
                                    rkmLoading = false,
                                    rkmError = null,
                                    tanggal = payload?.tanggal,
                                    idRkm = idRkmActual,
                                    members = merged
                                )
                            }
                        } else {
                            val msg = body?.message ?: "Gagal memproses data rencana kunjungan."
                            _uiState.update { it.copy(rkmLoading = false, rkmError = msg) }
                        }
                    } else if (response.code() == 401 || response.code() == 403) {
                        sessionManager.clearSession()
                        _uiState.update {
                            it.copy(
                                isLoggedIn = false,
                                rkmLoading = false,
                                rkmError = "Token sesi kedaluwarsa. Silakan masuk kembali."
                            )
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        val msg = parseErrorMessage(errorBody, "Gagal memuat RKM (HTTP ${response.code()})")
                        _uiState.update { it.copy(rkmLoading = false, rkmError = msg) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        rkmLoading = false,
                        rkmError = "Koneksi error: ${e.localizedMessage ?: "Periksa koneksi internet"}"
                    )
                }
            }
        }
    }

    fun selectMember(member: MemberItem?) {
        _uiState.update { it.copy(selectedMember = member) }
    }

    fun setSalesmanLocation(lat: Double, lon: Double) {
        sendGpsLocation(lat, lon)
    }

    fun openLaporan(member: MemberItem?) {
        _uiState.update {
            it.copy(
                activeLaporanMember = member,
                laporanError = null
            )
        }
    }

    fun saveDraft(draft: DraftLaporan) {
        draftRepository.saveDraft(draft)
    }

    fun getDraft(kodeMember: String): DraftLaporan? {
        return draftRepository.getDraft(kodeMember)
    }

    fun submitLaporan(draft: DraftLaporan, photoFile: File) {
        val token = sessionManager.token
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(laporanError = "Sesi tidak valid. Silakan login kembali.") }
            return
        }

        // 1. Validasi ID Rencana RKM dari data.rencana.id
        if (draft.idRkm <= 0) {
            _uiState.update { it.copy(laporanError = "id_rkm tidak valid. Pastikan ada rencana kunjungan aktif.") }
            return
        }

        // 2. Validasi kode member
        if (draft.kodeMember.isBlank() || draft.kodeMember.length > 50) {
            _uiState.update { it.copy(laporanError = "kode_member tidak valid.") }
            return
        }

        // 3. Validasi hasil kunjungan, sub-pilihan BERHASIL, catatan wajib, dan follow up TOKO TUTUP
        val validation = com.example.ui.components.LaporanValidator.validate(
            hasil = draft.hasil,
            orderHariIni = draft.orderHariIni,
            catatan = draft.catatan,
            followUp = draft.followUp
        )
        if (!validation.isValid) {
            _uiState.update { it.copy(laporanError = validation.firstError) }
            return
        }

        // 5. Validasi koordinat GPS
        val lat = draft.latitude
        val lon = draft.longitude
        if (lat == null || lon == null) {
            _uiState.update { it.copy(laporanError = "Koordinat GPS wajib diisi.") }
            return
        }
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            _uiState.update { it.copy(laporanError = "Koordinat GPS di luar rentang yang sah.") }
            return
        }

        // 6. Validasi file foto
        if (!photoFile.exists() || photoFile.length() < 1) {
            _uiState.update { it.copy(laporanError = "Foto kunjungan wajib diunggah.") }
            return
        }
        if (photoFile.length() > 5 * 1024 * 1024) {
            _uiState.update { it.copy(laporanError = "Foto tidak valid atau melebihi batas 5 MB.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingLaporan = true, laporanError = null) }
            try {
                withContext(Dispatchers.IO) {
                    val api = ApiClient.createService(sessionManager)

                    val idRkmBody = draft.idRkm.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val kodeMemberBody = draft.kodeMember.toRequestBody("text/plain".toMediaTypeOrNull())
                    val hasilBody = draft.hasil.toRequestBody("text/plain".toMediaTypeOrNull())
                    val catatanBody = draft.catatan.toRequestBody("text/plain".toMediaTypeOrNull())
                    val followUpVal = if (draft.hasil == "TOKO TUTUP") (draft.followUp?.trim() ?: "") else ""
                    val followUpBody = followUpVal.toRequestBody("text/plain".toMediaTypeOrNull())
                    val latBody = lat.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val lonBody = lon.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val orderVal = if (draft.hasil == "BERHASIL") (draft.orderHariIni ?: "0") else "0"
                    val orderBody = orderVal.toRequestBody("text/plain".toMediaTypeOrNull())

                    // Idempotency key harus 16-100 karakter: ^[A-Za-z0-9._:-]{16,100}$
                    val idempotencyKey = "rkm_${draft.idRkm}_${draft.kodeMember}_${System.currentTimeMillis()}"
                    val keyBody = idempotencyKey.toRequestBody("text/plain".toMediaTypeOrNull())

                    val requestFile = photoFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val photoPart = MultipartBody.Part.createFormData("foto", photoFile.name, requestFile)

                    val response = api.submitLaporan(
                        authHeader = "Bearer $token",
                        idRkm = idRkmBody,
                        kodeMember = kodeMemberBody,
                        hasil = hasilBody,
                        catatan = catatanBody,
                        followUp = followUpBody,
                        latitude = latBody,
                        longitude = lonBody,
                        idempotencyKey = keyBody,
                        orderHariIni = orderBody,
                        foto = photoPart
                    )

                    if (response.isSuccessful) {
                        val bodyString = response.body()?.string() ?: ""
                        val isSuccess = parseUploadSuccess(bodyString)

                        if (isSuccess) {
                            // HANYA hapus draft setelah server MENGONFIRMASI sukses
                            draftRepository.removeDraft(draft.kodeMember)

                            // Tandai member selesai dikunjungi pada daftar lokal dan marker
                            _uiState.update { current ->
                                val updatedMembers = current.members.map { m ->
                                    if (m.kodeMember == draft.kodeMember) {
                                        m.copy(status = "SELESAI", statusKunjungan = "SELESAI")
                                    } else m
                                }
                                current.copy(
                                    members = updatedMembers,
                                    activeLaporanMember = null,
                                    isSubmittingLaporan = false,
                                    laporanError = null
                                )
                            }
                        } else {
                            // Server mengembalikan HTTP 200/201 tapi JSON success == false
                            val errMsg = parseErrorMessage(bodyString, "Laporan tidak dapat diverifikasi server.")
                            // PERTAHANKAN DRAFT DAN FOTO
                            _uiState.update { it.copy(isSubmittingLaporan = false, laporanError = errMsg) }
                        }
                    } else if (response.code() == 401 || response.code() == 403) {
                        val errString = response.errorBody()?.string()
                        val errMsg = parseErrorMessage(errString, "Akses ditolak (HTTP ${response.code()})")
                        if (response.code() == 401) {
                            sessionManager.clearSession()
                            _uiState.update {
                                it.copy(
                                    isLoggedIn = false,
                                    isSubmittingLaporan = false,
                                    laporanError = "Token sesi kedaluwarsa. Silakan login kembali."
                                )
                            }
                        } else {
                            // PERTAHANKAN DRAFT DAN FOTO
                            _uiState.update { it.copy(isSubmittingLaporan = false, laporanError = errMsg) }
                        }
                    } else {
                        val errString = response.errorBody()?.string()
                        val errMsg = parseErrorMessage(errString, "Gagal upload laporan (HTTP ${response.code()})")
                        // PERTAHANKAN DRAFT DAN FOTO
                        _uiState.update { it.copy(isSubmittingLaporan = false, laporanError = errMsg) }
                    }
                }
            } catch (e: Exception) {
                // Jaringan terputus atau timeout: PERTAHANKAN DRAFT DAN FOTO
                _uiState.update {
                    it.copy(
                        isSubmittingLaporan = false,
                        laporanError = "Koneksi terputus: ${e.localizedMessage ?: "Gagal terhubung ke server"}. Draft aman tersimpan."
                    )
                }
            }
        }
    }

    private fun parseUploadSuccess(body: String): Boolean {
        return try {
            val json = JSONObject(body)
            json.optBoolean("success", false)
        } catch (_: Exception) {
            body.contains("\"success\":true")
        }
    }

    private fun parseErrorMessage(errorBody: String?, fallback: String): String {
        if (errorBody.isNullOrBlank()) return fallback
        // Tangani jika response berupa JSON
        try {
            val json = JSONObject(errorBody)
            val msg = json.optString("message", "")
            if (msg.isNotBlank()) return msg
        } catch (_: Exception) {}

        // Tangani jika response berupa error PHP HTML
        if (errorBody.contains("Undefined variable \$allowed") || errorBody.contains("in_array(): Argument #2")) {
            return "Server backend error (rkm2.php baris 36): Variabel \$allowed belum didefinisikan pada backend PHP."
        }
        if (errorBody.contains("Fatal error", ignoreCase = true)) {
            return "Server backend mengalami kendala fatal PHP saat memproses request."
        }

        if (errorBody.length < 150 && !errorBody.contains("<html", ignoreCase = true)) {
            return errorBody
        }
        return fallback
    }

    fun logout() {
        sessionManager.clearSession()
        _uiState.update {
            it.copy(
                isLoggedIn = false,
                token = null,
                kodeSalesman = "",
                namaSalesman = "",
                members = emptyList(),
                selectedMember = null,
                showLogoutConfirm = false
            )
        }
    }
}