package com.zhuxue.zhuxuebao.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhuxue.zhuxuebao.ui.components.ContentWidth
import com.zhuxue.zhuxuebao.ui.vm.AppTab
import com.zhuxue.zhuxuebao.ui.vm.MainViewModel
import com.zhuxue.zhuxuebao.ui.vm.PracticeViewModel
import com.zhuxue.zhuxuebao.ui.vm.UploadViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainVm: MainViewModel,
    uploadVm: UploadViewModel,
    practiceVm: PracticeViewModel,
    onOpenRecord: (Long) -> Unit,
    onOpenSubject: (String) -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val tab by mainVm.tab.collectAsStateWithLifecycle()
    val practiceState by practiceVm.state.collectAsStateWithLifecycle()
    val records by mainVm.records.collectAsStateWithLifecycle()
    val stats by mainVm.knowledgeStats.collectAsStateWithLifecycle()
    val settings by mainVm.settings.collectAsStateWithLifecycle()
    val pendingLearn by mainVm.pendingLearnTarget.collectAsStateWithLifecycle()
    val uiPrefs by mainVm.uiPrefs.collectAsStateWithLifecycle()

    var showUploadSheet by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showExitPracticeDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = tab == AppTab.PRACTICE && practiceState.active) {
        if (!practiceState.grading) showExitPracticeDialog = true
    }

    if (showExitPracticeDialog) {
        AlertDialog(
            onDismissRequest = { showExitPracticeDialog = false },
            title = { Text("退出本次练习？") },
            text = {
                Text("返回练习设置页会丢失本组已生成的题目和当前作答，需要重新出题、重新作答。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitPracticeDialog = false
                        practiceVm.endSession()
                    }
                ) { Text("仍要退出") }
            },
            dismissButton = {
                TextButton(onClick = { showExitPracticeDialog = false }) { Text("继续练习") }
            }
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris ->
        if (uris.isNotEmpty()) {
            uploadVm.start(uris)
            onOpenAnalysis()
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            uploadVm.start(listOf(uri))
            onOpenAnalysis()
        }
        pendingCameraUri = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(tab.label, style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { mainVm.setTab(item) },
                        icon = { Icon(tabIcon(item), contentDescription = item.label) },
                        label = { Text(item.label) }
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
            when (tab) {
                AppTab.LEARN -> LearnScreen(
                    stats = stats,
                    knowledgeOrder = uiPrefs.knowledgeOrder,
                    pendingLearnTarget = pendingLearn,
                    onConsumePending = mainVm::consumePendingLearnTarget,
                    onPractice = { target ->
                        practiceVm.preset(target.subject, target.point.name)
                        mainVm.setTab(AppTab.PRACTICE)
                    },
                    onDiscuss = mainVm::addDiscussedPoint,
                    onLearnGuide = mainVm::requestLearnGuide,
                    onUpload = { showUploadSheet = true }
                )
                AppTab.PRACTICE -> PracticeScreen(
                    viewModel = practiceVm,
                    stats = stats,
                    visibleSubjects = settings.visibleSubjects,
                    apiKeyConfigured = settings.isConfigured,
                    onOpenSettings = onOpenSettings
                )
                AppTab.REVIEW -> ReviewScreen(
                    records = records,
                    stats = stats,
                    visibleSubjects = settings.visibleSubjects,
                    onOpenSubject = onOpenSubject
                )
            }
        }
    }

    if (showUploadSheet) {
        UploadSourceSheet(
            onDismiss = { showUploadSheet = false },
            onCamera = {
                showUploadSheet = false
                val file = File(context.cacheDir, "camera")
                    .apply { mkdirs() }
                    .resolve("camera_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            },
            onGallery = {
                showUploadSheet = false
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadSourceSheet(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "上传错题或试卷",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "AI 会识别题目、归纳知识点，并给出学习方向。支持一次上传最多 4 张图片。",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            UploadActionRow(
                icon = Icons.Outlined.PhotoCamera,
                title = "拍照",
                subtitle = "适合单页错题，拍清楚题目和你的作答",
                onClick = onCamera
            )
            Spacer(Modifier.height(10.dp))
            UploadActionRow(
                icon = Icons.Outlined.PhotoLibrary,
                title = "从相册选择",
                subtitle = "最多 4 张，适合整张试卷",
                onClick = onGallery
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun tabIcon(tab: AppTab): ImageVector = when (tab) {
    AppTab.LEARN -> Icons.Outlined.MenuBook
    AppTab.PRACTICE -> Icons.Outlined.EditNote
    AppTab.REVIEW -> Icons.Outlined.History
}