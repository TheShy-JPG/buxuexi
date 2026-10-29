package com.zhuxue.zhuxuebao.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.zhuxue.zhuxuebao.data.KnowledgePoint
import com.zhuxue.zhuxuebao.data.KnowledgeStat
import com.zhuxue.zhuxuebao.data.PracticeQuestion
import com.zhuxue.zhuxuebao.data.SUBJECTS
import com.zhuxue.zhuxuebao.data.difficultyLabel
import com.zhuxue.zhuxuebao.data.frequencyLabel
import com.zhuxue.zhuxuebao.ui.util.accuracyColor
import com.zhuxue.zhuxuebao.ui.util.accuracyPercent
import com.zhuxue.zhuxuebao.ui.util.difficultyColor
import com.zhuxue.zhuxuebao.ui.util.formatAgo
import com.zhuxue.zhuxuebao.ui.util.frequencyColor
import com.zhuxue.zhuxuebao.ui.util.indentParagraphs
import com.zhuxue.zhuxuebao.ui.util.openUrl
import com.zhuxue.zhuxuebao.ui.util.resourcesFor
import com.zhuxue.zhuxuebao.ui.util.sourceTypeLabel
import com.zhuxue.zhuxuebao.ui.vm.LearnTarget
import java.io.File

val CONTENT_MAX_WIDTH = 960.dp

@Composable
fun ContentWidth(
    modifier: Modifier = Modifier,
    maxWidth: Dp = CONTENT_MAX_WIDTH,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
fun TagChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun SectionTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun StatBar(stat: KnowledgeStat, rank: Int, maxCount: Int) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$rank. ${stat.name}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
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
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val fraction = if (maxCount <= 0) 0f else stat.count.toFloat() / maxCount
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.06f, 1f))
                    .height(9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(difficultyColor(stat.difficulty))
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
fun KnowledgeStatMeta(stat: KnowledgeStat, showPracticeTime: Boolean = false) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TagChip(difficultyLabel(stat.difficulty), difficultyColor(stat.difficulty))
            TagChip(frequencyLabel(stat.frequency), frequencyColor(stat.frequency))
            if (stat.notPracticed) {
                TagChip("未练习", MaterialTheme.colorScheme.secondary)
            }
            accuracyPercent(stat.recentAccuracy)?.let { percent ->
                TagChip("正确率 $percent%", accuracyColor(percent))
            }
            stat.masteryPercent?.let { percent ->
                TagChip("掌握度 $percent%", MaterialTheme.colorScheme.tertiary)
            }
        }
        if (showPracticeTime && stat.lastPracticeAt != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                formatAgo(stat.lastPracticeAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun KnowledgeActionDialog(
    target: LearnTarget,
    onDismiss: () -> Unit,
    onStudy: () -> Unit,
    onPractice: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(
                    target.subject,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    target.point.name,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    TagChip(
                        difficultyLabel(target.point.difficulty),
                        difficultyColor(target.point.difficulty)
                    )
                    TagChip(
                        frequencyLabel(target.point.frequency),
                        frequencyColor(target.point.frequency)
                    )
                }
                if (target.point.errorPattern.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "错误表现",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        indentParagraphs(target.point.errorPattern),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start
                    )
                }
                if (target.point.learningDirection.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "建议的学习方向",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        indentParagraphs(target.point.learningDirection),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AI 建议仅供参考",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onPractice, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("练习")
                    }
                    Button(onClick = onStudy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("学习")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "不选择按钮，直接返回",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LearnPointDialog(
    onDismiss: () -> Unit,
    onConfirm: (subject: String, name: String, note: String) -> Unit
) {
    var subject by remember { mutableStateOf("数学") }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                Text(
                    "学习一个知识点",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "这个知识点暂时没有题目也没关系：先让 AI 指出学习方向，再推荐讲解视频和文章；以后练习会自动统计正确率。",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "科目",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SUBJECTS.forEach { item ->
                        FilterChip(
                            selected = subject == item,
                            onClick = { subject = item },
                            label = { Text(item) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("要学的知识点") },
                    placeholder = { Text("如：三角函数图像变换") }
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    label = { Text("想了解什么（可选）") },
                    placeholder = { Text("如：总记不住周期怎么求") }
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) onConfirm(subject, name.trim(), note.trim())
                        },
                        modifier = Modifier.weight(1f),
                        enabled = name.isNotBlank()
                    ) {
                        Text("记录并学习")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeResourcesSheet(
    target: LearnTarget,
    onDismiss: () -> Unit,
    onPractice: () -> Unit,
    loading: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                target.subject,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                target.point.name,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TagChip(
                    difficultyLabel(target.point.difficulty),
                    difficultyColor(target.point.difficulty)
                )
                TagChip(
                    frequencyLabel(target.point.frequency),
                    frequencyColor(target.point.frequency)
                )
            }

            when {
                loading -> {
                    Spacer(Modifier.height(30.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "AI 正在指出学习方向…\n先定方向，再推荐视频和文章。",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                }

                errorMessage != null -> {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        errorMessage,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onRetry ?: onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (onRetry != null) "重新获取学习方向" else "关闭")
                    }
                    Spacer(Modifier.height(12.dp))
                }

                else -> {
                    if (target.point.errorPattern.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "上次暴露的问题",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Start
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            indentParagraphs(target.point.errorPattern),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "建议的学习方向",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        indentParagraphs(
                            target.point.learningDirection.ifBlank {
                                "先过一遍概念和典型题型，再按易错点做少量针对练习。"
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AI 建议仅供参考",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(20.dp))
                    Text(
                        "推荐学习资源",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        indentParagraphs("点开平台搜索结果，挑适合自己的讲解。"),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.height(10.dp))
                    resourcesFor(target.point).forEach { resource ->
                        Surface(
                            onClick = { openUrl(context, resource.url) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (resource.platform == "VIDEO") {
                                        Icons.Outlined.PlayCircleOutline
                                    } else {
                                        Icons.Outlined.Article
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(resource.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (resource.platform == "VIDEO") "视频搜索结果" else "文章搜索结果",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Outlined.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onPractice, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("去练习这个知识点")
                    }
                }
            }
        }
    }
}

@Composable
fun AiErrorCard(
    message: String,
    needSettings: Boolean,
    onSettings: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "没成功",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (needSettings) {
                    Button(onClick = onSettings) { Text("去设置 API Key") }
                }
                if (onRetry != null) {
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }
        }
    }
}

@Composable
fun SourceLinks(question: PracticeQuestion) {
    val context = LocalContext.current
    val type = question.sourceType.uppercase()
    if (type == "AI" || type == "BANK") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TagChip(
                if (type == "BANK") "题库题目" else "AI 生成",
                MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (type == "BANK") {
                    "来自你上传的题库：${question.sourceTitle}"
                } else {
                    "没有找到合适真题，本题由 AI 生成"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TagChip(sourceTypeLabel(question.sourceType), MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(8.dp))
            Text(
                question.sourceTitle.ifBlank { "网络来源" },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (question.sourceUrl.isNotBlank() || question.paperUrl.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (question.sourceUrl.isNotBlank()) {
                    OutlinedButton(onClick = { openUrl(context, question.sourceUrl) }) {
                        Text("查看题目来源")
                    }
                }
                if (question.paperUrl.isNotBlank()) {
                    OutlinedButton(onClick = { openUrl(context, question.paperUrl) }) {
                        Text("查看完整试卷")
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenImageDialog(path: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() }
        ) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = Color.White)
            }
        }
    }
}

@Composable
fun LoadingBlock(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun KeyValueBlock(title: String, value: String) {
    if (value.isBlank()) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

fun KnowledgePoint.asLearnTarget(subject: String): LearnTarget = LearnTarget(subject, this)
