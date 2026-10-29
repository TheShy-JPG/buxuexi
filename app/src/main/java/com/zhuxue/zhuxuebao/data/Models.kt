package com.zhuxue.zhuxuebao.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

val SUBJECTS = listOf("语文", "数学", "英语", "物理", "化学", "生物", "政治", "历史", "地理")

fun matchSubject(value: String): String? {
    val text = value.trim()
    if (text.isBlank()) return null
    return SUBJECTS.firstOrNull { text.contains(it) }
}

@Serializable
data class ResourceLink(
    val title: String = "",
    val url: String = "",
    val platform: String = ""
)

@Serializable
data class KnowledgePoint(
    val name: String = "",
    val difficulty: String = "MEDIUM",
    val frequency: String = "OCCASIONAL",
    val errorPattern: String = "",
    val learningDirection: String = "",
    val searchQuery: String = "",
    val resources: List<ResourceLink> = emptyList()
)

@Serializable
data class AnalyzedQuestion(
    val knowledgePoint: String = "",
    val correct: Boolean = false,
    val note: String = ""
)

@Serializable
data class AnalysisResult(
    val title: String = "",
    val subject: String = "其他",
    val summary: String = "",
    val knowledgePoints: List<KnowledgePoint> = emptyList(),
    val thinkingAdvice: List<String> = emptyList(),
    val practiceFocus: List<String> = emptyList(),
    val questions: List<AnalyzedQuestion> = emptyList()
)

@Serializable
data class PracticeQuestion(
    val id: String = "",
    val text: String = "",
    val options: List<String> = emptyList(),
    val answer: String = "",
    val explanation: String = "",
    val sourceType: String = "AI",
    val sourceTitle: String = "",
    val sourceUrl: String = "",
    val paperUrl: String = "",
    val userAnswer: String = "",
    val userImagePath: String = "",
    val isCorrect: Boolean? = null,
    val feedback: String = "",
    val encouragement: String = "",
    val answered: Boolean = false,
    val submitted: Boolean = false
)

@Serializable
data class PracticePayload(
    val questions: List<PracticeQuestion> = emptyList(),
    val note: String = ""
)

@Serializable
data class GradeResult(
    val correct: Boolean = false,
    val analysis: String = "",
    val encouragement: String = ""
)

data class StudyRecord(
    val id: Long,
    val subject: String,
    val title: String,
    val createdAt: Long,
    val imagePaths: List<String>,
    val analysis: AnalysisResult
)

data class PracticeAttempt(
    val subject: String,
    val knowledgePoint: String,
    val correct: Boolean,
    val at: Long
)

data class DiscussedPoint(
    val id: Long,
    val subject: String,
    val name: String,
    val note: String,
    val at: Long,
    val learningDirection: String = "",
    val searchQuery: String = ""
)

data class WrongQuestionRef(val recordId: Long, val questionIndex: Int)

fun extractKeyword(text: String, fallback: String = "未命名错题"): String {
    val cleaned = text.replace(Regex("\\s+"), " ").trim()
    if (cleaned.isBlank()) return fallback
    val clause = cleaned
        .split('。', '，', '；', '：', '！', '？', '\n', '.', ',', ';', ':', '!', '?')
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
    var value = clause.ifBlank { cleaned }
    listOf("本次", "这次", "该题", "本题", "这道题", "主要", "问题", "集中在", "在于", "错题", "暴露", "说明")
        .forEach { value = value.removePrefix(it).trim() }
    return value.take(14).trim().ifBlank { fallback }
}

fun AnalysisResult.mentionsKnowledge(names: Set<String>): Boolean =
    knowledgePoints.any { it.name.trim() in names } ||
        questions.any { it.knowledgePoint.trim() in names }

data class KnowledgeStat(
    val name: String,
    val subject: String,
    val difficulty: String,
    val frequency: String,
    val count: Int,
    val lastAt: Long,
    val errorPattern: String,
    val learningDirection: String,
    val searchQuery: String,
    val resources: List<ResourceLink>,
    val correctQuestions: Int = 0,
    val wrongQuestions: Int = 0,
    val attemptCount: Int = 0,
    val correctAttempts: Int = 0,
    val recentAccuracy: Float? = null,
    val lastPracticeAt: Long? = null,
    val discussed: Boolean = false
) {
    val answeredTotal: Int get() = wrongQuestions + correctQuestions + attemptCount
    val masteryPercent: Int? get() =
        if (answeredTotal == 0) null else (correctQuestions + correctAttempts) * 100 / answeredTotal
    val improved: Boolean get() = attemptCount >= 3 && (recentAccuracy ?: 0f) >= 0.85f
    val notPracticed: Boolean get() = discussed && attemptCount == 0
}

private data class StatAccumulator(
    var name: String = "",
    var wrong: Int = 0,
    var correct: Int = 0,
    var lastAt: Long = 0L,
    var point: KnowledgePoint = KnowledgePoint(),
    var subject: String = "",
    var attempts: Int = 0,
    var attemptCorrect: Int = 0,
    var lastPracticeAt: Long = 0L,
    var discussed: Boolean = false,
    val recent: ArrayDeque<Boolean> = ArrayDeque()
)

fun List<StudyRecord>.aggregateKnowledge(
    attempts: List<PracticeAttempt> = emptyList(),
    discussedPoints: List<DiscussedPoint> = emptyList()
): List<KnowledgeStat> {
    val map = LinkedHashMap<String, StatAccumulator>()
    fun acc(subject: String, name: String): StatAccumulator =
        map.getOrPut(subject + "|" + name) {
            StatAccumulator(name = name, subject = subject)
        }

    for (record in this) {
        val points = record.analysis.knowledgePoints.filter { it.name.isNotBlank() }
        for (point in points) {
            val entry = acc(record.subject, point.name.trim())
            if (record.createdAt >= entry.lastAt) {
                entry.lastAt = record.createdAt
                entry.point = point
            }
        }
        val questions = record.analysis.questions.filter { it.knowledgePoint.isNotBlank() }
        for (question in questions) {
            val name = question.knowledgePoint.trim()
            val entry = acc(record.subject, name)
            if (question.correct) entry.correct += 1 else entry.wrong += 1
            if (record.createdAt >= entry.lastAt) entry.lastAt = record.createdAt
            if (entry.point.name.isBlank()) entry.point = KnowledgePoint(name = name)
        }
        val questionNames = questions.map { it.knowledgePoint.trim() }.toSet()
        for (point in points) {
            if (point.name.trim() !in questionNames) {
                acc(record.subject, point.name.trim()).wrong += 1
            }
        }
    }

    for (attempt in attempts.sortedBy { it.at }) {
        val entry = acc(attempt.subject, attempt.knowledgePoint.trim())
        entry.attempts += 1
        if (attempt.correct) entry.attemptCorrect += 1
        if (attempt.at >= entry.lastPracticeAt) entry.lastPracticeAt = attempt.at
        entry.recent.addLast(attempt.correct)
        while (entry.recent.size > 10) entry.recent.removeFirst()
    }

    for (point in discussedPoints) {
        val name = point.name.trim()
        if (name.isBlank()) continue
        val entry = acc(point.subject, name)
        entry.discussed = true
        if (point.at >= entry.lastAt) entry.lastAt = point.at
        if (entry.point.name.isBlank()) {
            entry.point = KnowledgePoint(
                name = name,
                difficulty = "MEDIUM",
                frequency = "OCCASIONAL",
                errorPattern = point.note,
                learningDirection = point.learningDirection,
                searchQuery = point.searchQuery.ifBlank { name }
            )
        } else if (entry.point.learningDirection.isBlank() && point.learningDirection.isNotBlank()) {
            entry.point = entry.point.copy(
                learningDirection = point.learningDirection,
                searchQuery = entry.point.searchQuery.ifBlank { point.searchQuery }
            )
        }
    }

    return map.values
        .map {
            KnowledgeStat(
                name = it.name.ifBlank { it.point.name.ifBlank { "未命名知识点" } },
                subject = it.subject,
                difficulty = it.point.difficulty.uppercase(),
                frequency = it.point.frequency.uppercase(),
                count = it.wrong,
                lastAt = it.lastAt,
                errorPattern = it.point.errorPattern,
                learningDirection = it.point.learningDirection,
                searchQuery = it.point.searchQuery,
                resources = it.point.resources,
                correctQuestions = it.correct,
                wrongQuestions = it.wrong,
                attemptCount = it.attempts,
                correctAttempts = it.attemptCorrect,
                recentAccuracy = if (it.recent.isEmpty()) {
                    null
                } else {
                    it.recent.count { ok -> ok }.toFloat() / it.recent.size
                },
                lastPracticeAt = it.lastPracticeAt.takeIf { time -> time > 0L },
                discussed = it.discussed
            )
        }
        .sortedWith(
            compareBy<KnowledgeStat> { sortBucket(it) }
                .thenBy { difficultyRank(it.difficulty) }
                .thenByDescending { it.count }
                .thenByDescending { it.lastAt }
        )
}

private fun sortBucket(stat: KnowledgeStat): Int {
    if (stat.improved) return 2
    if (stat.frequency == "FREQUENT" && stat.difficulty == "EASY") return 0
    if (stat.frequency == "FREQUENT" || stat.difficulty == "HARD") return 1
    return 3
}

fun KnowledgeStat.toKnowledgePoint(): KnowledgePoint = KnowledgePoint(
    name = name,
    difficulty = difficulty,
    frequency = frequency,
    errorPattern = errorPattern,
    learningDirection = learningDirection,
    searchQuery = searchQuery,
    resources = resources
)

fun difficultyRank(difficulty: String): Int = when (difficulty.uppercase()) {
    "EASY" -> 0
    "MEDIUM" -> 1
    "HARD" -> 2
    else -> 1
}

fun difficultyLabel(difficulty: String): String = when (difficulty.uppercase()) {
    "EASY" -> "易得分"
    "HARD" -> "较难"
    else -> "中等"
}

fun frequencyLabel(frequency: String): String = when (frequency.uppercase()) {
    "FREQUENT" -> "常错"
    else -> "偶发"
}
