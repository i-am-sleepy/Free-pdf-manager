package com.freepdf.manager.ui.scan

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.freepdf.manager.R
import com.freepdf.manager.core.Constants
import com.freepdf.manager.core.FileShare
import com.freepdf.manager.databinding.ActivityScanBinding
import com.freepdf.manager.image.ImageFilters
import com.freepdf.manager.pdf.PdfEngine
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Document scanner.
 *
 * Uses the ML Kit Document Scanner's intent-based API (verified against the
 * official Google sample): the scanner provides its own full-screen camera UI
 * with auto edge detection and perspective correction, so this activity only
 * manages the captured pages, the 8-filter pipeline and PDF output.
 *
 * Flow:
 *  1. "Start scan" -> GmsDocumentScanning.getClient(options)
 *        .getStartScanIntent(this) -> IntentSender -> launch
 *  2. Result: GmsDocumentScanningResult.fromActivityResultIntent(data)
 *        .pages (each .imageUri) + .pdf
 *  3. Pick a filter -> pages are decoded, filtered via [ImageFilters] and
 *     assembled into a PDF with [PdfEngine.imagesToPdf].
 *  4. Register in the local database and share.
 */
@AndroidEntryPoint
class ScanActivity : AppCompatActivity() {

    private var _binding: ActivityScanBinding? = null
    private val binding get() = _binding!!

    private var capturedUris: MutableList<Uri> = mutableListOf()
    private var currentFilter = ImageFilters.ScanFilter.ORIGINAL
    private var pageAdapter: ScanPageAdapter? = null

    private val scannerLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            onScannerResult(result.resultCode, result.data)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnStartScan.setOnClickListener { startScan() }
        binding.btnAddMore.setOnClickListener { startScan() }
        binding.btnSavePdf.setOnClickListener { saveAsPdf() }
        binding.tvFilter.setOnClickListener { cycleFilter() }

        val adapter = ScanPageAdapter()
        pageAdapter = adapter
        binding.rvPages.apply {
            layoutManager = GridLayoutManager(this@ScanActivity, 2)
            this.adapter = adapter
        }
    }

    // region ML Kit scanner (intent-based)

    private fun startScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setPageLimit(MAX_PAGES)
            .setGalleryImportAllowed(true)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { sender: IntentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { e: Exception ->
                Log.e(TAG, "Scanner unavailable", e)
                toast(R.string.scan_unavailable)
            }
    }

    private fun onScannerResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return
        val result = GmsDocumentScanningResult.fromActivityResultIntent(data) ?: return

        val pages = result.pages ?: return
        if (pages.isEmpty()) {
            toast(R.string.scan_no_pages)
            return
        }
        for (page in pages) {
            page.imageUri?.let { uri ->
                if (capturedUris.size < MAX_PAGES) capturedUris.add(uri)
            }
        }
        refresh()
    }

    // endregion

    // region UI state

    private fun refresh() {
        val hasPages = capturedUris.isNotEmpty()
        binding.emptyState.visibility = if (hasPages) View.GONE else View.VISIBLE
        binding.rvPages.visibility = if (hasPages) View.VISIBLE else View.GONE
        binding.bottomBar.visibility = if (hasPages) View.VISIBLE else View.GONE
        pageAdapter?.submit(capturedUris)
        updateFilterLabel()
    }

    private fun updateFilterLabel() {
        binding.tvFilter.text =
            getString(R.string.scan_filter_applied, getString(currentFilter.labelRes))
    }

    private fun cycleFilter() {
        val values = ImageFilters.ScanFilter.values()
        currentFilter = values[(currentFilter.ordinal + 1) % values.size]
        updateFilterLabel()
    }

    // endregion

    // region Save

    private fun saveAsPdf() {
        if (capturedUris.isEmpty()) return
        showProgress(true)
        lifecycleScope.launch {
            val bitmaps = withContext(Dispatchers.IO) {
                capturedUris.mapNotNull { uri -> decodePage(uri) }
                    .map { ImageFilters.applyScanFilter(it, currentFilter) }
            }
            if (bitmaps.isEmpty()) {
                showProgress(false)
                toast(R.string.scan_save_failed)
                return@launch
            }
            val out = withContext(Dispatchers.IO) {
                try {
                    val dir = File(filesDir, "pdfs").apply { mkdirs() }
                    val file = File(dir, Constants.defaultFileName("Scan"))
                    PdfEngine.imagesToPdf(bitmaps, file, Constants.PageSize.A4, 0f, -1)
                    file
                } catch (e: Exception) {
                    Log.e(TAG, "PDF build failed", e)
                    null
                } finally {
                    bitmaps.forEach { it.recycle() }
                }
            }
            showProgress(false)
            if (out == null) {
                toast(R.string.scan_save_failed)
                return@launch
            }
            FileShare.shareFile(this@ScanActivity, out, "application/pdf")
            capturedUris.clear()
            refresh()
            toast(R.string.scan_saved)
        }
    }

    /** Decodes a page with inSampleSize so large scans don't OOM. */
    private fun decodePage(uri: Uri, maxDim: Int = 1600): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            Log.e(TAG, "decode failed", e)
            null
        }
    }

    // endregion

    private fun showProgress(show: Boolean) {
        binding.progressIndicator.visibility = if (show) View.VISIBLE else View.GONE
        binding.bottomBar.visibility =
            if (show) View.INVISIBLE else if (capturedUris.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        private const val TAG = "ScanActivity"
        private const val MAX_PAGES = 50
    }
}

/** Simple grid of captured page thumbnails. */
class ScanPageAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<ScanPageAdapter.VH>() {

    private val uris = mutableListOf<Uri>()

    fun submit(list: List<Uri>) {
        uris.clear()
        uris.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: com.freepdf.manager.databinding.ItemScanPageBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: android.view.ViewGroup,
        viewType: Int
    ): VH {
        val binding = com.freepdf.manager.databinding.ItemScanPageBinding.inflate(
            android.view.LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val uri = uris[position]
        holder.binding.pageImage.post {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                holder.itemView.context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (bounds.outWidth / sample > 400 || bounds.outHeight / sample > 400) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = holder.itemView.context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                if (bmp != null) {
                    holder.binding.pageImage.setImageBitmap(bmp)
                }
            } catch (e: Exception) {
                // thumbnail is best-effort
            }
        }
    }

    override fun getItemCount(): Int = uris.size
}
