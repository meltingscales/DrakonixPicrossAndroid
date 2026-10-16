package com.drakonix.picross

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Shows the pixel art of every solved level; unsolved levels stay hidden. */
class GalleryActivity : Activity() {

    private lateinit var palette: Palette

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
        palette = Themes.current(this)
        findViewById<android.view.View>(android.R.id.content).setBackgroundColor(palette.bg)
        findViewById<TextView>(R.id.galleryTitle).setTextColor(palette.fg)
        val grid = findViewById<GridLayout>(R.id.galleryGrid)
        val prefs = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val density = resources.displayMetrics.density
        val thumbSize = (96 * density).toInt()

        Levels.all(this).forEach { level ->
            val solved = prefs.getBoolean(MainActivity.solvedKey(level), false)

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
            }
            item.addView(ImageView(this).apply {
                setImageBitmap(thumbnail(level, solved, thumbSize))
            }, LinearLayout.LayoutParams(thumbSize, thumbSize))
            item.addView(TextView(this).apply {
                text = if (solved) level.name else getString(R.string.gallery_unknown)
                gravity = Gravity.CENTER
                setTextColor(if (solved) palette.fg else palette.fg.withAlpha(0.5f))
            })

            grid.addView(item, GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
    }

    private fun thumbnail(level: Level, solved: Boolean, sizePx: Int): Bitmap {
        val src = Bitmap.createBitmap(level.cols, level.rows, Bitmap.Config.ARGB_8888)
        val back = palette.bg.blendToward(palette.fg, 0.08f)
        val hidden = palette.bg.blendToward(palette.fg, 0.18f)
        for (r in 0 until level.rows) for (c in 0 until level.cols) {
            src.setPixel(c, r, when {
                !solved -> hidden
                level.solution[r][c] -> palette.fg
                else -> back
            })
        }
        // Nearest-neighbor scale keeps pixel edges crisp.
        return Bitmap.createScaledBitmap(src, sizePx, sizePx, false)
    }
}
