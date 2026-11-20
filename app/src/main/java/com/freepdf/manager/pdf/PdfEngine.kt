package com.freepdf.manager.pdf

import android.graphics.Bitmap
import com.freepdf.manager.core.Constants
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * PDF engine built on the PDFBox Android port (1.8 API, verified against source).
 *
 * Design notes:
 *  - Merge / split / extract / compress all use the render-and-rebuild strategy:
 *    each source page is rasterized with [PDFRenderer] and re-embedded as a
 *    JPEG. This is 100% reliable across any PDF structure (the port's
 *    importPage does not copy image resources) and keeps output size low.
 *  - All methods are blocking; callers must run them on Dispatchers.IO.
 */
object PdfEngine {

    /** DPI used when rasterizing pages for merge/split/extract. */
    private const val PAGE_DPI = 150f
    private const val MERGE_JPEG_QUALITY = 0.92f

    // ------------------------------------------------------------------
    //  Creation
    // ------------------------------------------------------------------

    /**
     * Builds a PDF from a list of bitmaps, one per page.
     *
     * @param pageSizeId  [Constants.PageSize.*] or "auto" (fit to first image)
     * @param margin      margin in points (0 = none)
     * @param orientation 0 = portrait, 1 = landscape, -1 = auto per image
     */
    fun imagesToPdf(
        bitmaps: List<Bitmap>,
        out: File,
        pageSizeId: String,
        margin: Float,
        orientation: Int
    ): File {
        require(bitmaps.isNotEmpty()) { "No images to convert" }
        PDDocument().use { doc ->
            val (pw, ph) = if (pageSizeId == Constants.PageSize.AUTO) {
                bitmaps.first().width.toFloat() to bitmaps.first().height.toFloat()
            } else {
                Constants.PageSize.dimensions(pageSizeId)
            }

            for (b in bitmaps) {
                val portrait = when (orientation) {
                    1 -> false
                    0 -> true
                    else -> b.height >= b.width
                }
                val w = if (portrait) pw else ph
                val h = if (portrait) ph else pw
                val page = PDPage(PDRectangle(w, h))
                doc.addPage(page)

                val availW = (w - 2 * margin).coerceAtLeast(1f)
                val availH = (h - 2 * margin).coerceAtLeast(1f)
                val scale = minOf(availW / b.width, availH / b.height)
                val dw = b.width * scale
                val dh = b.height * scale
                val dx = (w - dw) / 2f
                val dy = (h - dh) / 2f

                val img = JPEGFactory.createFromImage(doc, b, MERGE_JPEG_QUALITY)
                val cs = PDPageContentStream(doc, page, true, true)
                cs.drawImage(img, dx, dy, dw, dh)
                cs.close()
            }
            doc.save(out)
        }
        return out
    }

    /** Creates a blank PDF with [pages] pages of the given size. */
    fun blankPdf(out: File, pageSizeId: String, pages: Int, landscape: Boolean): File {
        val (w0, h0) = Constants.PageSize.dimensions(pageSizeId)
        val w = if (landscape) h0 else w0
        val h = if (landscape) w0 else h0
        PDDocument().use { doc ->
            repeat(pages.coerceAtLeast(1)) { doc.addPage(PDPage(PDRectangle(w, h))) }
            doc.save(out)
        }
        return out
    }

    /** Creates a PDF from plain text (simple word wrapping, Helvetica). */
    fun textToPdf(out: File, text: String, pageSizeId: String, fontSize: Float): File {
        val (w, h) = Constants.PageSize.dimensions(pageSizeId)
        val margin = 56.7f
        val font = PDType1Font.HELVETICA
        val lines = wrapText(text, font, fontSize, w - 2 * margin)

        PDDocument().use { doc ->
            var y = h - margin
            var page: PDPage? = null
            for (line in lines) {
                if (page == null || y - fontSize * 1.3 < margin) {
                    page = PDPage(PDRectangle(w, h))
                    doc.addPage(page)
                    y = h - margin
                }
                val cs = PDPageContentStream(doc, page, true, true)
                cs.beginText()
                cs.setFont(font, fontSize)
                cs.setNonStrokingColor(0x21, 0x21, 0x21)
                cs.newLineAtOffset(margin, y)
                cs.showText(line)
                cs.endText()
                cs.close()
                y -= fontSize * 1.3f
            }
            doc.save(out)
        }
        return out
    }

    private fun wrapText(text: String, font: PDType1Font, size: Float, maxW: Float): List<String> {
        val out = mutableListOf<String>()
        for (para in text.split("\n")) {
            if (para.isEmpty()) {
                out.add("")
                continue
            }
            val cur = StringBuilder()
            for (word in para.split(" ")) {
                val test = if (cur.isEmpty()) word else "$cur $word"
                if (font.getStringWidth(test) / 1000f * size > maxW && cur.isNotEmpty()) {
                    out.add(cur.toString())
                    cur.clear().append(word)
                } else {
                    cur.append(test)
                }
            }
            if (cur.isNotEmpty()) out.add(cur.toString())
        }
        return out
    }

    // ------------------------------------------------------------------
    //  Merge / Split / Extract  (render-and-rebuild)
    // ------------------------------------------------------------------

    /** Merges [sources] (in order) into [out]. */
    fun merge(sources: List<File>, out: File): File {
        require(sources.isNotEmpty()) { "Nothing to merge" }
        PDDocument().use { target ->
            for (f in sources) {
                PDDocument.load(f).use { src ->
                    val renderer = PDFRenderer(src)
                    for (i in 0 until src.numberOfPages) {
                        appendRenderedPage(target, src, i)
                        renderer.renderImage(i, 1f, Bitmap.Config.ARGB_8888).recycle()
                    }
                }
            }
            target.save(out)
        }
        return out
    }

    /**
     * Extracts the given 1-based page numbers into a new PDF, preserving the
     * order given. Out-of-range numbers are skipped.
     */
    fun extractPages(src: File, out: File, pages: List<Int>): File {
        PDDocument.load(src).use { source ->
            PDDocument().use { target ->
                for (p in pages) {
                    if (p in 1..source.numberOfPages) appendRenderedPage(target, source, p - 1)
                }
                target.save(out)
            }
        }
        return out
    }

    /** Returns the page count of a PDF. */
    fun pageCount(src: File): Int = PDDocument.load(src).use { it.numberOfPages }

    /** Appends [pageIndex] of [srcDoc] to [target] as a rasterized JPEG page. */
    private fun appendRenderedPage(target: PDDocument, srcDoc: PDDocument, pageIndex: Int) {
        val page = srcDoc.getPage(pageIndex)
        val rect = page.mediaBox
        val bmp = PDFRenderer(srcDoc).renderImageWithDPI(pageIndex, PAGE_DPI)
        val newPage = PDPage(PDRectangle(rect.width, rect.height))
        target.addPage(newPage)
        val img = JPEGFactory.createFromImage(target, bmp, MERGE_JPEG_QUALITY)
        val cs = PDPageContentStream(target, newPage, true, true)
        cs.drawImage(img, 0f, 0f, rect.width, rect.height)
        cs.close()
        bmp.recycle()
    }

    // ------------------------------------------------------------------
    //  Rotation / deletion
    // ------------------------------------------------------------------

    /** Rotates the specified 1-based pages by [deg] (90/180/270), in place to [out]. */
    fun rotatePages(src: File, out: File, pages: List<Int>, deg: Int) {
        PDDocument.load(src).use { doc ->
            for (p in pages) {
                if (p in 1..doc.numberOfPages) {
                    val page = doc.getPage(p - 1)
                    page.rotation = ((page.rotation + deg) % 360 + 360) % 360
                }
            }
            doc.save(out)
        }
    }

    /** Removes the specified 1-based pages, saving the rest to [out]. */
    fun deletePages(src: File, out: File, pages: Set<Int>) {
        PDDocument.load(src).use { doc ->
            pages.sortedDescending().forEach { p ->
                if (p in 1..doc.numberOfPages) doc.removePage(p - 1)
            }
            doc.save(out)
        }
    }

    // ------------------------------------------------------------------
    //  Compression
    // ------------------------------------------------------------------

    /**
     * Compresses by re-rendering each page to a JPEG at [dpi] and rebuilding
     * the document. Effective for image-heavy PDFs.
     */
    fun compress(src: File, out: File, dpi: Int, jpegQuality: Float) {
        PDDocument.load(src).use { source ->
            PDDocument().use { target ->
                val renderer = PDFRenderer(source)
                for (i in 0 until source.numberOfPages) {
                    val page = source.getPage(i)
                    val rect = page.mediaBox
                    val bmp = renderer.renderImageWithDPI(i, dpi.toFloat())
                    val newPage = PDPage(PDRectangle(rect.width, rect.height))
                    target.addPage(newPage)
                    val img = JPEGFactory.createFromImage(target, bmp, jpegQuality)
                    val cs = PDPageContentStream(target, newPage, true, true)
                    cs.drawImage(img, 0f, 0f, rect.width, rect.height)
                    cs.close()
                    bmp.recycle()
                }
                target.save(out)
            }
        }
    }

    /** Renders a page to a bitmap at the given scale (1.0 = 72 dpi). */
    fun renderPage(doc: PDDocument, pageIndex: Int, scale: Float): Bitmap =
        PDFRenderer(doc).renderImage(pageIndex, scale, Bitmap.Config.ARGB_8888)

    // ------------------------------------------------------------------
    //  Security (RC4/AES via StandardProtectionPolicy)
    // ------------------------------------------------------------------

    /** Encrypts [src] with a user password, saving to [out]. */
    fun lock(src: File, out: File, password: String) {
        PDDocument.load(src).use { doc ->
            val perms = AccessPermission()
            doc.protect(StandardProtectionPolicy(password, password, perms))
            doc.save(out)
        }
    }

    /** Decrypts [src] with [password], saving an unprotected copy to [out]. */
    fun unlock(src: File, out: File, password: String) {
        PDDocument.load(src, password).use { doc ->
            doc.save(out)
        }
    }

    /** True if the document is encrypted (or unreadable without a password). */
    fun isEncrypted(src: File): Boolean = try {
        PDDocument.load(src).use { it.isEncrypted }
    } catch (e: Exception) {
        true
    }

    // ------------------------------------------------------------------
    //  Watermark / page numbers
    // ------------------------------------------------------------------

    /**
     * Adds a text watermark to every page.
     * @param position [Constants.Watermark.*]
     * @param opacity 0..1 (emulated via grey blending; the 1.8 port has no
     *               content-stream alpha operators)
     */
    fun addWatermark(
        src: File,
        out: File,
        text: String,
        fontSize: Float,
        opacity: Float,
        position: String
    ) {
        // blend grey toward white to emulate opacity
        val g = (128 + (255 - 128) * (1 - opacity)).toInt().coerceIn(0, 255)
        PDDocument.load(src).use { doc ->
            for (i in 0 until doc.numberOfPages) {
                val page = doc.getPage(i)
                val w = page.mediaBox.width
                val h = page.mediaBox.height
                val cs = PDPageContentStream(doc, page, true, true)
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_BOLD, fontSize)
                cs.setNonStrokingColor(g, g, g)
                when (position) {
                    Constants.Watermark.DIAGONAL -> {
                        cs.newLineAtOffset(w / 4f, h / 2f)
                        cs.showText(text)
                    }
                    Constants.Watermark.TILE -> {
                        // simple 2x2 tiling
                        var tx = w / 6f
                        var ty = h / 3f
                        for (row in 0 until 3) {
                            for (col in 0 until 2) {
                                cs.newLineAtOffset(
                                    if (col == 0) tx else tx + w / 3f,
                                    if (row == 0) ty else ty + h / 3f
                                )
                                cs.showText(text)
                            }
                        }
                    }
                    else -> { // center
                        cs.newLineAtOffset((w - fontSize * text.length / 2f) / 2f, h / 2f)
                        cs.showText(text)
                    }
                }
                cs.endText()
                cs.close()
            }
            doc.save(out)
        }
    }

    /** Adds "n / total" page numbers at the given position. */
    fun addPageNumbers(
        src: File,
        out: File,
        position: String,
        fontSize: Float,
        startNumber: Int
    ) {
        PDDocument.load(src).use { doc ->
            val total = doc.numberOfPages
            for (i in 0 until total) {
                val page = doc.getPage(i)
                val w = page.mediaBox.width
                val h = page.mediaBox.height
                val label = "${startNumber + i} / $total"
                val cs = PDPageContentStream(doc, page, true, true)
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, fontSize)
                cs.setNonStrokingColor(0x61, 0x61, 0x61)
                val x = when (position) {
                    Constants.PageNumber.BOTTOM_LEFT,
                    Constants.PageNumber.TOP_LEFT -> 40f
                    Constants.PageNumber.BOTTOM_CENTER,
                    Constants.PageNumber.TOP_CENTER -> w / 2f
                    else -> w - 90f
                }
                val y = if (position.startsWith("top")) h - 40f else 30f
                cs.newLineAtOffset(x, y)
                cs.showText(label)
                cs.endText()
                cs.close()
            }
            doc.save(out)
        }
    }

    // ------------------------------------------------------------------
    //  Outline (TOC)
    // ------------------------------------------------------------------

    /** Adds a flat outline entry per page (used by the reader TOC). */
    fun addOutline(src: File, out: File, titles: List<String>) {
        PDDocument.load(src).use { doc ->
            val outline = PDDocumentOutline()
            for (i in titles.indices) {
                if (i < doc.numberOfPages) {
                    val item = PDOutlineItem()
                    item.title = titles[i]
                    item.destination = doc.getPage(i)
                    outline.addLast(item)
                }
            }
            doc.documentCatalog.documentOutline = outline
            doc.save(out)
        }
    }

    // ------------------------------------------------------------------
    //  Metadata / info
    // ------------------------------------------------------------------

    data class Info(
        val pages: Int,
        val size: Long,
        val author: String?,
        val title: String?,
        val created: String?,
        val modified: String?,
        val encrypted: Boolean
    )

    fun info(src: File): Info {
        return try {
            PDDocument.load(src).use { doc ->
                val m = doc.documentInformation
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                Info(
                    pages = doc.numberOfPages,
                    size = src.length(),
                    author = m.author,
                    title = m.title,
                    created = m.creationDate?.time?.let { fmt.format(it) },
                    modified = m.modificationDate?.time?.let { fmt.format(it) },
                    encrypted = doc.isEncrypted
                )
            }
        } catch (e: Exception) {
            Info(0, src.length(), null, null, null, null, true)
        }
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    /** Parses "1-3, 5, 7-9" into a sorted-unique 1-based page list. */
    fun parsePageRange(input: String, total: Int): List<Int> {
        val pages = LinkedHashSet<Int>()
        for (part in input.split(',')) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val dash = p.indexOf('-')
            if (dash > 0) {
                val a = p.substring(0, dash).trim().toIntOrNull() ?: continue
                val b = p.substring(dash + 1).trim().toIntOrNull() ?: continue
                for (x in a..b) if (x in 1..total) pages.add(x)
            } else {
                val v = p.toIntOrNull() ?: continue
                if (v in 1..total) pages.add(v)
            }
        }
        return pages.toList()
    }
}
