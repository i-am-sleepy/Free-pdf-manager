package com.freepdf.manager.ui.files

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.freepdf.manager.R
import com.freepdf.manager.core.Constants
import com.freepdf.manager.core.humanSize
import com.freepdf.manager.databinding.ItemFileBinding
import com.freepdf.manager.db.FileEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for file rows (used by Home "Recent" and Files tab).
 */
class FileAdapter(
    private val onClick: (FileEntity) -> Unit,
    private val onLongClick: (FileEntity) -> Unit,
    private val onFavorite: (FileEntity) -> Unit
) : ListAdapter<FileEntity, FileAdapter.VH>(DIFF) {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    inner class VH(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.binding
        b.itemName.text = item.name
        b.itemMeta.text = buildMeta(item)
        b.itemIcon.setImageResource(iconFor(item))
        b.itemFavorite.visibility =
            if (item.is_favorite) View.VISIBLE else View.INVISIBLE

        b.root.setOnClickListener { onClick(item) }
        b.root.setOnLongClickListener { onLongClick(item); true }
        b.itemFavorite.setOnClickListener { onFavorite(item) }
    }

    private fun iconFor(item: FileEntity): Int = when (item.kind) {
        Constants.FileKind.FOLDER -> R.drawable.ic_folder
        Constants.FileKind.IMAGE -> R.drawable.ic_pdf_to_image
        else -> R.drawable.ic_page
    }

    private fun buildMeta(item: FileEntity): String {
        val parts = mutableListOf<String>()
        if (item.page_count > 0) parts.add("${item.page_count} pages")
        if (item.kind != Constants.FileKind.FOLDER) parts.add(item.size.humanSize())
        parts.add(dateFormat.format(Date(item.updated_at)))
        return parts.joinToString(" · ")
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FileEntity>() {
            override fun areItemsTheSame(a: FileEntity, b: FileEntity) = a.id == b.id
            override fun areContentsTheSame(a: FileEntity, b: FileEntity) = a == b
        }
    }
}
