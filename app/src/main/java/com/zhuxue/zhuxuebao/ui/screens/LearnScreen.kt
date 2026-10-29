package com.zhuxue.zhuxuebao.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhuxue.zhuxuebao.data.KnowledgePoint
import com.zhuxue.zhuxuebao.data.KnowledgeStat
import com.zhuxue.zhuxuebao.data.SUBJECTS
import com.zhuxue.zhuxuebao.data.toKnowledgePoint
import com.zhuxue.zhuxuebao.ui.components.ContentCard
import com.zhuxue.zhuxuebao.ui.components.EmptyState
import com.zhuxue.zhuxuebao.ui.components.KnowledgeActionDialog
import com.zhuxue.zhuxuebao.ui.components.KnowledgeResourcesSheet
import com.zhuxue.zhuxuebao.ui.components.KnowledgeStatMeta
import com.zhuxue.zhuxuebao.ui.components.LearnPointDialog
import com.zhuxue.zhuxuebao.ui.components.SectionTitle
import com.zhuxue.zhuxuebao.ui.components.TagChip
import com.zhuxue.zhuxuebao.ui.util.accuracyColor
import com.zhuxue.zhuxuebao.ui.util.accuracyPercent
import com.zhuxue.zhuxuebao.ui.util.applyUserOrder
import com.zhuxue.zhuxuebao.ui.util.formatAgo
import com.zhuxue.zhuxuebao.ui.vm.LearnTarget
import com.zhuxue.zhuxuebao.ui.vm.userFacingMessage

private const val STALE_DAYS_MILLIS = 3 * 86_400_000L
private const val STALE_ACCURACY = 0.85f

@Composable
fun LearnScreen(
    stats: List<KnowledgeStat>,
    knowledgeOrder: List<String>,
    pendingLearnTarget: LearnTarget?,
    onConsumePending: () -> Unit,
    onPractice: (LearnTarget) -> Unit,
    onDiscuss: (subject: String, name: String, note: String) -> Unit,
    onLearnGuide: (String, String, String, (Result<KnowledgePoint>) -> Unit) -> Unit,
    onUpload: () -> Unit
) {
    var actionTarget by remember { mutableStateOf<LearnTarget?>(null) }
    var guideTarget by remember { mutableStateOf<LearnTarget?>(null) }
    var guideLoading by remember { mutableStateOf(false) }
    var guideError by remember { mutableStateOf<String?>(null) }
    var guideToken by remember { mutableStateOf(0) }
    var showLearnDialog by remember { mutableStateOf(false) }

    fun startGuide(subject: String, name: String, note: String, base: KnowledgePoint? = null) {
        val token = guideToken + 1
        guideToken = token
        guideTarget = LearnTarget(
            subject = subject,
            point = base ?: KnowledgePoint(
                name = name,
                errorPattern = note,
                searchQuery = name
            )
        )
        guideLoading = true
        guideError = null
        onLearnGuide(subject, name, note) { result ->
            if (token != guideToken) return@onLearnGuide
            guideLoading = false
            result.onSuccess { point ->
                guideTarget = LearnTarget(subject, point)
            }.onFailure { error ->
                guideError = error.userFacingMessage()
            }
        }
    }

    fun dismissGuide() {
        guideToken += 1
        guideLoading = false
        guideError = null
        guideTarget = null
    }

    LaunchedEffect(pendingLearnTarget) {
        pendingLearnTarget?.let {
            guideToken += 1
            guideLoading = false
            guideError = null
            guideTarget = it
            onConsumePending()
        }
    }

    val grouped = stats.groupBy { it.subject }
    val subjects = SUBJECTS.filter { grouped.containsKey(it) } +
        grouped.keys.filterNot { it in SUBJECTS }

    val now = System.currentTimeMillis()
    val staleStats = stats.filter { stat ->
        val accuracy = stat.recentAccuracy
        val last = stat.lastPracticeAt
        accuracy != null && last != null &&
            accuracy < STALE_ACCURACY && now - last >= STALE_DAYS_MILLIS
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val budget = maxHeight * 2
        var used = 214.dp
        if (stats.isNotEmpty()) used += 44.dp

        val visibleStale = mutableListOf<KnowledgeStat>()
        if (staleStats.isNotEmpty()) {
            val headerCost = 60.dp
            val cardCost = 150.dp
            if (used + headerCost + cardCost <= budget) {
                used += headerCost
                for (stat in staleStats) {
                    if (used + cardCost > budget) break
                    visibleStale += stat
                    used += cardCost
                }
            }
        }

        val visibleSections = mutableListOf<Pair<String, List<KnowledgeStat>>>()
        if (stats.isNotEmpty()) {
            var reachedLimit = false
            for (subject in subjects) {
                val subjectStats = applyUserOrder(
                    grouped[subject].orEmpty(),
                    knowledgeOrder,
                    { "$subject|${it.name}" }
                )
                if (subjectStats.isEmpty()) continue
                if (used + 58.dp + 170.dp > budget) break
                used += 58.dp
                val visible = mutableListOf<KnowledgeStat>()
                for (stat in subjectStats) {
                    if (used + 170.dp > budget) {
                        reachedLimit = true
                        break
                    }
                    visible += stat
                    used += 170.dp
                }
                if (visible.isNotEmpty()) visibleSections += subject to visible
                if (reachedLimit) break
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LearnEntryCard(
                        icon = Icons.Outlined.AddPhotoAlternate,
                        title = "拍照上传",
                        subtitle = "错题或试卷，AI 归纳知识点",
                        actionLabel = "选择图片",
                        onClick = onUpload,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    LearnEntryCard(
                        icon = Icons.Outlined.MenuBook,
                        title = "学习知识点",
                        subtitle = "暂时没题目也能先学",
                        actionLabel = "记录知识点",
                        onClick = { showLearnDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            if (stats.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TagChip("共 ${stats.size} 个知识点", MaterialTheme.colorScheme.primary)
                        TagChip("${subjects.size} 个科目", MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            if (visibleStale.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "该复习了",
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                itemsIndexed(
                    visibleStale,
                    key = { index, stat -> "stale|${stat.subject}|${stat.name}|$index" }
                ) { _, stat ->
                    StaleKnowledgeCard(
                        stat = stat,
                        onClick = { actionTarget = LearnTarget(stat.subject, stat.toKnowledgePoint()) }
                    )
                }
            }

            if (stats.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.MenuBook,
                        title = "还没有学习方向",
                        message = "上传一份错题或试卷后，AI 会归纳知识点，并把视频和文章搜索链接整理到这里。",
                        actionLabel = "上传第一份错题",
                        onAction = onUpload
                    )
                }
            } else {
                visibleSections.forEach { (subject, subjectStats) ->
                    item(key = "header-$subject") {
                        SectionTitle(
                            title = subject,
                            subtitle = "${grouped[subject].orEmpty().size} 个知识点",
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    itemsIndexed(
                        subjectStats,
                        key = { index, stat -> "$subject|${stat.name}|$index" }
                    ) { _, stat ->
                        KnowledgeCard(
                            stat = stat,
                            onClick = {
                                actionTarget = LearnTarget(stat.subject, stat.toKnowledgePoint())
                            }
                        )
                    }
                }
            }
        }
    }

    actionTarget?.let { target ->
        KnowledgeActionDialog(
            target = target,
            onDismiss = { actionTarget = null },
            onStudy = {
                actionTarget = null
                if (target.point.learningDirection.isBlank()) {
                    startGuide(
                        target.subject,
                        target.point.name,
                        target.point.errorPattern,
                        target.point
                    )
                } else {
                    guideTarget = target
                    guideLoading = false
                    guideError = null
                }
            },
            onPractice = {
                actionTarget = null
                onPractice(target)
            }
        )
    }

    guideTarget?.let { target ->
        KnowledgeResourcesSheet(
            target = target,
            onDismiss = { dismissGuide() },
            onPractice = {
                dismissGuide()
                onPractice(target)
            },
            loading = guideLoading,
            errorMessage = guideError,
            onRetry = if (guideError != null) {
                {
                    startGuide(
                        target.subject,
                        target.point.name,
                        target.point.errorPattern,
                        target.point
                    )
                }
            } else {
                null
            }
        )
    }

    if (showLearnDialog) {
        LearnPointDialog(
            onDismiss = { showLearnDialog = false },
            onConfirm = { subject, name, note ->
                showLearnDialog = false
                onDiscuss(subject, name, note)
                startGuide(subject, name, note)
            }
        )
    }
}

@Composable
private fun LearnEntryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ContentCard(modifier = modifier.clickable(onClick = onClick)) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(3.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(actionLabel, maxLines = 1)
        }
    }
}

@Composable
private fun StaleKnowledgeCard(stat: KnowledgeStat, onClick: () -> Unit) {
    val percent = accuracyPercent(stat.recentAccuracy)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stat.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                TagChip(stat.subject, MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (percent != null) {
                    TagChip("正确率 $percent%", accuracyColor(percent))
                }
                stat.lastPracticeAt?.let { last ->
                    TagChip(formatAgo(last), MaterialTheme.colorScheme.secondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "点击查看：学习 / 练习",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun KnowledgeCard(stat: KnowledgeStat, onClick: () -> Unit) {
    ContentCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stat.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                when {
                    stat.count > 0 -> "错 ${stat.count} 次"
                    stat.discussed && stat.attemptCount == 0 -> "已记录"
                    else -> "无错题"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(7.dp))
        KnowledgeStatMeta(stat, showPracticeTime = true)
        if (stat.errorPattern.isNotBlank()) {
            Spacer(Modifier.height(9.dp))
            Text(
                stat.errorPattern,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (stat.learningDirection.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                "建议方向：${stat.learningDirection}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            "点击查看：学习 / 练习",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}