package com.example.simplemusictuner

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Менеджер для сохранения и загрузки настроек приложения
 */
class SettingsManager(private val context: Context) {
    
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tuner_settings")
        private val SENSITIVITY_KEY = floatPreferencesKey("sensitivity")
        
        // Значение чувствительности по умолчанию (1.2 = чуть выше стандартной, хорошо для гитары)
        const val DEFAULT_SENSITIVITY = 1.2f
    }
    
    /**
     * Получить поток с текущим значением чувствительности
     */
    val sensitivity: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[SENSITIVITY_KEY] ?: DEFAULT_SENSITIVITY
    }
    
    /**
     * Сохранить значение чувствительности
     * @param value значение от 0.1 (почти ничего не слышит) до 5.0 (все звуки)
     */
    suspend fun setSensitivity(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[SENSITIVITY_KEY] = value.coerceIn(0.1f, 5.0f)
        }
    }
}

