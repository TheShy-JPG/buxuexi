package com.zhuxue.zhuxuebao.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhuxue.zhuxuebao.data.KnowledgeStat
import com.zhuxue.zhuxuebao.data.StudyRecord
import com.zhuxue.zhuxuebao.data.WrongQuestionRef
import com.zhuxue.zhuxuebao.data.extractKeyword
import com.zhuxue.zhuxuebao.data.toKnowledgePoint
import com.zhuxue.zhuxuebao.ui.components.ContentWidth
import com.zhuxue.zhuxuebao.ui.components.EmptyState
import com.zhuxue.zhuxuebao.ui.components.KnowledgeActionDialog
import com.zhuxue.zhuxuebao.ui.components.KnowledgeResourcesSheet
import com.zhuxue.zhuxuebao.ui.components.KnowledgeStatMeta
import com.zhuxue.zhuxuebao.ui.util.applyUserOrder
import com.zhuxue.zhuxuebao.ui.util.formatDateTime
import com.zhuxue.zhuxuebao.ui.vm.LearnTarget

private data class WrongItem(
    val key: String,
    val ref: WrongQuestionRef,
    val name: String,
    val recordTitle: String,
    val note: String,
    val at: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectReviewScreen(
    subject: String,
    records: List<StudyRecord>,
    stats: List<KnowledgeStat>,
    knowledgeOrder: List<String>,
    wrongOrder: List<String>,
    onBack: () -> Unit,
    onPractice: (String, String) -> Unit,
    onOpenRecord: (Long) -> Unit,
    onSaveKnowledgeOrder: (String, List<String>) -> Unit,
    onSaveWrongOrder: (String, List<String>) -> Unit,
    onDeleteKnowledge: (String, List<String>) -> Unit,
    onDeleteWrongQuestions: (List<WrongQuestionRef>) -> Unit
) {
    var tabIndex by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<LearnTarget?>(null) }
    var resourceTarget by remember { mutableStateOf<LearnTarget?>(null) }

    val subjectRecords = remember(records, subject) {
        records.filter { it.subject == subject }.sortedByDescending { it.createdAt }
    }
    val subjectStats = remember(stats, knowledgeOrder, subject) {
        applyUserOrder(
            stats.filter { it.subject == subject },
            knowledgeOrder,
            { "$subject|${it.name}" }
        )
    }
    val wrongItems = remember(subjectRecords, subject, wrongOrder) {
        buildWrongItems(subject, subjectRecords, wrongOrder)
    }
    val currentKeys = if (tabIndex == 0) {
        subjectStats.map { "$subject|${it.name}" }.toSet()
    } else {
        wrongItems.map { it.key }.toSet()
    }
    val allSelected = currentKeys.isNotEmpty() && currentKeys.all { it in selected }

    LaunchedEffect(tabIndex) {
        editing = false
        selected = emptySet()
    }

    fun moveKnowledge(index: Int, delta: Int) {
        val target = index + delta
        if (target !in subjectStats.indices) return
        val order = subjectStats.map { "$subject|${it.name}" }.toMutableList()
        val key = order.removeAt(index)
        order.add(target, key)
        onSaveKnowledgeOrder(subject, order)
    }

    fun moveWrong(index: Int, delta: Int) {
        val target = index + delta
        if (target !in wrongItems.indices) return
        val order = wrongItems.map { it.key }.toMutableList()
        val key = order.removeAt(index)
        order.add(target, key)
        onSaveWrongOrder(subject, order)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(subject, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (editing) {
                                editing = false
                                selected = emptySet()
                            } else {
                                editing = true
                            }
                        }
                    ) {
                        Text(if (editing) "完成" else "编辑")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (editing) {
                Surface(tonalElevation = 3.dp) {
                    ContentWidth {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selected = if (allSelected) emptySet() else currentKeys
                                }
                            ) {
                                Text(if (allSelected) "取消全选" else "全选")
                            }
                            Button(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.weight(1f),
                                enabled = selected.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text("删除选中（${selected.size}）")
                            }
                        }
                    }
                }
            } else {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = tabIndex == 0,
                        onClick = { tabIndex = 0 },
                        icon = { Icon(Icons.Outlined.MenuBook, contentDescription = null) },
                        label = { Text("知识记录") }
                    )
                    NavigationBarItem(
                        selected = tabIndex == 1,
                        onClick = { tabIndex = 1 },
                        icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null) },
                        label = { Text("错题记录") }
                    )
                }
            }
        }
    ) { padding ->
        ContentWidth(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
        Column(
            Modifier
                .fillMaxSize()
        ) {
            if (tabIndex == 0) {
                KnowledgeRecordList(
                    subject = subject,
                    stats = subjectStats,
                    editing = editing,
                    selected = selected,
                    onToggle = { key ->
                        selected = if (key in selected) selected - key else selected + key
                    },
                    onOpenStat = { stat ->
                        actionTarget = LearnTarget(stat.subject, stat.toKnowledgePoint())
                    },
                    onMoveUp = { index -> moveKnowledge(index, -1) },
                    onMoveDown = { index -> moveKnowledge(index, 1) }
                )
            } else {
                WrongQuestionList(
                    items = wrongItems,
                    editing = editing,
                    selected = selected,
                    onToggle = { key ->
                        selected = if (key in selected) selected - key else selected + key
                    },
                    onOpenRecord = onOpenRecord,
                    onMoveUp = { index -> moveWrong(index, -1) },
                    onMoveDown = { index -> moveWrong(index, 1) }
                )
            }
        }
        }
    }

    if (showDeleteConfirm) {
        val deletingKnowledge = tabIndex == 0
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    if (deletingKnowledge) "删除知识点" else "删除错题",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    if (deletingKnowledge) {
                        "将删除选中的 ${selected.size} 个知识点，并删除包含这些知识点的原始错题记录和图片。" +
                            "同一条记录里的其他知识点也会一起消失，删除后不可恢复。"
                    } else {
                        "将删除选中的 ${selected.size} 道错题，删除后不可恢复。"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deletingKnowledge) {
                            val names = subjectStats
                                .filter { "$subject|${it.name}" in selected }
                                .map { it.name }
                            onDeleteKnowledge(subject, names)
                        } else {
                            val refs = wrongItems.filter { it.key in selected }.map { it.ref }
                            onDeleteWrongQuestions(refs)
                        }
                        showDeleteConfirm = false
                        selected = emptySet()
                        editing = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    actionTarget?.let { target ->
        KnowledgeActionDialog(
            target = target,
            onDismiss = { actionTarget = null },
            onStudy = {
                resourceTarget = target
                actionTarget = null
            },
            onPractice = {
                actionTarget = null
                onPractice(target.subject, target.point.name)
            }
        )
    }

    resourceTarget?.let { target ->
        KnowledgeResourcesSheet(
            target = target,
            onDismiss = { resourceTarget = null },
            onPractice = {
                resourceTarget = null
                onPractice(target.subject, target.point.name)
            }
        )
    }
}

@Composable
private fun KnowledgeRecordList(
    subject: String,
    stats: List<KnowledgeStat>,
    editing: Boolean,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onOpenStat: (KnowledgeStat) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit
) {
    if (stats.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.MenuBook,
            title = "还没有知识点记录",
            message = "上传错题或记录想学的知识点后，这里会按知识点汇总，并支持调整顺序。"
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(stats, key = { index, stat -> "knowledge-$subject-${stat.name}-$index" }) { index, stat ->
            val key = "$subject|${stat.name}"
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (editing) {
                                Modifier.clickable { onToggle(key) }
                            } else {
                                Modifier.clickable { onOpenStat(stat) }
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (editing) {
                        Checkbox(
                            checked = key in selected,
                            onCheckedChange = { onToggle(key) }
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${index + 1}. ${stat.name}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
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
                        Spacer(Modifier.height(5.dp))
                        KnowledgeStatMeta(stat)
                    }
                    if (editing) {
                        Column {
                            IconButton(onClick = { onMoveUp(index) }, enabled = index > 0) {
                                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移")
                            }
                            IconButton(
                                onClick = { onMoveDown(index) },
                                enabled = index < stats.lastIndex
                            ) {
                                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移")
                            }
                        }
                    } else {
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WrongQuestionList(
    items: List<WrongItem>,
    editing: Boolean,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onOpenRecord: (Long) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit
) {
    if (items.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.ErrorOutline,
            title = "还没有错题记录",
            message = "上传错题或试卷后，识别到的错题会集中在这里；没有具体知识点时会用关键词命名。"
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(items, key = { index, item -> "${item.key}-$index" }) { index, item ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (editing) {
                                Modifier.clickable { onToggle(item.key) }
                            } else {
                                Modifier.clickable { onOpenRecord(item.ref.recordId) }
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (editing) {
                        Checkbox(
                            checked = item.key in selected,
                            onCheckedChange = { onToggle(item.key) }
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${item.recordTitle} · ${formatDateTime(item.at)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.note.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                item.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (editing) {
                        Column {
                            IconButton(onClick = { onMoveUp(index) }, enabled = index > 0) {
                                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移")
                            }
                            IconButton(onClick = { onMoveDown(index) }, enabled = index < items.lastIndex) {
                                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移")
                            }
                        }
                    } else {
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun buildWrongItems(
    subject: String,
    records: List<StudyRecord>,
    order: List<String>
): List<WrongItem> {
    val items = mutableListOf<WrongItem>()
    records.forEach { record ->
        val title = record.title.ifBlank { "错题记录" }
        val questions = record.analysis.questions
        if (questions.isEmpty()) {
            items += WrongItem(
                key = "$subject|${record.id}|-1",
                ref = WrongQuestionRef(record.id, -1),
                name = extractKeyword(record.analysis.summary.ifBlank { title }),
                recordTitle = title,
                note = record.analysis.summary,
                at = record.createdAt
            )
        } else {
            questions.forEachIndexed { index, question ->
                if (question.correct) return@forEachIndexed
                val name = question.knowledgePoint.trim().ifBlank {
                    extractKeyword(
                        question.note.ifBlank {
                            record.analysis.summary.ifBlank { title }
                        }
                    )
                }
                items += WrongItem(
                    key = "$subject|${record.id}|$index",
                    ref = WrongQuestionRef(record.id, index),
                    name = name,
                    recordTitle = title,
                    note = question.note,
                    at = record.createdAt
                )
            }
        }
    }
    return applyUserOrder(items, order, { it.key })
}
