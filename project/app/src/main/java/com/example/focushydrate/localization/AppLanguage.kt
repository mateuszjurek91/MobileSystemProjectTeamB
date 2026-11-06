package com.example.focushydrate.localization

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object AppLanguage {
    fun current(context: Context): String = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
        .getString("language", null) ?: if (context.resources.configuration.locales[0].language == "pl") "pl" else "en"
    fun set(context: Context, language: String): Boolean {
        require(language == "pl" || language == "en")
        return context.getSharedPreferences("app_language", Context.MODE_PRIVATE).edit().putString("language", language).commit()
    }
    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag(current(context))
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
