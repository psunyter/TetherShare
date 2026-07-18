package sahin.tethershare

import android.content.Context
import android.content.SharedPreferences

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    companion object {
        const val THEME_KEY = "selected_theme"
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        const val THEME_AMOLED = 3
    }

    var selectedTheme: Int
        get() = prefs.getInt(THEME_KEY, THEME_SYSTEM)
        set(value) = prefs.edit().putInt(THEME_KEY, value).apply()
}
