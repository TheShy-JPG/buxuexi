package com.zhuxue.zhuxuebao.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `blank subjects fall back to first six`() {
        assertEquals(SUBJECTS.take(6), AppSettings.parseSubjects(null))
        assertEquals(SUBJECTS.take(6), AppSettings.parseSubjects("  "))
    }

    @Test
    fun `subjects are trimmed, deduplicated and capped`() {
        val parsed = AppSettings.parseSubjects(" 数学,数学,语文,英语,物理,化学,生物,历史,地理,政治 ")
        assertEquals(9, AppSettings.MAX_VISIBLE_SUBJECTS)
        assertEquals(listOf("数学", "语文", "英语", "物理", "化学", "生物", "历史", "地理", "政治"), parsed)
    }

    @Test
    fun `unknown sources are dropped and empty falls back to all`() {
        assertEquals(
            listOf(AppSettings.SOURCE_WEB, AppSettings.SOURCE_AI),
            AppSettings.parseSources("web,unknown,AI")
        )
        assertEquals(AppSettings.ALL_SOURCES, AppSettings.parseSources(""))
    }

    @Test
    fun `legacy model ids migrate to current defaults`() {
        assertEquals(
            AppSettings.DEFAULT_TEXT_MODEL,
            AppSettings.migratedModel(AppSettings.LEGACY_TEXT_MODEL, AppSettings.LEGACY_TEXT_MODEL, AppSettings.DEFAULT_TEXT_MODEL)
        )
        assertEquals(
            "custom-model",
            AppSettings.migratedModel("custom-model", AppSettings.LEGACY_TEXT_MODEL, AppSettings.DEFAULT_TEXT_MODEL)
        )
    }

    @Test
    fun `configured requires non blank key`() {
        assertTrue(AppSettings(apiKey = "abc").isConfigured)
        assertTrue(!AppSettings(apiKey = "  ").isConfigured)
    }
}
