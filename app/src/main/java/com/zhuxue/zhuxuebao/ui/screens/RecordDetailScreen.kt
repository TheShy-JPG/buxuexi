package com.zhuxue.zhuxuebao.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zhuxue.zhuxuebao.data.KnowledgePoint
import com.zhuxue.zhuxuebao.data.StudyRecord
import com.zhuxue.zhuxuebao.data.difficultyLabel
import com.zhuxue.zhuxuebao.data.frequencyLabel
import com.zhuxue.zhuxuebao.ui.components.ContentCard
import com.zhuxue.zhuxuebao.ui.components.ContentWidth
import com.zhuxue.zhuxuebao.ui.components.FullScreenImageDialog
import com.zhuxue.zhuxuebao.ui.components.KnowledgeActionDialog
import com.zhuxue.zhuxuebao.ui.components.LoadingBlock
import com.zhuxue.zhuxuebao.ui.components.SectionTitle
import com.zhuxue.zhuxuebao.ui.components.TagChip
import com.zhuxue.zhuxuebao.ui.util.difficultyColor
import com.zhuxue.zhuxuebao.ui.util.formatDateTime
import com.zhuxue.zhuxuebao.ui.util.frequencyColor
import com.zhuxue.zhuxuebao.ui.util.indentParagraphs
import com.zhuxue.zhuxuebao.ui.vm.LearnTarget
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    recordId: Long,
    records: List<StudyRecord>,
    onBack: () -> Unit,
    onStudy: (String, KnowledgePoint) -> Unit,
    onPractice: (String, String) -> Unit,
    onDelete: (StudyRecord) -> Unit
) {
    val record = records.firstOrNull { it.id == recordId }
    var previewPath by remember { mutableStateOf<String?>(null) }
    var actionTarget by remember { mutableStateOf<LearnTarget?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("错题详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (record != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除记录")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (record != null) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                    ContentWidth {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val point = record.analysis.knowledgePoints.firstOrNull()?.name
                                        ?: "本科目综合"
                                    onPractice(record.subject, point)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("立即练习本次知识点")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (record == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LoadingBlock("正在加载记录…")
            }
            return@Scaffold
        }

        ContentWidth(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (record.imagePaths.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(record.imagePaths) { path ->
                            AsyncImage(
                                model = File(path),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(width = 148.dp, height = 196.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { previewPath = path },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TagChip(record.subject, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatDateTime(record.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        record.title.ifBlank { "错题分析" },
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }

            if (record.analysis.summary.isNotBlank()) {
                item {
                    ContentCard {
                        Text("本次诊断", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            indentParagraphs(record.analysis.summary),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }

            if (record.analysis.questions.isNotEmpty()) {
                val questions = record.analysis.questions
                item {
                    ContentCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "逐题统计",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TagChip(
                                "答对 ${questions.count { it.correct }} 题",
                                Color(0xFF2E8B63)
                            )
                            Spacer(Modifier.width(6.dp))
                            TagChip(
                                "答错 ${questions.count { !it.correct }} 题",
                                MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        questions.forEachIndexed { index, question ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (question.correct) {
                                        Icons.Outlined.CheckCircle
                                    } else {
                                        Icons.Outlined.Cancel
                                    },
                                    contentDescription = null,
                                    tint = if (question.correct) {
                                        Color(0xFF2E8B63)
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "第 ${index + 1} 题 · ${question.knowledgePoint.ifBlank { "未标注知识点" }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (index != questions.lastIndex) Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }

            if (record.analysis.thinkingAdvice.isNotEmpty()) {
                item {
                    ContentCard {
                        Text("思考方式提醒", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        record.analysis.thinkingAdvice.forEachIndexed { index, advice ->
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    advice,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (index != record.analysis.thinkingAdvice.lastIndex) {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            if (record.analysis.practiceFocus.isNotEmpty()) {
                item {
                    ContentCard {
                        Text("优先练习方向", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        PracticeFocusChips(record.analysis.practiceFocus)
                    }
                }
            }

            item {
                SectionTitle(
                    title = "知识点"
                )
            }

            itemsIndexed(record.analysis.knowledgePoints) { _, point ->
                DetailKnowledgeCard(
                    point = point,
                    onClick = {
                        actionTarget = LearnTarget(record.subject, point)
                    }
                )
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
                onStudy(record?.subject.orEmpty(), target.point)
            },
            onPractice = {
                actionTarget = null
                onPractice(record?.subject.orEmpty(), target.point.name)
            }
        )
    }

    previewPath?.let { path ->
        FullScreenImageDialog(path = path, onDismiss = { previewPath = null })
    }

    if (showDeleteDialog && record != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    "删除这条记录？",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    "分析结果和上传的图片都会删除，无法恢复。",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(record)
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DetailKnowledgeCard(point: KnowledgePoint, onClick: () -> Unit) {
    ContentCard(modifier = Modifier.clickable(onClick = onClick)) {
        Text(
            point.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TagChip(difficultyLabel(point.difficulty), difficultyColor(point.difficulty))
            TagChip(frequencyLabel(point.frequency), frequencyColor(point.frequency))
        }
        if (point.errorPattern.isNotBlank()) {
            Spacer(Modifier.height(9.dp))
            Text(
                indentParagraphs(point.errorPattern),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
        if (point.learningDirection.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                indentParagraphs("方向：${point.learningDirection}"),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Start
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "点击查看：学习 / 练习",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeFocusChips(items: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            TagChip(item, MaterialTheme.colorScheme.secondary)
        }
    }
}
