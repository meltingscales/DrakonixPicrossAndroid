package com.drakonix.picross

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
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

    private val gridPaint = Paint().apply { strokeWidth = 2f }
    private val blockPaint = Paint().apply { strokeWidth = 5f }
    private val fillPaint = Paint()
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
        invalidate()
    }

    fun reset() {
        val l = level ?: return
        state = Array(l.rows) { IntArray(l.cols) }
        locked = false
        invalidate()
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
        cluePaint.textSize = cell * 0.55f
        clueDonePaint.textSize = cluePaint.textSize

        drawClues(canvas, l)
        drawCells(canvas, l)
        drawGrid(canvas, l)
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
                FILLED -> canvas.drawRect(left + 1, top + 1, left + cell - 1, top + cell - 1, fillPaint)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val l = level ?: return false
        if (locked) return false
        val c = ((event.x - originX) / cell).toInt()
        val r = ((event.y - originY) / cell).toInt()
        val inside = event.x >= originX && event.y >= originY && r < l.rows && c < l.cols

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!inside) return false
                painting = true
                paintFrom = state[r][c]
                paintTo = when (tool) {
                    Tool.FILL -> if (paintFrom == FILLED) EMPTY else FILLED
                    Tool.MARK -> if (paintFrom == MARKED) EMPTY else MARKED
                }
                applyPaint(r, c)
            }
            MotionEvent.ACTION_MOVE -> if (painting && inside) applyPaint(r, c)
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
