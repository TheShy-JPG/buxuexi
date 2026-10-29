package com.zhuxue.zhuxuebao.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class AppSettings(
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_BASE_URL,
    val textModel: String = DEFAULT_TEXT_MODEL,
    val visionModel: String = DEFAULT_VISION_MODEL,
    val webSearch: Boolean = true,
    val visibleSubjects: List<String> = SUBJECTS.take(6),
    val themeMode: String = THEME_LIGHT,
    val customBackground: String = "#FAF8F3",
    val customFont: String = "#1D211E",
    val questionSources: List<String> = listOf(SOURCE_BANK, SOURCE_WEB, SOURCE_AI)
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
        const val DEFAULT_TEXT_MODEL = "glm-5.3-flashx"
        const val DEFAULT_VISION_MODEL = "glm-5.3-flashx"
        const val LEGACY_TEXT_MODEL = "glm-4.7-flash"
        const val LEGACY_VISION_MODEL = "glm-4.6v"

        const val THEME_LIGHT = "LIGHT"
        const val THEME_DARK = "DARK"
        const val THEME_CUSTOM = "CUSTOM"
        const val MAX_VISIBLE_SUBJECTS = 9
        const val MIN_VISIBLE_SUBJECTS = 1

        const val SOURCE_BANK = "BANK"
        const val SOURCE_WEB = "WEB"
        const val SOURCE_AI = "AI"
        val ALL_SOURCES = listOf(SOURCE_BANK, SOURCE_WEB, SOURCE_AI)

        fun parseSources(value: String?): List<String> {
            val list = value.orEmpty()
                .split(',')
                .map { it.trim().uppercase() }
                .filter { it in ALL_SOURCES }
                .distinct()
            return if (list.isEmpty()) ALL_SOURCES else list
        }

        fun migratedModel(value: String?, legacy: String, fallback: String): String {
            val current = value.orEmpty().trim()
            return if (current.isBlank() || current == legacy) fallback else current
        }

        fun parseSubjects(value: String?): List<String> {
            val list = value.orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            return (if (list.isEmpty()) SUBJECTS.take(6) else list)
                .take(MAX_VISIBLE_SUBJECTS)
        }
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("zhuxue_settings")

class SettingsRepository(
    private val context: Context,
    private val secureStore: SecureStore = SecureStore()
) {

    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val API_KEY_SECURE = stringPreferencesKey("api_key_secure")
        val BASE_URL = stringPreferencesKey("base_url")
        val TEXT_MODEL = stringPreferencesKey("text_model")
        val VISION_MODEL = stringPreferencesKey("vision_model")
        val WEB_SEARCH = booleanPreferencesKey("web_search")
        val VISIBLE_SUBJECTS = stringPreferencesKey("visible_subjects")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CUSTOM_BACKGROUND = stringPreferencesKey("custom_background")
        val CUSTOM_FONT = stringPreferencesKey("custom_font")
        val QUESTION_SOURCES = stringPreferencesKey("question_sources")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            val encryptedKey = prefs[Keys.API_KEY_SECURE].orEmpty()
            AppSettings(
                apiKey = if (encryptedKey.isNotBlank()) {
                    secureStore.decrypt(encryptedKey).orEmpty()
                } else {
                    prefs[Keys.API_KEY].orEmpty()
                },
                baseUrl = prefs[Keys.BASE_URL].orEmpty().ifBlank { AppSettings.DEFAULT_BASE_URL },
                textModel = AppSettings.migratedModel(
                    prefs[Keys.TEXT_MODEL],
                    AppSettings.LEGACY_TEXT_MODEL,
                    AppSettings.DEFAULT_TEXT_MODEL
                ),
                visionModel = AppSettings.migratedModel(
                    prefs[Keys.VISION_MODEL],
                    AppSettings.LEGACY_VISION_MODEL,
                    AppSettings.DEFAULT_VISION_MODEL
                ),
                webSearch = prefs[Keys.WEB_SEARCH] ?: true,
                visibleSubjects = AppSettings.parseSubjects(prefs[Keys.VISIBLE_SUBJECTS]),
                themeMode = prefs[Keys.THEME_MODE].orEmpty()
                    .takeIf { it in setOf(AppSettings.THEME_LIGHT, AppSettings.THEME_DARK, AppSettings.THEME_CUSTOM) }
                    ?: AppSettings.THEME_LIGHT,
                customBackground = prefs[Keys.CUSTOM_BACKGROUND].orEmpty().ifBlank { "#FAF8F3" },
                customFont = prefs[Keys.CUSTOM_FONT].orEmpty().ifBlank { "#1D211E" },
                questionSources = AppSettings.parseSources(prefs[Keys.QUESTION_SOURCES])
            )
        }

    /** 把旧版本遗留的明文 Key 加密后删除明文；失败时保留原值，读取逻辑仍可用。 */
    suspend fun migrateLegacyKey() {
        context.settingsDataStore.edit { prefs ->
            val legacy = prefs[Keys.API_KEY]
            if (legacy.isNullOrBlank()) return@edit
            val encrypted = secureStore.encrypt(legacy)
            if (encrypted != null) {
                prefs[Keys.API_KEY_SECURE] = encrypted
                prefs.remove(Keys.API_KEY)
            }
        }
    }

    suspend fun save(settings: AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val key = settings.apiKey.trim()
            if (key.isBlank()) {
                prefs.remove(Keys.API_KEY_SECURE)
            } else {
                val encrypted = secureStore.encrypt(key)
                    ?: prefs[Keys.API_KEY_SECURE].orEmpty()
                if (encrypted.isNotBlank()) prefs[Keys.API_KEY_SECURE] = encrypted
            }
            prefs.remove(Keys.API_KEY)
            prefs[Keys.BASE_URL] = settings.baseUrl.trim().ifBlank { AppSettings.DEFAULT_BASE_URL }
            prefs[Keys.TEXT_MODEL] = settings.textModel.trim().ifBlank { AppSettings.DEFAULT_TEXT_MODEL }
            prefs[Keys.VISION_MODEL] = settings.visionModel.trim().ifBlank { AppSettings.DEFAULT_VISION_MODEL }
            prefs[Keys.WEB_SEARCH] = settings.webSearch
            prefs[Keys.VISIBLE_SUBJECTS] = settings.visibleSubjects
                .distinct()
                .take(AppSettings.MAX_VISIBLE_SUBJECTS)
                .joinToString(",")
            prefs[Keys.THEME_MODE] = settings.themeMode
            prefs[Keys.CUSTOM_BACKGROUND] = settings.customBackground.trim().ifBlank { "#FAF8F3" }
            prefs[Keys.CUSTOM_FONT] = settings.customFont.trim().ifBlank { "#1D211E" }
            prefs[Keys.QUESTION_SOURCES] = settings.questionSources
                .filter { it in AppSettings.ALL_SOURCES }
                .distinct()
                .ifEmpty { AppSettings.ALL_SOURCES }
                .joinToString(",")
        }
    }
}
