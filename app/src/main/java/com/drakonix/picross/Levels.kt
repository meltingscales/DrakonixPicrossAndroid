package com.drakonix.picross

import android.content.Context

/**
 * A picross puzzle. [grid] rows use '#' for filled and '.' for empty;
 * all rows must have equal length.
 */
class Level(val name: String, grid: List<String>) {
    init {
        require(grid.isNotEmpty()) { "Level $name has no rows" }
        require(grid.all { it.length == grid[0].length }) { "Level $name rows differ in width" }
    }

    val rows = grid.size
    val cols = grid[0].length
    val solution: Array<BooleanArray> =
        Array(rows) { r -> BooleanArray(cols) { c -> grid[r][c] == '#' } }

    val rowClues: List<List<Int>> = (0 until rows).map { r ->
        computeClues(cols) { c -> solution[r][c] }
    }
    val colClues: List<List<Int>> = (0 until cols).map { c ->
        computeClues(rows) { r -> solution[r][c] }
    }
}

/** Run lengths of filled cells along a line; [0] for an empty line. */
fun computeClues(count: Int, filled: (Int) -> Boolean): List<Int> {
    val clues = mutableListOf<Int>()
    var run = 0
    for (i in 0 until count) {
        if (filled(i)) run++ else {
            if (run > 0) clues.add(run)
            run = 0
        }
    }
    if (run > 0) clues.add(run)
    if (clues.isEmpty()) clues.add(0)
    return clues
}

/**
 * Loads levels from assets/levels/, one file per level, played in filename
 * order. File format: first non-blank line is the level name, every
 * following line of only '#'/'.' is a grid row; other lines are ignored.
 */
object Levels {
    private const val DIR = "levels"
    private val gridRow = Regex("^[#.]+$")

    @Volatile
    private var cache: List<Level>? = null

    fun all(context: Context): List<Level> =
        cache ?: context.assets.list(DIR).orEmpty().sorted().map { file ->
            parse(context.assets.open("$DIR/$file").bufferedReader().readLines())
        }.also {
            require(it.isNotEmpty()) { "assets/$DIR contains no levels" }
            cache = it
        }

    fun parse(lines: List<String>): Level {
        val nameIndex = lines.indexOfFirst { it.isNotBlank() }
        require(nameIndex >= 0) { "Level file is empty" }
        val grid = lines.drop(nameIndex + 1).map { it.trim() }.filter { gridRow.matches(it) }
        return Level(lines[nameIndex].trim(), grid)
    }
}
