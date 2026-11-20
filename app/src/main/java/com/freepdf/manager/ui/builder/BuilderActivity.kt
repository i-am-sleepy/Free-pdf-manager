package com.freepdf.manager.ui.builder

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.freepdf.manager.R
import com.freepdf.manager.core.Constants
import com.freepdf.manager.core.FileShare
import com.freepdf.manager.databinding.ActivityBuilderBinding
import com.freepdf.manager.pdf.PdfEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF Builder — three modes (selected via [EXTRA_MODE]):
 *  - [MODE_IMAGES]: pick images -> order them -> build a PDF
 *  - [MODE_TEXT]:   type/paste text -> build a PDF
 *  - [MODE_BLANK]:  create a blank N-page PDF
 *
 * Page size / margin / orientation are shared across modes and map 1:1 to
 * [Constants.PageSize] / [Constants.Margin].
 */
@AndroidEntryPoint
class BuilderActivity : AppCompatActivity() {

    private var _binding: ActivityBuilderBinding? = null
    private val binding get() = _binding!!

    private var mode = MODE_IMAGES
    private var imageAdapter: BuilderImageAdapter? = null

    private val pickImages =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { collectUris(it) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityBuilderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_IMAGES
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnBuild.setOnClickListener { build() }
        binding.btnAddImages.setOnClickListener { launchPicker() }

        setupSpinners()
        applyMode()

        imageAdapter = BuilderImageAdapter {
            refreshImageList()
        }
        binding.rvImages.layoutManager = LinearLayoutManager(this)
        binding.rvImages.adapter = imageAdapter
    }

    // region Modes

    private fun applyMode() {
        when (mode) {
            MODE_IMAGES -> {
                binding.textLayout.visibility = View.GONE
                binding.blankPageCountLayout.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
                binding.btnAddImages.visibility = View.VISIBLE
                refreshImageList()
            }
            MODE_TEXT -> {
                binding.tvEmpty.visibility = View.GONE
                binding.btnAddImages.visibility = View.GONE
                binding.rvImages.visibility = View.GONE
                binding.textLayout.visibility = View.VISIBLE
            }
            MODE_BLANK -> {
                binding.tvEmpty.visibility = View.GONE
                binding.btnAddImages.visibility = View.GONE
                binding.rvImages.visibility = View.GONE
                binding.textLayout.visibility = View.GONE
                binding.blankPageCountLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun launchPicker() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        pickImages.launch(intent)
    }

    private fun collectUris(data: Intent) {
        val uris = mutableListOf<Uri>()
        if (data.clipData != null) {
            for (i in 0 until data.clipData!!.itemCount) {
                data.clipData!!.getItemAt(i)?.uri?.let { uris.add(it) }
            }
        } else {
            data.data?.let { uris.add(it) }
        }
        imageAdapter?.addAll(uris)
        refreshImageList()
    }

    private fun refreshImageList() {
        val has = (imageAdapter?.itemCount ?: 0) > 0
        binding.tvEmpty.visibility = if (has) View.GONE else View.VISIBLE
        binding.rvImages.visibility = if (has) View.VISIBLE else View.GONE
    }

    // endregion

    // region Spinners

    private fun setupSpinners() {
        val sizes = listOf(
            Constants.PageSize.AUTO to getString(R.string.page_size_auto),
            Constants.PageSize.A3 to getString(R.string.page_size_a3),
            Constants.PageSize.A4 to getString(R.string.page_size_a4),
            Constants.PageSize.A5 to getString(R.string.page_size_a5),
            Constants.PageSize.B4 to getString(R.string.page_size_b4),
            Constants.PageSize.B5 to getString(R.string.page_size_b5),
            Constants.PageSize.LETTER to getString(R.string.page_size_letter),
            Constants.PageSize.LEGAL to getString(R.string.page_size_legal),
            Constants.PageSize.EXECUTIVE to getString(R.string.page_size_executive),
            Constants.PageSize.BUSINESS_CARD to getString(R.string.page_size_business_card)
        )
        binding.spinnerPageSize.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sizes.map { it.second })
        )
        binding.spinnerPageSize.setText(
            sizes.first { it.first == Constants.PageSize.A4 }.second, false
        )
        pageSizeSelection = sizes

        val margins = listOf(
            Constants.Margin.NONE to getString(R.string.margin_none),
            Constants.Margin.SMALL to getString(R.string.margin_small),
            Constants.Margin.MEDIUM to getString(R.string.margin_medium),
            Constants.Margin.LARGE to getString(R.string.margin_large)
        )
        binding.spinnerMargin.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, margins.map { it.second })
        )
        binding.spinnerMargin.setText(
            margins.first { it.first == Constants.Margin.NONE }.second, false
        )
        marginSelection = margins

        val orientations = listOf(
            -1 to getString(R.string.orientation_auto),
            0 to getString(R.string.orientation_portrait),
            1 to getString(R.string.orientation_landscape)
        )
        binding.spinnerOrientation.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, orientations.map { it.second })
        )
        binding.spinnerOrientation.setText(
            orientations.first { it.first == -1 }.second, false
        )
        orientationSelection = orientations
    }

    private var pageSizeSelection = listOf(Constants.PageSize.A4)
    private var marginSelection = listOf(Constants.Margin.NONE)
    private var orientationSelection = listOf(-1)

    private fun selectedPageSize(): String {
        val text = binding.spinnerPageSize.text?.toString().orEmpty()
        return pageSizeSelection.firstOrNull { it.second == text } ?: pageSizeSelection.first()
    }

    private fun selectedMargin(): Float {
        val text = binding.spinnerMargin.text?.toString().orEmpty()
        return marginSelection.firstOrNull { it.second == text } ?: marginSelection.first()
    }

    private fun selectedOrientation(): Int {
        val text = binding.spinnerOrientation.text?.toString().orEmpty()
        return orientationSelection.firstOrNull { it.second == text } ?: orientationSelection.first()
    }

    // endregion

    // region Build

    private fun build() {
        showProgress(true)
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) {
                try {
                    val dir = File(filesDir, "pdfs").apply { mkdirs() }
                    val file = File(dir, Constants.defaultFileName("PDF"))
                    when (mode) {
                        MODE_IMAGES -> {
                            val uris = imageAdapter?.uris.orEmpty()
                            if (uris.isEmpty()) {
                                null
                            } else {
                                val bitmaps = uris.mapNotNull { decodePage(it) }
                                if (bitmaps.isEmpty()) {
                                    null
                                } else {
                                    PdfEngine.imagesToPdf(
                                        bitmaps, file,
                                        selectedPageSize(), selectedMargin(), selectedOrientation()
                                    )
                                    bitmaps.forEach { it.recycle() }
                                    file
                                }
                            }
                        }
                        MODE_TEXT -> {
                            val text = binding.etText.text?.toString().orEmpty()
                            if (text.isBlank()) null
                            else {
                                PdfEngine.textToPdf(file, text, selectedPageSize(), 12f)
                                file
                            }
                        }
                        MODE_BLANK -> {
                            val pages = binding.etBlankPages.text?.toString()?.toIntOrNull() ?: 1
                            val landscape = selectedOrientation() == 1
                            PdfEngine.blankPdf(file, selectedPageSize(), pages, landscape)
                            file
                        }
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            showProgress(false)
            if (out == null) {
                Toast.makeText(this@BuilderActivity, R.string.error_generic, Toast.LENGTH_SHORT).show()
                return@launch
            }
            FileShare.shareFile(this@BuilderActivity, out, "application/pdf")
            Toast.makeText(this@BuilderActivity, R.string.pdf_created, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun decodePage(uri: Uri, maxDim: Int = 2000): Bitmap? {
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
            null
        }
    }

    // endregion

    private fun showProgress(show: Boolean) {
        binding.progressIndicator.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnBuild.isEnabled = !show
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val MODE_IMAGES = "images"
        const val MODE_TEXT = "text"
        const val MODE_BLANK = "blank"
    }
}

/** List of selected builder images with a remove button. Owns the data. */
class BuilderImageAdapter(
    private val onChanged: () -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<BuilderImageAdapter.VH>() {

    val uris = mutableListOf<Uri>()

    fun addAll(list: List<Uri>) {
        if (list.isEmpty()) return
        val start = uris.size
        uris.addAll(list)
        notifyItemRangeInserted(start, list.size)
        onChanged()
    }

    fun removeAt(position: Int) {
        if (position !in uris.indices) return
        uris.removeAt(position)
        notifyItemRemoved(position)
        onChanged()
    }

    class VH(val binding: com.freepdf.manager.databinding.ItemBuilderImageBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val binding = com.freepdf.manager.databinding.ItemBuilderImageBinding.inflate(
            android.view.LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val uri = uris[position]
        holder.binding.tvName.text = uri.lastPathSegment ?: "image"
        holder.binding.btnRemove.setOnClickListener {
            removeAt(holder.bindingAdapterPosition)
        }
        holder.binding.thumb.post {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                holder.itemView.context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (bounds.outWidth / sample > 96 || bounds.outHeight / sample > 96) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = holder.itemView.context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                if (bmp != null) holder.binding.thumb.setImageBitmap(bmp)
            } catch (e: Exception) {
                // thumbnail is best-effort
            }
        }
    }

    override fun getItemCount(): Int = uris.size
}
