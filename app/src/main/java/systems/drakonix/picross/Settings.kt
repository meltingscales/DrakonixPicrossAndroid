package systems.drakonix.picross

import android.content.Context

object Settings {
    private const val LIVES = "setting_lives"
    private const val TUTORIAL_OFFERED = "tutorial_offered"

    /** Lives-mode choices; 0 means off. */
    val livesChoices = listOf(0, 3, 5)

    private fun prefs(context: Context) =
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)

    fun lives(context: Context): Int = prefs(context).getInt(LIVES, 0)

    fun setLives(context: Context, lives: Int) {
        prefs(context).edit().putInt(LIVES, lives).apply()
    }

    /** True exactly once: the first time it is asked. */
    fun shouldOfferTutorial(context: Context): Boolean {
        val p = prefs(context)
        if (p.getBoolean(TUTORIAL_OFFERED, false)) return false
        p.edit().putBoolean(TUTORIAL_OFFERED, true).apply()
        return true
    }
}
