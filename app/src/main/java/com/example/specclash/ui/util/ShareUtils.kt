package com.example.specclash.ui.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Turns a Compose-captured [ImageBitmap] into a shareable PNG.
 *
 * The bitmap is written to `context.cacheDir/shares/verdict.png` and handed
 * off through the app's `FileProvider` (declared in AndroidManifest.xml,
 * authority `${applicationId}.fileprovider`) so third-party apps in the
 * share sheet can read it without a world-readable file: URI.
 */
object ShareUtils {

    private const val SHARE_DIR_NAME = "shares"
    private const val SHARE_FILE_NAME = "verdict.png"

    /** Saves [imageBitmap] as a PNG and launches the ACTION_SEND chooser. */
    suspend fun shareBitmap(
        context: Context,
        imageBitmap: ImageBitmap,
        chooserTitle: String = "Share verdict",
    ) {
        val file = withContext(Dispatchers.IO) { saveToCache(context, imageBitmap) }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle)
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun saveToCache(context: Context, imageBitmap: ImageBitmap): File {
        val bitmap: Bitmap = imageBitmap.asAndroidBitmap()
        val dir = File(context.cacheDir, SHARE_DIR_NAME).apply { mkdirs() }
        val file = File(dir, SHARE_FILE_NAME)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }
}
