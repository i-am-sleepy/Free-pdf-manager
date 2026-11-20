package com.freepdf.manager.ui.tools

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.freepdf.manager.R
import com.freepdf.manager.databinding.FragmentToolsBinding
import com.freepdf.manager.ui.builder.BuilderActivity
import com.freepdf.manager.ui.pdf.PdfToolsActivity
import com.freepdf.manager.ui.scan.ScanActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Tools tab: the full catalog of PDF tools.
 * Tapping a tool opens the matching screen (PdfToolsActivity with a tool id,
 * or the dedicated builder / scanner / reader activity).
 */
@AndroidEntryPoint
class ToolsFragment : Fragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ToolsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ToolsAdapter { tool -> onToolClicked(tool) }
        binding.toolsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = adapter
            isNestedScrollingEnabled = false
        }

        adapter.submit(
            listOf(
                // PDF creation
                ToolDef("image_to_pdf", R.string.tool_image_to_pdf, R.string.tool_image_to_pdf_desc, R.drawable.ic_image_to_pdf),
                ToolDef("text_to_pdf", R.string.tool_pdf_to_text, R.string.home_text_to_pdf_desc, R.drawable.ic_text_to_pdf),
                ToolDef("blank_pdf", R.string.home_blank_pdf, R.string.home_blank_pdf_desc, R.drawable.ic_blank_pdf),
                // PDF editing
                ToolDef("merge", R.string.tool_merge, R.string.tool_merge_desc, R.drawable.ic_merge),
                ToolDef("split", R.string.tool_split, R.string.tool_split_desc, R.drawable.ic_split),
                ToolDef("compress", R.string.tool_compress, R.string.tool_compress_desc, R.drawable.ic_compress),
                ToolDef("rotate", R.string.tool_rotate, R.string.tool_rotate_desc, R.drawable.ic_rotate),
                ToolDef("delete_pages", R.string.tool_delete_pages, R.string.tool_delete_pages_desc, R.drawable.ic_delete_pages),
                ToolDef("extract_pages", R.string.tool_extract_pages, R.string.tool_extract_pages_desc, R.drawable.ic_extract_pages),
                ToolDef("watermark", R.string.tool_watermark, R.string.tool_watermark_desc, R.drawable.ic_watermark),
                ToolDef("remove_watermark", R.string.tool_remove_watermark, R.string.tool_remove_watermark_desc, R.drawable.ic_remove_watermark),
                ToolDef("page_numbers", R.string.tool_page_numbers, R.string.tool_page_numbers_desc, R.drawable.ic_page_numbers),
                // Security
                ToolDef("lock", R.string.tool_lock, R.string.tool_lock_desc, R.drawable.ic_lock),
                ToolDef("unlock", R.string.tool_unlock, R.string.tool_unlock_desc, R.drawable.ic_unlock),
                // Conversions
                ToolDef("pdf_to_image", R.string.tool_pdf_to_image, R.string.tool_pdf_to_image_desc, R.drawable.ic_pdf_to_image),
                ToolDef("pdf_to_text", R.string.tool_pdf_to_text, R.string.tool_pdf_to_text_desc, R.drawable.ic_pdf_to_text),
                ToolDef("word_to_pdf", R.string.tool_word_to_pdf, R.string.tool_word_to_pdf_desc, R.drawable.ic_word_to_pdf),
                ToolDef("ppt_to_pdf", R.string.tool_ppt_to_pdf, R.string.tool_ppt_to_pdf_desc, R.drawable.ic_ppt_to_pdf),
                ToolDef("excel_to_pdf", R.string.tool_excel_to_pdf, R.string.tool_excel_to_pdf_desc, R.drawable.ic_excel_to_pdf),
                // Info & output
                ToolDef("pdf_info", R.string.tool_pdf_info, R.string.tool_pdf_info_desc, R.drawable.ic_pdf_info),
                ToolDef("print", R.string.tool_print, R.string.tool_print_desc, R.drawable.ic_print)
            )
        )
    }

    private fun onToolClicked(tool: ToolDef) {
        when (tool.id) {
            "image_to_pdf" -> {
                val intent = Intent(requireContext(), BuilderActivity::class.java)
                intent.putExtra(BuilderActivity.EXTRA_MODE, BuilderActivity.MODE_IMAGES)
                startActivity(intent)
            }
            "text_to_pdf" -> {
                val intent = Intent(requireContext(), BuilderActivity::class.java)
                intent.putExtra(BuilderActivity.EXTRA_MODE, BuilderActivity.MODE_TEXT)
                startActivity(intent)
            }
            "blank_pdf" -> {
                val intent = Intent(requireContext(), BuilderActivity::class.java)
                intent.putExtra(BuilderActivity.EXTRA_MODE, BuilderActivity.MODE_BLANK)
                startActivity(intent)
            }
            "scan" -> startActivity(Intent(requireContext(), ScanActivity::class.java))
            else -> {
                val intent = Intent(requireContext(), PdfToolsActivity::class.java)
                intent.putExtra(PdfToolsActivity.EXTRA_TOOL, tool.id)
                startActivity(intent)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
