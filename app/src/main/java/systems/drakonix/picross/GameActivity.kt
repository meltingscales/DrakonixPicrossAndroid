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
        val fillToggle = findViewById<ToggleButton>(R.id.toolFill)
        val markToggle = findViewById<ToggleButton>(R.id.toolMark)
        listOf<TextView>(fillToggle, markToggle, findViewById(R.id.resetButton)).forEach {
            it.backgroundTintList = android.content.res.ColorStateList.valueOf(buttonTint)
            it.setTextColor(palette.fg)
        }
        fillToggle.isChecked = true
        fillToggle.setOnClickListener {
            board.tool = PicrossView.Tool.FILL
            fillToggle.isChecked = true
            markToggle.isChecked = false
        }
        markToggle.setOnClickListener {
            board.tool = PicrossView.Tool.MARK
            markToggle.isChecked = true
            fillToggle.isChecked = false
        }

        findViewById<Button>(R.id.resetButton).setOnClickListener { board.reset() }

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
