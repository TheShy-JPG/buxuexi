package com.zhuxue.zhuxuebao.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zhuxue.zhuxuebao.data.KnowledgeStat
import com.zhuxue.zhuxuebao.data.PracticeQuestion
import com.zhuxue.zhuxuebao.ui.components.AiErrorCard
import com.zhuxue.zhuxuebao.ui.components.ContentCard
import com.zhuxue.zhuxuebao.ui.components.ContentWidth
import com.zhuxue.zhuxuebao.ui.components.KeyValueBlock
import com.zhuxue.zhuxuebao.ui.components.LoadingBlock
import com.zhuxue.zhuxuebao.ui.components.SourceLinks
import com.zhuxue.zhuxuebao.ui.components.TagChip
import com.zhuxue.zhuxuebao.ui.util.optionLabel
import com.zhuxue.zhuxuebao.ui.vm.PracticeState
import com.zhuxue.zhuxuebao.ui.vm.PracticeViewModel
import java.io.File

@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    stats: List<KnowledgeStat>,
    visibleSubjects: List<String>,
    apiKeyConfigured: Boolean,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val answerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.setAnswerImage(uri)
    }

    ContentWidth(Modifier.fillMaxSize()) {
        if (state.active) {
            PracticeSession(
                state = state,
                viewModel = viewModel,
                onOpenSettings = onOpenSettings,
                onPickAnswerImage = {
                    answerPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
        } else {
            PracticeSetup(
                state = state,
                stats = stats,
                visibleSubjects = visibleSubjects,
                apiKeyConfigured = apiKeyConfigured,
                viewModel = viewModel,
                onOpenSettings = onOpenSettings
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeSetup(
    state: PracticeState,
    stats: List<KnowledgeStat>,
    visibleSubjects: List<String>,
    apiKeyConfigured: Boolean,
    viewModel: PracticeViewModel,
    onOpenSettings: () -> Unit
) {
    val suggestions = stats
        .filter { it.subject == state.subject }
        .distinctBy { it.name }
        .take(8)
    val subjectChoices = (visibleSubjects + state.subject).distinct()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!apiKeyConfigured) {
            item {
                ContentCard {
                    Text("还没配置 API Key", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "识图、出题和批改都需要智谱 API。填写后回到这里即可开始。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onOpenSettings) { Text("去设置") }
                }
            }
        }

        item {
            ContentCard {
                Text("科目", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                subjectChoices.chunked(3).forEach { rowSubjects ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowSubjects.forEach { subject ->
                            FilterChip(
                                selected = state.subject == subject,
                                onClick = { viewModel.setSubject(subject) },
                                label = { Text(subject, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowSubjects.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Spacer(Modifier.height(16.dp))
                Text("知识点", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.knowledgePoint,
                    onValueChange = viewModel::setKnowledgePoint,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("要练什么") },
                    placeholder = { Text("如：函数单调性、带电粒子在磁场中的运动") }
                )

                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "你错过的知识点",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestions.forEach { stat ->
                            FilterChip(
                                selected = state.knowledgePoint == stat.name,
                                onClick = { viewModel.setKnowledgePoint(stat.name) },
                                label = { Text(stat.name) }
                            )
                        }
                    }
                }
            }
        }

        item {
            ContentCard {
                Text("练习方式", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.mode == "SINGLE",
                        onClick = { viewModel.setMode("SINGLE") },
                        label = { Text("单题练习") }
                    )
                    FilterChip(
                        selected = state.mode == "MULTI",
                        onClick = { viewModel.setMode("MULTI") },
                        label = { Text("多题练习") }
                    )
                }

                if (state.mode == "MULTI") {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("一次做几道", style = MaterialTheme.typography.titleSmall)
                        }
                        IconButton(
                            onClick = { viewModel.setCount(state.count - 1) },
                            enabled = state.count > 2
                        ) {
                            Icon(Icons.Outlined.Remove, contentDescription = "减少题量")
                        }
                        Text("${state.count} 道", style = MaterialTheme.typography.titleMedium)
                        IconButton(
                            onClick = { viewModel.setCount(state.count + 1) },
                            enabled = state.count < 8
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "增加题量")
                        }
                    }
                }
            }
        }

        state.error?.let { message ->
            item {
                AiErrorCard(
                    message = message,
                    needSettings = state.needSettings,
                    onSettings = onOpenSettings,
                    onRetry = { viewModel.start() }
                )
            }
        }

        item {
            Button(
                onClick = viewModel::start,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.generating
            ) {
                if (state.generating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("搜题中…")
                } else {
                    Text("开始练习")
                }
            }
        }
    }
}

@Composable
private fun PracticeSession(
    state: PracticeState,
    viewModel: PracticeViewModel,
    onOpenSettings: () -> Unit,
    onPickAnswerImage: () -> Unit
) {
    if (state.multiMode && state.finished) {
        PracticeResults(
            state = state,
            viewModel = viewModel,
            onOpenSettings = onOpenSettings
        )
        return
    }

    val question = state.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ContentCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "第 ${state.currentIndex + 1} / ${state.questions.size} 题",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TagChip(state.subject, MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    state.knowledgePoint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                val progress = if (state.multiMode) {
                    state.submittedCount.toFloat() / state.questions.size.coerceAtLeast(1)
                } else {
                    (state.currentIndex + if (question?.answered == true) 1 else 0)
                        .toFloat() / state.questions.size.coerceAtLeast(1)
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (question == null) {
            item {
                ContentCard {
                    Text("题目已结束", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = viewModel::endSession) { Text("返回练习设置") }
                }
            }
            return@LazyColumn
        }

        item {
            QuestionCard(
                question = question,
                selectedOption = state.selectedOption,
                answered = question.answered,
                onSelectOption = viewModel::selectOption
            )
        }

        if (state.multiMode) {
            if (!question.submitted) {
                item {
                    AnswerEditor(
                        state = state,
                        viewModel = viewModel,
                        onPickAnswerImage = onPickAnswerImage
                    )
                }

                state.error?.let { message ->
                    item {
                        AiErrorCard(
                            message = message,
                            needSettings = state.needSettings,
                            onSettings = onOpenSettings
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = viewModel::markDontKnow,
                            modifier = Modifier.weight(1f),
                            enabled = !state.grading
                        ) {
                            Text("不会")
                        }
                        Button(
                            onClick = viewModel::submit,
                            modifier = Modifier.weight(1f),
                            enabled = !state.grading
                        ) {
                            Text(
                                if (state.submittedCount + 1 >= state.questions.size) {
                                    "提交全部并批改"
                                } else {
                                    "提交，下一题"
                                }
                            )
                        }
                    }
                }
            } else {
                item { LoadingBlock("答案已记下，正在批改全部题目…") }
            }
        } else if (!question.answered) {
            item {
                AnswerEditor(
                    state = state,
                    viewModel = viewModel,
                    onPickAnswerImage = onPickAnswerImage
                )
            }

            state.error?.let { message ->
                item {
                    AiErrorCard(
                        message = message,
                        needSettings = state.needSettings,
                        onSettings = onOpenSettings
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = viewModel::markDontKnow,
                        modifier = Modifier.weight(1f),
                        enabled = !state.grading
                    ) {
                        Text("不会，看解析")
                    }
                    Button(
                        onClick = viewModel::submit,
                        modifier = Modifier.weight(1f),
                        enabled = !state.grading
                    ) {
                        if (state.grading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("批改中")
                        } else {
                            Text("提交答案")
                        }
                    }
                }
            }
        } else {
            item { ResultCard(question) }

            item {
                if (state.isLastQuestion) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = viewModel::endSession,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("结束练习")
                        }
                        Button(
                            onClick = viewModel::practiceAgain,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("再练一组")
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = viewModel::endSession,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("结束练习")
                        }
                        Button(
                            onClick = viewModel::next,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("下一题")
                        }
                    }
                }
            }
        }

        if (state.note.isNotBlank()) {
            item {
                Text(
                    "题目来源说明：${state.note}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PracticeResults(
    state: PracticeState,
    viewModel: PracticeViewModel,
    onOpenSettings: () -> Unit
) {
    val total = state.questions.size
    val correct = state.correctCount
    val percent = if (total == 0) 0 else correct * 100 / total

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ContentCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "这组做完了",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TagChip(state.subject, MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    state.knowledgePoint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "答对 $correct / $total 题 · 正确率 $percent%",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { correct.toFloat() / total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        state.error?.let { message ->
            item {
                AiErrorCard(
                    message = message,
                    needSettings = state.needSettings,
                    onSettings = onOpenSettings
                )
            }
        }

        itemsIndexed(state.questions) { index, question ->
            Column {
                Text(
                    "第 ${index + 1} 题",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(Modifier.height(6.dp))
                ResultCard(question)
            }
        }

        if (state.note.isNotBlank()) {
            item {
                Text(
                    "题目来源说明：${state.note}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = viewModel::endSession,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("结束练习")
                }
                Button(
                    onClick = viewModel::practiceAgain,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("再练一组")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionCard(
    question: PracticeQuestion,
    selectedOption: String,
    answered: Boolean,
    onSelectOption: (String) -> Unit
) {
    ContentCard {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TagChip("题目", MaterialTheme.colorScheme.tertiary)
            if (question.options.isNotEmpty()) {
                TagChip("选择题", MaterialTheme.colorScheme.secondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(question.text, style = MaterialTheme.typography.bodyLarge)

        if (question.options.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            question.options.forEachIndexed { index, option ->
                val selected = selectedOption == option
                Surface(
                    onClick = { onSelectOption(option) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !answered,
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                ('A' + index).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            optionLabel(index, option).removePrefix("${('A' + index)}. "),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
            }
        }
    }
}

@Composable
private fun AnswerEditor(
    state: PracticeState,
    viewModel: PracticeViewModel,
    onPickAnswerImage: () -> Unit
) {
    ContentCard {
        Text("你的作答", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.answerText,
            onValueChange = viewModel::updateAnswerText,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            label = { Text("写下答案或关键步骤") },
            placeholder = { Text("也可以只上传手写答案的照片") }
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onPickAnswerImage) {
                Icon(
                    Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (state.answerImageUri == null) "上传手写答案" else "更换图片")
            }
            Spacer(Modifier.weight(1f))
            if (state.answerImageUri != null) {
                IconButton(onClick = { viewModel.setAnswerImage(null) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "移除图片")
                }
            }
        }
        state.answerImageUri?.let { uri ->
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = Uri.parse(uri),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 150.dp, height = 110.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun ResultCard(question: PracticeQuestion) {
    val correct = question.isCorrect == true
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (correct) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                contentDescription = null,
                tint = if (correct) Color(0xFF2E8B63) else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (correct) "答对了" else "这题没对",
                style = MaterialTheme.typography.titleMedium,
                color = if (correct) Color(0xFF2E8B63) else MaterialTheme.colorScheme.error
            )
        }
        if (question.feedback.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(question.feedback, style = MaterialTheme.typography.bodyMedium)
        }
        if (question.encouragement.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                question.encouragement,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        KeyValueBlock("你的答案", question.userAnswer)
        if (question.userImagePath.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = File(question.userImagePath),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 150.dp, height = 110.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.height(10.dp))
        KeyValueBlock("参考答案", question.answer)
        Spacer(Modifier.height(10.dp))
        KeyValueBlock(
            "解析",
            question.explanation.ifBlank { "暂无解析，回到参考答案核对关键步骤。" }
        )
        Spacer(Modifier.height(12.dp))
        SourceLinks(question)
    }
}
