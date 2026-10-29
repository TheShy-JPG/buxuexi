# 补学习（BuXueXi）

面向高中生的本地优先错题诊断与练习 App。拍照上传错题或试卷，由智谱 GLM 视觉模型归纳薄弱知识点和错误思维，只给学习方向与检索入口，不直接讲解知识点；之后可以按知识点进入学习或练习。

- 技术栈：Kotlin + Jetpack Compose（Material 3）+ Room + DataStore + OkHttp
- 最低系统：Android 8.0（API 26）
- 许可：MIT，见 [LICENSE](LICENSE)

> 本项目是个人学习项目，与智谱（Zhipu AI）无隶属关系。AI 生成内容可能有误，请以教材和老师讲解为准。

## 功能

| 模块 | 说明 |
| --- | --- |
| 上传分析 | 相册多选（最多 4 张）或拍照，视觉模型输出结构化诊断 |
| 学习方向 | 只给学习顺序、易错点和检索关键词；视频/文章链接由客户端生成 |
| 复习 | 按科目记录错题，知识点错误频次图表，常错易得分优先 |
| 学习 | 按科目聚合薄弱点，可录入“想学的知识点”，由 AI 给学习方向 |
| 练习 | 单题/多题（2–8 道），作答前隐藏答案；文字或手写照片作答，AI 批改 |
| 题目来源 | 本地题库 → 联网高考真题 → AI 原创，可调整优先级 |
| 本地题库 | 导入可复制文字的 `.docx` / `.pdf`，分块索引后按关键词离线检索 |
| 主题 | 浅色 / 深色 / 自定义背景与文字颜色 |

> 想换模型服务（例如 DeepSeek）、把科目改成大学课程，或修改软件名？先看 [docs/CUSTOMIZE.md](docs/CUSTOMIZE.md)。

## 构建

需要 JDK 17 及以上、Android SDK Platform 35 与 Build-Tools 35.0.1。项目已配置阿里云 Maven 镜像与腾讯 Gradle 镜像，国内网络可直接构建。

```bash
# 调试包
./gradlew :app:assembleDebug

# 单元测试
./gradlew testDebugUnitTest
```

如果命令行默认的 Java 版本低于 17，先指向 JDK 17+（Android Studio 自带的 JBR 也可以）：

```bash
# Git Bash / macOS / Linux
export JAVA_HOME="/path/to/jdk-17"
# Windows PowerShell
# $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

安装到已开启 USB 调试的设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可以在 Android Studio 中直接打开项目运行。`local.properties` 由 Android Studio 自动生成，不要提交。

### 发布包与签名

1. 生成密钥（只需一次，务必离线备份）：

```bash
keytool -genkeypair -v -keystore release/zhuxue-release.jks \
  -alias zhuxue -keyalg RSA -keysize 2048 -validity 10000
```

2. 复制 `keystore.properties.example` 为 `keystore.properties`，填入路径和口令。
3. 构建：`./gradlew :app:assembleRelease`。Release 默认开启 R8 压缩与资源收缩。

`keystore.properties`、`*.jks`、`*.keystore` 已在 `.gitignore` 中排除，**任何时候都不要提交**。发布到 GitHub 时，把签名后的 APK 放到 Releases，而不是提交进仓库。

## 获取并配置智谱 API Key

1. 打开 <https://bigmodel.cn> 注册登录，完成实名认证。
2. 进入「个人中心 → API Keys」新建密钥并立即保存。
3. 打开补学习 → 右上角设置 → 粘贴 Key → “保存并测试连接”。

默认配置（无需修改）：

- 接口地址：`https://open.bigmodel.cn/api/paas/v4`
- 文本 / 识图模型：`glm-5.3-flashx`（可在设置页改成控制台显示的任意模型 ID）
- 联网搜索：开启

模型 ID 会随平台更新。若提示“模型不存在”，到智谱控制台的“模型广场”复制最新 ID。平台按 token 计费，以智谱控制台价格为准。

想改用 DeepSeek：在设置里把接口地址改成 `https://api.deepseek.com/v1`、文本模型改成 `deepseek-chat`，并填入 DeepSeek 的 Key。注意 DeepSeek 暂不支持识图，联网真题也会自动跳过，细节与限制见 [docs/CUSTOMIZE.md](docs/CUSTOMIZE.md)。

## 数据与安全

- **API Key 加密保存**：使用 Android Keystore 的 AES-GCM 密钥加密后写入 DataStore；旧版本的明文 Key 会在启动时自动迁移并删除明文。
- **不参与系统备份**：`allowBackup=false`，并同时提供 `backup_rules.xml` 与 `data_extraction_rules.xml`，Key、数据库和错题照片不会进入云备份或换机迁移。
- **设置页禁止截屏**：设置页打开时启用 `FLAG_SECURE`，避免 Key 出现在截图和最近任务预览中。
- **接口地址限制**：只接受 HTTPS，且域名属于 `bigmodel.cn` 或 `deepseek.com`；debug 构建额外允许回环地址用于本地联调，防止 Key 被发送到任意主机。
- **导入上限**：单个题库文件 30 MB，解压后的 Word 正文也有上限，防止压缩炸弹。
- **外链限制**：只打开 `http` / `https` 链接。

数据去向：

- 留在本机：错题记录、分析结果、练习统计、题库文字、压缩后的图片、设置。
- 发送给智谱：上传的图片、题目文本、学生作答，以及联网搜索关键词。除此之外没有统计、广告或第三方上报。
- 卸载应用会删除应用私有目录中的全部数据。

## 项目结构

```
app/src/main/java/com/zhuxue/zhuxuebao/
├─ MainActivity.kt / ZhuxueApplication.kt
├─ ai/
│  ├─ ZhipuClient.kt        # 智谱请求、提示词、JSON 解析
│  └─ AiEndpoint.kt         # 接口地址校验与拼接
├─ data/
│  ├─ Models.kt             # 跨层数据模型与科目
│  ├─ SettingsRepository.kt # DataStore 设置 + Key 加解密
│  ├─ SecureStore.kt        # Android Keystore AES-GCM
│  ├─ QuestionBankRepository.kt / QuestionChunker.kt
│  ├─ RecordRepository.kt / PracticeRepository.kt / DiscussionRepository.kt
│  ├─ ImageStore.kt / UiPrefsRepository.kt
│  └─ local/                # Room 实体、DAO、数据库迁移
├─ di/AppContainer.kt       # 手动依赖注入
└─ ui/
   ├─ screens/              # 复习、学习、练习、上传、详情、设置
   ├─ components/           # 卡片、标签、图表、弹窗
   ├─ theme/                # Material 3 主题与自定义颜色
   ├─ util/                 # 日期、链接、颜色工具
   └─ vm/ViewModels.kt      # 主界面 / 上传 / 练习状态
```

更细的模块说明见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，二次开发入口见 [docs/EXTENDING.md](docs/EXTENDING.md)，换模型 / 改学科 / 改名的具体步骤见 [docs/CUSTOMIZE.md](docs/CUSTOMIZE.md)，最初的需求记录见 [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md)。

## 测试

- 单元测试覆盖接口地址校验、设置解析、题库分块：`./gradlew testDebugUnitTest`。
- 大屏布局已在 2560×1600 的 Android 15 平板模拟器上验证横竖屏，内容限制在 960dp，复习页自动 3 列。
- 尚未覆盖：UI 自动化测试、真实机型兼容性矩阵、AI 输出质量的系统评测。

## 已知限制

- 题库检索是 SQL `LIKE` 关键词匹配，不是语义检索；换同义词可能找不到。
- 题库总容量上限 1200 万字（约 40 个文件），超过请在设置中删旧文件。
- 数据库与图片是应用私有的明文存储，未做整库加密；设备已 root 时无法保证防护。
- 联网搜索依赖智谱服务端工具，搜索可用性与返回链接由平台决定。
- `targetSdk` 为 35；若要发布到 Google Play，需按当年政策升级到要求的 API 级别。
- 练习正确率统计等端到端流程仍需要手工回归。

## 常见问题

| 现象 | 处理 |
| --- | --- |
| `adb devices` 为空 | 换数据线或 USB 接口，手机切换为“传输文件”，重新授权调试 |
| 构建报 JDK 版本错误 | 在 Android Studio 的 Gradle 设置里选择 JDK 17+，或设置 `JAVA_HOME` |
| 分析提示 401 / 鉴权失败 | Key 复制不完整或已失效，重新生成并保存 |
| 提示模型不存在 | 到智谱控制台“模型广场”复制最新模型 ID，在设置页替换 |
| 联网搜索报错 | 客户端会自动重试无搜索模式；仍失败可在设置关闭联网搜索 |
| 上传后一直转圈 | 检查能否访问 `open.bigmodel.cn`；大图分析通常需要 10–30 秒 |
| 相机无法打开 | 确认已授予相机权限，部分机型需在系统设置中手动允许 |

## 示例题库

根目录的 `补学习示例题库.docx` 仅用于演示导入格式与分块效果，题目均为示意内容。分发或再发布前请替换为自己拥有版权的内容；使用者需自行确保上传的试卷、题目不侵犯他人著作权。

## 贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)。安全问题请勿公开提交 Issue，处理方式见其中的“报告安全问题”。

## 第三方组件

见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。各依赖保留其原始许可证。
