package systems.drakonix.picross

import android.content.Context

/**
 * A picross puzzle. [grid] rows use '.' for empty and any other character
 * for filled; all rows must have equal length. [colors] maps fill characters
 * to ARGB colors for the solved picture; unmapped fills (e.g. '#') use the
 * theme's foreground.
 */
class Level(val name: String, private val grid: List<String>, private val colors: Map<Char, Int> = emptyMap()) {
    init {
        require(grid.isNotEmpty()) { "Level $name has no rows" }
        require(grid.all { it.length == grid[0].length }) { "Level $name rows differ in width" }
    }

    val rows = grid.size
    val cols = grid[0].length
    val solution: Array<BooleanArray> =
        Array(rows) { r -> BooleanArray(cols) { c -> grid[r][c] != '.' } }

    val rowClues: List<List<Int>> = (0 until rows).map { r ->
        computeClues(cols) { c -> solution[r][c] }
    }
    val colClues: List<List<Int>> = (0 until cols).map { c ->
        computeClues(rows) { r -> solution[r][c] }
    }

    /** Start index of each solution run, aligned with the clue numbers (empty for a [0] line). */
    val rowRunStarts: List<List<Int>> = (0 until rows).map { r ->
        runStarts(cols) { c -> solution[r][c] }
    }
    val colRunStarts: List<List<Int>> = (0 until cols).map { c ->
        runStarts(rows) { r -> solution[r][c] }
    }

    /** Picture color of a filled cell, or null to use the theme foreground. */
    fun colorAt(r: Int, c: Int): Int? = colors[grid[r][c]]
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

private fun runStarts(count: Int, filled: (Int) -> Boolean): List<Int> =
    (0 until count).filter { filled(it) && (it == 0 || !filled(it - 1)) }

/**
 * Which clue numbers of a line are placed correctly: the player has filled
 * exactly that solution run, with no filled cell touching either end. Order
 * independent, so the 3 in "1 3 1" can fade before either 1.
 */
fun cluesDone(clues: List<Int>, starts: List<Int>, count: Int, filled: (Int) -> Boolean): List<Boolean> {
    if (starts.isEmpty()) return listOf((0 until count).none(filled))
    return clues.indices.map { i ->
        val s = starts[i]
        val e = s + clues[i]
        (s until e).all(filled) && (s == 0 || !filled(s - 1)) && (e == count || !filled(e))
    }
}

/**
 * Loads levels from assets/levels/, one file per level, played in filename
 * order. File format (blank lines ignored):
 *
 *     Level Name
 *     R=#E53935        optional color definitions, before the grid
 *     ..RR#..          grid rows: '.' empty, '#' or a defined char filled
 */
object Levels {
    private const val DIR = "levels"
    private val colorDef = Regex("""^(\S)=#([0-9A-Fa-f]{6})$""")

    @Volatile
    private var cache: List<Level>? = null

    fun all(context: Context): List<Level> =
        cache ?: context.assets.list(DIR).orEmpty().sorted().map { file ->
            parse(file, context.assets.open("$DIR/$file").bufferedReader().readLines())
        }.also {
            require(it.isNotEmpty()) { "assets/$DIR contains no levels" }
            cache = it
        }

    fun parse(source: String, lines: List<String>): Level {
        val content = lines.map { it.trim() }.filter { it.isNotEmpty() }
        require(content.isNotEmpty()) { "$source: empty level file" }
        val colors = mutableMapOf<Char, Int>()
        val grid = mutableListOf<String>()
        for (line in content.drop(1)) {
            val def = colorDef.matchEntire(line)
            if (def != null && grid.isEmpty()) {
                val key = def.groupValues[1][0]
                require(key != '.') { "$source: '.' is reserved for empty cells" }
                colors[key] = (0xFF000000 or def.groupValues[2].toLong(16)).toInt()
            } else {
                val bad = line.firstOrNull { it != '.' && it != '#' && it !in colors }
                require(bad == null) { "$source: undefined cell character '$bad' in \"$line\"" }
                grid.add(line)
            }
        }
        return Level(content[0], grid, colors)
    }
}
