package com.zhuxue.zhuxuebao.ui.vm

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zhuxue.zhuxuebao.ai.AiException
import com.zhuxue.zhuxuebao.ai.ZhipuClient
import com.zhuxue.zhuxuebao.data.AnalysisResult
import com.zhuxue.zhuxuebao.data.AppSettings
import com.zhuxue.zhuxuebao.data.DiscussionRepository
import com.zhuxue.zhuxuebao.data.ImageStore
import com.zhuxue.zhuxuebao.data.KnowledgePoint
import com.zhuxue.zhuxuebao.data.KnowledgeStat
import com.zhuxue.zhuxuebao.data.PracticeQuestion
import com.zhuxue.zhuxuebao.data.PracticeRepository
import com.zhuxue.zhuxuebao.data.QuestionBankFile
import com.zhuxue.zhuxuebao.data.QuestionBankRepository
import com.zhuxue.zhuxuebao.data.RecordRepository
import com.zhuxue.zhuxuebao.data.SettingsRepository
import com.zhuxue.zhuxuebao.data.StudyRecord
import com.zhuxue.zhuxuebao.data.UiPrefs
import com.zhuxue.zhuxuebao.data.UiPrefsRepository
import com.zhuxue.zhuxuebao.data.WrongQuestionRef
import com.zhuxue.zhuxuebao.data.aggregateKnowledge
import com.zhuxue.zhuxuebao.data.extractKeyword
import com.zhuxue.zhuxuebao.data.matchSubject
import com.zhuxue.zhuxuebao.data.mentionsKnowledge
import com.zhuxue.zhuxuebao.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppTab(val label: String) {
    LEARN("学习"),
    PRACTICE("练习"),
    REVIEW("复习")
}

data class LearnTarget(
    val subject: String,
    val point: KnowledgePoint
)

sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Testing : ConnectionTest
    data class Success(val message: String) : ConnectionTest
    data class Failure(val message: String) : ConnectionTest
}

class MainViewModel(
    private val recordRepository: RecordRepository,
    private val settingsRepository: SettingsRepository,
    private val aiClient: ZhipuClient,
    private val imageStore: ImageStore,
    private val practiceRepository: PracticeRepository,
    private val discussionRepository: DiscussionRepository,
    private val questionBankRepository: QuestionBankRepository,
    private val uiPrefsRepository: UiPrefsRepository
) : ViewModel() {

    val records: StateFlow<List<StudyRecord>> = recordRepository.observeRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val knowledgeStats: StateFlow<List<KnowledgeStat>> = combine(
        records,
        practiceRepository.observeAttempts(),
        discussionRepository.observePoints()
    ) { recordList, attempts, discussed ->
        recordList.aggregateKnowledge(attempts, discussed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bankFiles: StateFlow<List<QuestionBankFile>> = questionBankRepository.observeFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiPrefs: StateFlow<UiPrefs> = uiPrefsRepository.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiPrefs())

    init {
        viewModelScope.launch {
            runCatching {
                questionBankRepository.cleanupLegacyFiles()
                val keep = recordRepository.observeRecords().first()
                    .flatMap { it.imagePaths }
                    .toSet()
                withContext(Dispatchers.IO) { imageStore.pruneOrphans(keep) }
            }
        }
    }

    private val _tab = MutableStateFlow(AppTab.REVIEW)
    val tab: StateFlow<AppTab> = _tab.asStateFlow()

    private val _pendingLearnTarget = MutableStateFlow<LearnTarget?>(null)
    val pendingLearnTarget: StateFlow<LearnTarget?> = _pendingLearnTarget.asStateFlow()

    private val _connectionTest = MutableStateFlow<ConnectionTest>(ConnectionTest.Idle)
    val connectionTest: StateFlow<ConnectionTest> = _connectionTest.asStateFlow()

    fun setTab(tab: AppTab) {
        _tab.value = tab
    }

    fun openLearn(subject: String, point: KnowledgePoint) {
        _pendingLearnTarget.value = LearnTarget(subject, point)
        _tab.value = AppTab.LEARN
    }

    fun consumePendingLearnTarget() {
        _pendingLearnTarget.value = null
    }

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            settingsRepository.save(settings)
            _connectionTest.value = ConnectionTest.Idle
        }
    }

    fun addDiscussedPoint(subject: String, name: String, note: String) {
        viewModelScope.launch {
            discussionRepository.add(subject, name, note)
        }
    }

    fun requestLearnGuide(
        subject: String,
        name: String,
        note: String,
        onResult: (Result<KnowledgePoint>) -> Unit
    ) {
        viewModelScope.launch {
            val result = runCatching { aiClient.learnGuide(subject, name, note) }
            result.getOrNull()?.let { point ->
                runCatching {
                    discussionRepository.updateGuide(
                        subject = subject,
                        name = name,
                        learningDirection = point.learningDirection,
                        searchQuery = point.searchQuery
                    )
                }
            }
            onResult(result)
        }
    }

    fun saveKnowledgeOrder(subject: String, keys: List<String>) {
        viewModelScope.launch { uiPrefsRepository.saveKnowledgeOrder(subject, keys) }
    }

    fun saveWrongOrder(subject: String, keys: List<String>) {
        viewModelScope.launch { uiPrefsRepository.saveWrongOrder(subject, keys) }
    }

    fun deleteKnowledge(subject: String, names: List<String>) {
        val targets = names.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (targets.isEmpty()) return
        viewModelScope.launch {
            records.value
                .filter { it.subject == subject && it.analysis.mentionsKnowledge(targets) }
                .forEach { record ->
                    recordRepository.delete(record.id)
                    imageStore.delete(record.imagePaths)
                }
            practiceRepository.deleteForPoints(subject, targets.toList())
            discussionRepository.deleteForNames(subject, targets.toList())
        }
    }

    fun deleteWrongQuestions(refs: List<WrongQuestionRef>) {
        if (refs.isEmpty()) return
        viewModelScope.launch {
            val current = records.value.associateBy { it.id }
            refs.groupBy { it.recordId }.forEach { (recordId, group) ->
                val record = current[recordId] ?: return@forEach
                val indices = group.map { it.questionIndex }.filter { it >= 0 }.toSet()
                val remaining = record.analysis.questions.filterIndexed { index, _ ->
                    index !in indices
                }
                if (remaining.isEmpty() || indices.isEmpty()) {
                    recordRepository.delete(record.id)
                    imageStore.delete(record.imagePaths)
                } else {
                    recordRepository.updateQuestions(
                        record.id,
                        record.analysis.copy(questions = remaining)
                    )
                }
            }
        }
    }

    fun addBankFile(uri: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = runCatching { questionBankRepository.add(uri) }
                .exceptionOrNull()
                ?.message
            onResult(error)
        }
    }

    fun deleteBankFile(id: Long) {
        viewModelScope.launch { questionBankRepository.delete(id) }
    }

    fun saveAndTest(settings: AppSettings) {
        viewModelScope.launch {
            settingsRepository.save(settings)
            _connectionTest.value = ConnectionTest.Testing
            _connectionTest.value = try {
                val reply = aiClient.testConnection()
                ConnectionTest.Success("连接成功：$reply")
            } catch (error: Exception) {
                ConnectionTest.Failure(error.userFacingMessage())
            }
        }
    }

    fun resetConnectionTest() {
        _connectionTest.value = ConnectionTest.Idle
    }

    fun deleteRecord(record: StudyRecord) {
        viewModelScope.launch {
            recordRepository.delete(record.id)
            imageStore.delete(record.imagePaths)
        }
    }
}

sealed interface UploadState {
    data object Idle : UploadState
    data class Working(val imagePaths: List<String>, val message: String) : UploadState
    data class Error(val message: String, val imagePaths: List<String>) : UploadState
    data class NeedsSubject(
        val imagePaths: List<String>,
        val analysis: AnalysisResult
    ) : UploadState
    data class Saved(val recordId: Long) : UploadState
}

class UploadViewModel(
    private val recordRepository: RecordRepository,
    private val aiClient: ZhipuClient,
    private val imageStore: ImageStore
) : ViewModel() {

    private val _state = MutableStateFlow<UploadState>(UploadState.Idle)
    val state: StateFlow<UploadState> = _state.asStateFlow()

    private var imagePaths: List<String> = emptyList()
    private var subjectHint: String? = null

    fun start(uris: List<Uri>, subjectHint: String? = null) {
        if (_state.value is UploadState.Working) return
        this.subjectHint = subjectHint
        viewModelScope.launch {
            _state.value = UploadState.Working(emptyList(), "正在读取并压缩图片…")
            val paths = withContext(Dispatchers.IO) {
                uris.mapNotNull { imageStore.importRecordImage(it) }
            }
            if (paths.isEmpty()) {
                _state.value = UploadState.Error("图片读取失败，请重新选择", emptyList())
                return@launch
            }
            imagePaths = paths
            analyze()
        }
    }

    fun retry() {
        if (imagePaths.isEmpty()) {
            _state.value = UploadState.Idle
            return
        }
        viewModelScope.launch { analyze() }
    }

    fun chooseSubject(subject: String) {
        val current = _state.value as? UploadState.NeedsSubject ?: return
        val known = matchSubject(subject) ?: return
        viewModelScope.launch {
            _state.value = UploadState.Working(current.imagePaths, "正在保存…")
            try {
                saveResult(current.analysis.copy(subject = known))
            } catch (error: Exception) {
                _state.value = UploadState.Error(error.userFacingMessage(), current.imagePaths)
            }
        }
    }

    private suspend fun analyze() {
        _state.value = UploadState.Working(imagePaths, "AI 正在识别题目、归纳知识点…")
        try {
            val images = withContext(Dispatchers.IO) {
                imagePaths.mapNotNull { imageStore.readBytes(it) }
            }
            if (images.isEmpty()) throw AiException("图片读取失败，请重试")
            var result: AnalysisResult = aiClient.analyze(images, subjectHint)
            if (result.knowledgePoints.isEmpty()) {
                val keyword = extractKeyword(
                    result.summary.ifBlank { result.title },
                    fallback = "未命名知识点"
                )
                result = result.copy(
                    knowledgePoints = listOf(
                        KnowledgePoint(
                            name = keyword,
                            difficulty = "MEDIUM",
                            frequency = "FREQUENT",
                            errorPattern = result.summary,
                            searchQuery = keyword
                        )
                    )
                )
            }
            val knownSubject = matchSubject(result.subject)
            if (knownSubject == null) {
                _state.value = UploadState.NeedsSubject(imagePaths, result)
            } else {
                saveResult(result.copy(subject = knownSubject))
            }
        } catch (error: Exception) {
            _state.value = UploadState.Error(error.userFacingMessage(), imagePaths)
        }
    }

    private suspend fun saveResult(result: AnalysisResult) {
        val id = recordRepository.save(
            subject = result.subject,
            title = result.title,
            imagePaths = imagePaths,
            analysis = result
        )
        _state.value = UploadState.Saved(id)
    }

    fun reset() {
        _state.value = UploadState.Idle
        imagePaths = emptyList()
        subjectHint = null
    }

    fun discard() {
        imageStore.delete(imagePaths)
        reset()
    }
}

data class PracticeState(
    val subject: String = "数学",
    val knowledgePoint: String = "",
    val mode: String = "SINGLE",
    val count: Int = 3,
    val questions: List<PracticeQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val generating: Boolean = false,
    val grading: Boolean = false,
    val error: String? = null,
    val needSettings: Boolean = false,
    val note: String = "",
    val answerText: String = "",
    val selectedOption: String = "",
    val answerImageUri: String? = null,
    val finished: Boolean = false
) {
    val active: Boolean get() = questions.isNotEmpty()
    val current: PracticeQuestion? get() = questions.getOrNull(currentIndex)
    val isLastQuestion: Boolean get() = currentIndex >= questions.lastIndex
    val multiMode: Boolean get() = mode == "MULTI"
    val allSubmitted: Boolean get() = questions.isNotEmpty() && questions.all { it.submitted }
    val submittedCount: Int get() = questions.count { it.submitted }
    val correctCount: Int get() = questions.count { it.isCorrect == true }
}

class PracticeViewModel(
    private val aiClient: ZhipuClient,
    private val imageStore: ImageStore,
    private val practiceRepository: PracticeRepository,
    private val settingsRepository: SettingsRepository,
    private val questionBankRepository: QuestionBankRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PracticeState())
    val state: StateFlow<PracticeState> = _state.asStateFlow()

    fun setSubject(value: String) {
        _state.update { it.copy(subject = value) }
    }

    fun setKnowledgePoint(value: String) {
        _state.update { it.copy(knowledgePoint = value) }
    }

    fun setMode(value: String) {
        _state.update { it.copy(mode = value) }
    }

    fun setCount(value: Int) {
        _state.update { it.copy(count = value.coerceIn(2, 8)) }
    }

    fun preset(subject: String, knowledgePoint: String, mode: String = "SINGLE") {
        _state.value = PracticeState(
            subject = subject,
            knowledgePoint = knowledgePoint,
            mode = mode,
            count = 3
        )
    }

    fun updateAnswerText(value: String) {
        _state.update { it.copy(answerText = value) }
    }

    fun selectOption(option: String) {
        _state.update {
            it.copy(selectedOption = if (it.selectedOption == option) "" else option)
        }
    }

    fun setAnswerImage(uri: Uri?) {
        _state.update { it.copy(answerImageUri = uri?.toString()) }
    }

    fun start() {
        val snapshot = _state.value
        if (snapshot.generating) return
        if (snapshot.knowledgePoint.isBlank()) {
            _state.update { it.copy(error = "先填写要练习的知识点") }
            return
        }
        cleanupAnswerImages(snapshot.questions)
        viewModelScope.launch {
            _state.update {
                it.copy(
                    generating = true,
                    error = null,
                    needSettings = false,
                    questions = emptyList(),
                    currentIndex = 0,
                    answerText = "",
                    selectedOption = "",
                    answerImageUri = null,
                    note = "",
                    finished = false
                )
            }
            try {
                val settings = settingsRepository.settings.first()
                val sources = settings.questionSources
                val bankAvailable = AppSettings.SOURCE_BANK in sources &&
                    questionBankRepository.hasFiles()
                val bankText = if (bankAvailable) {
                    questionBankRepository.combinedText(
                        snapshot.subject,
                        snapshot.knowledgePoint.trim()
                    )
                } else {
                    null
                }
                val payload = aiClient.generateQuestions(
                    subject = snapshot.subject,
                    knowledgePoint = snapshot.knowledgePoint.trim(),
                    count = if (snapshot.mode == "MULTI") snapshot.count else 1,
                    mode = snapshot.mode,
                    sources = sources,
                    bankText = bankText,
                    bankAvailable = bankAvailable
                )
                _state.update {
                    it.copy(
                        generating = false,
                        questions = payload.questions,
                        note = payload.note,
                        currentIndex = 0
                    )
                }
            } catch (error: Exception) {
                val message = error.userFacingMessage()
                _state.update {
                    it.copy(
                        generating = false,
                        error = message,
                        needSettings = message.contains("API Key", ignoreCase = true)
                    )
                }
            }
        }
    }

    fun submit() {
        val snapshot = _state.value
        val question = snapshot.current ?: return
        if (question.submitted || question.answered || snapshot.grading) return

        val answer = buildAnswer(snapshot)
        if (answer.isBlank() && snapshot.answerImageUri == null) {
            _state.update {
                it.copy(error = "先写下你的答案，或上传手写答案；不会就点“不会”")
            }
            return
        }

        if (snapshot.multiMode) submitMulti(snapshot, answer) else submitSingle(snapshot, question, answer)
    }

    private fun submitSingle(
        snapshot: PracticeState,
        question: PracticeQuestion,
        answer: String
    ) {
        viewModelScope.launch {
            _state.update { it.copy(grading = true, error = null) }
            var importedPath: String? = null
            try {
                val imageBytes = snapshot.answerImageUri?.let { uriString ->
                    val path = imageStore.importAnswerImage(Uri.parse(uriString))
                    importedPath = path
                    path?.let { imageStore.readBytes(it) }
                }
                if (snapshot.answerImageUri != null && imageBytes == null) {
                    throw AiException("手写答案读取失败，请重新上传")
                }
                val grade = aiClient.grade(
                    subject = snapshot.subject,
                    question = question,
                    userAnswer = answer,
                    imageBytes = imageBytes
                )
                updateCurrent {
                    it.copy(
                        answered = true,
                        submitted = true,
                        isCorrect = grade.correct,
                        userAnswer = answer.ifBlank { "（图片作答）" },
                        userImagePath = importedPath.orEmpty(),
                        feedback = grade.analysis,
                        encouragement = grade.encouragement
                    )
                }
                practiceRepository.addAttempt(snapshot.subject, snapshot.knowledgePoint, grade.correct)
                _state.update {
                    it.copy(
                        grading = false,
                        answerText = "",
                        selectedOption = "",
                        answerImageUri = null,
                        error = null
                    )
                }
            } catch (error: Exception) {
                importedPath?.let { imageStore.delete(listOf(it)) }
                val message = error.userFacingMessage()
                _state.update {
                    it.copy(
                        grading = false,
                        error = message,
                        needSettings = message.contains("API Key", ignoreCase = true)
                    )
                }
            }
        }
    }

    private fun submitMulti(snapshot: PracticeState, answer: String) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            var importedPath: String? = null
            try {
                if (snapshot.answerImageUri != null) {
                    importedPath = imageStore.importAnswerImage(Uri.parse(snapshot.answerImageUri))
                    if (importedPath == null) throw AiException("手写答案读取失败，请重新上传")
                }
            } catch (error: Exception) {
                _state.update { it.copy(error = error.userFacingMessage()) }
                return@launch
            }
            updateCurrent {
                it.copy(
                    submitted = true,
                    userAnswer = answer.ifBlank { "（图片作答）" },
                    userImagePath = importedPath.orEmpty()
                )
            }
            _state.update {
                it.copy(answerText = "", selectedOption = "", answerImageUri = null, error = null)
            }
            if (_state.value.questions.all { question -> question.submitted }) {
                gradeAll()
            } else {
                next()
            }
        }
    }

    fun markDontKnow() {
        val snapshot = _state.value
        val question = snapshot.current ?: return
        if (question.submitted || question.answered || snapshot.grading) return

        if (snapshot.multiMode) {
            updateCurrent { it.copy(submitted = true, userAnswer = "不会") }
            _state.update {
                it.copy(answerText = "", selectedOption = "", answerImageUri = null, error = null)
            }
            if (_state.value.questions.all { q -> q.submitted }) gradeAll() else next()
        } else {
            updateCurrent {
                it.copy(
                    answered = true,
                    submitted = true,
                    isCorrect = false,
                    userAnswer = "不会",
                    feedback = "先看参考答案，把关键一步和对应知识点记下来。",
                    encouragement = "这道题先收下，下一题继续。"
                )
            }
            recordAttempt(snapshot.subject, snapshot.knowledgePoint, false)
            _state.update {
                it.copy(answerText = "", selectedOption = "", answerImageUri = null, error = null)
            }
        }
    }

    private fun gradeAll() {
        viewModelScope.launch {
            _state.update { it.copy(grading = true, error = null) }
            val snapshot = _state.value
            val subject = snapshot.subject
            val knowledgePoint = snapshot.knowledgePoint
            var failure: String? = null

            for ((index, question) in snapshot.questions.withIndex()) {
                if (!question.submitted) continue
                if (question.userAnswer == "不会") {
                    updateAt(index) {
                        it.copy(
                            answered = true,
                            isCorrect = false,
                            feedback = "先看参考答案，把关键一步和对应知识点记下来。",
                            encouragement = "这道题先收下，剩下的继续。"
                        )
                    }
                    practiceRepository.addAttempt(subject, knowledgePoint, false)
                    continue
                }
                try {
                    val bytes = question.userImagePath.takeIf { it.isNotBlank() }
                        ?.let { imageStore.readBytes(it) }
                    val grade = aiClient.grade(
                        subject = subject,
                        question = question,
                        userAnswer = question.userAnswer,
                        imageBytes = bytes
                    )
                    updateAt(index) {
                        it.copy(
                            answered = true,
                            isCorrect = grade.correct,
                            feedback = grade.analysis,
                            encouragement = grade.encouragement
                        )
                    }
                    practiceRepository.addAttempt(subject, knowledgePoint, grade.correct)
                } catch (error: Exception) {
                    failure = error.userFacingMessage()
                    updateAt(index) {
                        it.copy(
                            answered = true,
                            isCorrect = false,
                            feedback = "这题批改失败，先对照参考答案核对。",
                            encouragement = "答案已经保留，批改可以稍后重试。"
                        )
                    }
                }
            }

            _state.update {
                it.copy(
                    grading = false,
                    finished = true,
                    error = failure?.let { message -> "部分题目批改失败：$message" }
                )
            }
        }
    }

    fun next() {
        _state.update {
            if (it.currentIndex < it.questions.lastIndex) {
                it.copy(
                    currentIndex = it.currentIndex + 1,
                    answerText = "",
                    selectedOption = "",
                    answerImageUri = null,
                    error = null
                )
            } else {
                it
            }
        }
    }

    fun practiceAgain() {
        start()
    }

    fun endSession() {
        cleanupAnswerImages(_state.value.questions)
        _state.update {
            it.copy(
                questions = emptyList(),
                currentIndex = 0,
                answerText = "",
                selectedOption = "",
                answerImageUri = null,
                error = null,
                note = "",
                grading = false,
                generating = false,
                finished = false
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun buildAnswer(state: PracticeState): String = buildString {
        if (state.selectedOption.isNotBlank()) append(state.selectedOption)
        if (state.answerText.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(state.answerText.trim())
        }
    }

    private fun recordAttempt(subject: String, knowledgePoint: String, correct: Boolean) {
        viewModelScope.launch {
            practiceRepository.addAttempt(subject, knowledgePoint, correct)
        }
    }

    private fun cleanupAnswerImages(questions: List<PracticeQuestion>) {
        val paths = questions.mapNotNull { question ->
            question.userImagePath.takeIf { it.isNotBlank() }
        }
        if (paths.isNotEmpty()) imageStore.delete(paths)
    }

    private fun updateCurrent(transform: (PracticeQuestion) -> PracticeQuestion) {
        _state.update { state ->
            val index = state.currentIndex
            if (index !in state.questions.indices) return@update state
            val updated = state.questions.toMutableList()
            updated[index] = transform(updated[index])
            state.copy(questions = updated)
        }
    }

    private fun updateAt(index: Int, transform: (PracticeQuestion) -> PracticeQuestion) {
        _state.update { state ->
            if (index !in state.questions.indices) return@update state
            val updated = state.questions.toMutableList()
            updated[index] = transform(updated[index])
            state.copy(questions = updated)
        }
    }
}

class AppVmFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> MainViewModel(
                container.recordRepository,
                container.settingsRepository,
                container.aiClient,
                container.imageStore,
                container.practiceRepository,
                container.discussionRepository,
                container.questionBankRepository,
                container.uiPrefsRepository
            )
            modelClass.isAssignableFrom(UploadViewModel::class.java) -> UploadViewModel(
                container.recordRepository,
                container.aiClient,
                container.imageStore
            )
            modelClass.isAssignableFrom(PracticeViewModel::class.java) -> PracticeViewModel(
                container.aiClient,
                container.imageStore,
                container.practiceRepository,
                container.settingsRepository,
                container.questionBankRepository
            )
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        return viewModel as T
    }
}

fun Throwable.userFacingMessage(): String =
    (this as? AiException)?.message
        ?: message?.takeIf { it.isNotBlank() }
        ?: "操作失败，请重试"
