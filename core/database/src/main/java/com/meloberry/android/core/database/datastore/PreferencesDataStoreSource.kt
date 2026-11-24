package com.meloberry.android.core.database.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meloberry.android.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.meloberryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "meloberry_preferences"
)

@Singleton
class PreferencesDataStoreSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sampleSeededKey = booleanPreferencesKey("sample_seeded")
    private val appThemeModeKey = stringPreferencesKey("app_theme_mode")

    val sampleSeeded: Flow<Boolean> = context.meloberryDataStore.data.map { prefs ->
        prefs[sampleSeededKey] ?: false
    }

    val appThemeMode: Flow<ThemeMode> = context.meloberryDataStore.data.map { prefs ->
        ThemeMode.fromRaw(prefs[appThemeModeKey])
    }

    suspend fun setSampleSeeded(seeded: Boolean) {
        context.meloberryDataStore.edit { prefs ->
            prefs[sampleSeededKey] = seeded
        }
    }

    suspend fun setAppThemeMode(themeMode: ThemeMode) {
        context.meloberryDataStore.edit { prefs ->
            prefs[appThemeModeKey] = themeMode.name.lowercase()
        }
    }
}
