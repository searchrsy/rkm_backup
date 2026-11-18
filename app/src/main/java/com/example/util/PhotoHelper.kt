package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object PhotoHelper {

    fun createTempImageUri(context: Context): Pair<Uri, File> {
        val photosDir = File(context.cacheDir, "photos")
        if (!photosDir.exists()) {
            photosDir.mkdirs()
        }
        val file = File(photosDir, "rkm_photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Pair(uri, file)
    }

    fun compressPhoto(sourceFile: File, context: Context): File {
        if (!sourceFile.exists()) return sourceFile

        // 1. Baca dimensi tanpa decode seluruh pixel ke RAM
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(sourceFile.absolutePath, boundsOptions)

        val maxDimension = 1280
        val longestSide = max(boundsOptions.outWidth, boundsOptions.outHeight)
        var sampleSize = 1
        if (longestSide > maxDimension) {
            sampleSize = longestSide / maxDimension
        }

        // 2. Decode dengan inSampleSize
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = max(1, sampleSize)
            inPreferredConfig = Bitmap.Config.RGB_565 // Menghemat 50% RAM dibanding ARGB_8888
        }

        val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
            ?: return sourceFile

        // 3. Simpan hasil kompresi ke file baru
        val compressedFile = File(context.cacheDir, "cmp_${sourceFile.name}")
        try {
            FileOutputStream(compressedFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }

        return compressedFile
    }
}
