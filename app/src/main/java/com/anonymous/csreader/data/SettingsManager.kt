package com.anonymous.csreader.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("csreader_settings", Context.MODE_PRIVATE)

    var theme: String
        get() = prefs.getString("theme", "light") ?: "light"
        set(value) = prefs.edit().putString("theme", value).apply()

    var fontSize: Int
        get() = prefs.getInt("font_size", 16)
        set(value) = prefs.edit().putInt("font_size", value).apply()

    var pageTransition: String
        get() = prefs.getString("page_transition", "slide") ?: "slide"
        set(value) = prefs.edit().putString("page_transition", value).apply()
}
