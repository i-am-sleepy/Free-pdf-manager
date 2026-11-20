package com.freepdf.manager.ui.reader

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.freepdf.manager.R
import com.freepdf.manager.core.FileShare
import com.freepdf.manager.databinding.ActivityReaderBinding
import com.freepdf.manager.pdf.PdfEngine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * PDF reader.
 *
 * Rendering uses the PDFBox port's PDFRenderer (the same engine as the
 * reference app — it ships no native libs). Pages are rendered lazily on a
 * single background thread (PDDocument is not thread-safe) and cached in a
 * small LRU so scrolling stays smooth.
 */
@AndroidEntryPoint
class ReaderActivity : AppCompatActivity() {

    private var _binding: ActivityReaderBinding? = null
    private val binding get() = _binding!!

    private var doc: PDDocument? = null
    private var pageCount = 0
    private var scale = BASE_SCALE

    private val renderExecutor = Executors.newSingleThreadExecutor()
    private val pageCache = LinkedHashMap<Int, Bitmap>(CACHE_SIZE, 0.75f, true)
    private val cacheLock = Any()

    private var pageAdapter: PageAdapter? = null

    private val pickPdf =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { loadFromUri(it) } ?: finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val path = intent.getStringExtra(EXTRA_PATH)
        val name = intent.getStringExtra(EXTRA_NAME)
        binding.toolbar.title = name ?: getString(R.string.reader_title)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnZoomIn.setOnClickListener { zoomIn() }
        binding.btnZoomOut.setOnClickListener { zoomOut() }
        binding.btnToc.setOnClickListener { showToc() }

        if (path == null) {
            // Launched from the home quick-actions without a file — pick one.
            pickPdf.launch(arrayOf("application/pdf"))
        } else {
            openDocument(path, null)
        }
    }

    /** Copies a content:// URI into app storage, then opens it. */
    private fun loadFromUri(uri: Uri) {
        lifecycleScope.launch {
            showProgress(true)
            val file = withContext(Dispatchers.IO) {
                FileShare.copyUriToAppStorage(this@ReaderActivity, uri, File(filesDir, "pdfs"),
                    "read_${System.currentTimeMillis()}.pdf")
            }
            showProgress(false)
            if (file == null) {
                Toast.makeText(this@ReaderActivity, R.string.error_read_file, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                openDocument(file.absolutePath, null)
            }
        }
    }

    // region Document loading

    private fun openDocument(path: String, password: String?) {
        showProgress(true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val d = if (password.isNullOrBlank()) {
                        PDDocument.load(File(path))
                    } else {
                        PDDocument.load(File(path), password)
                    }
                    LoadResult.Ok(d)
                } catch (e: Exception) {
                    LoadResult.Error(e)
                }
            }
            showProgress(false)
            when (result) {
                is LoadResult.Ok -> {
                    doc?.close()
                    doc = result.document
                    pageCount = result.document.numberOfPages
                    scale = BASE_SCALE
                    synchronized(cacheLock) {
                        pageCache.values.forEach { it.recycle() }
                        pageCache.clear()
                    }
                    pageAdapter = PageAdapter(this)
                    pageAdapter?.setCount(pageCount)
                    binding.viewPager.adapter = pageAdapter
                    binding.viewPager.offscreenPageLimit = 1
                    binding.viewPager.registerOnPageChangeCallback(object :
                        ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(position: Int) {
                            updatePageLabel(position + 1)
                        }
                    })
                    updatePageLabel(1)
                }
                is LoadResult.Error -> {
                    if (password != null) {
                        Toast.makeText(this@ReaderActivity, R.string.wrong_password, Toast.LENGTH_SHORT).show()
                    } else if (isPasswordProtected(path)) {
                        promptPassword(path)
                    } else {
                        Toast.makeText(this@ReaderActivity, R.string.error_read_file, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }
    }

    private fun isPasswordProtected(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        return try {
            PDDocument.load(file).use { it.isEncrypted }
        } catch (e: Exception) {
            true
        }
    }

    private fun promptPassword(path: String) {
        val input = com.google.android.material.textfield.TextInputEditText(this).apply {
            hint = getString(R.string.enter_password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.reader_password_prompt)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                openDocument(path, input.text?.toString().orEmpty())
            }
            .setNegativeButton(R.string.cancel) { _, _ -> finish() }
            .show()
    }

    // endregion

    // region Zoom / TOC

    private fun zoomIn() {
        if (scale >= MAX_SCALE) return
        scale = (scale + 0.25f).coerceAtMost(MAX_SCALE)
        clearCache()
        pageAdapter?.notifyDataSetChanged()
    }

    private fun zoomOut() {
        if (scale <= BASE_SCALE) return
        scale = (scale - 0.25f).coerceAtLeast(BASE_SCALE)
        clearCache()
        pageAdapter?.notifyDataSetChanged()
    }

    private fun clearCache() {
        synchronized(cacheLock) {
            pageCache.values.forEach { it.recycle() }
            pageCache.clear()
        }
    }

    private fun showToc() {
        if (pageCount == 0) return
        val items = (1..pageCount).map { "Page $it" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.reader_toc)
            .setItems(items) { _, which ->
                binding.viewPager.setCurrentItem(which, true)
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun updatePageLabel(page: Int) {
        binding.tvPage.text = getString(R.string.reader_page_of, page, pageCount)
    }

    // endregion

    /**
     * Renders [pageIndex] at the current scale on the single render thread
     * (with LRU caching) and sets the result on [target]. Called from the
     * adapter; safe to call repeatedly.
     */
    fun renderPageInto(pageIndex: Int, target: ImageView) {
        val s = scale
        target.tag = pageIndex
        renderExecutor.execute {
            val bmp = renderPageBlocking(pageIndex, s)
            target.post {
                // Guard against the view being recycled in the meantime.
                if (target.tag == pageIndex && bmp != null) {
                    target.setImageBitmap(bmp)
                    target.minimumWidth = bmp.width
                    target.minimumHeight = bmp.height
                }
            }
        }
    }

    /** Blocking render with cache. Must be called on the render thread only. */
    private fun renderPageBlocking(pageIndex: Int, s: Float): Bitmap? {
        synchronized(cacheLock) {
            pageCache[pageIndex]?.let { return it }
        }
        val d = doc ?: return null
        val bmp = try {
            PdfEngine.renderPage(d, pageIndex, s)
        } catch (e: Exception) {
            null
        } ?: return null
        synchronized(cacheLock) {
            while (pageCache.size >= CACHE_SIZE) {
                // accessOrder=true: first key is the least-recently-used page
                val key = pageCache.keys.first()
                pageCache.remove(key)?.recycle()
            }
            pageCache[pageIndex] = bmp
        }
        return bmp
    }

    private fun showProgress(show: Boolean) {
        binding.progressIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        renderExecutor.shutdown()
        doc?.close()
        doc = null
        clearCache()
        _binding = null
    }

    private sealed class LoadResult {
        data class Ok(val document: PDDocument) : LoadResult()
        data class Error(val exception: Exception) : LoadResult()
    }

    companion object {
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_NAME = "extra_name"
        private const val BASE_SCALE = 1.0f
        private const val MAX_SCALE = 4.0f
        private const val CACHE_SIZE = 4
    }
}

/**
 * Renders pages on demand via [ReaderActivity.renderPageInto], which does the
 * actual (cached, background) work.
 */
class PageAdapter(private val activity: ReaderActivity) :
    androidx.recyclerview.widget.RecyclerView.Adapter<PageAdapter.VH>() {

    private var count = 0

    fun setCount(n: Int) {
        count = n
        notifyDataSetChanged()
    }

    class VH(val binding: com.freepdf.manager.databinding.ItemReaderPageBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val binding = com.freepdf.manager.databinding.ItemReaderPageBinding.inflate(
            android.view.LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.binding.pageImage.setImageBitmap(null)
        activity.renderPageInto(position, holder.binding.pageImage)
    }

    override fun getItemCount(): Int = count
}
