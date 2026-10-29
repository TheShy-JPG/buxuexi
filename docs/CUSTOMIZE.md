# 改造指南：换模型、改学科、改名

这份文档面向想拿本项目做二次开发的人，回答两个最常见的问题：

- 怎么换成 DeepSeek 这类别的模型服务？
- 怎么把“高中九科”改成大学课程，让大学生也能用？

项目全貌和更细的模块说明见 [ARCHITECTURE.md](ARCHITECTURE.md)，其他改动入口见 [EXTENDING.md](EXTENDING.md)。所有路径都相对仓库根目录。

## 0. 先说改名

显示名已经改为 **补学习**。改名只动了展示层，包名、数据库名、主题名保持不变，这样老用户覆盖安装后数据还在。

| 改什么 | 位置 |
| --- | --- |
| 桌面图标下的名字 | `app/src/main/res/values/strings.xml` 的 `app_name` |
| 设置页底部版本行 | `ui/screens/SettingsScreen.kt` 里带 `v1.0` 的那行文案 |
| 文档标题与说明 | `README.md`、`CONTRIBUTING.md`、`THIRD_PARTY_NOTICES.md`、`docs/REQUIREMENTS.md` |
| 示例题库文件名 | 根目录 `补学习示例题库.docx`，同时改 README 中的引用 |

以下内部标识**故意没改**，改它们会带来数据迁移或“变成另一个 App”的代价：

- `applicationId` / 包名 `com.zhuxue.zhuxuebao`：改了就是新应用，旧应用的数据库、照片、Key 都不会跟过来。
- Room 数据库 `zhuxuebao.db`、DataStore `zhuxue_settings`：改了要写迁移，否则用户数据“消失”。
- `Theme.ZhuxueBao`：纯内部资源名，用户看不到。

确认要彻底换包名时，记得同步改 `app/build.gradle.kts` 的 `namespace` 与 `applicationId`、`AndroidManifest.xml`、`themes.xml`，并自己决定是否提供数据迁移。GitHub 仓库名与包名无关，可以单独起名。

## 1. 换成 DeepSeek

### 1.1 只改设置，不用重新编译

应用的请求体是标准 OpenAI `chat/completions` 格式，DeepSeek 兼容。当前白名单已包含 `deepseek.com`，直接打开「设置 → AI 模型」填写：

| 字段 | 值 |
| --- | --- |
| API Key | 在 <https://platform.deepseek.com/api_keys> 创建，**只填在 App 里，不要写进代码或提交到仓库** |
| 接口地址 | `https://api.deepseek.com/v1`（`https://api.deepseek.com` 也可以） |
| 文本模型 | `deepseek-chat`；想试推理模型填 `deepseek-reasoner` |
| 识图模型 | 见下面的限制，DeepSeek 目前没有可用的视觉接口 |

保存后点“测试连接”。连接测试只发一条极短的文本请求，能通过就说明 Key、地址、模型名三者匹配。

### 1.2 切过去之后，哪些功能会变

**联网真题会自动跳过。** 联网检索依赖智谱专有的 `web_search` 工具，DeepSeek 没有对应协议。代码里有两道保护：

- `ai/AiEndpoint.kt` 的 `supportsWebSearch()` 只对 `bigmodel.cn` 返回 true。
- `ai/ZhipuClient.kt` 的 `generateFromWeb()` 在调用前检查，不是智谱就抛出一条普通失败，让练习继续尝试下一个题目来源。

所以切到 DeepSeek 后，请把题目来源调成「本地题库 → AI 原创」，或在设置里关掉联网搜索，不要期待“联网高考真题”还能工作。

**识图功能不可用。** DeepSeek 的对话接口只接受文本。以下两处会失败：

- 上传错题/试卷分析（`ZhipuClient.analyze()`，使用 `visionModel`）；
- 用照片作答后的批改（`ZhipuClient.grade()`，有图片时也会切到 `visionModel`）。

当前 `AppSettings` 只有一个 `baseUrl` 和一个 `apiKey`，文本和识图共用同一家服务商。因此“纯 DeepSeek”配置只能使用文字相关功能：录入想学的知识点、看学习方向、生成文字练习题、批改文字作答。**如果拍照上传是核心入口，请保留智谱配置**，把 DeepSeek 当成“文本备用模型”的思路需要先做 1.4 的双服务商改造。

**推理模型可能被截断。** `deepseek-reasoner` 会先输出思考内容，容易吃满 `max_tokens`。如果界面提示“AI 输出达到长度上限被截断”，改用 `deepseek-chat`，或在 `ai/ZhipuClient.kt` 中调大对应调用的 `maxTokens`（见第 3 节）。

**JSON 输出仍靠提示词。** 本应用没有使用服务商的 `response_format`，而是靠系统提示词 + 宽松解析（允许 ```` ```json ```` 包裹）。DeepSeek 一般能遵守；如果频繁解析失败，优先检查是不是把模型换成了不适合指令跟随的版本。

### 1.3 把 DeepSeek 设成默认值（可选）

如果这是你 fork 的主用法，可以改 `data/SettingsRepository.kt` 的 `AppSettings.Companion`：

```kotlin
const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
const val DEFAULT_TEXT_MODEL = "deepseek-chat"
const val DEFAULT_VISION_MODEL = "deepseek-chat" // 仅占位；识图仍不可用
```

同时把 `SettingsScreen.kt` 中“模型名以智谱控制台为准”的说明改成对应服务商。注意 `LEGACY_*` 与 `migratedModel()` 是给智谱旧模型名做自动升级的，换服务商后要么删掉，要么改成你自己的旧值，避免把用户填的 DeepSeek 模型名误判成旧智谱 ID。

### 1.4 同时用两家（需要改结构）

想“DeepSeek 出题 + 智谱识图”，需要把单一配置拆成两份：

1. `AppSettings` 增加 `visionBaseUrl`、`visionApiKey`（或增加 `provider` 概念），`SettingsRepository` 增加对应的 DataStore key 与加解密逻辑。
2. `AiEndpoint.resolve()` 本来就能分别校验两个地址；`ZhipuClient.analyze()` / `grade()` 改用视觉配置，其余调用继续用文本配置。
3. 设置页拆成“文本模型 / 识图模型”两组输入，并明确提示两把 Key 的来源。
4. 补单元测试：两个地址各自的域名校验、Key 为空时的报错、迁移后旧配置能读到。

这是本项目目前最值得做的扩展之一，但涉及设置结构、存储和 UI，建议单独一个 PR。

### 1.5 接入其他服务商

1. 在 `ai/AiEndpoint.kt` 的 `OFFICIAL_DOMAINS` 增加**官方**域名，仍然只允许 HTTPS。不要改成“允许任意域名”，这是防止 Bearer Key 被发往任意主机的最后一道防线。
2. 如果对方支持类似 `web_search` 的工具，在 `chat()` 中实现其工具协议，并把域名加进 `WEB_SEARCH_DOMAINS`，同时调整 `extractSearchLinks()`；否则保持 `supportsWebSearch()` 返回 false，让联网来源自动跳过。
3. 在 `AiEndpointTest` 里补：正常地址、相似域名（如 `api.deepseek.com.evil.example`）、HTTP、带账号密码或查询参数的地址。

### 1.6 安全底线

- 不要把 Key 硬编码进 `BuildConfig`、资源文件或示例配置；本项目只接受用户在设置页输入，并用 Android Keystore 加密保存。
- 不要为了“兼容”临时服务关闭 HTTPS 或放宽域名白名单。
- 不要把 Key 写进日志、崩溃上报或剪贴板。
- 换服务商不会改变数据去向：图片、题目文本、学生作答会发送给你配置的那家服务商，设置页的隐私说明要同步改。

## 2. 改学科，让大学生也能用

### 2.1 唯一的科目清单

`data/Models.kt` 的 `SUBJECTS` 是所有科目的唯一来源，界面、筛选、匹配都依赖它：

```kotlin
val SUBJECTS = listOf(
    "高等数学", "线性代数", "概率论", "大学物理",
    "电路", "数据结构", "C语言", "大学英语"
)
```

两个注意点：

- **顺序会影响匹配。** `matchSubject()` 用 `SUBJECTS.firstOrNull { text.contains(it) }`，把 AI 返回的科目名映射到清单。如果同时有“数学”和“高等数学”，更具体的必须排在前面，否则“高等数学”会先命中“数学”。
- **不要留下重复或互相包含的短名**（如“物理”与“大学物理”），否则归类会不稳定。

### 2.2 默认显示哪些、最多几门

- 新安装默认显示前 6 门：`SettingsRepository.kt` 中 `AppSettings.visibleSubjects = SUBJECTS.take(6)`，`parseSubjects()` 的兜底也是 `SUBJECTS.take(6)`。改完 `SUBJECTS` 后，把你最常用的 6 门放在前面即可。
- 最多 9 门：`AppSettings.MAX_VISIBLE_SUBJECTS = 9`（最少 1 门）。大学课程往往超过 9 门，需要同时调大这个常量；设置页的 3 列网格会自然换行，复习页网格按宽度自动分列，一般不用改布局。
- 老用户的选择存在 DataStore 里，改默认值不会影响他们；需要让已经装过的人看到新科目，得在设置页重新勾选，或清除应用数据。
- 设置页说明文案 `SettingsScreen.kt` 里带有“最多/已选”字样的那一段会自动读取常量，但“默认 6 门”是写死的文字，记得一起改。

### 2.3 科目图标只取首字

`ui/screens/ReviewScreen.kt` 的科目方块用 `subject.take(1)` 当图标。“高等数学”和“高数”都会显示“高”，两个字的课程也可能撞车。需要区分时改成映射：

```kotlin
val badge = when (subject) {
    "高等数学" -> "高数"
    "线性代数" -> "线代"
    "概率论" -> "概率"
    "数据结构" -> "DS"
    else -> subject.take(1)
}
```

### 2.4 提示词里的“高中 / 高考”

AI 的语气、难度和“真题”定义都写在提示词里，这是换成大学场景时**最容易被漏掉**的部分。先全局搜一遍：

```bash
grep -rn "高中\|高考" app/src/main/java
```

需要一起改的位置：

| 位置 | 现在的内容 | 建议 |
| --- | --- | --- |
| `ZhipuClient.kt` 的 `LEARN_GUIDE_SYSTEM` | “高中学习规划师”“中国高中生”“高中教材顺序” | 改成“大学课程学习规划师”“大学生”“教材/课程大纲顺序” |
| `ANALYSIS_SYSTEM` | “高中学习诊断师” | 同上 |
| `WEB_SYSTEM` | 只认高考真题，列出全国卷/新高考卷 | 改成“期末真题 / 考研真题 / 课程原题”，并同步改“真题”的判定标准 |
| `AI_SYSTEM`、`BANK_SYSTEM`、`GRADE_SYSTEM` | “高中出题老师/批改老师”“适合中国高中生” | 改成大学课程语境 |
| `generateFromWeb()` 的用户提示与 `searchQuery` | “请围绕高中…”“…高考真题” | 改成“请围绕…”“…期末真题”等 |
| `generateFromAi()` 的用户提示 | “请围绕高中…” | 同上 |
| `ZhipuClient.kt` 中 `parseQuestions()` 附近的百度兜底链接 | 用“高考真题”“高考试卷”拼搜索词 | 改成对应考试的搜索词 |
| `ZhipuClient.kt` 的 `buildSearchResources()` | 学习资源搜索词带“高中” | 改成“大学”或具体课程名 |
| `sourceLabel()`、`defaultNote` | “联网高考真题”“已结合联网高考真题搜索出题” | 改成新来源的准确说法 |
| 设置页来源文案 | `SettingsScreen.kt` 中“联网高考真题”“联网搜索高考真题原题” | 同步 |
| README 功能表 | “联网高考真题” | 同步 |

改完提示词后，务必实际生成几道题看输出：难度、术语、答案格式都可能需要再调。改动提示词不需要动解析层，除非你同时改了 JSON 字段。

### 2.5 已有数据不会自动改名

- 记录里的科目是**保存当时的字符串**。把“数学”改成“高等数学”后，旧记录的科目仍是“数学”，不会自动迁移；新分析才会使用新清单。
- 复习页只展示用户勾选的科目；被移出 `SUBJECTS` 的旧科目不会出现在默认视图里，但数据还在数据库里。学习页会把不在清单里的旧科目追加到末尾（`LearnScreen.kt` 里 `filterNot { it in SUBJECTS }`）。
- 如果确实要把旧数据统一改名，需要写一次性 Room 迁移或 `UPDATE`，并先备份数据库；只改 `SUBJECTS` 不会动数据库。
- 科目改名不涉及表结构，不需要升 `AppDatabase.version`。

### 2.6 示例题库

根目录的 `补学习示例题库.docx` 只是演示导入格式。换成大学场景时，建议替换成你自己拥有版权的课程习题，并同步改 README 与 `THIRD_PARTY_NOTICES.md` 中的文件名。

## 3. 其他常用开关

| 想改什么 | 位置 |
| --- | --- |
| 练习一次几道题 | 界面限制 2–8 在 `ui/screens/PracticeScreen.kt`；后端兜底 `ZhipuClient.generateQuestions()` 的 `count.coerceIn(1, 8)`，两处要一起改 |
| 单次输出长度 | `ZhipuClient.kt` 各调用点的 `maxTokens`；联网出题用常量 `WEB_QUESTION_MAX_TOKENS`（默认 12288），其余多为 4096 |
| 题目来源顺序与开关 | 设置页“题目来源优先级”；常量在 `AppSettings.SOURCE_*` / `ALL_SOURCES` |
| 题库数量、字数、分块 | `data/QuestionBankRepository.kt`、`data/QuestionChunker.kt`，详见 EXTENDING.md |
| 大屏内容宽度 | `ui/components/Components.kt` 的 `CONTENT_MAX_WIDTH`（默认 960dp） |
| 配色与自定义主题 | `ui/theme/Theme.kt` 与 `SettingsScreen.kt` 顶部的预设色 |

## 4. 改完怎么验

```bash
export JAVA_HOME=/path/to/jdk-17   # 或 Android Studio 自带 JBR
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
```

手工回归至少覆盖：

1. 设置页能保存、能测试连接；切回默认配置也正常。
2. 上传一张错题图片能出分析（如果保留智谱识图）。
3. 练习能按新科目生成题目，作答、批改、统计都正常。
4. 联网来源在新服务商下被跳过，而不是报一个看不懂的错误。
5. 旧版本覆盖安装后，历史记录还在。

## 5. 不建议做的事

- 把 Key 或签名文件提交进仓库。
- 为了省事允许任意 HTTP 地址，或关闭 HTTPS 校验。
- 删掉 `allowBackup=false` 与备份排除规则。
- 只改界面文案、不改提示词，就宣称“已适配大学场景”。
- 直接改包名或数据库名却不做迁移，导致老用户数据丢失。
