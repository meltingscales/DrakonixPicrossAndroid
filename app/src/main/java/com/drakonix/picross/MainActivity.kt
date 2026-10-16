package com.drakonix.picross

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout

class MainActivity : Activity() {

    private lateinit var grid: GridLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        grid = findViewById(R.id.levelGrid)
        findViewById<Button>(R.id.galleryButton).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        populate()
    }

    private fun populate() {
        grid.removeAllViews()
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Levels.all(this).forEachIndexed { i, level ->
            val solved = prefs.getBoolean(solvedKey(level), false)
            val button = Button(this).apply {
                text = getString(
                    if (solved) R.string.level_button_solved else R.string.level_button,
                    i + 1, level.name, level.rows, level.cols
                )
                isAllCaps = false
                setOnClickListener {
                    startActivity(
                        Intent(this@MainActivity, GameActivity::class.java)
                            .putExtra(GameActivity.EXTRA_LEVEL, i)
                    )
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            grid.addView(button, params)
        }
    }

    companion object {
        const val PREFS = "picross"
        fun solvedKey(level: Level) = "solved_${level.name}"
    }
}
