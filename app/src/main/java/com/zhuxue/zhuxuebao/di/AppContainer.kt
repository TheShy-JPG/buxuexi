package com.zhuxue.zhuxuebao.di

import android.content.Context
import com.zhuxue.zhuxuebao.ai.ZhipuClient
import com.zhuxue.zhuxuebao.data.DiscussionRepository
import com.zhuxue.zhuxuebao.data.ImageStore
import com.zhuxue.zhuxuebao.data.PracticeRepository
import com.zhuxue.zhuxuebao.data.QuestionBankRepository
import com.zhuxue.zhuxuebao.data.RecordRepository
import com.zhuxue.zhuxuebao.data.SecureStore
import com.zhuxue.zhuxuebao.data.SettingsRepository
import com.zhuxue.zhuxuebao.data.UiPrefsRepository
import com.zhuxue.zhuxuebao.data.local.AppDatabase
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    private val database = AppDatabase.get(context)

    val recordRepository = RecordRepository(database.studyRecordDao())
    val practiceRepository = PracticeRepository(database.practiceResultDao())
    val discussionRepository = DiscussionRepository(database.discussedPointDao())
    val questionBankRepository = QuestionBankRepository(context.applicationContext, database.questionBankDao())
    val settingsRepository = SettingsRepository(context.applicationContext, SecureStore())
    val uiPrefsRepository = UiPrefsRepository(context.applicationContext)
    val imageStore = ImageStore(context.applicationContext)
    val aiClient = ZhipuClient { settingsRepository.settings.first() }
}
