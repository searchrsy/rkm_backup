package com.example.data.network

import com.example.config.AppConfig
import com.example.data.model.GpsPayload
import com.example.data.model.GpsResponse
import com.example.data.model.LoginRequest
import com.example.data.model.LoginConfigResponse
import com.example.data.model.LoginResponse
import com.example.data.model.LokasiTerakhirResponse
import com.example.data.model.RiwayatLokasiResponse
import com.example.data.model.RkmResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Url

interface RkmApiService {

    @GET(AppConfig.PATH_LOGIN_CONFIG)
    suspend fun getLoginConfig(
        @Header("X-API-Key") apiKey: String
    ): Response<LoginConfigResponse>

    @POST(AppConfig.PATH_LOGIN)
    @Headers("Content-Type: application/json")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @GET(AppConfig.PATH_RKM)
    suspend fun getRkm(
        @Header("Authorization") authHeader: String
    ): Response<RkmResponse>

    @Multipart
    @POST(AppConfig.PATH_LAPORAN)
    suspend fun submitLaporan(
        @Header("Authorization") authHeader: String,
        @Part("id_rkm") idRkm: RequestBody,
        @Part("kode_member") kodeMember: RequestBody,
        @Part("hasil") hasil: RequestBody,
        @Part("catatan") catatan: RequestBody,
        @Part("follow_up") followUp: RequestBody,
        @Part("latitude") latitude: RequestBody,
        @Part("longitude") longitude: RequestBody,
        @Part("idempotency_key") idempotencyKey: RequestBody,
        @Part("order_hari_ini") orderHariIni: RequestBody,
        @Part foto: MultipartBody.Part
    ): Response<ResponseBody>

    @POST
    @Headers("Content-Type: application/json")
    suspend fun sendGps(
        @Url url: String,
        @Header("X-API-Key") apiKey: String,
        @Body payload: GpsPayload
    ): Response<GpsResponse>

    @GET
    suspend fun getLokasiTerakhir(
        @Url url: String,
        @Header("Authorization") authHeader: String? = null,
        @Header("X-API-Key") apiKey: String? = null,
        @Query("kode_salesman") kodeSalesman: String? = null
    ): Response<LokasiTerakhirResponse>

    @GET
    suspend fun getRiwayatLokasi(
        @Url url: String,
        @Header("Authorization") authHeader: String? = null,
        @Header("X-API-Key") apiKey: String? = null,
        @Query("dari") dari: String? = null,
        @Query("limit") limit: Int? = 2000,
        @Query("kode_salesman") kodeSalesman: String? = null,
        @Query("device_id") deviceId: String? = null
    ): Response<RiwayatLokasiResponse>
}