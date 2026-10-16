package com.drakonix.picross

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.ToggleButton

class GameActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        val levels = Levels.all(this)
        val index = intent.getIntExtra(EXTRA_LEVEL, 0).coerceIn(0, levels.size - 1)
        val level = levels[index]

        findViewById<TextView>(R.id.gameTitle).text =
            getString(R.string.game_title, index + 1, level.name)

        val board = findViewById<PicrossView>(R.id.board)
        board.setLevel(level)

        val fillToggle = findViewById<ToggleButton>(R.id.toolFill)
        val markToggle = findViewById<ToggleButton>(R.id.toolMark)
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
            getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(MainActivity.solvedKey(level), true).apply()
            AlertDialog.Builder(this)
                .setTitle(R.string.solved_title)
                .setMessage(getString(R.string.solved_message, level.name))
                .setPositiveButton(R.string.solved_back) { _, _ -> finish() }
                .setCancelable(true)
                .show()
        }
    }

    companion object {
        const val EXTRA_LEVEL = "level"
    }
}
