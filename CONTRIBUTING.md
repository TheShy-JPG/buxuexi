# 贡献指南

感谢愿意改进补学习。这个项目优先保证三件事：学生的数据安全、离线可用、界面简单。

## 开发环境

- JDK 17 及以上
- Android Studio 或命令行 Gradle
- Android SDK Platform 35、Build-Tools 35.0.1
- 最低运行设备：Android 8.0（API 26）

```bash
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
```

`local.properties` 由 Android Studio 生成，不要提交。构建镜像已配置为国内可访问的阿里云 / 腾讯镜像。

## 提交前检查

- [ ] `./gradlew testDebugUnitTest` 通过
- [ ] `./gradlew :app:assembleDebug` 通过
- [ ] 涉及界面的改动在手机或平板尺寸上都看过（横竖屏各一次更好）
- [ ] 没有提交 Key、`keystore.properties`、签名文件、用户照片、真实试卷
- [ ] 新增文案是中文，和现有语气一致
- [ ] 没有为了省事关闭 HTTPS、备份排除、导入上限等安全约束

## 代码约定

- Kotlin 官方风格，4 空格缩进；Compose 函数保持“状态在上、纯 UI 在下”。
- 业务规则放仓库层，界面只做展示和事件转发。
- 新增依赖前先确认许可证兼容 MIT，并更新 `THIRD_PARTY_NOTICES.md`。
- 修改数据库结构必须附带迁移，不能只改实体。
- 注释只解释“为什么”，不复述代码。

## 分支与提交

- 从 `main` 拉分支，一个 PR 只做一件事。
- 提交信息用一句话说明动机，例如：`fix: 导入题库时限制解压后大小`。
- PR 描述写清楚：改了什么、为什么、怎么验证的、有哪些已知限制。

## 报告安全问题

不要公开提交安全相关的 Issue。请使用 GitHub 仓库的 **Security → Report a vulnerability**（私密安全公告），或在仓库主页找到维护者联系方式私下沟通。请附上复现步骤、影响范围和可能的修复方向。

重点关注的区域：

- `SettingsRepository` / `SecureStore`：Key 的存储与迁移
- `AiEndpoint` / `ZhipuClient`：请求地址、鉴权头、响应解析
- `QuestionBankRepository`：文件导入、解压、容量限制
- `AndroidManifest.xml` 与 `res/xml/`：导出组件、备份规则
- `ImageStore` / `FileProvider`：图片路径与临时文件

## 不适合直接提交的改动

- 把接口地址改成允许任意 HTTP 域名
- 把 Key 写入日志、崩溃上报或剪贴板
- 引入统计、广告、账号体系（与“本地优先”的目标冲突，需先讨论）
- 提交受版权保护的真题、试卷或扫描件
