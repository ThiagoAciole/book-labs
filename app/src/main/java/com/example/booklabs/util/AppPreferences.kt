package com.example.booklabs.util

import android.content.Context

object AppPreferences {
    private const val PREFS_NAME = "comic_prefs"
    private const val KEY_COMIC_DIR = "comic_dir_uri"

    fun saveComicDir(context: Context, uri: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_COMIC_DIR, uri).apply()
    }

    fun loadComicDir(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_COMIC_DIR, null)
    }
}
