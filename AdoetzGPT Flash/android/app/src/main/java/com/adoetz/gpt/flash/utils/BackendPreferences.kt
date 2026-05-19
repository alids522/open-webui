package com.adoetz.gpt.flash.utils

import android.content.Context
import android.preference.PreferenceManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "flash_prefs")

/**
 * BackendPreferences — persists backend URL using DataStore.
 *
 * Also writes to SharedPreferences for synchronous access from NativeBridge.
 */
class BackendPreferences(private val context: Context) {

    companion object {
        private val KEY_BACKEND_URL = stringPreferencesKey("backend_url")
        private const val SP_KEY_BACKEND_URL = "backend_url"
    }

    /** Flow of the stored backend URL. Emits null when not set. */
    val backendUrlFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_BACKEND_URL]
    }

    /** Persist the backend URL. */
    suspend fun saveBackendUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BACKEND_URL] = url
        }
        // Also write to SharedPreferences for synchronous NativeBridge reads
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(SP_KEY_BACKEND_URL, url)
            .apply()
    }

    /** Clear the backend URL. */
    suspend fun clearBackendUrl() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_BACKEND_URL)
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove(SP_KEY_BACKEND_URL)
            .apply()
    }

    /** Synchronous read for NativeBridge (fallback to SharedPreferences). */
    fun getSavedUrlSync(): String? {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(SP_KEY_BACKEND_URL, null)
    }
}
