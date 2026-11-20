package com.freepdf.manager.ui.tools

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.freepdf.manager.databinding.ItemToolBinding

/**
 * One PDF tool entry. [id] is a stable tool identifier (see ToolDef).
 */
data class ToolDef(
    val id: String,
    val titleRes: Int,
    val descRes: Int,
    val iconRes: Int
)

class ToolsAdapter(private val onClick: (ToolDef) -> Unit) :
    RecyclerView.Adapter<ToolsAdapter.VH>() {

    private val items = mutableListOf<ToolDef>()

    fun submit(list: List<ToolDef>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemToolBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemToolBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.toolTitle.setText(item.titleRes)
        holder.binding.toolDesc.setText(item.descRes)
        holder.binding.toolIcon.setImageResource(item.iconRes)
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
