package com.freepdf.manager.ui.edit.filter

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.freepdf.manager.databinding.ItemFilterBinding
import com.freepdf.manager.image.ImageFilters

/**
 * Horizontal row of filter thumbnails in the editor. Each cell shows a
 * small preview of the current image with the filter applied, so the user
 * can see the effect before committing.
 */
class FilterAdapter(
    private val onClick: (ImageFilters.EditorFilter, Bitmap) -> Unit
) : RecyclerView.Adapter<FilterAdapter.VH>() {

    data class Item(val filter: ImageFilters.EditorFilter, val label: String)

    private val items = mutableListOf<Item>()
    private val previews = mutableMapOf<ImageFilters.EditorFilter, Bitmap>()

    constructor(list: List<Item>, onClick: (ImageFilters.EditorFilter, Bitmap) -> Unit) : this(onClick) {
        submit(list)
    }

    fun submit(list: List<Item>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    /** Generates the small preview bitmaps for every filter. */
    fun updatePreviews(base: Bitmap) {
        val thumb = Bitmap.createScaledBitmap(base, 72, 72, true)
        for (item in items) {
            val p = ImageFilters.applyEditorFilter(thumb, item.filter)
            previews[item.filter] = p
        }
        thumb.recycle()
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemFilterBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFilterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.label.text = item.label
        holder.binding.preview.setImageBitmap(previews[item.filter])
        holder.binding.root.setOnClickListener {
            onClick(item.filter, previews[item.filter] ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        }
    }

    override fun getItemCount(): Int = items.size

    fun recyclePreviews() {
        previews.values.forEach { it.recycle() }
        previews.clear()
    }
}
