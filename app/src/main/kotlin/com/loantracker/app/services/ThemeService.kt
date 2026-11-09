package com.loantracker.app.services

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.dataStore by preferencesDataStore(name = "settings")

object ThemeService {

    private val KEY = stringPreferencesKey("theme_mode")

    fun observe(context: Context): Flow<ThemeMode> =
        context.dataStore.data.map { prefs ->
            val name = prefs[KEY] ?: "SYSTEM"
            try { ThemeMode.valueOf(name) } catch (e: Exception) { ThemeMode.SYSTEM }
        }

    suspend fun setMode(context: Context, mode: ThemeMode) {
        context.dataStore.edit { it[KEY] = mode.name }
    }
}
