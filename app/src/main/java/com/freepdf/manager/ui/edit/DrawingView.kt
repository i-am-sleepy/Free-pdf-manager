package com.freepdf.manager.ui.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * Free-hand drawing canvas with undo/redo, color and brush size.
 * Used both for the doodle overlay and the signature pad.
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var currentPath = Path()
    private val strokes = mutableListOf<Path>()
    private val undoStack = mutableListOf<Pair<Path, Paint>>()
    private val redoStack = mutableListOf<Pair<Path, Paint>>()

    private var lastX = 0f
    private var lastY = 0f

    var strokeColor: Int = Color.BLACK
        set(value) {
            field = value
            strokePaint.color = value
        }

    var strokeWidth: Float = 12f
        set(value) {
            field = value
            strokePaint.strokeWidth = value
        }

    var isEraser: Boolean = false
        set(value) {
            field = value
            strokePaint.xfermode = if (value) android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) else null
        }

    fun undo() {
        if (strokes.isEmpty()) return
        val last = strokes.removeAt(strokes.size - 1)
        undoStack.add(last to strokeSnapshot())
        redoStack.clear()
        invalidate()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val (path, paint) = redoStack.removeAt(redoStack.size - 1)
        strokes.add(path)
        undoStack.add(path to paint)
        invalidate()
    }

    fun canUndo() = strokes.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    fun clear() {
        strokes.clear()
        undoStack.clear()
        redoStack.clear()
        invalidate()
    }

    fun hasContent() = strokes.isNotEmpty()

    /**
     * Renders all strokes into a transparent [size]x[size] bitmap so they can
     * be composited onto the edited image.
     */
    fun toBitmap(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawStrokes(canvas)
        return bmp
    }

    private fun strokeSnapshot(): Paint {
        val p = Paint(strokePaint)
        return p
    }

    private fun drawStrokes(canvas: Canvas) {
        for (s in strokes) {
            canvas.drawPath(s, strokePaint)
        }
        if (currentPath != Path()) {
            canvas.drawPath(currentPath, strokePaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawStrokes(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = x
                lastY = y
                currentPath.reset()
                currentPath.moveTo(x, y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                val dy = y - lastY
                if (hypot(dx, dy) >= 1f) {
                    currentPath.quadTo(lastX, lastY, (lastX + x) / 2, (lastY + y) / 2)
                    lastX = x
                    lastY = y
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                currentPath.lineTo(x, y)
                strokes.add(currentPath)
                undoStack.add(currentPath to strokeSnapshot())
                redoStack.clear()
                currentPath = Path()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
