package systems.drakonix.picross

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

class PicrossView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Tool { FILL, MARK, PAN }

    companion object {
        const val EMPTY = 0
        const val FILLED = 1
        const val MARKED = 2
        private const val GHOST_TAP_MS = 300L
    }

    var tool: Tool = Tool.FILL
    var onSolved: (() -> Unit)? = null

    private var level: Level? = null
    private var state: Array<IntArray> = emptyArray()
    private var locked = false

    // Drag painting: on down we decide what value we are painting and only
    // repaint cells that had the same state as the first touched cell.
    private var paintFrom = EMPTY
    private var paintTo = EMPTY
    private var painting = false

    // A stroke locks to the row or column it first moves along.
    private enum class Axis { NONE, ROW, COL }
    private var axis = Axis.NONE
    private var strokeR = 0
    private var strokeC = 0

    // The first finger of a pinch lands slightly before the second; if the
    // second arrives within this window the stroke is undone, not committed.
    private var strokeStartMs = 0L
    private var strokeSnapshot: Array<IntArray>? = null

    // Zoom (pinch) and pan (two-finger drag). One finger always paints.
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var panLastX = 0f
    private var panLastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val old = zoom
                zoom = (zoom * d.scaleFactor).coerceIn(1f, 4f)
                // Keep the pinch focus stationary while zooming.
                panX = d.focusX - (d.focusX - panX) * (zoom / old)
                panY = d.focusY - (d.focusY - panY) * (zoom / old)
                clampPan()
                invalidate()
                return true
            }
        }
    )

    private val gridPaint = Paint().apply { strokeWidth = 2f }
    private val blockPaint = Paint().apply { strokeWidth = 5f }
    private val fillPaint = Paint()
    private val revealPaint = Paint()
    private val markPaint = Paint().apply {
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private val cluePaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val clueDonePaint = Paint(cluePaint)
    private val clueCursorPaint = Paint(cluePaint).apply { isFakeBoldText = true }
    private val bgPaint = Paint()
    private val cursorLinePaint = Paint()
    private val cursorCellPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    // Cell under the painting finger, or -1 when not touching.
    private var cursorR = -1
    private var cursorC = -1

    init {
        setPalette(Themes.all[0])
    }

    fun setPalette(p: Palette) {
        bgPaint.color = p.bg
        gridPaint.color = p.fg.withAlpha(0.25f)
        blockPaint.color = p.fg.withAlpha(0.55f)
        fillPaint.color = p.fg
        markPaint.color = p.accent
        cluePaint.color = p.fg
        clueDonePaint.color = p.fg.withAlpha(0.35f)
        clueCursorPaint.color = p.accent
        cursorLinePaint.color = p.accent.withAlpha(0.18f)
        cursorCellPaint.color = p.accent
        invalidate()
    }

    // Geometry, recomputed in onDraw.
    private var cell = 0f
    private var originX = 0f
    private var originY = 0f

    fun setLevel(level: Level) {
        this.level = level
        state = Array(level.rows) { IntArray(level.cols) }
        locked = false
        resetViewport()
        invalidate()
    }

    fun reset() {
        val l = level ?: return
        state = Array(l.rows) { IntArray(l.cols) }
        locked = false
        resetViewport()
        invalidate()
    }

    private fun resetViewport() {
        zoom = 1f
        panX = 0f
        panY = 0f
    }

    fun resetView() {
        resetViewport()
        invalidate()
    }

    private fun clampPan() {
        panX = panX.coerceIn(width * (1 - zoom), 0f)
        panY = panY.coerceIn(height * (1 - zoom), 0f)
    }

    /** Board state as one char per cell ('0' empty, '1' filled, '2' marked), row-major. */
    fun exportState(): String =
        state.joinToString("") { row -> row.joinToString("") }

    /** True if any cell is filled or marked. */
    fun hasProgress(): Boolean = state.any { row -> row.any { it != EMPTY } }

    /** Restores a board exported by [exportState]; ignores input that doesn't fit. */
    fun importState(saved: String) {
        val l = level ?: return
        if (saved.length != l.rows * l.cols || saved.any { it !in '0'..'2' }) return
        for (r in 0 until l.rows) for (c in 0 until l.cols) {
            state[r][c] = saved[r * l.cols + c] - '0'
        }
        invalidate()
    }

    private fun maxRowClueLen() = level?.rowClues?.maxOf { it.size } ?: 0
    private fun maxColClueLen() = level?.colClues?.maxOf { it.size } ?: 0

    override fun onDraw(canvas: Canvas) {
        val l = level ?: return
        val clueCols = maxRowClueLen()
        val clueRows = maxColClueLen()
        val totalCols = l.cols + clueCols
        val totalRows = l.rows + clueRows
        cell = min(width.toFloat() / totalCols, height.toFloat() / totalRows)
        val boardW = cell * totalCols
        val boardH = cell * totalRows
        originX = (width - boardW) / 2 + clueCols * cell
        originY = (height - boardH) / 2 + clueRows * cell

        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(zoom, zoom)
        drawCells(canvas, l)
        drawGrid(canvas, l)
        if (cursorR >= 0) {
            val left = originX + cursorC * cell
            val top = originY + cursorR * cell
            canvas.drawRect(left, top, left + cell, top + cell, cursorCellPaint)
        }
        canvas.restore()

        drawClues(canvas, l)
    }

    /** Screen-space grid geometry: cell size, grid top-left, and clue band edges. */
    private class Layout(val cs: Float, val gx: Float, val gy: Float, val rowBandRight: Float, val colBandBottom: Float)

    private fun layout(): Layout {
        val cs = cell * zoom
        val gx = panX + originX * zoom
        val gy = panY + originY * zoom
        // Clue bands follow the grid but stick to the view edge once scrolled past it.
        return Layout(cs, gx, gy, max(gx, maxRowClueLen() * cs), max(gy, maxColClueLen() * cs))
    }

    /** Drawn in screen space, over the cells, so pinned clues stay readable while zoomed. */
    private fun drawClues(canvas: Canvas, l: Level) {
        val g = layout()
        cluePaint.textSize = g.cs * 0.55f
        clueDonePaint.textSize = cluePaint.textSize
        clueCursorPaint.textSize = cluePaint.textSize
        val textOffset = g.cs * 0.68f

        canvas.drawRect(0f, 0f, g.rowBandRight, height.toFloat(), bgPaint)
        for (r in 0 until l.rows) {
            val clues = l.rowClues[r]
            val paint = when {
                r == cursorR && axis != Axis.COL -> clueCursorPaint
                clues == currentRowClues(r) -> clueDonePaint
                else -> cluePaint
            }
            clues.forEachIndexed { i, n ->
                val x = g.rowBandRight - (clues.size - i - 0.5f) * g.cs
                val y = g.gy + r * g.cs + textOffset
                canvas.drawText(n.toString(), x, y, paint)
            }
        }

        canvas.drawRect(0f, 0f, width.toFloat(), g.colBandBottom, bgPaint)
        for (c in 0 until l.cols) {
            val clues = l.colClues[c]
            val paint = when {
                c == cursorC && axis != Axis.ROW -> clueCursorPaint
                clues == currentColClues(c) -> clueDonePaint
                else -> cluePaint
            }
            clues.forEachIndexed { i, n ->
                val x = g.gx + c * g.cs + g.cs / 2
                val y = g.colBandBottom - (clues.size - i) * g.cs + textOffset
                canvas.drawText(n.toString(), x, y, paint)
            }
        }

        // Corner where the bands overlap hides clues scrolled under both.
        canvas.drawRect(0f, 0f, g.rowBandRight, g.colBandBottom, bgPaint)
        if (g.rowBandRight > g.gx) {
            canvas.drawLine(g.rowBandRight, g.colBandBottom, g.rowBandRight, height.toFloat(), blockPaint)
        }
        if (g.colBandBottom > g.gy) {
            canvas.drawLine(g.rowBandRight, g.colBandBottom, width.toFloat(), g.colBandBottom, blockPaint)
        }
    }

    private fun drawCells(canvas: Canvas, l: Level) {
        if (cursorR >= 0) {
            // Once a stroke locks to a line, only that line stays highlighted.
            if (axis != Axis.COL) {
                canvas.drawRect(originX, originY + cursorR * cell, originX + l.cols * cell, originY + (cursorR + 1) * cell, cursorLinePaint)
            }
            if (axis != Axis.ROW) {
                canvas.drawRect(originX + cursorC * cell, originY, originX + (cursorC + 1) * cell, originY + l.rows * cell, cursorLinePaint)
            }
        }
        for (r in 0 until l.rows) for (c in 0 until l.cols) {
            val left = originX + c * cell
            val top = originY + r * cell
            when (state[r][c]) {
                FILLED -> {
                    // Picture colors stay hidden until solved; they'd give the answer away.
                    val paint = if (locked) {
                        revealPaint.apply { color = l.colorAt(r, c) ?: fillPaint.color }
                    } else fillPaint
                    canvas.drawRect(left + 1, top + 1, left + cell - 1, top + cell - 1, paint)
                }
                MARKED -> {
                    val inset = cell * 0.28f
                    canvas.drawLine(left + inset, top + inset, left + cell - inset, top + cell - inset, markPaint)
                    canvas.drawLine(left + cell - inset, top + inset, left + inset, top + cell - inset, markPaint)
                }
            }
        }
    }

    private fun drawGrid(canvas: Canvas, l: Level) {
        val right = originX + l.cols * cell
        val bottom = originY + l.rows * cell
        for (c in 0..l.cols) {
            val x = originX + c * cell
            val p = if (c % 5 == 0) blockPaint else gridPaint
            canvas.drawLine(x, originY, x, bottom, p)
        }
        for (r in 0..l.rows) {
            val y = originY + r * cell
            val p = if (r % 5 == 0) blockPaint else gridPaint
            canvas.drawLine(originX, y, right, y, p)
        }
    }

    private fun currentRowClues(r: Int): List<Int> {
        val l = level!!
        return computeClues(l.cols) { c -> state[r][c] == FILLED }
    }

    private fun currentColClues(c: Int): List<Int> {
        val l = level!!
        return computeClues(l.rows) { r -> state[r][c] == FILLED }
    }

    private fun isSolved(): Boolean {
        val l = level ?: return false
        return (0 until l.rows).all { l.rowClues[it] == currentRowClues(it) } &&
            (0 until l.cols).all { l.colClues[it] == currentColClues(it) }
    }

    /** Row/col under a screen point, through the zoom/pan transform; null if off-board. */
    private fun cellAt(x: Float, y: Float): Pair<Int, Int>? {
        val l = level ?: return null
        val g = layout()
        if (x < g.rowBandRight || y < g.colBandBottom) return null // under pinned clues
        val bx = (x - panX) / zoom
        val by = (y - panY) / zoom
        if (bx < originX || by < originY) return null
        val c = ((bx - originX) / cell).toInt()
        val r = ((by - originY) / cell).toInt()
        return if (r < l.rows && c < l.cols) r to c else null
    }

    private fun midOf(event: MotionEvent, skipIndex: Int = -1): Pair<Float, Float> {
        var sx = 0f
        var sy = 0f
        var n = 0
        for (i in 0 until event.pointerCount) {
            if (i == skipIndex) continue
            sx += event.getX(i)
            sy += event.getY(i)
            n++
        }
        return sx / n to sy / n
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (level == null || locked) return false
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                panLastX = event.x
                panLastY = event.y
                if (tool == Tool.PAN) return true
                val (r, c) = cellAt(event.x, event.y) ?: return true
                painting = true
                paintFrom = state[r][c]
                paintTo = when (tool) {
                    Tool.FILL -> if (paintFrom == FILLED) EMPTY else FILLED
                    Tool.MARK -> if (paintFrom == MARKED) EMPTY else MARKED
                    Tool.PAN -> return true
                }
                axis = Axis.NONE
                strokeR = r
                strokeC = c
                strokeStartMs = event.eventTime
                strokeSnapshot = Array(state.size) { state[it].copyOf() }
                moveCursor(r, c)
                applyPaint(r, c)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val snapshot = strokeSnapshot
                if (painting && snapshot != null && event.eventTime - strokeStartMs < GHOST_TAP_MS) {
                    state = snapshot
                    invalidate()
                }
                endStroke()
                val (mx, my) = midOf(event)
                panLastX = mx
                panLastY = my
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2 || tool == Tool.PAN) {
                    val (mx, my) = midOf(event)
                    panX += mx - panLastX
                    panY += my - panLastY
                    panLastX = mx
                    panLastY = my
                    clampPan()
                    invalidate()
                } else if (painting) {
                    dragTo(event.x, event.y)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val (mx, my) = midOf(event, skipIndex = event.actionIndex)
                panLastX = mx
                panLastY = my
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endStroke()
        }
        return true
    }

    private fun endStroke() {
        painting = false
        axis = Axis.NONE
        strokeSnapshot = null
        moveCursor(-1, -1)
    }

    /** Extends the stroke toward a screen point, locked to the stroke's row or column. */
    private fun dragTo(x: Float, y: Float) {
        val l = level ?: return
        // Raw cell coords, unclamped, so drifting off the line still tracks along it.
        val r = floor(((y - panY) / zoom - originY) / cell).toInt()
        val c = floor(((x - panX) / zoom - originX) / cell).toInt()
        if (axis == Axis.NONE) {
            if (r == strokeR && c == strokeC) return
            axis = if (abs(c - strokeC) >= abs(r - strokeR)) Axis.ROW else Axis.COL
            invalidate()
        }
        val (tr, tc) = when (axis) {
            Axis.ROW -> strokeR to c.coerceIn(0, l.cols - 1)
            else -> r.coerceIn(0, l.rows - 1) to strokeC
        }
        // Fill every cell between the cursor and the target so fast swipes don't skip.
        var cr = cursorR
        var cc = cursorC
        while (painting && (cr != tr || cc != tc)) {
            cr += (tr - cr).sign
            cc += (tc - cc).sign
            applyPaint(cr, cc)
        }
        if (painting) moveCursor(tr, tc)
    }

    private fun moveCursor(r: Int, c: Int) {
        if (r == cursorR && c == cursorC) return
        cursorR = r
        cursorC = c
        invalidate()
    }

    private fun applyPaint(r: Int, c: Int) {
        if (state[r][c] != paintFrom || state[r][c] == paintTo) return
        state[r][c] = paintTo
        invalidate()
        if (isSolved()) {
            locked = true
            endStroke()
            onSolved?.invoke()
        }
    }
}
