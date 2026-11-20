package com.freepdf.manager.ui.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PageRange
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.freepdf.manager.R
import com.freepdf.manager.core.Constants
import com.freepdf.manager.core.FileShare
import com.freepdf.manager.core.humanSize
import com.freepdf.manager.databinding.ActivityPdfToolsBinding
import com.freepdf.manager.pdf.OfficeConverter
import com.freepdf.manager.pdf.PdfEngine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * PDF Tools — one screen, many tools. [EXTRA_TOOL] (a string id from
 * [ToolsFragment]) selects which tool to run; the panel is configured to
 * match. All processing is on-device (PDFBox port + POI + ML Kit OCR).
 *
 * Tools:
 *  merge, split, compress, rotate, delete_pages, extract_pages,
 *  watermark, page_numbers, lock, unlock, pdf_to_image, pdf_to_text,
 *  word_to_pdf, ppt_to_pdf, excel_to_pdf, pdf_info, print, remove_watermark
 */
@AndroidEntryPoint
class PdfToolsActivity : AppCompatActivity() {

    private var _binding: ActivityPdfToolsBinding? = null
    private val binding get() = _binding!!

    private var tool = "merge"
    private var sourceFile: File? = null
    private val mergeFiles = mutableListOf<File>()
    private var textRecognizer: TextRecognizer? = null

    private val pickSingle =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { copyToAppStorage(it)?.let { f -> onSourcePicked(f) } }
        }

    private val pickMultiple =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            for (u in uris) {
                copyToAppStorage(u)?.let { mergeFiles.add(it) }
            }
            updateMergeCount()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityPdfToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tool = intent.getStringExtra(EXTRA_TOOL) ?: "merge"
        setupSpinners()

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnPickFile.setOnClickListener { pickForTool() }
        binding.btnAddFile.setOnClickListener { pickForTool() }
        binding.btnApply.setOnClickListener { applyTool() }

        configurePanel()
    }

    // region Tool config

    private fun titleRes(): Int = when (tool) {
        "merge" -> R.string.tool_merge
        "split" -> R.string.tool_split
        "compress" -> R.string.tool_compress
        "rotate" -> R.string.tool_rotate
        "delete_pages" -> R.string.tool_delete_pages
        "extract_pages" -> R.string.tool_extract_pages
        "watermark" -> R.string.tool_watermark
        "remove_watermark" -> R.string.tool_remove_watermark
        "page_numbers" -> R.string.tool_page_numbers
        "lock" -> R.string.tool_lock
        "unlock" -> R.string.tool_unlock
        "pdf_to_image" -> R.string.tool_pdf_to_image
        "pdf_to_text" -> R.string.tool_pdf_to_text
        "word_to_pdf" -> R.string.tool_word_to_pdf
        "ppt_to_pdf" -> R.string.tool_ppt_to_pdf
        "excel_to_pdf" -> R.string.tool_excel_to_pdf
        "pdf_info" -> R.string.tool_pdf_info
        "print" -> R.string.tool_print
        else -> R.string.tools_title
    }

    private fun configurePanel() {
        binding.toolbar.title = getString(titleRes())
        // Hide everything, then show what the tool needs.
        binding.mergeRow.visibility = View.GONE
        binding.pagesLayout.visibility = View.GONE
        binding.rgRotate.visibility = View.GONE
        binding.rgCompress.visibility = View.GONE
        binding.wmTextLayout.visibility = View.GONE
        binding.wmPosLayout.visibility = View.GONE
        binding.pnPosLayout.visibility = View.GONE
        binding.passwordLayout.visibility = View.GONE
        binding.tvInfo.visibility = View.GONE

        when (tool) {
            "merge" -> {
                binding.mergeRow.visibility = View.VISIBLE
                updateMergeCount()
            }
            "split", "delete_pages", "extract_pages" -> {
                binding.pagesLayout.visibility = View.VISIBLE
            }
            "rotate" -> {
                binding.pagesLayout.visibility = View.VISIBLE
                binding.rgRotate.visibility = View.VISIBLE
            }
            "compress" -> binding.rgCompress.visibility = View.VISIBLE
            "watermark" -> {
                binding.wmTextLayout.visibility = View.VISIBLE
                binding.wmPosLayout.visibility = View.VISIBLE
            }
            "page_numbers" -> binding.pnPosLayout.visibility = View.VISIBLE
            "lock", "unlock" -> binding.passwordLayout.visibility = View.VISIBLE
            "pdf_info" -> binding.tvInfo.visibility = View.VISIBLE
        }
    }

    // endregion

    // region File picking

    private fun pickForTool() {
        when (tool) {
            "merge" -> {
                pickMultiple.launch(arrayOf("application/pdf"))
            }
            "word_to_pdf" -> pickSingle.launch(
                arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            )
            "ppt_to_pdf" -> pickSingle.launch(
                arrayOf("application/vnd.openxmlformats-officedocument.presentationml.presentation")
            )
            "excel_to_pdf" -> pickSingle.launch(
                arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            )
            else -> pickSingle.launch(arrayOf("application/pdf"))
        }
    }

    private fun onSourcePicked(file: File) {
        sourceFile = file
        binding.tvSource.text = file.name
        when (tool) {
            "pdf_info" -> showInfo()
            "pdf_to_text" -> initRecognizer()
        }
    }

    private fun updateMergeCount() {
        binding.tvMergeCount.text =
            if (mergeFiles.isEmpty()) "" else getString(R.string.merge_files_count, mergeFiles.size)
    }

    /** Copies a content:// URI into app storage so PDFBox/POI can read it. */
    private fun copyToAppStorage(uri: Uri): File? {
        return try {
            val dir = File(filesDir, "picked").apply { mkdirs() }
            val name = "picked_${System.currentTimeMillis()}_${uri.lastPathSegment?.substringAfterLast('/') ?: "file"}"
            val dest = File(dir, name)
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            if (dest.length() > 0) dest else null
        } catch (e: Exception) {
            Log.e(TAG, "copy failed", e)
            null
        }
    }

    // endregion

    // region Spinners

    private fun setupSpinners() {
        val wm = listOf(
            Constants.Watermark.CENTER to getString(R.string.wm_center),
            Constants.Watermark.DIAGONAL to getString(R.string.wm_diagonal),
            Constants.Watermark.TILE to getString(R.string.wm_tile)
        )
        binding.spinnerWmPos.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, wm.map { it.second })
        )
        binding.spinnerWmPos.setText(wm[0].second, false)
        wmPos = wm.map { it.first }

        val pn = listOf(
            Constants.PageNumber.BOTTOM_LEFT to getString(R.string.pnp_bottom_left),
            Constants.PageNumber.BOTTOM_CENTER to getString(R.string.pnp_bottom_center),
            Constants.PageNumber.BOTTOM_RIGHT to getString(R.string.pnp_bottom_right),
            Constants.PageNumber.TOP_LEFT to getString(R.string.pnp_top_left),
            Constants.PageNumber.TOP_CENTER to getString(R.string.pnp_top_center),
            Constants.PageNumber.TOP_RIGHT to getString(R.string.pnp_top_right)
        )
        binding.spinnerPnPos.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, pn.map { it.second })
        )
        binding.spinnerPnPos.setText(pn[2].second, false)
        pnPos = pn.map { it.first }
    }

    private var wmPos = listOf(Constants.Watermark.CENTER)
    private var pnPos = listOf(Constants.PageNumber.BOTTOM_RIGHT)

    private fun selectedWmPos(): String = wmPos[binding.spinnerWmPos.selectedItemPosition]
    private fun selectedPnPos(): String = pnPos[binding.spinnerPnPos.selectedItemPosition]

    // endregion

    // region Apply

    private fun applyTool() {
        val src = sourceFile
        when (tool) {
            "merge" -> applyMerge()
            "split", "extract_pages" -> {
                if (src == null) return
                val pages = pagesFromInput() ?: return
                runEngine { PdfEngine.extractPages(src, out(), pages) }
            }
            "delete_pages" -> {
                if (src == null) return
                val pages = pagesFromInput() ?: return
                runEngine { PdfEngine.deletePages(src, out(), pages.toSet()) }
            }
            "rotate" -> {
                if (src == null) return
                val pages = pagesFromInput() ?: return
                val deg = when (binding.rgRotate.checkedRadioButtonId) {
                    R.id.rb180 -> 180
                    R.id.rb270 -> 270
                    else -> 90
                }
                runEngine { PdfEngine.rotatePages(src, out(), pages, deg) }
            }
            "compress" -> {
                if (src == null) return
                val (dpi, quality) = when (binding.rgCompress.checkedRadioButtonId) {
                    R.id.rbMedium -> 150 to 0.8f
                    R.id.rbMax -> 300 to 0.95f
                    else -> 72 to 0.6f
                }
                runEngine { PdfEngine.compress(src, out(), dpi, quality) }
            }
            "watermark" -> {
                if (src == null) return
                val text = binding.etWmText.text?.toString().orEmpty()
                if (text.isBlank()) {
                    toast(R.string.error_generic); return
                }
                runEngine { PdfEngine.addWatermark(src, out(), text, 48f, 0.5f, selectedWmPos()) }
            }
            "page_numbers" -> {
                if (src == null) return
                runEngine { PdfEngine.addPageNumbers(src, out(), selectedPnPos(), 12f, 1) }
            }
            "lock" -> {
                if (src == null) return
                val pw = binding.etPassword.text?.toString().orEmpty()
                if (pw.isEmpty()) { toast(R.string.error_password); return }
                runEngine { PdfEngine.lock(src, out(), pw) }
            }
            "unlock" -> {
                if (src == null) return
                val pw = binding.etPassword.text?.toString().orEmpty()
                runEngine { PdfEngine.unlock(src, out(), pw) }
            }
            "pdf_to_image" -> {
                if (src == null) return
                pdfToImage(src)
            }
            "pdf_to_text" -> {
                if (src == null) return
                pdfToText(src)
            }
            "word_to_pdf", "ppt_to_pdf", "excel_to_pdf" -> {
                if (src == null) return
                officeToPdf(src)
            }
            "pdf_info" -> {
                if (src == null) return
                showInfo()
            }
            "print" -> {
                if (src == null) return
                printPdf(src)
            }
            "remove_watermark" -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.work_in_progress)
                    .setMessage(R.string.wip_ocr_desc)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }
    }

    private fun applyMerge() {
        if (mergeFiles.size < 2) {
            toast(R.string.error_generic); return
        }
        runEngine { PdfEngine.merge(mergeFiles, out()) }
    }

    /** Runs a blocking PdfEngine call on IO and shares the result. */
    private fun runEngine(block: (File) -> File) {
        showProgress(true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    block(out())
                } catch (e: Exception) {
                    Log.e(TAG, "engine failed", e)
                    null
                }
            }
            showProgress(false)
            if (result == null) {
                toast(R.string.error_generic)
            } else {
                FileShare.shareFile(this@PdfToolsActivity, result, "application/pdf")
                toast(R.string.tool_applied)
            }
        }
    }

    private fun out(): File {
        val dir = File(filesDir, "pdfs").apply { mkdirs() }
        return File(dir, Constants.defaultFileName("PDF"))
    }

    private fun pagesFromInput(): List<Int>? {
        val src = sourceFile ?: return null
        val total = try { PdfEngine.pageCount(src) } catch (e: Exception) { 0 }
        val input = binding.etPages.text?.toString()?.trim().orEmpty()
        if (input.isEmpty()) {
            toast(R.string.invalid_pages_range); return null
        }
        val pages = PdfEngine.parsePageRange(input, total)
        if (pages.isEmpty()) {
            toast(R.string.invalid_pages_range); return null
        }
        return pages
    }

    // endregion

    // region Info

    private fun showInfo() {
        val src = sourceFile ?: return
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { PdfEngine.info(src) }
            binding.tvInfo.visibility = View.VISIBLE
            binding.tvInfo.text = buildString {
                appendLine(getString(R.string.file_pages, info.pages))
                appendLine(getString(R.string.file_size, info.size.humanSize()))
                if (info.author != null) appendLine("Author: ${info.author}")
                if (info.title != null) appendLine("Title: ${info.title}")
                if (info.created != null) appendLine(getString(R.string.created_on, info.created))
                if (info.modified != null) appendLine(getString(R.string.modified_on, info.modified))
                if (info.encrypted) appendLine("🔒 " + getString(R.string.lock_pdf))
            }
        }
    }

    // endregion

    // region PDF -> Image

    private fun pdfToImage(src: File) {
        showProgress(true)
        lifecycleScope.launch {
            val zipPath = withContext(Dispatchers.IO) {
                try {
                    val dir = File(filesDir, "pdfs").apply { mkdirs() }
                    val base = Constants.defaultFileName("Pages").removeSuffix(".pdf")
                    val outDir = File(dir, base).apply { mkdirs() }
                    PDDocument.load(src).use { doc ->
                        for (i in 0 until doc.numberOfPages) {
                            val bmp = PdfEngine.renderPage(doc, i, 2.0f)
                            val f = File(outDir, "${base}_p${i + 1}.jpg")
                            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                            bmp.recycle()
                        }
                    }
                    val zip = File(dir, "$base.zip")
                    ZipFile(zip).use { z ->
                        for (f in outDir.listFiles().orEmpty()) {
                            z.putNextEntry(ZipEntry(f.name))
                            f.inputStream().use { z.copyFrom(it) }
                            z.closeEntry()
                        }
                    }
                    zip
                } catch (e: Exception) {
                    Log.e(TAG, "pdf_to_image failed", e)
                    null
                }
            }
            showProgress(false)
            if (zipPath == null) toast(R.string.error_generic)
            else {
                FileShare.shareFile(this@PdfToolsActivity, zipPath, "application/zip")
                toast(R.string.tool_applied)
            }
        }
    }

    // endregion

    // region PDF -> Text (ML Kit OCR)

    private fun initRecognizer() {
        if (textRecognizer == null) {
            textRecognizer = TextRecognition.getClient()
        }
    }

    private fun pdfToText(src: File) {
        showProgress(true)
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    val recognizer = TextRecognition.getClient()
                    val sb = StringBuilder()
                    PDDocument.load(src).use { doc ->
                        for (i in 0 until doc.numberOfPages) {
                            val bmp = PdfEngine.renderPage(doc, i, 2.0f)
                            val image = InputImage.fromBitmap(bmp, 0)
                            val t = recognizer.process(image).result
                            if (t.isNotBlank()) {
                                sb.append("Page ${i + 1}\n")
                                sb.append(t).append("\n\n")
                            }
                            bmp.recycle()
                        }
                    }
                    recognizer.close()
                    sb.toString()
                } catch (e: Exception) {
                    Log.e(TAG, "pdf_to_text failed", e)
                    null
                }
            }
            showProgress(false)
            if (text == null) {
                toast(R.string.error_generic)
            } else {
                val dir = File(filesDir, "pdfs").apply { mkdirs() }
                val f = File(dir, Constants.defaultFileName("Text").removeSuffix(".pdf") + ".txt")
                f.writeText(text)
                FileShare.shareFile(this@PdfToolsActivity, f, "text/plain")
                toast(R.string.tool_applied)
            }
        }
    }

    // endregion

    // region Office -> PDF (POI)

    private fun officeToPdf(src: File) {
        showProgress(true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val text = OfficeConverter.extractText(src)
                    if (text.isBlank()) null
                    else {
                        val out = out()
                        PdfEngine.textToPdf(out, text, Constants.PageSize.A4, 12f)
                        out
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "office_to_pdf failed", e)
                    null
                }
            }
            showProgress(false)
            if (result == null) toast(R.string.error_generic)
            else {
                FileShare.shareFile(this@PdfToolsActivity, result, "application/pdf")
                toast(R.string.tool_applied)
            }
        }
    }

    // endregion

    // region Print

    private fun printPdf(src: File) {
        try {
            val doc = PdfDocument()
            PDDocument.load(src).use { pdf ->
                for (i in 0 until pdf.numberOfPages) {
                    val page = pdf.getPage(i)
                    val w = page.mediaBox.width.toInt()
                    val h = page.mediaBox.height.toInt()
                    val pdfPage = doc.startPage(
                        PdfDocument.PageInfo.Builder(w, h, i + 1).create()
                    )
                    val bmp = PdfEngine.renderPage(pdf, i, 1.0f)
                    pdfPage.canvas.drawBitmap(bmp, null,
                        android.graphics.Rect(0, 0, w, h), null)
                    bmp.recycle()
                    doc.finishPage(pdfPage)
                }
            }

            val pageCount = doc.pageCount
            val adapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    oldPageRanges: PageRange?,
                    newAttributes: PrintAttributes,
                    newPageRanges: PageRange?,
                    cancelCallback: android.print.Cancellable,
                    resultCallback: OnLayoutResultCallback
                ) {
                    resultCallback.onLayoutFinished(
                        PrintDocumentInfo.Builder(src.name)
                            .setPageCount(pageCount)
                            .build(), false)
                }

                override fun onRender(
                    pages: Array<out String>,
                    attrs: PrintAttributes,
                    cancelCallback: android.print.Cancellable,
                    resultCallback: OnRenderResultCallback
                ) {
                    doc.writeTo(resultCallback.writePdfContentStream)
                    doc.close()
                    resultCallback.onRenderFinished(pages)
                }
            }
            val pm = getSystemService(PRINT_SERVICE) as PrintManager
            val criteria = PrintAttributes.Builder().build()
            pm.print(src.name, adapter, criteria)
        } catch (e: Exception) {
            Log.e(TAG, "print failed", e)
            toast(R.string.error_generic)
        }
    }

    // endregion

    private fun showProgress(show: Boolean) {
        binding.progressIndicator.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnApply.isEnabled = !show
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        textRecognizer?.close()
        textRecognizer = null
        _binding = null
    }

    companion object {
        private const val TAG = "PdfToolsActivity"
        const val EXTRA_TOOL = "extra_tool"
    }
}
