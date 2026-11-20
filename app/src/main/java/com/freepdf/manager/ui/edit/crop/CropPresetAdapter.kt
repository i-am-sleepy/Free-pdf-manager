package com.freepdf.manager.ui.edit.crop

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.freepdf.manager.databinding.ItemCropPresetBinding

/**
 * Horizontal row of crop-ratio chips in the editor's crop panel.
 * A null ratio means "free crop" (no ratio applied).
 */
class CropPresetAdapter(
    private val onClick: (Float?) -> Unit
) : RecyclerView.Adapter<CropPresetAdapter.VH>() {

    data class Preset(val ratio: Float?, val label: String)

    private val items = mutableListOf<Preset>()

    fun submit(list: List<Preset>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemCropPresetBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCropPresetBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.chip.text = item.label
        holder.binding.chip.setOnClickListener { onClick(item.ratio) }
    }

    override fun getItemCount(): Int = items.size
}
