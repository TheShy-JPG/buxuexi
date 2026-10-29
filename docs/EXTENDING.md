# 二次开发指南

按“想改什么”查下面的清单。改完记得跑 `./gradlew testDebugUnitTest` 和 `./gradlew :app:assembleDebug`，并在真机或模拟器上走一遍相关界面。

## 换模型或默认接口地址

- `data/SettingsRepository.kt` 的 `AppSettings.Companion`：`DEFAULT_BASE_URL`、`DEFAULT_TEXT_MODEL`、`DEFAULT_VISION_MODEL`。
- 旧默认值放在 `LEGACY_*`，`migratedModel()` 会把用户没改过的旧 ID 自动升级。
- 只改模型 ID 不需要动网络层；模型必须兼容 OpenAI 的 `chat/completions` 结构。
- 具体到 DeepSeek 的配置、限制与双服务商改造，见 [CUSTOMIZE.md](CUSTOMIZE.md)。

## 接入其他 OpenAI 兼容服务

1. 在 `ai/AiEndpoint.kt` 的 `OFFICIAL_DOMAINS` 中加入服务商**官方**域名（保持 HTTPS）。目前内置智谱与 DeepSeek。这是 Key 的最后一道防线，不要改成“允许任意域名”。
2. `ai/ZhipuClient.kt` 的请求体已经是通用格式；需要额外鉴权头或不同路径时改 `post()` / `endpoint()`。
3. 联网真题依赖智谱的 `web_search` 工具。`AiEndpoint.supportsWebSearch()` 目前只对 `bigmodel.cn` 返回 true，`generateFromWeb()` 会据此跳过不支持的服务商，避免把普通 AI 题标成“联网真题”。换服务商时二选一：
   - 保持跳过，让练习回退到本地题库或 AI 原创；
   - 在 `chat()` 中为服务商实现对应的工具协议，同步调整 `extractSearchLinks()`，并把域名加入 `WEB_SEARCH_DOMAINS`。
4. 若要支持本地调试服务器，使用 debug 构建：`AiEndpoint.resolve(..., allowLoopback = BuildConfig.DEBUG)` 已放行 `localhost` / `127.0.0.1` / `10.0.2.2`。

不要为了“方便”把 Key 通过 URL 参数传递，也不要关闭 HTTPS 校验。

## 调整提示词和 AI 输出的 JSON 契约

- 所有系统提示词集中在 `ZhipuClient` 的 `companion object` 末尾（分析、学习方向、题库、联网、原创、批改）。
- 提示词和解析函数是一对：改了字段名就要同步改 `parseAnalysis()` / `parseQuestions()` / `parseGrade()`。
- 解析层对模型输出比较宽容（允许代码块包裹、布尔字符串），但对必填字段缺失会回退到失败提示。新增字段时提供默认值，避免旧记录反序列化失败。

## 增删科目

- `data/Models.kt` 的 `SUBJECTS` 是唯一科目来源，`matchSubject()` 也依赖它；`matchSubject()` 按清单顺序做包含匹配，更具体的名称要排在前面。
- 默认显示前 6 门；上限是 `MAX_VISIBLE_SUBJECTS = 9`，设置页的说明文案也要同步。
- 复习页网格按 `maxWidth` 自动分列，不需要改布局；科目变多时检查底部标签和小屏换行。
- 已保存的记录不会因为删科目而删除，只是不再出现在对应分组的默认视图里。
- 想让科目更贴近大学课程，见 [CUSTOMIZE.md](CUSTOMIZE.md) 的“改学科”一节，那里还列了提示词和图标需要同步修改的位置。

## 增加题目来源

1. `AppSettings.SOURCE_*` 与 `ALL_SOURCES` 增加常量。
2. `ZhipuClient.generateQuestions()` 的 `when (source)` 增加分支，或复用现有 `generateFrom*`。
3. 设置页的“题目来源优先级”列表和 `sourceTypeLabel()` 增加文案。
4. `parseQuestion()` 中决定新来源是否允许外链、如何写 `sourceType`。

来源顺序就是尝试顺序；单个来源失败会继续下一个，全部失败才报错。新增来源要考虑超时叠加，避免一次练习等待过久。

## 修改题库限制或分块策略

- `data/QuestionBankRepository.kt` 顶部：文件数、单文件字数、总字数上限；底部：检索词数量、每词召回数、提前停止阈值、导入大小上限。
- `data/QuestionChunker.kt`：`CHUNK_SIZE`（默认 900）与 `MIN_FLUSH_SIZE`（默认 400）。改大分块会减少数据库行数，但单次召回更容易超出 9000 字预算。
- 改上限后同步 README、设置页文案和 `SettingsScreen` 中的容量提示。
- 如果分块结构变化（例如增加标题字段），必须写 Room 迁移并重新处理已有 `question_chunks`。

## 调整主题与配色

- `ui/theme/Theme.kt` 定义浅色 / 深色 / 自定义主题的 Material 3 色彩。
- 设置页的预设色在 `SettingsScreen.kt` 顶部 `BACKGROUND_PRESETS` / `FONT_PRESETS`；自定义颜色用 `#RRGGBB` 或 `#AARRGGBB`。
- 大屏内容宽度由 `ui/components/Components.kt` 的 `CONTENT_MAX_WIDTH = 960.dp` 控制，`ContentWidth` 包住各页内容。

## 新增页面

1. 在 `ui/screens/` 新建 Composable，状态放到现有 ViewModel 或新 ViewModel。
2. 新 ViewModel 需要仓库时，在 `ui/vm/ViewModels.kt` 的 `AppVmFactory` 中构造。
3. 在 `ui/ZhuxueApp.kt` 的 `NavHost` 注册路由；需要参数时使用 `navArgument`，中文参数先 `Uri.encode`。
4. 跨标签页跳转沿用 `MainViewModel.setTab()` 或 `pendingLearnTarget` 的模式，不要直接持有另一个页面的状态。

## 新增数据表或字段

1. 新建实体与 DAO，在 `AppDatabase` 的 `entities` 和抽象方法中注册。
2. `version` 加 1，新增 `Migration(old, new)`，只写 SQL，不依赖实体类。
3. 加入 `addMigrations(...)`。
4. 用旧版数据库升级测试：安装旧 APK → 造数据 → 覆盖安装新 APK → 检查数据仍在。
5. 仓库层暴露 `Flow` 或挂起函数，界面不要直接碰 DAO。

## 测试

- 纯逻辑优先写成 JVM 单元测试，放在 `app/src/test/java/...`：地址校验、设置解析、分块、JSON 解析的纯函数部分。
- 依赖 Android 的部分（DataStore、Keystore、ContentResolver、PDFBox）目前只能手工回归，建议后续补 instrumented test。
- 发布前至少手工验证：导入题库 → 生成练习 → 提交批改 → 查看统计 → 删除记录。

## 发布

1. 准备 `keystore.properties`（模板见根目录 `keystore.properties.example`），确保已被 `.gitignore` 排除。
2. `./gradlew :app:assembleRelease`，产物在 `app/build/outputs/apk/release/`。
3. 用 `apksigner verify --print-certs` 检查签名，再安装到空数据设备上跑一遍主流程。
4. APK 放 GitHub Releases，不提交进仓库。

## 不要做的事

- 不要提交 API Key、`keystore.properties`、签名文件、用户照片或真实试卷。
- 不要恢复 `allowBackup`，也不要删除备份排除规则。
- 不要放宽 `AiEndpoint` 的 HTTPS / 域名限制来“兼容”临时服务。
- 不要把完整题库或大图读进内存拼接；导入和检索都依赖现在的大小上限。
