package com.freepdf.manager.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import com.freepdf.manager.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure-Kotlin image processing pipeline (no native libraries — the reference
 * APK ships zero .so files, so all effects are done on bitmaps).
 *
 * Every function returns a NEW bitmap and leaves the input untouched, which
 * makes undo/redo in the editor trivial (just keep the previous bitmap).
 */
object ImageFilters {

    /** The 8 scan filters, matching the reference app's filter set. */
    enum class ScanFilter(val labelRes: Int) {
        ORIGINAL(R.string.filter_original),
        BW(R.string.filter_bw),
        BW2(R.string.filter_bw2),
        GRAY(R.string.filter_gray),
        INVERT(R.string.filter_invert),
        ENHANCE(R.string.filter_enhance),
        SUPER(R.string.filter_super),
        SUPER2(R.string.filter_super2)
    }

    /** The 7 editor filters (creative presets). */
    enum class EditorFilter {
        ORIGINAL, B_W, GRAY, SEPIA, VIVID, WARM, COOL
    }

    // region Scan filters

    /**
     * Applies the selected scan filter to a captured page.
     *
     * ORIGINAL  – as captured
     * BW       – hard threshold, black ink on white paper
     * BW2      – inverted threshold (white ink on black paper)
     * GRAY     – luminance grayscale
     * INVERT   – photographic negative
     * ENHANCE  – grayscale + auto contrast stretch
     * SUPER    – ENHANCE + sharpen + slight saturation boost
     * SUPER2   – SUPER + stronger sharpen (halo removal for prints)
     */
    fun applyScanFilter(src: Bitmap, filter: ScanFilter): Bitmap = when (filter) {
        ScanFilter.ORIGINAL -> src.copy(Bitmap.Config.ARGB_8888, false)
        ScanFilter.BW -> threshold(src, 140, invert = false)
        ScanFilter.BW2 -> threshold(src, 140, invert = true)
        ScanFilter.GRAY -> toGrayscale(src)
        ScanFilter.INVERT -> invert(src)
        ScanFilter.ENHANCE -> contrastStretch(toGrayscale(src))
        ScanFilter.SUPER -> sharpen(contrastStretch(toGrayscale(src)), strength = 1.0f)
        ScanFilter.SUPER2 -> sharpen(contrastStretch(toGrayscale(src)), strength = 2.0f)
    }

    /** Hard threshold: pixels above [t] become white, below become black. */
    private fun threshold(src: Bitmap, t: Int, invert: Boolean): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val lum = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)).roundToInt()
            val white = lum >= t
            val v = if (white == invert) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            pixels[i] = v
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /** Luminance-weighted grayscale. */
    private fun toGrayscale(src: Bitmap): Bitmap {
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        return colorMatrix(src, cm)
    }

    /** Photographic negative. */
    private fun invert(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = Color.alpha(c)
            pixels[i] = Color.argb(a, 255 - Color.red(c), 255 - Color.green(c), 255 - Color.blue(c))
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Auto contrast: stretches the histogram so the 1st/99th percentiles map
     * to black/white. Gives scanned pages crisp blacks without clipping.
     */
    private fun contrastStretch(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // Histogram of luminance
        val hist = IntArray(256)
        for (c in pixels) {
            val lum = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)).roundToInt()
            hist[lum]++
        }
        val total = pixels.size.toLong()
        var low = 0
        var high = 255
        var acc = 0L
        for (i in 0 until 256) {
            acc += hist[i]
            if (acc >= total * 0.01) { low = i; break }
        }
        acc = 0L
        for (i in 255 downTo 0) {
            acc += hist[i]
            if (acc >= total * 0.01) { high = i; break }
        }
        if (high - low < 8) return src.copy(Bitmap.Config.ARGB_8888, false)
        val scale = 255f / (high - low)

        for (i in pixels.indices) {
            val c = pixels[i]
            val a = Color.alpha(c)
            fun map(v: Int): Int {
                val m = ((v - low) * scale).roundToInt().coerceIn(0, 255)
                return m
            }
            pixels[i] = Color.argb(a, map(Color.red(c)), map(Color.green(c)), map(Color.blue(c)))
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /** 3x3 unsharp mask with [strength] (1.0 mild, 2.0 strong). */
    private fun sharpen(src: Bitmap, strength: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val result = IntArray(w * h)
        val c = 1f + 4f * strength
        val s = -strength

        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    result[i] = pixels[i]
                    continue
                }
                val p = pixels
                for (ch in 0..3) {
                    fun comp(idx: Int): Float = (p[idx] ushr (ch * 8)) and 0xFF
                    val v = c * comp(i) +
                        s * (comp(i - 1) + comp(i + 1) + comp(i - w) + comp(i + w))
                    result[i] = result[i] or (v.roundToInt().coerceIn(0, 255) shl (ch * 8))
                }
            }
        }
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }

    // endregion

    // region Adjustments

    /**
     * Applies brightness / contrast / saturation adjustments.
     * Each value is in -100..100 (percent), 0 = unchanged.
     */
    fun adjust(src: Bitmap, brightness: Float, contrast: Float, saturation: Float): Bitmap {
        if (brightness == 0f && contrast == 0f && saturation == 0f) {
            return src.copy(Bitmap.Config.ARGB_8888, false)
        }
        val cm = ColorMatrix()
        // Build: scale channels around 0.5, then offset for brightness.
        val scale = if (contrast > 0) 1f + contrast / 100f else 1f / (1f - contrast / 100f)
        val translate = brightness / 100f * 255f

        val m = FloatArray(20)
        // R,G,B rows: [scale,0,0,0,offset]
        val off = 127.5f - 127.5f * scale + translate
        m[0] = scale; m[1] = 0f; m[2] = 0f; m[3] = 0f; m[4] = off
        m[5] = 0f; m[6] = scale; m[7] = 0f; m[8] = 0f; m[9] = off
        m[10] = 0f; m[11] = 0f; m[12] = scale; m[13] = 0f; m[14] = off
        // Alpha row identity
        m[15] = 1f; m[16] = 0f; m[17] = 0f; m[18] = 0f; m[19] = 0f
        cm.set(m)

        var out = colorMatrix(src, cm)
        if (saturation != 0f) {
            val sat = ColorMatrix()
            sat.setSaturation((100f + saturation) / 100f)
            out = colorMatrix(out, sat)
        }
        return out
    }

    // endregion

    // region Editor filters

    /** Applies a creative editor filter preset. */
    fun applyEditorFilter(src: Bitmap, filter: EditorFilter): Bitmap = when (filter) {
        EditorFilter.ORIGINAL -> src.copy(Bitmap.Config.ARGB_8888, false)
        EditorFilter.B_W -> threshold(src, 128, invert = false)
        EditorFilter.GRAY -> toGrayscale(src)
        EditorFilter.SEPIA -> sepia(src)
        EditorFilter.VIVID -> vivid(src)
        EditorFilter.WARM -> warm(src)
        EditorFilter.COOL -> cool(src)
    }

    private fun sepia(src: Bitmap): Bitmap {
        val m = floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        return colorMatrix(src, ColorMatrix(m))
    }

    private fun vivid(src: Bitmap): Bitmap {
        val m = floatArrayOf(
            1.2f, 0f, 0f, 0f, 8f,
            0f, 1.2f, 0f, 0f, 8f,
            0f, 0f, 1.2f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f
        )
        return colorMatrix(src, ColorMatrix(m))
    }

    private fun warm(src: Bitmap): Bitmap {
        val m = floatArrayOf(
            1.1f, 0f, 0f, 0f, 10f,
            0f, 1.0f, 0f, 0f, 0f,
            0f, 0f, 0.9f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        )
        return colorMatrix(src, ColorMatrix(m))
    }

    private fun cool(src: Bitmap): Bitmap {
        val m = floatArrayOf(
            0.9f, 0f, 0f, 0f, -10f,
            0f, 1.0f, 0f, 0f, 0f,
            0f, 0f, 1.1f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )
        return colorMatrix(src, ColorMatrix(m))
    }

    // endregion

    // region Helpers

    private fun colorMatrix(src: Bitmap, cm: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /**
     * Composite [overlay] (e.g. doodle strokes or a signature) on top of [base]
     * using its alpha channel.
     */
    fun overlay(base: Bitmap, overlay: Bitmap): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        canvas.drawBitmap(overlay, 0f, 0f, paint)
        return out
    }

    /** Crops [src] to [rect] (rect in src pixel coordinates). */
    fun crop(src: Bitmap, rect: Rect): Bitmap {
        val r = Rect(rect)
        r.left = r.left.coerceIn(0, src.width)
        r.top = r.top.coerceIn(0, src.height)
        r.right = r.right.coerceIn(0, src.width)
        r.bottom = r.bottom.coerceIn(0, src.height)
        if (r.width() <= 0 || r.height() <= 0) return src.copy(Bitmap.Config.ARGB_8888, false)
        return Bitmap.createBitmap(src, r.left, r.top, r.width(), r.height())
    }

    /** Rotates 90° clockwise / counter-clockwise. */
    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src.copy(Bitmap.Config.ARGB_8888, false)
        val out = Bitmap.createBitmap(
            if (degrees % 180 == 0) src.width else src.height,
            if (degrees % 180 == 0) src.height else src.width,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.rotate(degrees.toFloat(), src.width / 2f, src.height / 2f)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** Horizontal / vertical flip. */
    fun flip(src: Bitmap, horizontal: Boolean): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val matrix = android.graphics.Matrix()
        if (horizontal) matrix.preScale(-1f, 1f) else matrix.preScale(1f, -1f)
        canvas.drawBitmap(src, matrix, null)
        return out
    }

    /**
     * Perspective-corrects a quadrilateral [quad] (4 corners of a detected
     * document, in pixel coords of [src]) to a rectangle.
     *
     * Uses the standard homography approach with a 4x4 matrix approximation
     * (affine + bilinear fallback). For a phone-captured document the camera
     * is close to fronto-parallel, so the affine approximation is accurate
     * enough and far cheaper than a full 3x3 homography solve.
     */
    fun perspectiveCorrect(src: Bitmap, quad: List<IntArray>): Bitmap {
        if (quad.size != 4) return src.copy(Bitmap.Config.ARGB_8888, false)
        val (tl, tr, br, bl) = quad
        val dstW = maxOf(
            (tr[0] - tl[0]).let { abs(it) },
            (br[0] - bl[0]).let { abs(it) },
            1
        )
        val dstH = maxOf(
            (bl[0] - tl[0]).let { abs(it) },
            (br[0] - tr[0]).let { abs(it) },
            1
        )
        if (dstW < 8 || dstH < 8) return src.copy(Bitmap.Config.ARGB_8888, false)

        // Solve 3x3 homography from 4 point correspondences.
        val H = solveHomography(tl, tr, br, bl, dstW, dstH) ?: return src.copy(Bitmap.Config.ARGB_8888, false)
        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(src.width * src.height)
        src.getPixels(srcPixels, 0, src.width, 0, 0, src.width, src.height)
        val dstPixels = IntArray(dstW * dstH)
        val w = src.width.toFloat()
        val h = src.height.toFloat()

        for (y in 0 until dstH) {
            for (x in 0 until dstW) {
                // Inverse map: dst -> src
                val denom = H[6] * x + H[7] * y + H[8]
                if (abs(denom) < 1e-9f) continue
                val sx = (H[0] * x + H[1] * y + H[2]) / denom
                val sy = (H[3] * x + H[4] * y + H[5]) / denom
                if (sx < 0 || sy < 0 || sx >= w || sy >= h) continue
                // Bilinear sample
                val x0 = sx.toInt()
                val y0 = sy.toInt()
                val fx = sx - x0
                val fy = sy - y0
                val i00 = y0 * src.width + x0
                val c00 = srcPixels[i00]
                val c10 = if (x0 + 1 < src.width) srcPixels[i00 + 1] else c00
                val c01 = if (y0 + 1 < src.height) srcPixels[i00 + src.width] else c00
                val c11 = if (x0 + 1 < src.width && y0 + 1 < src.height)
                    srcPixels[i00 + src.width + 1] else c00
                fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).roundToInt()
                val r = lerp(lerp(Color.red(c00), Color.red(c10), fx),
                    lerp(Color.red(c01), Color.red(c11), fx), fy)
                val g = lerp(lerp(Color.green(c00), Color.green(c10), fx),
                    lerp(Color.green(c01), Color.green(c11), fx), fy)
                val b = lerp(lerp(Color.blue(c00), Color.blue(c10), fx),
                    lerp(Color.blue(c01), Color.blue(c11), fx), fy)
                val a = lerp(lerp(Color.alpha(c00), Color.alpha(c10), fx),
                    lerp(Color.alpha(c01), Color.alpha(c11), fx), fy)
                dstPixels[y * dstW + x] = Color.argb(a, r, g, b)
            }
        }
        out.setPixels(dstPixels, 0, dstW, 0, 0, dstW, dstH)
        return out
    }

    /**
     * Solves the 3x3 homography matrix mapping the destination grid
     * (0,0)-(dstW,dstH) to the source quadrilateral. Returns 9 floats
     * (row-major) or null if the quad is degenerate.
     */
    private fun solveHomography(
        tl: IntArray, tr: IntArray, br: IntArray, bl: IntArray,
        dstW: Int, dstH: Int
    ): FloatArray? {
        // dst points (grid) -> src points (quad)
        val dx = floatArrayOf(0f, dstW.toFloat(), dstW.toFloat(), 0f)
        val dy = floatArrayOf(0f, 0f, dstH.toFloat(), dstH.toFloat())
        val sx = floatArrayOf(tl[0].toFloat(), tr[0].toFloat(), br[0].toFloat(), bl[0].toFloat())
        val sy = floatArrayOf(tl[1].toFloat(), tr[1].toFloat(), br[1].toFloat(), bl[1].toFloat())

        // Solve 8 equations (h33 = 1) via Gaussian elimination.
        // For each point:
        //   x' = (h11 x + h12 y + h13) / (h31 x + h32 y + 1)
        //   y' = (h21 x + h22 y + h23) / (h31 x + h32 y + 1)
        // Linear in h11..h32 (8 unknowns).
        val A = Array(8) { FloatArray(9) } // 8 rows, 8 cols + rhs
        for (i in 0 until 4) {
            val x = dx[i]; val y = dy[i]
            A[i * 2][0] = x; A[i * 2][1] = y; A[i * 2][2] = 1f; A[i * 2][3] = 0f; A[i * 2][4] = 0f; A[i * 2][5] = 0f; A[i * 2][6] = -sx[i] * x; A[i * 2][7] = -sx[i] * y; A[i * 2][8] = sx[i]
            A[i * 2 + 1][0] = 0f; A[i * 2 + 1][1] = 0f; A[i * 2 + 1][2] = 0f; A[i * 2 + 1][3] = x; A[i * 2 + 1][4] = y; A[i * 2 + 1][5] = 1f; A[i * 2 + 1][6] = -sy[i] * x; A[i * 2 + 1][7] = -sy[i] * y; A[i * 2 + 1][8] = sy[i]
        }
        val h = gaussianSolve(A, 8) ?: return null

        // h = [h11, h12, h13, h21, h22, h23, h31, h32]
        // Return row-major 3x3 with h33 = 1:
        return floatArrayOf(
            h[0], h[1], h[2],
            h[3], h[4], h[5],
            h[6], h[7], 1f
        )
    }

    private fun gaussianSolve(A: Array<FloatArray>, n: Int): FloatArray? {
        val m = Array(n) { FloatArray(n + 1) }
        for (i in 0 until n) {
            for (j in 0 until n + 1) m[i][j] = A[i][j]
        }
        for (col in 0 until n) {
            // Partial pivoting
            var maxRow = col
            for (r in col + 1 until n) {
                if (abs(m[r][col]) > abs(m[maxRow][col])) maxRow = r
            }
            if (abs(m[maxRow][col]) < 1e-9f) return null
            if (maxRow != col) m[col] = m[maxRow].copyOf()
            for (r in col + 1 until n) {
                val factor = m[r][col] / m[col][col]
                for (c in col until n + 1) m[r][c] -= factor * m[col][c]
            }
        }
        val x = FloatArray(n)
        for (i in n - 1 downTo 0) {
            var sum = m[i][n]
            for (j in i + 1 until n) sum -= m[i][j] * x[j]
            x[i] = sum / m[i][i]
        }
        return x
    }

    // endregion
}
