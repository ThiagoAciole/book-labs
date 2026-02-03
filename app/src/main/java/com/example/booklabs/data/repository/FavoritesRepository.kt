package com.example.booklabs.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.booklabs.model.File

object FavoritesRepository {
    private const val PREFS_NAME = "readlab_favorites"
    private const val KEY_FAVORITES = "favorite_paths"
    
    // In-memory cache
    private val favoritePaths = mutableSetOf<String>()
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        favoritePaths.addAll(savedSet)
        isInitialized = true
    }

    fun isFavorite(path: String): Boolean {
        return favoritePaths.contains(path)
    }

    fun toggleFavorite(context: Context, path: String) {
        if (!isInitialized) init(context)
        
        if (favoritePaths.contains(path)) {
            favoritePaths.remove(path)
        } else {
            favoritePaths.add(path)
        }
        save(context)
    }

    private fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_FAVORITES, favoritePaths).apply()
    }
    
    fun getAllFavorites(): Set<String> = favoritePaths.toSet()
}
