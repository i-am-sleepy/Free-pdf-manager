package com.freepdf.manager.ui.edit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.freepdf.manager.R
import com.freepdf.manager.core.Constants
import com.freepdf.manager.core.FileShare
import com.freepdf.manager.databinding.ActivityEditBinding
import com.freepdf.manager.image.ImageFilters
import com.freepdf.manager.ui.edit.crop.CropPresetAdapter
import com.freepdf.manager.ui.edit.filter.FilterAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Image editor: crop, adjust, filters, doodle, text and signature.
 * All processing is on-device via [ImageFilters] (pure Kotlin, no native libs).
 */
@AndroidEntryPoint
class EditActivity : AppCompatActivity() {

    private var _binding: ActivityEditBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditViewModel by viewModels()

    private var activePanel: View? = null

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { loadFromUri(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.edit_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_save) {
                saveAndShare()
                true
            } else {
                false
            }
        }

        val path = intent.getStringExtra(EXTRA_PATH)
        if (path != null) {
            loadFromFile(path)
        } else {
            pickImage.launch("image/*")
        }

        setupToolButtons()
        setupCropPanel()
        setupAdjustPanel()
        setupFilterPanel()
        setupDoodlePanel()
        setupTextPanel()
        setupSignaturePanel()

        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    // region Loading

    private fun loadFromUri(uri: Uri) {
        lifecycleScope.launch {
            showProgress(true)
            val file = withContext(Dispatchers.IO) {
                val dest = File(cacheDir, "edit_source_${System.currentTimeMillis()}.jpg")
                FileShare.copyUriToAppStorage(this@EditActivity, uri, cacheDir, dest.name) ?: return@withContext null
            }
            showProgress(false)
            file?.let { loadFromFile(it.absolutePath) }
                ?: toast(R.string.edit_no_image)
        }
    }

    private fun loadFromFile(path: String) {
        lifecycleScope.launch {
            showProgress(true)
            val bmp = withContext(Dispatchers.IO) { decodeSampled(path) }
            showProgress(false)
            if (bmp == null) {
                toast(R.string.edit_no_image)
                finish()
                return@launch
            }
            viewModel.load(bmp)
            renderCurrent()
        }
    }

    /** Decodes with inSampleSize so huge photos don't OOM. */
    private fun decodeSampled(path: String, maxDim: Int = 2560): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sample = 1
            while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
                sample *= 2
            }
            val decode = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, decode)
        } catch (e: Exception) {
            Log.e(TAG, "decode failed", e)
            null
        }
    }

    // endregion

    // region UI wiring

    private fun setupToolButtons() {
        binding.btnCrop.setOnClickListener { togglePanel(binding.panelCrop) }
        binding.btnAdjust.setOnClickListener { togglePanel(binding.panelAdjust) }
        binding.btnFilters.setOnClickListener { togglePanel(binding.rvFilters) }
        binding.btnDoodle.setOnClickListener { togglePanel(binding.panelDoodle) }
        binding.btnText.setOnClickListener { togglePanel(binding.panelText) }
        binding.btnSignature.setOnClickListener { togglePanel(binding.panelSignature) }
    }

    private fun togglePanel(panel: View) {
        if (panel.visibility == View.VISIBLE) {
            hideAllPanels()
        } else {
            hideAllPanels()
            panel.visibility = View.VISIBLE
            activePanel = panel
        }
    }

    private fun hideAllPanels() {
        binding.panelCrop.visibility = View.GONE
        binding.panelAdjust.visibility = View.GONE
        binding.rvFilters.visibility = View.GONE
        binding.panelDoodle.visibility = View.GONE
        binding.panelText.visibility = View.GONE
        binding.panelSignature.visibility = View.GONE
        activePanel = null
    }

    private fun setupCropPanel() {
        val presets = listOf(
            CropPresetAdapter.Preset(null, getString(R.string.ratio_free)),
            CropPresetAdapter.Preset(1f, getString(R.string.ratio_1_1)),
            CropPresetAdapter.Preset(4f / 3f, getString(R.string.ratio_4_3)),
            CropPresetAdapter.Preset(3f / 4f, getString(R.string.ratio_3_4)),
            CropPresetAdapter.Preset(16f / 9f, getString(R.string.ratio_16_9)),
            CropPresetAdapter.Preset(Constants.PageSize.dimensions(Constants.PageSize.A4).first /
                    Constants.PageSize.dimensions(Constants.PageSize.A4).second, getString(R.string.ratio_a4))
        )
        val adapter = CropPresetAdapter { ratio -> applyCrop(ratio) }
        binding.rvCropPresets.apply {
            layoutManager = LinearLayoutManager(this@EditActivity, LinearLayoutManager.HORIZONTAL, false)
            this.adapter = adapter
        }
        adapter.submit(presets)
        binding.btnApplyCrop.setOnClickListener { hideAllPanels() }
    }

    private fun applyCrop(ratio: Float?) {
        val src = viewModel.current ?: return
        if (ratio == null) {
            hideAllPanels()
            return
        }
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val rect = if (w / h > ratio) {
            val newW = h * ratio
            Rect(((w - newW) / 2).toInt(), 0, ((w + newW) / 2).toInt(), h.toInt())
        } else {
            val newH = w / ratio
            Rect(0, ((h - newH) / 2).toInt(), w.toInt(), ((h + newH) / 2).toInt())
        }
        lifecycleScope.launch {
            showProgress(true)
            withContext(Dispatchers.Default) { viewModel.crop(rect) }
            showProgress(false)
            renderCurrent()
            hideAllPanels()
        }
    }

    private fun setupAdjustPanel() {
        binding.btnApplyAdjust.setOnClickListener {
            val b = binding.sliderBrightness.value
            val c = binding.sliderContrast.value
            val s = binding.sliderSaturation.value
            lifecycleScope.launch {
                showProgress(true)
                withContext(Dispatchers.Default) {
                    viewModel.applyAdjust(b, c, s)
                }
                showProgress(false)
                renderCurrent()
                hideAllPanels()
            }
        }
    }

    private fun setupFilterPanel() {
        val filters = listOf(
            FilterAdapter.Item(ImageFilters.EditorFilter.ORIGINAL, getString(R.string.edit_filter_original)),
            FilterAdapter.Item(ImageFilters.EditorFilter.B_W, getString(R.string.edit_filter_bw)),
            FilterAdapter.Item(ImageFilters.EditorFilter.GRAY, getString(R.string.edit_filter_gray)),
            FilterAdapter.Item(ImageFilters.EditorFilter.SEPIA, getString(R.string.edit_filter_sepia)),
            FilterAdapter.Item(ImageFilters.EditorFilter.VIVID, getString(R.string.edit_filter_vivid)),
            FilterAdapter.Item(ImageFilters.EditorFilter.WARM, getString(R.string.edit_filter_warm)),
            FilterAdapter.Item(ImageFilters.EditorFilter.COOL, getString(R.string.edit_filter_cool))
        )
        val adapter = FilterAdapter(filters) { filter, base ->
            lifecycleScope.launch {
                showProgress(true)
                withContext(Dispatchers.Default) {
                    viewModel.applyFilter(filter)
                }
                showProgress(false)
                renderCurrent()
            }
        }
        binding.rvFilters.apply {
            layoutManager = LinearLayoutManager(this@EditActivity, LinearLayoutManager.HORIZONTAL, false)
            this.adapter = adapter
        }
        // Thumbnails are generated lazily from the current image.
        lifecycleScope.launch {
            val bmp = viewModel.current
            if (bmp != null) {
                withContext(Dispatchers.Default) {
                    adapter.updatePreviews(bmp)
                }
            }
        }
    }

    private fun setupDoodlePanel() {
        binding.doodleView.strokeWidth = 14f
        binding.btnDoodleUndo.setOnClickListener { binding.doodleView.undo() }
        binding.btnDoodleRedo.setOnClickListener { binding.doodleView.redo() }
        binding.btnDoodleEraser.setOnClickListener {
            binding.doodleView.isEraser = !binding.doodleView.isEraser
        }
        binding.btnDoodleApply.setOnClickListener {
            if (!binding.doodleView.hasContent()) {
                hideAllPanels()
                return@setOnClickListener
            }
            val src = viewModel.current ?: return@setOnClickListener
            lifecycleScope.launch {
                showProgress(true)
                withContext(Dispatchers.Default) {
                    val overlay = binding.doodleView.toBitmap(src.width, src.height)
                    viewModel.overlay(overlay)
                    overlay.recycle()
                }
                showProgress(false)
                binding.doodleView.clear()
                renderCurrent()
                hideAllPanels()
            }
        }
    }

    private fun setupTextPanel() {
        binding.btnApplyText.setOnClickListener {
            val text = binding.textInput.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) {
                hideAllPanels()
                return@setOnClickListener
            }
            val size = binding.sliderTextSize.value.toInt()
            val src = viewModel.current ?: return@setOnClickListener
            lifecycleScope.launch {
                showProgress(true)
                withContext(Dispatchers.Default) {
                    val out = src.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = Canvas(out)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = size * 4f
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText(
                        text,
                        out.width / 2f,
                        out.height - size * 2f,
                        paint
                    )
                    viewModel.commit(out)
                }
                showProgress(false)
                renderCurrent()
                hideAllPanels()
            }
        }
    }

    private fun setupSignaturePanel() {
        binding.signatureView.strokeWidth = 8f
        binding.btnSignatureClear.setOnClickListener { binding.signatureView.clear() }
        binding.btnSignatureApply.setOnClickListener {
            if (!binding.signatureView.hasContent()) {
                hideAllPanels()
                return@setOnClickListener
            }
            val src = viewModel.current ?: return@setOnClickListener
            lifecycleScope.launch {
                showProgress(true)
                withContext(Dispatchers.Default) {
                    // Signature goes in the bottom-right quadrant.
                    val sig = binding.signatureView.toBitmap(
                        src.width / 2, src.height / 2
                    )
                    val out = src.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = Canvas(out)
                    canvas.drawBitmap(
                        sig,
                        (out.width - sig.width).toFloat(),
                        (out.height - sig.height).toFloat(),
                        null
                    )
                    sig.recycle()
                    viewModel.commit(out)
                }
                showProgress(false)
                binding.signatureView.clear()
                renderCurrent()
                hideAllPanels()
            }
        }
    }

    // endregion

    // region Save

    private fun renderCurrent() {
        viewModel.current?.let { binding.imageView.setImageBitmap(it) }
    }

    private fun saveAndShare() {
        val bmp = viewModel.current ?: return
        val name = Constants.defaultFileName("Edited").replace(".pdf", ".jpg")
        lifecycleScope.launch {
            showProgress(true)
            val result = withContext(Dispatchers.IO) {
                try {
                    val dir = File(filesDir, "pdfs").apply { mkdirs() }
                    val file = File(dir, name)
                    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    file
                } catch (e: Exception) {
                    Log.e(TAG, "save failed", e)
                    null
                }
            }
            showProgress(false)
            if (result == null) {
                toast(R.string.edit_save_failed)
                return@launch
            }
            FileShare.shareFile(this@EditActivity, result, "image/jpeg")
            finish()
        }
    }

    private val backCallback = object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when {
                activePanel != null -> hideAllPanels()
                viewModel.canUndo() -> {
                    viewModel.undo()?.let { binding.imageView.setImageBitmap(it) }
                }
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    }

    // endregion

    private fun showProgress(show: Boolean) {
        binding.progressIndicator.visibility = if (show) View.VISIBLE else View.GONE
        binding.bottomToolBar.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        private const val TAG = "EditActivity"
        const val EXTRA_PATH = "extra_path"
    }
}
