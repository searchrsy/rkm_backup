package com.example.update

import com.example.config.AppConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.File

@JsonClass(generateAdapter = true)
data class UpdateCheckResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "updateEnabled") val updateEnabled: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: UpdateData? = null
)

@JsonClass(generateAdapter = true)
data class UpdateData(
    @Json(name = "versionCode") val versionCode: Long = 0L,
    @Json(name = "versionName") val versionName: String? = null,
    @Json(name = "apkUrl") val apkUrl: String? = null,
    @Json(name = "sha256") val sha256: String? = null,
    @Json(name = "size") val size: Long = 0L,
    @Json(name = "changelog") val changelog: String? = null,
    @Json(name = "forceUpdate") val forceUpdate: Boolean = false
) {
    /** Wajib dipasang: dialog tidak bisa ditutup. Diatur AppConfig.UPDATE_WAJIB atau forceUpdate dari server. */
    val isMandatory: Boolean
        get() = AppConfig.UPDATE_WAJIB || forceUpdate

    val sizeInMbFormatted: String
        get() {
            if (size <= 0) return "0 MB"
            val mb = size.toDouble() / (1024.0 * 1024.0)
            return String.format(java.util.Locale.US, "%.1f MB", mb)
        }
}

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()

    data class Available(
        val data: UpdateData,
        val isMetered: Boolean,
        val showMeteredWarning: Boolean = false
    ) : UpdateState()

    data class Downloading(
        val data: UpdateData,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progress: Float
    ) : UpdateState()

    data class Verifying(val data: UpdateData) : UpdateState()

    data class ReadyToInstall(
        val data: UpdateData,
        val apkFile: File
    ) : UpdateState()

    data class Installing(
        val data: UpdateData,
        val apkFile: File
    ) : UpdateState()

    data class Failed(
        val data: UpdateData?,
        val reason: String,
        val canRetry: Boolean = true
    ) : UpdateState()
}

/** Data update dari state mana pun yang membawa data (null untuk Idle/Checking). */
fun UpdateState.dataOrNull(): UpdateData? = when (this) {
    is UpdateState.Available -> data
    is UpdateState.Downloading -> data
    is UpdateState.Verifying -> data
    is UpdateState.ReadyToInstall -> data
    is UpdateState.Installing -> data
    is UpdateState.Failed -> data
    else -> null
}