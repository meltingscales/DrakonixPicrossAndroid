package com.drakonix.picross

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Shows the pixel art of every solved level; unsolved levels stay hidden. */
class GalleryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
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
            })

            grid.addView(item, GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
    }

    private fun thumbnail(level: Level, solved: Boolean, sizePx: Int): Bitmap {
        val src = Bitmap.createBitmap(level.cols, level.rows, Bitmap.Config.ARGB_8888)
        val fill = Color.parseColor("#263238")
        val back = Color.parseColor("#ECEFF1")
        val hidden = Color.parseColor("#CFD8DC")
        for (r in 0 until level.rows) for (c in 0 until level.cols) {
            src.setPixel(c, r, when {
                !solved -> hidden
                level.solution[r][c] -> fill
                else -> back
            })
        }
        // Nearest-neighbor scale keeps pixel edges crisp.
        return Bitmap.createScaledBitmap(src, sizePx, sizePx, false)
    }
}
