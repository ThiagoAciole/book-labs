package com.example.booklabs.data.repository

import android.content.Context
import android.content.SharedPreferences

object ReaderThemeRepository {
    private const val PREFS_NAME = "readlab_reader_theme"
    private const val KEY_IS_DARK_THEME = "is_dark_theme"
    private lateinit var sharedPreferences: SharedPreferences
    
    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun saveTheme(isDark: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_IS_DARK_THEME, isDark)
            .apply()
    }
    
    fun isDarkTheme(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_DARK_THEME, true) // Default: dark
    }
}
