package com.zhuxue.zhuxuebao.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import com.zhuxue.zhuxuebao.data.KnowledgePoint
import com.zhuxue.zhuxuebao.data.KnowledgeStat
import com.zhuxue.zhuxuebao.data.ResourceLink
import com.zhuxue.zhuxuebao.data.toKnowledgePoint
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDateTime(time: Long): String =
    SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(time))

const val CONTENT_INDENT = "\u3000\u3000"

fun indentParagraphs(text: String): String =
    text.trim().lines().joinToString("\n") { line ->
        if (line.isBlank()) "" else CONTENT_INDENT + line.trimStart()
    }

fun formatShortDate(time: Long): String =
    SimpleDateFormat("M月d日", Locale.CHINA).format(Date(time))

fun formatAgo(at: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - at).coerceAtLeast(0L)
    val days = diff / 86_400_000L
    val hours = diff / 3_600_000L
    val minutes = diff / 60_000L
    return when {
        days >= 1L -> "已 $days 天未练"
        hours >= 1L -> "已 $hours 小时未练"
        minutes >= 1L -> "已 $minutes 分钟未练"
        else -> "刚刚练过"
    }
}

fun accuracyPercent(accuracy: Float?): Int? =
    accuracy?.let { value -> Math.round(value * 100f) }

fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    if (uri.scheme?.lowercase() !in setOf("http", "https")) {
        Toast.makeText(context, "只支持打开网页链接", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, uri)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "没有可以打开该链接的应用", Toast.LENGTH_SHORT).show() }
}

fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

fun difficultyColor(difficulty: String): Color = when (difficulty.uppercase()) {
    "EASY" -> Color(0xFF2E8B63)
    "HARD" -> Color(0xFFC0504D)
    else -> Color(0xFFC0842B)
}

fun frequencyColor(frequency: String): Color =
    if (frequency.uppercase() == "FREQUENT") Color(0xFFC0504D) else Color(0xFF6C7A72)

fun accuracyColor(percent: Int): Color =
    if (percent >= 85) Color(0xFF2E8B63) else Color(0xFFC0504D)

fun optionLabel(index: Int, text: String): String {
    val letter = ('A' + index).toString()
    val trimmed = text.trim()
    return if (trimmed.startsWith(letter)) trimmed else "$letter. $trimmed"
}

fun sourceTypeLabel(type: String): String = when (type.uppercase()) {
    "REAL" -> "高考真题"
    "WEB" -> "网络题"
    "BANK" -> "题库题目"
    else -> "AI 生成"
}

fun resourcesFor(point: KnowledgePoint): List<ResourceLink> {
    if (point.resources.isNotEmpty()) return point.resources
    val query = point.searchQuery.ifBlank { "${point.name} 高中 学习方法" }
    return listOf(
        ResourceLink(
            title = "B站 · ${point.name} 讲解",
            url = "https://search.bilibili.com/all?keyword=${URLEncoder.encode(query, "UTF-8")}",
            platform = "VIDEO"
        ),
        ResourceLink(
            title = "百度 · ${point.name} 学习方法",
            url = "https://www.baidu.com/s?wd=${URLEncoder.encode("$query 学习方法", "UTF-8")}",
            platform = "ARTICLE"
        )
    )
}

fun KnowledgeStat.asPoint(): KnowledgePoint = toKnowledgePoint()

fun <T> applyUserOrder(items: List<T>, order: List<String>, key: (T) -> String): List<T> {
    if (order.isEmpty() || items.size < 2) return items
    val index = order.withIndex().associate { it.value to it.index }
    return items.withIndex()
        .sortedWith(compareBy({ index[key(it.value)] ?: Int.MAX_VALUE }, { it.index }))
        .map { it.value }
}
