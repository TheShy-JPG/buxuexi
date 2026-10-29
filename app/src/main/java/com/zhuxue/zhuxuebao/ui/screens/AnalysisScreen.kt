package com.zhuxue.zhuxuebao.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zhuxue.zhuxuebao.data.SUBJECTS
import com.zhuxue.zhuxuebao.ui.components.AiErrorCard
import com.zhuxue.zhuxuebao.ui.components.ContentCard
import com.zhuxue.zhuxuebao.ui.components.ContentWidth
import com.zhuxue.zhuxuebao.ui.components.FullScreenImageDialog
import com.zhuxue.zhuxuebao.ui.components.LoadingBlock
import com.zhuxue.zhuxuebao.ui.components.TagChip
import com.zhuxue.zhuxuebao.ui.util.indentParagraphs
import com.zhuxue.zhuxuebao.ui.vm.UploadState
import com.zhuxue.zhuxuebao.ui.vm.UploadViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnalysisScreen(
    uploadVm: UploadViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val state by uploadVm.state.collectAsStateWithLifecycle()
    val saved = state as? UploadState.Saved
    var previewPath by remember { mutableStateOf<String?>(null) }
    var chosenSubject by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state) {
        if (state !is UploadState.NeedsSubject) chosenSubject = null
    }

    LaunchedEffect(saved) {
        saved?.let { onSaved(it.recordId) }
    }

    BackHandler(enabled = saved == null) { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("AI 分析") },
                navigationIcon = {
                    IconButton(onClick = { if (saved == null) onBack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val imagePaths = when (val current = state) {
            is UploadState.Working -> current.imagePaths
            is UploadState.Error -> current.imagePaths
            is UploadState.NeedsSubject -> current.imagePaths
            else -> emptyList()
        }

        ContentWidth(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (imagePaths.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(imagePaths) { path ->
                            AsyncImage(
                                model = File(path),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(width = 128.dp, height = 168.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { previewPath = path },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            when (val current = state) {
                is UploadState.Working -> {
                    item {
                        ContentCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TagChip("识别中", MaterialTheme.colorScheme.primary)
                                TagChip("${current.imagePaths.size} 张图片", MaterialTheme.colorScheme.tertiary)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "正在把图片里的错题拆成知识点，并生成学习方向。整张试卷可能需要 30-60 秒。",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    item { LoadingBlock(current.message) }
                }

                is UploadState.Error -> {
                    item {
                        AiErrorCard(
                            message = current.message,
                            needSettings = current.message.contains("API Key", ignoreCase = true),
                            onSettings = onOpenSettings,
                            onRetry = if (current.imagePaths.isNotEmpty()) {
                                { uploadVm.retry() }
                            } else {
                                null
                            }
                        )
                    }
                    item {
                        ContentCard {
                            Text("图片要求", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                indentParagraphs("题目和你的作答都要拍清楚；一张图里尽量只放一页内容。换图后重新上传即可。"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }

                is UploadState.NeedsSubject -> {
                    item {
                        ContentCard {
                            Text(
                                "没有识别出科目",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "AI 已经归纳出知识点，但判断不了属于哪一科。请选择要放入的科目，再保存这条记录。",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    item {
                        ContentCard {
                            Text(
                                "选择科目",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(10.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(
                                    6.dp,
                                    Alignment.CenterHorizontally
                                ),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SUBJECTS.forEach { subject ->
                                    FilterChip(
                                        selected = chosenSubject == subject,
                                        onClick = { chosenSubject = subject },
                                        label = { Text(subject) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { chosenSubject?.let(uploadVm::chooseSubject) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = chosenSubject != null
                            ) {
                                Text("保存到所选科目")
                            }
                        }
                    }
                }

                is UploadState.Saved -> {
                    item { LoadingBlock("分析完成，正在打开结果…") }
                }

                UploadState.Idle -> {
                    item {
                        ContentCard {
                            Text(
                                "没有待分析的图片",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "返回学习页，点击“上传错题 / 试卷”重新开始。",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onBack) { Text("返回") }
                        }
                    }
                }
            }

            item {
                Box(Modifier.fillMaxWidth()) {
                    Text(
                        "提示：分析只给诊断、学习方向和资源链接，不直接给出解法。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        }
    }

    previewPath?.let { path ->
        FullScreenImageDialog(path = path, onDismiss = { previewPath = null })
    }
}
