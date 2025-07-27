package com.sauletbek.quickprogress


import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale
import android.app.Activity
import android.content.Intent


fun setAppLocale(context: Context, languageCode: String): Context {
    val locale = Locale(languageCode)
    Locale.setDefault(locale)

    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.createConfigurationContext(config)
    } else {
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        context
    }
}

fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java)
    if (context is Activity) {
        context.finish()
    }
    context.startActivity(intent)
}

fun saveLanguagePreference(context: Context, languageCode: String) {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    prefs.edit().putString("app_language", languageCode).apply()
}