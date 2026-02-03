package com.example.booklabs.data.repository

import android.content.Context
import android.content.SharedPreferences

object ReadingProgressRepository {
    private const val PREFS_NAME = "readlab_reading_progress"
    private lateinit var sharedPreferences: SharedPreferences
    
    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun saveProgress(filePath: String, currentPage: Int) {
        sharedPreferences.edit()
            .putInt(filePath, currentPage)
            .apply()
    }
    
    fun getProgress(filePath: String): Int {
        return sharedPreferences.getInt(filePath, 0)
    }
    
    fun clearProgress(filePath: String) {
        sharedPreferences.edit()
            .remove(filePath)
            .apply()
    }
}
