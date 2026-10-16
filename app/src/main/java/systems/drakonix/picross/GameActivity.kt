package systems.drakonix.picross

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView

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
        val maxLives = Settings.lives(this)
        board.setLives(maxLives, prefs.getInt(MainActivity.livesKey(level), maxLives))
        prefs.getString(MainActivity.progressKey(level), null)?.let { board.importState(it) }

        val livesText = findViewById<TextView>(R.id.livesText)
        livesText.setTextColor(palette.accent)
        fun showLives(left: Int) {
            livesText.visibility = if (board.maxLives > 0) View.VISIBLE else View.GONE
            // U+FE0E asks for the text glyph, not the red emoji, so hearts take the theme color.
            livesText.text = "♥\uFE0E".repeat(left) + "♡\uFE0E".repeat(board.maxLives - left)
        }
        showLives(board.lives)
        board.onLivesChanged = { showLives(it) }
        board.onOutOfLives = {
            AlertDialog.Builder(this)
                .setTitle(R.string.out_of_lives_title)
                .setMessage(R.string.out_of_lives_message)
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(false)
                .show()
            board.reset()
            showLives(board.lives)
        }

        ToolBar(
            board,
            mapOf(
                PicrossView.Tool.FILL to findViewById(R.id.toolFill),
                PicrossView.Tool.MARK to findViewById(R.id.toolMark),
                PicrossView.Tool.PAN to findViewById(R.id.toolPan),
            ),
            palette,
        )
        val resetView = findViewById<Button>(R.id.resetViewButton)
        val clear = findViewById<Button>(R.id.resetButton)
        val buttonTint = palette.bg.blendToward(palette.fg, 0.12f)
        listOf(resetView, clear).forEach { it.stylize(buttonTint, palette.fg) }

        resetView.setOnClickListener { board.resetView() }
        clear.setOnClickListener {
            if (!board.hasProgress()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_title)
                .setMessage(R.string.clear_message)
                .setPositiveButton(R.string.reset) { _, _ ->
                    board.reset()
                    showLives(board.lives)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        board.onSolved = {
            solved = true
            prefs.edit()
                .putBoolean(MainActivity.solvedKey(level), true)
                .remove(MainActivity.progressKey(level))
                .remove(MainActivity.livesKey(level))
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
        if (solved) return
        val edit = prefs.edit()
        if (board.hasProgress()) {
            edit.putString(MainActivity.progressKey(level), board.exportState())
        } else {
            edit.remove(MainActivity.progressKey(level))
        }
        if (board.maxLives > 0 && board.lives < board.maxLives) {
            edit.putInt(MainActivity.livesKey(level), board.lives)
        } else {
            edit.remove(MainActivity.livesKey(level))
        }
        edit.apply()
    }

    companion object {
        const val EXTRA_LEVEL = "level"
    }
}
