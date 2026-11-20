package com.freepdf.manager.ui.files

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.freepdf.manager.R
import com.freepdf.manager.core.FileShare
import com.freepdf.manager.core.humanSize
import com.freepdf.manager.databinding.FragmentFilesBinding
import com.freepdf.manager.db.FileEntity
import com.freepdf.manager.ui.reader.ReaderActivity
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

/**
 * Files tab: Recent / Favorites / Recycle Bin, search, and file actions.
 */
@AndroidEntryPoint
class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FilesViewModel by viewModels()
    private lateinit var adapter: FileAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FileAdapter(
            onClick = { entity -> onFileClick(entity) },
            onLongClick = { entity -> showActions(entity) },
            onFavorite = { entity -> viewModel.setFavorite(entity, !entity.is_favorite) }
        )
        binding.filesList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = adapter
        }

        binding.filesTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setTab(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(binding.searchInput.text?.toString().orEmpty())
                true
            } else false
        }

        observe()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.files.collect { files ->
                    adapter.submitList(files)
                    val emptyText = when (viewModel.tab.value) {
                        FilesViewModel.TAB_BIN -> getString(R.string.recycle_bin_empty)
                        else -> getString(R.string.files_empty_hint)
                    }
                    binding.filesEmpty.text = emptyText
                    binding.filesEmpty.visibility =
                        if (files.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun runSearch(query: String) {
        if (query.isBlank()) {
            binding.searchInput.clearFocus()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val results = viewModel.searchResults(query)
            adapter.submitList(results)
            binding.filesEmpty.visibility =
                if (results.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun onFileClick(entity: FileEntity) {
        if (entity.deleted) {
            showActions(entity)
            return
        }
        val intent = android.content.Intent(requireContext(), ReaderActivity::class.java)
        intent.putExtra(ReaderActivity.EXTRA_PATH, entity.path)
        intent.putExtra(ReaderActivity.EXTRA_NAME, entity.name)
        startActivity(intent)
    }

    private fun showActions(entity: FileEntity) {
        val options = if (entity.deleted) {
            arrayOf(
                getString(R.string.files_restore),
                getString(R.string.delete)
            )
        } else {
            arrayOf(
                getString(R.string.files_open),
                getString(R.string.files_share),
                getString(R.string.files_favorite),
                getString(R.string.files_recycle_bin)
            )
        }
        AlertDialog.Builder(requireContext())
            .setTitle(entity.name)
            .setItems(options) { _, which ->
                if (entity.deleted) {
                    when (which) {
                        0 -> viewModel.restore(entity)
                        1 -> viewModel.hardDelete(entity)
                    }
                } else {
                    when (which) {
                        0 -> onFileClick(entity)
                        1 -> FileShare.shareFile(
                            requireContext(),
                            File(entity.path),
                            FileShare.mimeTypeFor(File(entity.path))
                        )
                        2 -> viewModel.setFavorite(entity, !entity.is_favorite)
                        3 -> confirmMoveToBin(entity)
                    }
                }
            }
            .show()
    }

    private fun confirmMoveToBin(entity: FileEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_move_bin)
            .setMessage("${entity.name} (${entity.size.humanSize()})")
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.softDelete(entity) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
