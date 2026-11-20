package com.freepdf.manager.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.freepdf.manager.App
import java.io.File

/**
 * Helpers for sharing / opening files via FileProvider URIs.
 */
object FileShare {

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = uriFor(context, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    fun mimeTypeFor(file: File): String = when {
        file.name.lowercase().endsWith(".pdf") -> "application/pdf"
        file.name.lowercase().endsWith(".txt") -> "text/plain"
        else -> "image/*"
    }

    /** Resolves a content:// URI to a real file inside the app's storage. */
    fun copyUriToAppStorage(context: Context, uri: Uri, destDir: File, name: String): File? {
        return try {
            val dest = File(destDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest
        } catch (e: Exception) {
            null
        }
    }
}

/** Human-readable file size. */
fun Long.humanSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = this.toDouble()
    var i = 0
    while (size >= 1024 && i < units.lastIndex) {
        size /= 1024
        i++
    }
    return if (i == 0) "${this} B" else String.format("%.1f %s", size, units[i])
}
