package com.zhuxue.zhuxuebao.ui.screens

import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhuxue.zhuxuebao.BuildConfig
import com.zhuxue.zhuxuebao.ai.AiEndpoint
import com.zhuxue.zhuxuebao.data.AppSettings
import com.zhuxue.zhuxuebao.data.QUESTION_BANK_MAX_FILES
import com.zhuxue.zhuxuebao.data.QUESTION_BANK_MAX_TOTAL_CHARS
import com.zhuxue.zhuxuebao.data.SUBJECTS
import com.zhuxue.zhuxuebao.ui.components.ContentCard
import com.zhuxue.zhuxuebao.ui.components.ContentWidth
import com.zhuxue.zhuxuebao.ui.components.TagChip
import com.zhuxue.zhuxuebao.ui.theme.parseHexColor
import com.zhuxue.zhuxuebao.ui.util.findActivity
import com.zhuxue.zhuxuebao.ui.util.formatAgo
import com.zhuxue.zhuxuebao.ui.util.openUrl
import com.zhuxue.zhuxuebao.ui.vm.ConnectionTest
import com.zhuxue.zhuxuebao.ui.vm.MainViewModel

private val BACKGROUND_PRESETS = listOf(
    "#FAF8F3", "#FFFFFF", "#EEF3FA", "#F2F6F4", "#1E1F22", "#101418"
)

private val FONT_PRESETS = listOf(
    "#1D211E", "#000000", "#334155", "#1F3A5F", "#FFFFFF", "#E8EAED"
)

private const val DOCX_MIME =
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val testState by viewModel.connectionTest.collectAsStateWithLifecycle()
    val bankFiles by viewModel.bankFiles.collectAsStateWithLifecycle()

    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var textModel by remember { mutableStateOf(settings.textModel) }
    var visionModel by remember { mutableStateOf(settings.visionModel) }
    var webSearch by remember { mutableStateOf(settings.webSearch) }
    var showKey by remember { mutableStateOf(false) }
    var selectedSubjects by remember { mutableStateOf(settings.visibleSubjects) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var customBackground by remember { mutableStateOf(settings.customBackground) }
    var customFont by remember { mutableStateOf(settings.customFont) }
    var sources by remember { mutableStateOf(settings.questionSources) }
    var askSave by remember { mutableStateOf(false) }
    var bankBusy by remember { mutableStateOf(false) }

    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    LaunchedEffect(settings) {
        apiKey = settings.apiKey
        baseUrl = settings.baseUrl
        textModel = settings.textModel
        visionModel = settings.visionModel
        webSearch = settings.webSearch
        selectedSubjects = settings.visibleSubjects
        themeMode = settings.themeMode
        customBackground = settings.customBackground
        customFont = settings.customFont
        sources = settings.questionSources
    }

    fun currentSettings() = AppSettings(
        apiKey = apiKey,
        baseUrl = baseUrl,
        textModel = textModel,
        visionModel = visionModel,
        webSearch = webSearch,
        visibleSubjects = selectedSubjects,
        themeMode = themeMode,
        customBackground = customBackground,
        customFont = customFont,
        questionSources = sources
    )

    val dirty = currentSettings() != settings

    fun validateEndpoint(): Boolean {
        val error = runCatching {
            AiEndpoint.resolve(baseUrl, allowLoopback = BuildConfig.DEBUG)
        }.exceptionOrNull()
        if (error != null) {
            Toast.makeText(context, error.message ?: "服务地址无效", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    fun save(): Boolean {
        if (!validateEndpoint()) return false
        if (themeMode == AppSettings.THEME_CUSTOM &&
            (parseHexColor(customBackground) == null || parseHexColor(customFont) == null)
        ) {
            Toast.makeText(context, "颜色请用 #RRGGBB 或 #AARRGGBB 格式", Toast.LENGTH_SHORT).show()
            return false
        }
        if (sources.isEmpty()) {
            Toast.makeText(context, "至少保留一个题目来源", Toast.LENGTH_SHORT).show()
            return false
        }
        viewModel.saveSettings(currentSettings())
        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
        return true
    }

    fun requestExit() {
        if (dirty) askSave = true else onBack()
    }

    BackHandler { requestExit() }

    val bankPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            bankBusy = true
            viewModel.addBankFile(uri) { error ->
                bankBusy = false
                Toast.makeText(
                    context,
                    error ?: "已加入题库",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { requestExit() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                ContentWidth {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (dirty) "保存设置" else "设置已保存")
                        }
                    }
                }
            }
        }
    ) { padding ->
        ContentWidth(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            item {
                ContentCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "AI 服务",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        TagChip("智谱 GLM", MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Key 加密保存在本机，不参与系统备份；识图、出题和批改都会直接请求智谱开放平台。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("API Key") },
                        placeholder = { Text("粘贴 AI 服务商提供的 API Key") },
                        visualTransformation = if (showKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showKey) "隐藏" else "显示"
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("接口地址") },
                        placeholder = { Text(AppSettings.DEFAULT_BASE_URL) }
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = textModel,
                        onValueChange = { textModel = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("文本模型") },
                        placeholder = { Text(AppSettings.DEFAULT_TEXT_MODEL) }
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = visionModel,
                        onValueChange = { visionModel = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("识图模型") },
                        placeholder = { Text(AppSettings.DEFAULT_VISION_MODEL) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "默认是智谱：GLM-5.3 FlashX 原生支持识图，文本和识图可填同一个 ID，模型名以控制台“模型广场”为准。也可以在下方按文档改用 DeepSeek 等兼容服务；识图和联网真题目前依赖智谱。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("联网搜索真题", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "关闭后联网高考真题来源会直接跳过，不再检索网络。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = webSearch,
                            onCheckedChange = { webSearch = it }
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            textModel = AppSettings.DEFAULT_TEXT_MODEL
                            visionModel = AppSettings.DEFAULT_VISION_MODEL
                            baseUrl = AppSettings.DEFAULT_BASE_URL
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("恢复默认模型与地址")
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { if (validateEndpoint()) viewModel.saveAndTest(currentSettings()) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = testState != ConnectionTest.Testing
                    ) {
                        if (testState == ConnectionTest.Testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("正在测试连接…")
                        } else {
                            Text("保存并测试连接")
                        }
                    }
                    when (val result = testState) {
                        is ConnectionTest.Success -> {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                result.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is ConnectionTest.Failure -> {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                result.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> Unit
                    }
                }
            }

            item {
                ContentCard {
                    Text("练习与复习科目", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "练习页每行 3 个；复习页用方块网格展示，宽屏自动变 3 列（默认 6 门）。最多 ${AppSettings.MAX_VISIBLE_SUBJECTS} 门，最少 ${AppSettings.MIN_VISIBLE_SUBJECTS} 门；已选 ${selectedSubjects.size} 门。",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SUBJECTS.chunked(3).forEach { rowSubjects ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowSubjects.forEach { subject ->
                                    val selected = selectedSubjects.contains(subject)
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            when {
                                                selected && selectedSubjects.size > AppSettings.MIN_VISIBLE_SUBJECTS -> {
                                                    selectedSubjects = selectedSubjects - subject
                                                }
                                                selected -> Toast.makeText(
                                                    context,
                                                    "至少保留 ${AppSettings.MIN_VISIBLE_SUBJECTS} 门科目",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                selectedSubjects.size < AppSettings.MAX_VISIBLE_SUBJECTS -> {
                                                    selectedSubjects = (selectedSubjects + subject)
                                                        .sortedBy { SUBJECTS.indexOf(it) }
                                                }
                                                else -> Toast.makeText(
                                                    context,
                                                    "最多选择 ${AppSettings.MAX_VISIBLE_SUBJECTS} 门",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        label = {
                                            Text(
                                                subject,
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                            )
                                        }
                                    )
                                }
                                repeat(3 - rowSubjects.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            item {
                ContentCard {
                    Text("本地题库", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "支持文字版 Word(.docx) 和 PDF；扫描件、照片版 PDF 读不出文字，会被拒绝。上传后可作为练习题目来源。",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { bankPicker.launch(arrayOf("application/pdf", DOCX_MIME)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !bankBusy
                    ) {
                        if (bankBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("正在解析题库…")
                        } else {
                            Icon(
                                Icons.Outlined.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("上传题库文件")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "已用 ${bankFiles.sumOf { it.charCount }} 字 / ${QUESTION_BANK_MAX_TOTAL_CHARS} 字，" +
                            "${bankFiles.size}/$QUESTION_BANK_MAX_FILES 个文件。上传后只保留可检索的文字，原始文件会立即删除。",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (bankFiles.isEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "还没有上传题库",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        bankFiles.forEach { file ->
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        file.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${file.charCount} 字 · ${formatAgo(file.addedAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteBankFile(file.id) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "删除题库")
                                }
                            }
                        }
                    }
                }
            }

            item {
                ContentCard {
                    Text("题目来源优先级", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "练习时从上到下依次尝试，前一个来源找不到题目才用下一个；只保留一个也可以。",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    sources.forEachIndexed { index, source ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(18.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(sourceName(source), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    sourceHint(source),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { sources = sources.moved(index, index - 1) },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移")
                            }
                            IconButton(
                                onClick = { sources = sources.moved(index, index + 1) },
                                enabled = index < sources.lastIndex
                            ) {
                                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移")
                            }
                            Switch(
                                checked = true,
                                onCheckedChange = {
                                    if (sources.size <= 1) {
                                        Toast.makeText(
                                            context,
                                            "至少保留一个题目来源",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        sources = sources - source
                                    }
                                }
                            )
                        }
                    }
                    val disabledSources = AppSettings.ALL_SOURCES.filterNot { it in sources }
                    if (disabledSources.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "已关闭，点一下启用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            disabledSources.forEach { source ->
                                FilterChip(
                                    selected = false,
                                    onClick = { sources = sources + source },
                                    label = { Text("启用${sourceName(source)}") }
                                )
                            }
                        }
                    }
                    if (AppSettings.SOURCE_BANK in sources && bankFiles.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "提示：本地题库还没上传文件，轮到它时会自动跳过。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item {
                ContentCard {
                    Text("主题", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "选“自定义”可以自己定背景色和字体色，直接点常用色块最省事。",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = themeMode == AppSettings.THEME_LIGHT,
                            onClick = { themeMode = AppSettings.THEME_LIGHT },
                            label = { Text("浅色") }
                        )
                        FilterChip(
                            selected = themeMode == AppSettings.THEME_DARK,
                            onClick = { themeMode = AppSettings.THEME_DARK },
                            label = { Text("深色") }
                        )
                        FilterChip(
                            selected = themeMode == AppSettings.THEME_CUSTOM,
                            onClick = { themeMode = AppSettings.THEME_CUSTOM },
                            label = { Text("自定义") }
                        )
                    }

                    if (themeMode == AppSettings.THEME_CUSTOM) {
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = customBackground,
                            onValueChange = { customBackground = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("背景色") },
                            placeholder = { Text("#FAF8F3") },
                            trailingIcon = {
                                ColorSwatch(hex = customBackground)
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "常用背景色",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        ColorPresetRow(
                            presets = BACKGROUND_PRESETS,
                            selected = customBackground,
                            onSelect = { customBackground = it }
                        )
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = customFont,
                            onValueChange = { customFont = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("字体色") },
                            placeholder = { Text("#1D211E") },
                            trailingIcon = {
                                ColorSwatch(hex = customFont)
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "常用字体色",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        ColorPresetRow(
                            presets = FONT_PRESETS,
                            selected = customFont,
                            onSelect = { customFont = it }
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "支持 #RRGGBB 或 #AARRGGBB；浅色背景自动配浅色主题，深色背景自动配深色主题。改完点底部“保存设置”生效。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                ContentCard {
                    Text("如何获取 API Key", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    val steps = listOf(
                        "打开 bigmodel.cn，用手机号注册并登录。",
                        "进入「个人中心 → API Keys」，新建一个密钥。",
                        "复制密钥（通常只显示一次），粘贴到上面的 API Key 输入框。",
                        "保存后点“测试连接”，成功即可开始上传错题。",
                        "模型名可能随平台更新，以官方模型文档显示的 ID 为准。",
                        "改用 DeepSeek 等兼容服务时，接口地址、模型 ID 和 Key 要来自同一家服务商，详见仓库 docs/CUSTOMIZE.md。"
                    )
                    steps.forEachIndexed { index, step ->
                        Row {
                            Text(
                                "${index + 1}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(20.dp)
                            )
                            Text(step, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (index != steps.lastIndex) Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { openUrl(context, "https://bigmodel.cn/usercenter/apikeys") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("获取 API Key")
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        TextButton(
                            onClick = {
                                openUrl(context, "https://docs.bigmodel.cn/cn/guide/start/model-overview")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("查看模型 ID")
                        }
                    }
                }
            }

            item {
                ContentCard {
                    Text("隐私与费用", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "1. API Key 只保存在本机应用数据中，只会发送给你在“接口地址”里配置的 AI 服务商。\n" +
                            "2. 上传的错题图片会发送给所配置的识图模型用于分析和批改。\n" +
                            "3. 上传的题库文件只保存在本机，只有练习时才会截取相关片段发给模型。\n" +
                            "4. 平台按 token 计费，免费额度和价格以服务商控制台为准；建议先用少量图片测试。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Text(
                    "补学习 v1.0 · 只给学习方向和资源，不直接喂答案",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
            }
        }
    }

    if (askSave) {
        AlertDialog(
            onDismissRequest = { askSave = false },
            title = {
                Text(
                    "保存设置？",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    "你修改了设置但还没保存。",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (save()) {
                        askSave = false
                        onBack()
                    }
                }) {
                    Text("保存并退出")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { askSave = false }) {
                        Text("取消")
                    }
                    TextButton(onClick = {
                        askSave = false
                        onBack()
                    }) {
                        Text("不保存")
                    }
                }
            }
        )
    }
}

@Composable
private fun ColorSwatch(hex: String) {
    val color = parseHexColor(hex)
    Box(
        Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(color ?: MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
private fun ColorPresetRow(
    presets: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { hex ->
            val color = parseHexColor(hex) ?: MaterialTheme.colorScheme.surfaceVariant
            val isSelected = selected.trim().equals(hex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "当前颜色",
                        tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun sourceName(source: String): String = when (source) {
    AppSettings.SOURCE_BANK -> "本地题库"
    AppSettings.SOURCE_WEB -> "联网高考真题"
    else -> "AI 原创题"
}

private fun sourceHint(source: String): String = when (source) {
    AppSettings.SOURCE_BANK -> "从你上传的 Word / PDF 题库中选题"
    AppSettings.SOURCE_WEB -> "联网搜索高考真题原题"
    else -> "AI 按知识点原创，找不到题时兜底"
}

private fun List<String>.moved(from: Int, to: Int): List<String> {
    if (from == to || from !in indices || to !in indices) return this
    val list = toMutableList()
    val item = list.removeAt(from)
    list.add(to, item)
    return list
}
