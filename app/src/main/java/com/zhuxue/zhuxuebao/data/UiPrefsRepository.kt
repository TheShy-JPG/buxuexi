package com.zhuxue.zhuxuebao.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class UiPrefs(
    val knowledgeOrder: List<String> = emptyList(),
    val wrongOrder: List<String> = emptyList()
)

private val Context.uiPrefsDataStore: DataStore<Preferences> by preferencesDataStore("zhuxue_ui_prefs")

class UiPrefsRepository(private val context: Context) {

    private object Keys {
        val KNOWLEDGE_ORDER = stringPreferencesKey("knowledge_order")
        val WRONG_ORDER = stringPreferencesKey("wrong_order")
    }

    val prefs: Flow<UiPrefs> = context.uiPrefsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            UiPrefs(
                knowledgeOrder = split(prefs[Keys.KNOWLEDGE_ORDER]),
                wrongOrder = split(prefs[Keys.WRONG_ORDER])
            )
        }

    suspend fun saveKnowledgeOrder(subject: String, keys: List<String>) {
        save(Keys.KNOWLEDGE_ORDER, subject, keys)
    }

    suspend fun saveWrongOrder(subject: String, keys: List<String>) {
        save(Keys.WRONG_ORDER, subject, keys)
    }

    private suspend fun save(
        key: Preferences.Key<String>,
        subject: String,
        keys: List<String>
    ) {
        val prefix = "$subject|"
        context.uiPrefsDataStore.edit { prefs ->
            val kept = split(prefs[key]).filterNot { it.startsWith(prefix) }
            prefs[key] = (kept + keys.distinct()).joinToString("\n")
        }
    }

    private fun split(value: String?): List<String> =
        value.orEmpty().split('\n').map { it.trim() }.filter { it.isNotBlank() }
}
