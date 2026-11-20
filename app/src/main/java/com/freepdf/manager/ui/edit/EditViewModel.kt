package com.freepdf.manager.ui.edit

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.freepdf.manager.image.ImageFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Holds the editor's bitmap state and a bounded undo/redo stack.
 *
 * Keeping the bitmaps (not the view) in the ViewModel means the UI can be
 * recreated (rotation, config change) without losing the work-in-progress
 * image. The stack is capped at [MAX_HISTORY] to bound memory.
 */
@HiltViewModel
class EditViewModel @Inject constructor() : ViewModel() {

    private val history = ArrayDeque<Bitmap>()
    private val redoStack = ArrayDeque<Bitmap>()

    private var _original: Bitmap? = null
    private var _current: Bitmap? = null

    val original: Bitmap? get() = _original
    val current: Bitmap? get() = _current

    /** Loads a fresh image, clearing any previous session. */
    fun load(bitmap: Bitmap) {
        history.clear()
        redoStack.clear()
        _original = bitmap
        _current = bitmap
    }

    /** Commits a new state, pushing the previous one onto the undo stack. */
    fun commit(newBitmap: Bitmap) {
        val prev = _current ?: return
        if (history.size >= MAX_HISTORY) {
            history.removeFirst()
        }
        history.addLast(prev)
        _current = newBitmap
        redoStack.clear()
    }

    fun canUndo(): Boolean = history.isNotEmpty()

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo(): Bitmap? {
        val prev = history.removeLastOrNull() ?: return null
        redoStack.addLast(_current!!)
        _current = prev
        return prev
    }

    fun redo(): Bitmap? {
        val next = redoStack.removeLastOrNull() ?: return null
        history.addLast(_current!!)
        _current = next
        return next
    }

    /** Convenience: apply an adjust and commit in one step. */
    fun applyAdjust(brightness: Float, contrast: Float, saturation: Float): Bitmap? {
        val src = _current ?: return null
        val out = ImageFilters.adjust(src, brightness, contrast, saturation)
        if (out != src) commit(out)
        return _current
    }

    /** Convenience: apply an editor filter and commit. */
    fun applyFilter(filter: ImageFilters.EditorFilter): Bitmap? {
        val src = _current ?: return null
        val out = ImageFilters.applyEditorFilter(src, filter)
        commit(out)
        return _current
    }

    fun crop(rect: android.graphics.Rect): Bitmap? {
        val src = _current ?: return null
        val out = ImageFilters.crop(src, rect)
        commit(out)
        return _current
    }

    fun rotate(degrees: Int): Bitmap? {
        val src = _current ?: return null
        commit(ImageFilters.rotate(src, degrees))
        return _current
    }

    fun flip(horizontal: Boolean): Bitmap? {
        val src = _current ?: return null
        commit(ImageFilters.flip(src, horizontal))
        return _current
    }

    fun overlay(overlayBitmap: Bitmap): Bitmap? {
        val src = _current ?: return null
        commit(ImageFilters.overlay(src, overlayBitmap))
        return _current
    }

    override fun onCleared() {
        super.onCleared()
        _original?.recycle()
        _current?.recycle()
        history.forEach { it.recycle() }
        redoStack.forEach { it.recycle() }
        history.clear()
        redoStack.clear()
    }

    companion object {
        const val MAX_HISTORY = 20
    }
}
