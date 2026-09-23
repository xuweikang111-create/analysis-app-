# 养龙虾 AI v1.1.1

Android 原生 AI 伙伴应用。交互方向接近 Claw 类 AI 客户端，但保留独立的龙虾养成、知识库和网页总结能力。

## 已实现

- Kotlin + Jetpack Compose + Material 3
- MVVM + Clean Architecture
- 多龙虾 / 多角色、等级、经验、心情、饱食度、亲密度
- 每日签到、喂食、互动、聊天任务
- 多会话聊天、上下文、SSE 流式输出、停止、重新生成
- 消息编辑 / 删除 / 长按复制
- 会话 JSON 导入导出、Token 近似统计、模型切换
- OpenAI Compatible、Anthropic Claude、Gemini 原生适配
- DeepSeek / Kimi / Qwen / GLM 等 OpenAI 兼容服务预设
- 自定义 Base URL / API Key / Model / Header / System Prompt
- API Key 使用 Android Keystore AES-GCM 加密保存
- Room + DataStore 本地持久化
- 微信 / QQ / 浏览器 Android Intent 分享接收
- 剪贴板 URL 识别（可关闭）
- 网页抓取、正文清洗、超长分段、AI 总结、知识库保存
- 深色模式
- v1.1.1：浅灰底黑色乌鸦 Launcher 图标

## Android / Build

- minSdk 26（Android 8.0）
- targetSdk / compileSdk 36
- AGP 8.13.2
- Gradle 8.13
- Kotlin 2.3.21
- Room 2.8.5
- KSP 2.3.12
- Hilt 2.57.2（kapt）

构建产物：`app/build/outputs/apk/debug/app-debug.apk`。GitHub Actions 已包含 Android SDK、Gradle、assembleDebug、lintDebug 与 APK artifact 上传步骤。

> 工程不包含任何 API Key。用户首次使用时在“设置”中自行添加模型配置。