package com.zhuxue.zhuxuebao.ai

import com.zhuxue.zhuxuebao.BuildConfig
import com.zhuxue.zhuxuebao.data.AnalysisResult
import com.zhuxue.zhuxuebao.data.AnalyzedQuestion
import com.zhuxue.zhuxuebao.data.AppJson
import com.zhuxue.zhuxuebao.data.AppSettings
import com.zhuxue.zhuxuebao.data.GradeResult
import com.zhuxue.zhuxuebao.data.KnowledgePoint
import com.zhuxue.zhuxuebao.data.PracticePayload
import com.zhuxue.zhuxuebao.data.PracticeQuestion
import com.zhuxue.zhuxuebao.data.ResourceLink
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class AiException(message: String, val code: Int = 0) : Exception(message)

class ZhipuClient(private val settingsProvider: suspend () -> AppSettings) {

    private val json = AppJson
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun analyze(images: List<ByteArray>, subjectHint: String?): AnalysisResult {
        if (images.isEmpty()) throw AiException("没有可分析的图片，请重新上传")
        val settings = currentSettings()
        val encoded = withContext(Dispatchers.Default) {
            images.map { Base64.getEncoder().encodeToString(it) }
        }
        val content = buildJsonArray {
            encoded.forEach { value ->
                add(
                    buildJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put("url", value)
                        }
                    }
                )
            }
            add(
                buildJsonObject {
                    put("type", "text")
                    put(
                        "text",
                        buildString {
                            append("请分析这些图片中的错题或试卷。")
                            if (!subjectHint.isNullOrBlank()) append("科目提示：$subjectHint。")
                            append("如果图片中有多道错题，请逐题归纳。只输出约定的 JSON。")
                        }
                    )
                }
            )
        }
        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", ANALYSIS_SYSTEM)
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", content)
            })
        }
        val output = chat(
            settings = settings,
            messages = messages,
            model = settings.visionModel,
            useSearch = false,
            temperature = 0.2,
            maxTokens = 4096
        )
        return parseAnalysis(output.text, subjectHint)
    }

    suspend fun learnGuide(subject: String, name: String, note: String): KnowledgePoint {
        val settings = currentSettings()
        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", LEARN_GUIDE_SYSTEM)
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", buildString {
                    append("科目：$subject\n")
                    append("想学的知识点：$name\n")
                    if (note.isNotBlank()) append("学生补充的困惑：$note\n")
                    append("请先指出学习方向，再给出检索关键词。只输出约定的 JSON。")
                })
            })
        }
        val output = chat(
            settings = settings,
            messages = messages,
            model = settings.textModel,
            useSearch = false,
            temperature = 0.4,
            maxTokens = 1000
        )
        val root = parseJsonObject(output.text)
        val direction = root.str("learningDirection").ifBlank { root.str("direction") }
        val keyPoints = stringList(root["keyPoints"])
        val point = KnowledgePoint(
            name = name,
            difficulty = normalizeDifficulty(root.str("difficulty")),
            frequency = normalizeFrequency(root.str("frequency")),
            errorPattern = root.str("errorPattern").ifBlank { note },
            learningDirection = buildString {
                if (direction.isNotBlank()) append(direction)
                if (keyPoints.isNotEmpty()) {
                    if (isNotEmpty()) append('\n')
                    append("先看：")
                    append(keyPoints.joinToString(" → "))
                }
            }.ifBlank { "先把概念和常见题型过一遍，再按易错点做少量针对练习。" },
            searchQuery = root.str("searchQuery").ifBlank { "$subject $name" }
        )
        return point.copy(resources = buildSearchResources(point))
    }

    suspend fun generateQuestions(
        subject: String,
        knowledgePoint: String,
        count: Int,
        mode: String,
        sources: List<String>,
        bankText: String?,
        bankAvailable: Boolean = true
    ): PracticePayload {
        val settings = currentSettings()
        val actualCount = count.coerceIn(1, 8)
        val enabled = sources
            .map { it.uppercase() }
            .filter { it in AppSettings.ALL_SOURCES }
            .distinct()
            .ifEmpty { listOf(AppSettings.SOURCE_AI) }
        val attempted = mutableListOf<String>()
        var lastError: AiException? = null

        for (source in enabled) {
            attempted += sourceLabel(source)
            try {
                val payload = when (source) {
                    AppSettings.SOURCE_BANK -> {
                        if (bankText.isNullOrBlank()) {
                            lastError = AiException(
                                if (bankAvailable) "题库中没有找到相关题目" else "还没有上传题库"
                            )
                            continue
                        }
                        generateFromBank(
                            settings, subject, knowledgePoint, actualCount, mode, bankText
                        )
                    }
                    AppSettings.SOURCE_WEB -> generateFromWeb(
                        settings, subject, knowledgePoint, actualCount, mode
                    )
                    else -> generateFromAi(
                        settings, subject, knowledgePoint, actualCount, mode
                    )
                }
                if (payload.questions.isNotEmpty()) return payload
                if (source == AppSettings.SOURCE_BANK) {
                    lastError = AiException("题库中没有找到相关题目")
                }
            } catch (error: AiException) {
                if (error.code != 0) throw error
                lastError = error
            }
        }

        val detail = lastError?.message
            ?.takeIf { it.isNotBlank() && !it.contains("没有生成有效题目") }
        throw AiException(
            buildString {
                append("这次没有找到合适的题目")
                if (attempted.isNotEmpty()) append("（已尝试：${attempted.joinToString("、")}）")
                append("。可以换个知识点、补充题库，或到设置里调整题目来源。")
                if (detail != null) append(" 提示：$detail")
            }
        )
    }

    private suspend fun generateFromBank(
        settings: AppSettings,
        subject: String,
        knowledgePoint: String,
        count: Int,
        mode: String,
        bankText: String
    ): PracticePayload {
        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", BANK_SYSTEM)
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", """
                    科目：$subject
                    知识点：$knowledgePoint
                    需要题目数量：$count
                    练习模式：${if (mode == "MULTI") "多题练习" else "单题练习"}

                    题库原文如下（【】内是文件名）：
                    <<<
                    $bankText
                    >>>

                    请只从题库原文中挑选题目，只输出约定的 JSON。
                """.trimIndent())
            })
        }
        val output = chat(
            settings = settings,
            messages = messages,
            model = settings.textModel,
            useSearch = false,
            temperature = 0.2,
            maxTokens = 4096
        )
        return parseQuestions(
            output.text, emptyList(), count,
            defaultNote = "题目取自你上传的本地题库。"
        )
    }

    private suspend fun generateFromWeb(
        settings: AppSettings,
        subject: String,
        knowledgePoint: String,
        count: Int,
        mode: String
    ): PracticePayload {
        if (!settings.webSearch) throw AiException("设置里已关闭联网搜索")
        if (!AiEndpoint.supportsWebSearch(settings.baseUrl)) {
            throw AiException("当前 AI 服务商不支持联网真题检索，已跳过联网来源")
        }
        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", WEB_SYSTEM)
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", """
                    请围绕高中${subject}知识点“$knowledgePoint”准备 $count 道练习题。
                    练习模式：${if (mode == "MULTI") "多题练习" else "单题练习"}。
                    真题只认高考真题；请先联网检索“$subject $knowledgePoint 高考真题”。
                    只输出约定的 JSON。
                """.trimIndent())
            })
        }
        val output = chat(
            settings = settings,
            messages = messages,
            model = settings.textModel,
            useSearch = true,
            temperature = 0.4,
            maxTokens = WEB_QUESTION_MAX_TOKENS,
            searchQuery = "$subject $knowledgePoint 高考真题"
        )
        return parseQuestions(
            output.text, output.links, count,
            defaultNote = "已结合联网高考真题搜索出题。"
        )
    }

    private suspend fun generateFromAi(
        settings: AppSettings,
        subject: String,
        knowledgePoint: String,
        count: Int,
        mode: String
    ): PracticePayload {
        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", AI_SYSTEM)
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", """
                    请围绕高中${subject}知识点“$knowledgePoint”原创 $count 道练习题。
                    练习模式：${if (mode == "MULTI") "多题练习" else "单题练习"}。
                    只输出约定的 JSON。
                """.trimIndent())
            })
        }
        val output = chat(
            settings = settings,
            messages = messages,
            model = settings.textModel,
            useSearch = false,
            temperature = 0.7,
            maxTokens = 4096
        )
        return parseQuestions(
            output.text, emptyList(), count,
            defaultNote = "题目由 AI 自编生成。"
        )
    }

    private fun sourceLabel(source: String): String = when (source) {
        AppSettings.SOURCE_BANK -> "本地题库"
        AppSettings.SOURCE_WEB -> "联网高考真题"
        else -> "AI 自编题"
    }

    suspend fun grade(
        subject: String,
        question: PracticeQuestion,
        userAnswer: String,
        imageBytes: ByteArray?
    ): GradeResult {
        val settings = currentSettings()
        val model = if (imageBytes != null) settings.visionModel else settings.textModel
        val answerText = userAnswer.ifBlank { "（学生以图片作答，请先识别图片中的内容）" }
        val prompt = buildString {
            append("科目：$subject\n")
            append("题目：${question.text}\n")
            if (question.options.isNotEmpty()) {
                append("选项：\n")
                question.options.forEachIndexed { index, option ->
                    append(optionLabel(index, option)).append('\n')
                }
            }
            append("参考答案：${question.answer}\n")
            append("参考解析：${question.explanation}\n")
            append("学生作答：$answerText\n")
            append("请判断学生是否答对，并输出约定的 JSON。")
        }

        val userContent: JsonElement = if (imageBytes != null) {
            val encoded = withContext(Dispatchers.Default) {
                Base64.getEncoder().encodeToString(imageBytes)
            }
            buildJsonArray {
                add(buildJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") {
                        put("url", encoded)
                    }
                })
                add(buildJsonObject {
                    put("type", "text")
                    put("text", prompt)
                })
            }
        } else {
            JsonPrimitive(prompt)
        }

        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", GRADE_SYSTEM.replace("{subject}", subject))
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", userContent)
            })
        }
        val output = chat(
            settings = settings,
            messages = messages,
            model = model,
            useSearch = false,
            temperature = 0.2,
            maxTokens = 1200
        )
        return parseGrade(output.text)
    }

    suspend fun testConnection(): String {
        val settings = currentSettings()
        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", "请只回复四个字：连接成功")
            })
        }
        val output = chat(
            settings = settings,
            messages = messages,
            model = settings.textModel,
            useSearch = false,
            temperature = 0.1,
            maxTokens = 32
        )
        return output.text.take(40)
    }

    private suspend fun currentSettings(): AppSettings {
        val settings = settingsProvider()
        if (settings.apiKey.isBlank()) {
            throw AiException("请先在设置中填写 AI 服务商的 API Key")
        }
        return settings
    }

    private suspend fun chat(
        settings: AppSettings,
        messages: JsonArray,
        model: String,
        useSearch: Boolean,
        temperature: Double,
        maxTokens: Int,
        searchQuery: String? = null
    ): ChatOutput {
        val body = withContext(Dispatchers.Default) {
            buildJsonObject {
                put("model", model)
                put("messages", messages)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
                put("stream", false)
                if (useSearch) {
                    put("tools", buildJsonArray { add(webSearchTool(searchQuery)) })
                }
            }
        }

        val response = try {
            post(settings, body)
        } catch (error: AiException) {
            val canRetryWithoutTools = useSearch && error.code in setOf(400, 422, 500, -1)
            if (!canRetryWithoutTools) throw error
            post(settings, JsonObject(body.filterKeys { it != "tools" }))
        }

        val message = response["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?: throw AiException("AI 响应缺少 message 字段，请重试")

        val text = extractContent(message)
        if (text.isBlank()) {
            val finishReason = response["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("finish_reason")
                ?.jsonPrimitive
                ?.contentOrNull
            if (finishReason == "length") {
                throw AiException("AI 输出达到长度上限被截断，请重试或减少题量")
            }
            throw AiException("AI 没有返回内容，请重试")
        }
        return ChatOutput(text = text, links = extractSearchLinks(response, message))
    }

    private suspend fun post(settings: AppSettings, body: JsonObject): JsonObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(endpoint(settings.baseUrl))
                .header("Authorization", "Bearer ${settings.apiKey}")
                .post(body.toString().toRequestBody(mediaType))
                .build()

            try {
                client.newCall(request).await().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val detail = runCatching {
                            json.parseToJsonElement(raw)
                                .jsonObject["error"]
                                ?.jsonObject
                                ?.get("message")
                                ?.jsonPrimitive
                                ?.contentOrNull
                        }.getOrNull()
                        val hint = when (response.code) {
                            401, 403 -> "API Key 无效或没有权限"
                            404 -> "模型不存在，请在设置中修改模型 ID"
                            429 -> "请求太频繁或额度不足"
                            in 500..599 -> "AI 服务暂时不可用"
                            else -> "AI 请求失败（${response.code}）"
                        }
                        throw AiException(
                            if (detail.isNullOrBlank()) hint else "$hint：$detail",
                            response.code
                        )
                    }
                    runCatching {
                        json.parseToJsonElement(raw).jsonObject
                    }.getOrElse {
                        throw AiException("无法解析 AI 响应，请重试")
                    }
                }
            } catch (error: AiException) {
                throw error
            } catch (error: IOException) {
                throw AiException("网络连接失败，请检查网络后重试", -1)
            }
        }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isCancelled) {
                    response.close()
                    return
                }
                continuation.resume(response)
            }
        })
    }

    private fun endpoint(baseUrl: String): String = try {
        AiEndpoint.resolve(baseUrl, allowLoopback = BuildConfig.DEBUG)
    } catch (error: IllegalArgumentException) {
        throw AiException(error.message ?: "服务地址无效", CODE_INVALID_ENDPOINT)
    }

    private fun webSearchTool(searchQuery: String?): JsonObject = buildJsonObject {
        put("type", "web_search")
        putJsonObject("web_search") {
            put("enable", true)
            put("search_result", true)
            put("search_engine", "search_pro")
            put("count", 5)
            put("search_recency_filter", "noLimit")
            put("content_size", "medium")
            if (!searchQuery.isNullOrBlank()) put("search_query", searchQuery)
        }
    }

    private fun extractContent(message: JsonObject): String {
        val content = message["content"] ?: return ""
        return when (content) {
            is JsonPrimitive -> content.contentOrNull.orEmpty()
            is JsonArray -> content.mapNotNull { element ->
                (element as? JsonObject)?.str("text")?.takeIf { it.isNotBlank() }
            }.joinToString("\n")
            else -> ""
        }
    }

    private fun extractSearchLinks(root: JsonObject, message: JsonObject): List<ResourceLink> {
        val candidates = listOf(
            root["search_result"],
            message["search_result"],
            root["web_search"],
            message["web_search"]
        )
        val links = mutableListOf<ResourceLink>()
        candidates.filterNotNull().forEach { element ->
            val array = when (element) {
                is JsonArray -> element
                is JsonObject -> (element["search_result"] as? JsonArray) ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
            array.forEach { item ->
                val obj = item as? JsonObject ?: return@forEach
                val url = obj.str("link").ifBlank { obj.str("url") }
                if (!url.startsWith("http")) return@forEach
                val title = obj.str("title").ifBlank { obj.str("content").take(60) }
                links += ResourceLink(title = title, url = url, platform = "SEARCH")
            }
        }
        return links.distinctBy { it.url }
    }

    private fun parseAnalysis(text: String, subjectHint: String?): AnalysisResult {
        val root = parseJsonObject(text)
        val subject = root.str("subject").ifBlank { subjectHint.orEmpty() }.ifBlank { "其他" }
        val title = root.str("title").ifBlank { "${subject}错题分析" }
        val summary = root.str("summary")
        val points = (root["knowledgePoints"] as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj.str("name")
            if (name.isBlank()) return@mapNotNull null
            val point = KnowledgePoint(
                name = name,
                difficulty = normalizeDifficulty(obj.str("difficulty")),
                frequency = normalizeFrequency(obj.str("frequency")),
                errorPattern = obj.str("errorPattern"),
                learningDirection = obj.str("learningDirection"),
                searchQuery = obj.str("searchQuery")
            )
            point.copy(resources = buildSearchResources(point))
        }
        val questions = (root["questions"] as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj.str("knowledgePoint").ifBlank { obj.str("name") }
            if (name.isBlank()) return@mapNotNull null
            AnalyzedQuestion(
                knowledgePoint = name,
                correct = obj["correct"].toBooleanValue(),
                note = obj.str("note")
            )
        }
        if (summary.isBlank() && points.isEmpty()) {
            throw AiException("AI 没有识别出有效的错题信息，请换一张更清晰的图片重试")
        }
        return AnalysisResult(
            title = title,
            subject = subject,
            summary = summary,
            knowledgePoints = points,
            thinkingAdvice = stringList(root["thinkingAdvice"]),
            practiceFocus = stringList(root["practiceFocus"]),
            questions = questions
        )
    }

    private fun parseQuestions(
        text: String,
        searchLinks: List<ResourceLink>,
        requestedCount: Int,
        defaultNote: String
    ): PracticePayload {
        val root = parseJsonObject(text)
        val realLinks = searchLinks.map { it.url }
        val questions = (root["questions"] as? JsonArray).orEmpty().mapNotNull { element ->
            parseQuestion(element, realLinks)
        }.take(requestedCount)
        if (questions.isEmpty()) {
            throw AiException("没有生成有效题目，请检查网络后重试")
        }
        return PracticePayload(
            questions = questions,
            note = root.str("note").ifBlank { defaultNote }
        )
    }

    private fun parseQuestion(element: JsonElement, allowedLinks: List<String>): PracticeQuestion? {
        val obj = element as? JsonObject ?: return null
        val text = obj.str("text")
        val answer = obj.str("answer")
        if (text.isBlank() || answer.isBlank()) return null

        val options = (obj["options"] as? JsonArray).orEmpty().mapNotNull { option ->
            (option as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        }
        var sourceType = obj.str("sourceType").uppercase()
            .takeIf { it in setOf("REAL", "WEB", "AI", "BANK") } ?: "AI"
        var sourceTitle = obj.str("sourceTitle")
        var sourceUrl = obj.str("sourceUrl")
        var paperUrl = obj.str("paperUrl")

        if (sourceType == "AI") {
            sourceTitle = ""
            sourceUrl = ""
            paperUrl = ""
        } else if (sourceType == "BANK") {
            sourceUrl = ""
            paperUrl = ""
            if (sourceTitle.isBlank()) sourceTitle = "本地题库"
        } else {
            sourceUrl = matchLink(sourceUrl, allowedLinks).orEmpty()
            paperUrl = matchLink(paperUrl, allowedLinks).orEmpty()
            if (sourceUrl.isBlank() && sourceTitle.isNotBlank()) {
                sourceUrl = baiduSearch("$sourceTitle 高考真题")
            }
            if (paperUrl.isBlank() && sourceType == "REAL" && sourceTitle.isNotBlank()) {
                paperUrl = baiduSearch("$sourceTitle 高考试卷")
            }
            if (sourceTitle.isBlank()) sourceTitle = "高考真题"
        }

        return PracticeQuestion(
            id = UUID.randomUUID().toString(),
            text = text,
            options = options,
            answer = answer,
            explanation = obj.str("explanation"),
            sourceType = sourceType,
            sourceTitle = sourceTitle,
            sourceUrl = sourceUrl,
            paperUrl = paperUrl
        )
    }

    private fun matchLink(candidate: String, allowed: List<String>): String? {
        if (candidate.isBlank()) return null
        val target = normalizeUrl(candidate)
        if (target.length < 10) return null
        return allowed.firstOrNull { normalizeUrl(it) == target }
            ?: allowed.firstOrNull {
                val real = normalizeUrl(it)
                real.length > 12 && (target.startsWith(real) || real.startsWith(target))
            }
    }

    private fun normalizeUrl(url: String): String = url.trim().lowercase()
        .substringBefore('#')
        .substringBefore('?')
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .removeSuffix("/")

    private fun parseGrade(text: String): GradeResult {
        val root = parseJsonObject(text)
        val correctElement = root["correct"]
        val correct = correctElement?.let { element ->
            (element as? JsonPrimitive)?.booleanOrNull
                ?: (element as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
        } ?: false
        return GradeResult(
            correct = correct,
            analysis = root.str("analysis").ifBlank { "答案已记录，结合参考答案再核对一下关键步骤。" },
            encouragement = root.str("encouragement").ifBlank {
                if (correct) "这道题拿下，继续保持。" else "先把这个坑记住，下一题会更好。"
            }
        )
    }

    private fun parseJsonObject(text: String): JsonObject {
        val cleaned = text.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw AiException("AI 返回的格式不正确，请重试")
        }
        return runCatching {
            json.parseToJsonElement(cleaned.substring(start, end + 1)).jsonObject
        }.getOrElse {
            throw AiException("AI 返回的 JSON 无法解析，请重试")
        }
    }

    private fun buildSearchResources(point: KnowledgePoint): List<ResourceLink> {
        val query = point.searchQuery.ifBlank {
            listOf(point.name, "高中", "学习方法").joinToString(" ")
        }
        return listOf(
            ResourceLink(
                title = "B站 · ${point.name} 讲解",
                url = "https://search.bilibili.com/all?keyword=${encode(query)}",
                platform = "VIDEO"
            ),
            ResourceLink(
                title = "百度 · ${point.name} 学习方法",
                url = "https://www.baidu.com/s?wd=${encode("$query 学习方法")}",
                platform = "ARTICLE"
            )
        )
    }

    private fun normalizeDifficulty(value: String): String = when (value.uppercase()) {
        "EASY" -> "EASY"
        "HARD" -> "HARD"
        else -> "MEDIUM"
    }

    private fun normalizeFrequency(value: String): String = when (value.uppercase()) {
        "FREQUENT" -> "FREQUENT"
        else -> "OCCASIONAL"
    }

    private fun stringList(element: JsonElement?): List<String> =
        (element as? JsonArray).orEmpty().mapNotNull { item ->
            (item as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        }

    private fun JsonElement?.toBooleanValue(): Boolean {
        val primitive = this as? JsonPrimitive ?: return false
        return primitive.booleanOrNull ?: primitive.contentOrNull?.toBooleanStrictOrNull() ?: false
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun baiduSearch(query: String): String =
        "https://www.baidu.com/s?wd=${encode(query)}"

    private fun optionLabel(index: Int, text: String): String {
        val letter = ('A' + index).toString()
        val trimmed = text.trim()
        return if (trimmed.startsWith(letter)) trimmed else "$letter. $trimmed"
    }

    private data class ChatOutput(val text: String, val links: List<ResourceLink>)

    private companion object {
        const val CODE_INVALID_ENDPOINT = -2

        const val WEB_QUESTION_MAX_TOKENS = 12288

        const val LEARN_GUIDE_SYSTEM = """
你是一名高中学习规划师，服务对象是中国高中生。
学生想学一个知识点，可能暂时没有对应题目。你的任务是先指出学习方向，学生之后会自己去看讲解视频和文章。
必须遵守：
1. 只给学习路径和重点：先学什么、必须掌握什么、常见易错点、建议怎么练；不要讲具体题目的解答，不要长篇讲课。
2. 学习方向用 2-4 句话，具体、可执行，贴合高中教材顺序。
3. keyPoints 给 2-4 个先后学习的小主题，按学习顺序排列。
4. searchQuery 填适合搜索讲解视频和总结文章的中文关键词，包含科目和知识点。
5. 只输出一个 JSON 对象，不要 Markdown 代码块，不要额外说明。

JSON 结构：
{
  "learningDirection": "2-4 句话的学习方向",
  "keyPoints": ["先学的子主题", "接着学的子主题"],
  "searchQuery": "搜索关键词",
  "difficulty": "EASY|MEDIUM|HARD",
  "errorPattern": "如果学生补充了困惑，用一句话复述；没有就留空"
}
"""

        const val ANALYSIS_SYSTEM = """
你是一名高中学习诊断师，服务对象是中国高中生。
你的工作是根据错题或试卷图片做“诊断”，而不是讲课。
必须遵守：
1. 不讲解具体知识点，不给公式推导、解题步骤或题目答案。
2. 不编造视频、文章链接或试题来源；链接由客户端生成。
3. 每道错题尽量归纳到具体的高中知识点，并描述错误的思维表现。
4. 只输出一个 JSON 对象，不要 Markdown 代码块，不要额外说明。

difficulty 取值：EASY（基础、短期练习易拿分）、MEDIUM（中等综合）、HARD（综合性强、难拿分）。
frequency 取值：FREQUENT（同类错误反复出现或属于高频考点）、OCCASIONAL（偶发）。
searchQuery 填用于搜索该知识点讲解视频和学习方法的中文关键词。

questions 是对图片中每道题的统计：如果图片包含多道题（整张试卷），按题号逐题列出；knowledgePoint 必须使用 knowledgePoints 里出现过的名称。判断依据：有批改痕迹按批改结果，没有则根据作答内容判断；单选题无法确定时 correct 填 false。如果只有一道错题，questions 只给一条且 correct 为 false。

JSON 结构：
{
  "title": "简短标题，如：函数单调性错题分析",
  "subject": "数学",
  "summary": "2-4 句话说明本次错题暴露的主要问题",
  "knowledgePoints": [
    {
      "name": "知识点名称",
      "difficulty": "EASY|MEDIUM|HARD",
      "frequency": "FREQUENT|OCCASIONAL",
      "errorPattern": "错误思维或习惯的具体表现，只描述问题",
      "learningDirection": "建议先学什么、练什么，不给具体解法",
      "searchQuery": "搜索关键词"
    }
  ],
  "questions": [
    {"knowledgePoint": "知识点名称", "correct": false, "note": "对错依据，可留空"}
  ],
  "thinkingAdvice": ["针对思考方式的建议，每条一句"],
  "practiceFocus": ["优先练习的方向或题型"]
}
"""

        const val WEB_SYSTEM = """
你是一名严谨的高中出题老师。
要求：
1. 这里的“真题”只指高考真题：全国卷、新高考卷以及各省高考卷的原题。模拟题、练习题只能算网络题，不能标成真题。
2. 本轮已提供联网搜索工具，你必须先检索该知识点的高考真题。
3. 如果搜索结果中有可用题目，优先选用：高考原题 sourceType 填 REAL，其他网络题目填 WEB；sourceTitle 写来源名称（如“2024 新课标Ⅰ卷”），并把搜索结果中的 link 原样复制到 sourceUrl；整卷链接原样复制到 paperUrl。
4. sourceUrl 和 paperUrl 只能使用联网搜索结果中真实存在的链接，不能编造；找不到就留空。
5. 题目适合中国高中生，题干清晰，答案明确；选择题 options 至少 4 个，非选择题 options 为空数组。
6. 不要在题干中泄露答案。
7. 只输出一个 JSON 对象，不要 Markdown 代码块。

JSON 结构：
{
  "questions": [
    {
      "text": "题干",
      "options": ["选项内容1", "选项内容2", "选项内容3", "选项内容4"],
      "answer": "参考答案",
      "explanation": "简短解析",
      "sourceType": "REAL|WEB",
      "sourceTitle": "高考卷名称或网页来源名称",
      "sourceUrl": "题目链接，可为空",
      "paperUrl": "完整试卷链接，可为空"
    }
  ],
  "note": "一句话说明题目来源，如：2024 新课标Ⅰ卷；或搜索结果里没有合适的高考真题。"
}
"""

        const val AI_SYSTEM = """
你是一名高中出题老师，正在为指定知识点原创练习题。
要求：
1. 题目必须原创，不能声称来自某年某卷的高考真题。
2. 每道题 sourceType 统一填 AI，sourceTitle、sourceUrl、paperUrl 一律留空。
3. 题目适合中国高中生，题干清晰，答案明确；选择题 options 至少 4 个，非选择题 options 为空数组。
4. 不要在题干中泄露答案。
5. 只输出一个 JSON 对象，不要 Markdown 代码块。

JSON 结构：
{
  "questions": [
    {
      "text": "题干",
      "options": ["选项内容1", "选项内容2", "选项内容3", "选项内容4"],
      "answer": "参考答案",
      "explanation": "简短解析",
      "sourceType": "AI",
      "sourceTitle": "",
      "sourceUrl": "",
      "paperUrl": ""
    }
  ],
  "note": "一句话说明，如：题目由 AI 根据知识点自编。"
}
"""

        const val BANK_SYSTEM = """
你是一名高中出题助手，只能使用用户上传的题库原文。
要求：
1. 只从题库原文中挑选与指定知识点相关的题目，不要自己编题，也不要引用题库之外的内容。
2. 题干和选项尽量保持原文，不要改写题目条件；原文有答案和解析时优先沿用。
3. 如果原文没有答案，请补全参考答案和简短解析。
4. 每道题 sourceType 统一填 BANK；sourceTitle 填题库文件名（题库原文中用【文件名】标注）；sourceUrl 和 paperUrl 留空。
5. 如果题库原文中没有相关题目，questions 返回空数组，note 写“题库中没有相关题目”。
6. 只输出一个 JSON 对象，不要 Markdown 代码块。

JSON 结构：
{
  "questions": [
    {
      "text": "题干",
      "options": ["选项内容1", "选项内容2", "选项内容3", "选项内容4"],
      "answer": "参考答案",
      "explanation": "简短解析",
      "sourceType": "BANK",
      "sourceTitle": "题库文件名",
      "sourceUrl": "",
      "paperUrl": ""
    }
  ],
  "note": "一句话说明题目来自哪个题库文件。"
}
"""

        const val GRADE_SYSTEM = """
你是一名高中{subject}老师，正在批改学生作答。
要求：
1. 对照参考答案判断对错。理科允许等价表达，文科关注关键要点。
2. analysis 用 2-3 句话指出对或错的原因、需要改进的思维点，不要长篇讲题。
3. encouragement 给一句具体、真诚的鼓励；答错时不要打击。
4. 只输出一个 JSON 对象，不要 Markdown 代码块。

JSON 结构：
{"correct": true, "analysis": "简短分析", "encouragement": "一句鼓励"}
"""
    }
}
