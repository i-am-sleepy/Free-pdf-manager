package com.freepdf.manager.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.freepdf.manager.databinding.FragmentHomeBinding
import com.freepdf.manager.db.FileEntity
import com.freepdf.manager.ui.builder.BuilderActivity
import com.freepdf.manager.ui.edit.EditActivity
import com.freepdf.manager.ui.files.FileAdapter
import com.freepdf.manager.ui.pdf.PdfToolsActivity
import com.freepdf.manager.ui.reader.ReaderActivity
import com.freepdf.manager.ui.scan.ScanActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Home tab: quick actions + recent files.
 */
@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by activityViewModels()

    private lateinit var fileAdapter: FileAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileAdapter = FileAdapter(
            onClick = { entity -> openFile(entity) },
            onLongClick = { entity -> showFileActions(entity) },
            onFavorite = { entity -> toggleFavorite(entity) }
        )
        binding.recentList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fileAdapter
            isNestedScrollingEnabled = false
        }

        binding.actionCreatePdf.setOnClickListener {
            startActivity(Intent(requireContext(), BuilderActivity::class.java))
        }
        binding.actionScan.setOnClickListener {
            startActivity(Intent(requireContext(), ScanActivity::class.java))
        }
        binding.actionEdit.setOnClickListener {
            startActivity(Intent(requireContext(), EditActivity::class.java))
        }
        binding.actionPdfTools.setOnClickListener {
            startActivity(Intent(requireContext(), PdfToolsActivity::class.java))
        }
        binding.actionReader.setOnClickListener {
            startActivity(Intent(requireContext(), ReaderActivity::class.java))
        }
        binding.actionTextToPdf.setOnClickListener {
            val intent = Intent(requireContext(), BuilderActivity::class.java)
            intent.putExtra(BuilderActivity.EXTRA_MODE, BuilderActivity.MODE_TEXT)
            startActivity(intent)
        }

        observeRecent()
    }

    private fun observeRecent() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recent.collect { files ->
                    fileAdapter.submitList(files)
                    binding.recentEmpty.visibility =
                        if (files.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun openFile(entity: FileEntity) {
        val intent = Intent(requireContext(), ReaderActivity::class.java)
        intent.putExtra(ReaderActivity.EXTRA_PATH, entity.path)
        intent.putExtra(ReaderActivity.EXTRA_NAME, entity.name)
        startActivity(intent)
    }

    private fun showFileActions(entity: FileEntity) {
        val options = arrayOf(
            getString(com.freepdf.manager.R.string.files_open),
            getString(com.freepdf.manager.R.string.files_share),
            getString(
                com.freepdf.manager.R.string.files_favorite
            )
        )
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(entity.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openFile(entity)
                    1 -> shareFile(entity)
                    2 -> toggleFavorite(entity)
                }
            }
            .show()
    }

    private fun toggleFavorite(entity: FileEntity) {
        viewModel.toggleFavorite(entity)
    }

    private fun shareFile(entity: FileEntity) {
        com.freepdf.manager.core.FileShare.shareFile(
            requireContext(),
            java.io.File(entity.path),
            com.freepdf.manager.core.FileShare.mimeTypeFor(java.io.File(entity.path))
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
