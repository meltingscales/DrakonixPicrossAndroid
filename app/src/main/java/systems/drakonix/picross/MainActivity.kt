package systems.drakonix.picross

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
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
        findViewById<Button>(R.id.themeButton).setOnClickListener { showOptions() }
        findViewById<Button>(R.id.helpButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_text)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.tutorial) { _, _ -> startTutorial() }
                .setNegativeButton(R.string.credits) { _, _ -> showCredits() }
                .show()
        }
        if (Settings.shouldOfferTutorial(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.tutorial_offer_title)
                .setMessage(R.string.tutorial_offer_message)
                .setPositiveButton(R.string.tutorial_offer_yes) { _, _ -> startTutorial() }
                .setNegativeButton(R.string.tutorial_offer_no, null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        populate()
    }

    private fun showCredits() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.credits)
            .setMessage(getString(R.string.credits_text, version))
            .setPositiveButton(android.R.string.ok, null)
            .show()
        // Make the URLs tappable; they open in the browser, so no internet permission is needed.
        dialog.findViewById<TextView>(android.R.id.message)?.let {
            Linkify.addLinks(it, Linkify.WEB_URLS)
            it.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun startTutorial() {
        startActivity(Intent(this, TutorialActivity::class.java))
    }

    private fun showOptions() {
        val livesLabel = { n: Int -> if (n == 0) getString(R.string.lives_off) else getString(R.string.lives_n, n) }
        val items = arrayOf(
            getString(R.string.option_theme, Themes.current(this).name),
            getString(R.string.option_lives, livesLabel(Settings.lives(this))),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.options_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickTheme()
                    1 -> pickLives(livesLabel)
                }
            }
            .show()
    }

    private fun pickLives(label: (Int) -> String) {
        val choices = Settings.livesChoices
        AlertDialog.Builder(this)
            .setTitle(R.string.lives_title)
            .setSingleChoiceItems(
                choices.map(label).toTypedArray(),
                choices.indexOf(Settings.lives(this)).coerceAtLeast(0),
            ) { dialog, which ->
                Settings.setLives(this, choices[which])
                dialog.dismiss()
            }
            .show()
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
        findViewById<Button>(R.id.helpButton).stylize(plainTint, palette.fg)

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
        fun livesKey(level: Level) = "lives_${level.name}"
    }
}

fun Button.stylize(background: Int, text: Int) {
    backgroundTintList = ColorStateList.valueOf(background)
    setTextColor(text)
}
