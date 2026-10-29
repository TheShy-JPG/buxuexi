# 架构说明

本文给二次开发者一个整体地图：应用怎么启动、数据怎么流动、改动应该落在哪一层。

## 分层

```
┌──────────────────────────────────────────────┐
│ ui/  Compose 界面 + ViewModel（状态与事件）    │
│  screens · components · theme · util · vm     │
└──────────────────┬───────────────────────────┘
                   │ 调用挂起函数 / 订阅 Flow
┌──────────────────▼───────────────────────────┐
│ data/ 仓库层（业务规则、边界校验）             │
│  Record · Practice · Discussion · QuestionBank│
│  Settings · UiPrefs · ImageStore              │
└──────────┬───────────────────────┬───────────┘
           │                       │
┌──────────▼─────────┐   ┌─────────▼───────────┐
│ data/local/ Room   │   │ ai/ ZhipuClient      │
│ 实体 · DAO · 迁移   │   │ 请求 · 提示词 · 解析  │
└────────────────────┘   └─────────────────────┘
```

- 没有引入框架级 DI。`di/AppContainer.kt` 在 Application 启动时手工组装仓库和 AI 客户端，界面通过构造函数拿到同一个容器。
- ViewModel 只做状态编排，不直接访问 Room / DataStore / OkHttp。
- 仓库层负责所有输入校验：文件大小、题库容量、关键词清洗、路径删除等。

## 启动流程

1. `ZhuxueApplication.onCreate()` 创建 `AppContainer`。
2. 容器构建 Room 数据库、各仓库、`ImageStore`、`ZhipuClient`，其中 `ZhipuClient` 通过回调读取当前设置。
3. Application 在 IO 协程里调用 `SettingsRepository.migrateLegacyKey()`，把旧版明文 `api_key` 加密后删除。
4. `MainActivity` 订阅 `settings`，用 `ZhuxueTheme` 包裹 `ZhuxueApp`。
5. `ZhuxueApp` 的 `NavHost` 提供路由：`main`、`subject/{subject}`、`analysis`、`record/{recordId}`、`settings`；`MainScreen` 内部再切换复习 / 学习 / 练习三个标签页。

`MainViewModel.openLearn()` 通过 `pendingLearnTarget` 把“从复习页跳到学习页”的意图传给 `MainScreen`，避免在导航层再开一条路由。

## 核心数据流

### 上传与诊断

```
相册/相机 → ImageStore.importRecordImage()（压缩到私有目录）
        → ZhipuClient.analyze()（visionModel）
        → 解析 JSON 为 AnalysisResult
        → 科目无法识别时等待用户选择
        → RecordRepository.save()（图片路径 + 分析 JSON）
```

- 图片始终以文件路径保存，数据库只存 JSON 数组。
- 分析提示词要求模型“只诊断、不讲课”，资源链接由客户端根据 `searchQuery` 生成。

### 学习方向

`LearnScreen` 对单个知识点调用 `ZhipuClient.learnGuide()`，把 `learningDirection` 和 `searchQuery` 写回 `discussed_points`，之后再次打开无需重新请求。

### 练习

```
PracticeViewModel
  → 读取题目来源顺序（BANK / WEB / AI）
  → 若启用题库：QuestionBankRepository.combinedText() 取相关分块
  → ZhipuClient.generateQuestions()：按来源依次尝试，失败自动回退
  → 学生作答（文字或照片）→ ZhipuClient.grade()
  → PracticeRepository.addAttempt() 记录对错
```

- 题库检索：把知识点切成 2 字片段 + 完整词 + 科目名，最多 10 个词；每个词最多取 80 个分块，候选达到 160 个时提前停止；按命中次数排序后拼接到约 9000 字再发给模型。
- 每题有独立状态：`answered` / `submitted` / `isCorrect`，多题练习在全部提交前不显示答案。

### 题库导入

```
SAF 选择文件 → 扩展名检查 → 声明大小检查（30 MB）
→ 流式复制到 cache/bank_import（复制时再次限流）
→ DOCX：读取 word/document.xml（限制解压后字符数）
   PDF：PDFBox 逐页提取（临时文件模式，降低堆内存）
→ 文字量检查（≥40 个非空白字符、单文件 ≤200 万字、总量 ≤1200 万字）
→ QuestionChunker.split()（约 900 字一块）
→ Room 事务写入 question_bank + question_chunks
→ 原始临时文件立即删除
```

`QuestionChunker` 在段落边界切块，短段落会合并到接近 900 字，避免出现只有一两行的碎片。

## 数据库（版本 5）

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `study_records` | subject, title, createdAt, imagePathsJson, analysisJson | 一次上传对应一条记录 |
| `practice_results` | subject, knowledgePoint, correct, createdAt | 单题对错流水，用于正确率 |
| `discussed_points` | subject, name, note, learningDirection, searchQuery | 学习页知识点与 AI 建议 |
| `question_bank` | name, charCount, addedAt, text（旧字段，现为空） | 题库文件元信息 |
| `question_chunks` | fileId, fileName, position, text（fileId 建索引） | 检索分块 |

新增表或字段时：

1. 修改实体并提升 `@Database(version = ...)`。
2. 在 `AppDatabase` 中增加 `Migration`，用纯 SQL 完成数据迁移。
3. 把新迁移加入 `addMigrations(...)`。
4. 用旧版数据库文件手工验证升级，不要只依赖全新安装。

`exportSchema = false`，没有 schema 快照；后续若需要更严格的迁移测试，建议开启导出并加入 MigrationTestHelper。

## 存储位置

| 路径 | 内容 |
| --- | --- |
| `files/datastore/zhuxue_settings.preferences_pb` | API Key 密文、接口地址、模型、主题、科目、题目来源 |
| `files/datastore/zhuxue_ui_prefs.preferences_pb` | 知识点 / 错题的排序偏好 |
| `databases/zhuxuebao.db` | Room 数据库 |
| `files/record_images/` | 压缩后的错题 / 试卷图片 |
| `files/answer_images/` | 手写作答图片 |
| `cache/camera/` | 拍照临时文件，导入后清理 |
| `cache/bank_import/` | 题库导入临时文件，解析后清理 |

## 线程模型

- Room、DataStore、文件读写、PDF/Word 解析都在 `Dispatchers.IO`。
- Base64 编码等 CPU 工作在 `Dispatchers.Default`。
- 网络请求使用 OkHttp 的异步回调，通过 `suspendCancellableCoroutine` 转成挂起函数；取消协程会取消请求。
- 界面只订阅 `StateFlow` / `Flow`，不在 Composable 中做阻塞 IO。

## 安全设计

| 风险 | 处理 |
| --- | --- |
| Key 被备份带到云端 | `allowBackup=false` + 两套备份排除规则 |
| Key 明文落盘 | Keystore AES-GCM 加密；启动迁移旧明文 |
| Key 被发到恶意主机 | 只允许 HTTPS + `bigmodel.cn`；debug 放行回环地址 |
| 设置页被截图 | 进入设置页加 `FLAG_SECURE`，离开时清除 |
| 导入压缩炸弹 / 超大文件 | 30 MB 文件上限、解压字符上限、流式复制限流 |
| 恶意深链 | `openUrl` 只接受 http/https |
| 导出组件泄露 | 只有启动 Activity 导出；FileProvider 不导出且只暴露 cache/camera |

它**不能**防护的情况：设备已 root、系统级录屏、用户主动把 Key 发给别人、模型服务商侧的数据处理。数据库和图片没有整库加密，这是当前有意的取舍（性能与实现复杂度）。

## 已知技术债

- 题库检索是 `LIKE` 匹配，没有分词表或 FTS；数据量大时靠“长词优先 + 提前停止”控制延迟。
- AI 返回依赖 JSON 契约，模型升级后可能需要同步调整 `ZhipuClient` 的解析与提示词。
- 没有 UI 自动化测试与真实机型矩阵；发布前需要手工回归上传、练习、题库三条主链路。
- `targetSdk` 仍为 35，上架应用商店前需按政策升级。
- 发布签名依赖本地 `keystore.properties`，没有内置 CI 打包。
