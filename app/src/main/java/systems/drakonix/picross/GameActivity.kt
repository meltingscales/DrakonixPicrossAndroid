package systems.drakonix.picross

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.ToggleButton

class GameActivity : Activity() {

    private lateinit var board: PicrossView
    private lateinit var level: Level
    private lateinit var prefs: SharedPreferences
    private var solved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        val levels = Levels.all(this)
        val index = intent.getIntExtra(EXTRA_LEVEL, 0).coerceIn(0, levels.size - 1)
        level = levels[index]
        prefs = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)

        val palette = Themes.current(this)
        findViewById<View>(android.R.id.content).setBackgroundColor(palette.bg)
        findViewById<TextView>(R.id.gameTitle).apply {
            text = getString(R.string.game_title, index + 1, level.name)
            setTextColor(palette.fg)
        }

        board = findViewById(R.id.board)
        board.setPalette(palette)
        board.setLevel(level)
        prefs.getString(MainActivity.progressKey(level), null)?.let { board.importState(it) }

        val buttonTint = palette.bg.blendToward(palette.fg, 0.12f)
        val toggles = mapOf(
            PicrossView.Tool.FILL to findViewById<ToggleButton>(R.id.toolFill),
            PicrossView.Tool.MARK to findViewById<ToggleButton>(R.id.toolMark),
            PicrossView.Tool.PAN to findViewById<ToggleButton>(R.id.toolPan),
        )
        val resetView = findViewById<Button>(R.id.resetViewButton)
        val clear = findViewById<Button>(R.id.resetButton)
        (toggles.values + resetView + clear).forEach<TextView> {
            it.backgroundTintList = android.content.res.ColorStateList.valueOf(buttonTint)
            it.setTextColor(palette.fg)
        }
        fun selectTool(tool: PicrossView.Tool) {
            board.tool = tool
            toggles.forEach { (t, button) ->
                val selected = t == tool
                button.isChecked = selected
                button.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (selected) palette.accent else buttonTint
                )
                button.setTextColor(if (selected) palette.bg else palette.fg)
            }
        }
        toggles.forEach { (tool, button) -> button.setOnClickListener { selectTool(tool) } }
        selectTool(PicrossView.Tool.FILL)

        resetView.setOnClickListener { board.resetView() }
        clear.setOnClickListener {
            if (!board.hasProgress()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_title)
                .setMessage(R.string.clear_message)
                .setPositiveButton(R.string.reset) { _, _ -> board.reset() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        board.onSolved = {
            solved = true
            prefs.edit()
                .putBoolean(MainActivity.solvedKey(level), true)
                .remove(MainActivity.progressKey(level))
                .apply()
            AlertDialog.Builder(this)
                .setTitle(R.string.solved_title)
                .setMessage(getString(R.string.solved_message, level.name))
                .setPositiveButton(R.string.solved_back) { _, _ -> finish() }
                .setCancelable(true)
                .show()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!solved && board.hasProgress()) {
            prefs.edit().putString(MainActivity.progressKey(level), board.exportState()).apply()
        } else if (!solved) {
            prefs.edit().remove(MainActivity.progressKey(level)).apply()
        }
    }

    companion object {
        const val EXTRA_LEVEL = "level"
    }
}
