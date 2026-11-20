package com.freepdf.manager.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global app constants: file naming, page sizes (in points), DPI presets,
 * scan filter ids, watermark positions and page-number positions.
 */
object Constants {

    const val APP_NAME = "Free PDF Manager"

    /** File name used for newly generated files, with the date stamp. */
    fun defaultFileName(prefix: String = "Document"): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "$prefix_$stamp.pdf"
    }

    /** Page sizes as width x height in PDF points (1 pt = 1/72 inch). */
    object PageSize {
        const val AUTO = "auto"
        const val A3 = "a3"
        const val A4 = "a4"
        const val A5 = "a5"
        const val B4 = "b4"
        const val B5 = "b5"
        const val LETTER = "letter"
        const val LEGAL = "legal"
        const val EXECUTIVE = "executive"
        const val BUSINESS_CARD = "business_card"

        /** Returns (width, height) in points, portrait orientation. */
        fun dimensions(id: String): Pair<Float, Float> = when (id) {
            A3 -> 841.89f to 1190.55f
            A4 -> 595.28f to 841.89f
            A5 -> 419.53f to 595.28f
            B4 -> 728.5f to 1031.81f
            B5 -> 513.78f to 728.5f
            LETTER -> 612f to 792f
            LEGAL -> 612f to 1008f
            EXECUTIVE -> 522f to 756f
            BUSINESS_CARD -> 172.77f to 95.25f
            else -> 595.28f to 841.89f // fallback A4
        }
    }

    /** Margin presets in points. */
    object Margin {
        const val NONE = 0f
        const val SMALL = 28.35f   // ~1 cm
        const val MEDIUM = 56.7f   // ~2 cm
        const val LARGE = 85.05f   // ~3 cm
    }

    /** Compression presets: target DPI for image re-encoding. */
    object Compress {
        const val LOW = 72
        const val MEDIUM = 150
        const val MAX = 300
    }

    /** Scan filter identifiers. */
    object ScanFilter {
        const val ORIGINAL = "original"
        const val B_W = "bw"
        const val B_W_ENHANCED = "bw2"
        const val GRAY = "gray"
        const val INVERT = "invert"
        const val ENHANCE = "enhance"
        const val SUPER = "super"
        const val SUPER2 = "super2"
    }

    /** Watermark placement. */
    object Watermark {
        const val CENTER = "center"
        const val DIAGONAL = "diagonal"
        const val TILE = "tile"
    }

    /** Page-number placement. */
    object PageNumber {
        const val BOTTOM_LEFT = "bl"
        const val BOTTOM_CENTER = "bc"
        const val BOTTOM_RIGHT = "br"
        const val TOP_LEFT = "tl"
        const val TOP_CENTER = "tc"
        const val TOP_RIGHT = "tr"
    }

    /** File kinds stored in the local database. */
    object FileKind {
        const val PDF = "pdf"
        const val IMAGE = "image"
        const val FOLDER = "folder"
    }

    /** File utilities. */
    object Files {
        fun isPdf(name: String): Boolean = name.lowercase(Locale.US).endsWith(".pdf")
        fun isImage(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            return ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "tiff", "gif")
        }
        fun extOf(file: File): String = file.extension.lowercase(Locale.US)
    }
}
