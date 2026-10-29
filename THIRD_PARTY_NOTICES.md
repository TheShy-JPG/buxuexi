# 第三方组件与许可

本项目以 MIT 许可发布，以下依赖保留各自原始许可证。完整版本号见 `app/build.gradle.kts`。

| 组件 | 用途 | 许可证 |
| --- | --- | --- |
| Kotlin / kotlinx.coroutines / kotlinx.serialization | 语言与协程、JSON | Apache-2.0 |
| AndroidX Core / Activity / Lifecycle / Navigation | 基础组件与页面导航 | Apache-2.0 |
| Jetpack Compose（UI、Material 3、Icons） | 界面 | Apache-2.0 |
| Room | 本地数据库 | Apache-2.0 |
| DataStore | 设置存储 | Apache-2.0 |
| OkHttp | 网络请求 | Apache-2.0 |
| Coil | 图片加载 | Apache-2.0 |
| PDFBox-Android | PDF 文字提取 | Apache-2.0 |
| JUnit 4 | 单元测试 | Eclipse Public License 1.0 |

## 服务与内容

- 智谱开放平台（<https://bigmodel.cn>）是独立的第三方服务，使用需遵守其服务条款与计费规则；本项目与智谱无隶属关系。
- DeepSeek 开放平台（<https://platform.deepseek.com>）可由用户在设置中自行接入，属于独立的第三方服务，使用需遵守其服务条款与计费规则；本项目与 DeepSeek 无隶属关系。
- `补学习示例题库.docx` 仅用于演示导入格式，题目为示意内容，不代表任何真实试卷。分发前请替换为自己拥有版权的内容。
- 应用内生成的 B 站、百度检索链接指向第三方网站，内容与版权由对应平台负责。

## Apache-2.0 说明

Apache-2.0 组件的使用不改变本项目整体采用 MIT 许可的事实。再分发二进制时请保留各依赖自带的 NOTICE / 许可信息；Gradle 打包已排除部分重复的 META-INF 许可文件，完整文本可从各依赖仓库获取。
