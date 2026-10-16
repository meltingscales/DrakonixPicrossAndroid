package com.drakonix.picross

import android.content.Context
import android.graphics.Color

/**
 * A minimal three-color palette, Downwell style: background, foreground,
 * and one accent that has to read against the background.
 */
class Palette(val name: String, bg: String, fg: String, accent: String) {
    val bg = Color.parseColor(bg)
    val fg = Color.parseColor(fg)
    val accent = Color.parseColor(accent)
}

object Themes {
    private const val KEY = "theme"

    val all = listOf(
        Palette("Paper", "#FAFAFA", "#263238", "#26A69A"),
        Palette("Downwell", "#0D0D0D", "#F2F2F2", "#E5473C"),
        Palette("Game Boy", "#0F380F", "#9BBC0F", "#E0F8D0"),
        Palette("CGA", "#000000", "#55FFFF", "#FF55FF"),
        Palette("Virtual Boy", "#000000", "#F83030", "#FF9F9F"),
        Palette("Sakura", "#FFE9F0", "#4A2432", "#E84A7A"),
    )

    fun current(context: Context): Palette {
        val name = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        return all.firstOrNull { it.name == name } ?: all[0]
    }

    fun setCurrent(context: Context, palette: Palette) {
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, palette.name).apply()
    }
}

/** This color with alpha scaled to [fraction] of opaque. */
fun Int.withAlpha(fraction: Float): Int =
    Color.argb((255 * fraction).toInt(), Color.red(this), Color.green(this), Color.blue(this))

/** Linear blend from [this] toward [other]. */
fun Int.blendToward(other: Int, t: Float): Int = Color.rgb(
    (Color.red(this) + (Color.red(other) - Color.red(this)) * t).toInt(),
    (Color.green(this) + (Color.green(other) - Color.green(this)) * t).toInt(),
    (Color.blue(this) + (Color.blue(other) - Color.blue(this)) * t).toInt(),
)
