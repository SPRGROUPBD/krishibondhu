package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "krishi_bondhu_prefs")

data class UserSessionPrefs(
    val activeUserId: String = "",
    val language: AppLanguage = AppLanguage.BENGALI,
    val darkMode: Boolean = false
)

class UserPreferences(private val context: Context) {
    private val keyUserId = stringPreferencesKey("active_user_id")
    private val keyLanguage = stringPreferencesKey("app_language")
    private val keyDarkMode = booleanPreferencesKey("dark_mode")

    val sessionPrefs: Flow<UserSessionPrefs> = context.dataStore.data.map { prefs ->
        val langName = prefs[keyLanguage] ?: AppLanguage.BENGALI.name
        val lang = runCatching { AppLanguage.valueOf(langName) }.getOrDefault(AppLanguage.BENGALI)
        val rawUid = prefs[keyUserId] ?: ""
        // Never auto-login legacy demo IDs
        val cleanUid = if (rawUid in setOf("buyer_01", "farmer_01", "admin_01")) "" else rawUid
        UserSessionPrefs(
            activeUserId = cleanUid,
            language = lang,
            darkMode = prefs[keyDarkMode] ?: false
        )
    }

    suspend fun setActiveUserId(userId: String) {
        context.dataStore.edit { it[keyUserId] = userId }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[keyLanguage] = language.name }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[keyDarkMode] = enabled }
    }
}
