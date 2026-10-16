package systems.drakonix.picross

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.min

class PicrossView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Tool { FILL, MARK }

    companion object {
        const val EMPTY = 0
        const val FILLED = 1
        const val MARKED = 2
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

    init {
        setPalette(Themes.all[0])
    }

    fun setPalette(p: Palette) {
        gridPaint.color = p.fg.withAlpha(0.25f)
        blockPaint.color = p.fg.withAlpha(0.55f)
        fillPaint.color = p.fg
        markPaint.color = p.accent
        cluePaint.color = p.fg
        clueDonePaint.color = p.fg.withAlpha(0.35f)
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
        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(zoom, zoom)
        val clueCols = maxRowClueLen()
        val clueRows = maxColClueLen()
        val totalCols = l.cols + clueCols
        val totalRows = l.rows + clueRows
        cell = min(width.toFloat() / totalCols, height.toFloat() / totalRows)
        val boardW = cell * totalCols
        val boardH = cell * totalRows
        originX = (width - boardW) / 2 + clueCols * cell
        originY = (height - boardH) / 2 + clueRows * cell
        cluePaint.textSize = cell * 0.55f
        clueDonePaint.textSize = cluePaint.textSize

        drawClues(canvas, l)
        drawCells(canvas, l)
        drawGrid(canvas, l)
        canvas.restore()
    }

    private fun drawClues(canvas: Canvas, l: Level) {
        val textOffset = cell * 0.68f
        for (r in 0 until l.rows) {
            val clues = l.rowClues[r]
            val done = clues == currentRowClues(r)
            clues.forEachIndexed { i, n ->
                val x = originX - (clues.size - i - 0.5f) * cell
                val y = originY + r * cell + textOffset
                canvas.drawText(n.toString(), x, y, if (done) clueDonePaint else cluePaint)
            }
        }
        for (c in 0 until l.cols) {
            val clues = l.colClues[c]
            val done = clues == currentColClues(c)
            clues.forEachIndexed { i, n ->
                val x = originX + c * cell + cell / 2
                val y = originY - (clues.size - i - 1) * cell - cell + textOffset
                canvas.drawText(n.toString(), x, y, if (done) clueDonePaint else cluePaint)
            }
        }
    }

    private fun drawCells(canvas: Canvas, l: Level) {
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
                val (r, c) = cellAt(event.x, event.y) ?: return true
                painting = true
                paintFrom = state[r][c]
                paintTo = when (tool) {
                    Tool.FILL -> if (paintFrom == FILLED) EMPTY else FILLED
                    Tool.MARK -> if (paintFrom == MARKED) EMPTY else MARKED
                }
                applyPaint(r, c)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                painting = false
                val (mx, my) = midOf(event)
                panLastX = mx
                panLastY = my
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val (mx, my) = midOf(event)
                    panX += mx - panLastX
                    panY += my - panLastY
                    panLastX = mx
                    panLastY = my
                    clampPan()
                    invalidate()
                } else if (painting) {
                    cellAt(event.x, event.y)?.let { (r, c) -> applyPaint(r, c) }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val (mx, my) = midOf(event, skipIndex = event.actionIndex)
                panLastX = mx
                panLastY = my
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> painting = false
        }
        return true
    }

    private fun applyPaint(r: Int, c: Int) {
        if (state[r][c] != paintFrom || state[r][c] == paintTo) return
        state[r][c] = paintTo
        invalidate()
        if (isSolved()) {
            locked = true
            painting = false
            onSolved?.invoke()
        }
    }
}
