package systems.drakonix.picross

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var grid: GridLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        grid = findViewById(R.id.levelGrid)
        findViewById<Button>(R.id.galleryButton).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        findViewById<Button>(R.id.themeButton).setOnClickListener { pickTheme() }
    }

    override fun onResume() {
        super.onResume()
        populate()
    }

    private fun pickTheme() {
        val names = Themes.all.map { it.name }.toTypedArray()
        val checked = Themes.all.indexOf(Themes.current(this))
        AlertDialog.Builder(this)
            .setTitle(R.string.theme_title)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                Themes.setCurrent(this, Themes.all[which])
                dialog.dismiss()
                populate()
            }
            .show()
    }

    private fun populate() {
        val palette = Themes.current(this)
        findViewById<View>(android.R.id.content).setBackgroundColor(palette.bg)
        findViewById<TextView>(R.id.titleText).setTextColor(palette.fg)
        findViewById<TextView>(R.id.pickText).setTextColor(palette.fg)

        val plainTint = palette.bg.blendToward(palette.fg, 0.12f)
        findViewById<Button>(R.id.galleryButton).stylize(plainTint, palette.fg)
        findViewById<Button>(R.id.themeButton).stylize(plainTint, palette.fg)

        grid.removeAllViews()
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Levels.all(this).forEachIndexed { i, level ->
            val solved = prefs.getBoolean(solvedKey(level), false)
            val inProgress = !solved && prefs.contains(progressKey(level))
            val button = Button(this).apply {
                text = getString(
                    when {
                        solved -> R.string.level_button_solved
                        inProgress -> R.string.level_button_progress
                        else -> R.string.level_button
                    },
                    i + 1, level.name, level.rows, level.cols
                )
                isAllCaps = false
                when {
                    solved -> stylize(palette.accent, palette.bg)
                    inProgress -> stylize(palette.bg.blendToward(palette.accent, 0.35f), palette.fg)
                    else -> stylize(plainTint, palette.fg)
                }
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
        fun progressKey(level: Level) = "progress_${level.name}"
    }
}

fun Button.stylize(background: Int, text: Int) {
    backgroundTintList = ColorStateList.valueOf(background)
    setTextColor(text)
}
