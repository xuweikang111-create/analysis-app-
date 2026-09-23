# 养龙虾 AI v1.1.0

Android 原生 Kotlin + Jetpack Compose AI 伙伴应用。v1.1.0 以“接近 Kimi Claw 的简洁使用体验 + 能直接构建安装”为优先目标，在保留龙虾养成特色的同时收敛首页和聊天页。

## v1.1.0 重点

- 首页改成 AI 伙伴控制台：角色切换、状态、今日互动、一键开始聊天。
- 聊天页收敛顶部按钮，使用稳定的标准 `DropdownMenu`，降低 Material 3 实验 API 兼容风险。
- 未配置模型时，聊天页直接引导到设置。
- 保留多会话、流式输出、停止、重新生成、编辑、删除、复制、导入/导出、Token 近似统计。
- 修复知识库 AI 总结/问答取消任务时被误报为失败的问题。
- 修复 Gemini 自定义 Base URL 在 `/v1` 场景下错误拼接 `/v1/v1beta` 的问题。
- 新增正式 adaptive launcher icon。
- GitHub Actions 不再依赖项目自定义 Wrapper：Runner 安装 Gradle 8.13 + Android SDK 36 后直接构建 APK。

## 核心能力

- 龙虾养成：等级、经验、心情、饱食度、亲密度、多角色、自定义 Prompt、每日任务。
- 模型接入：自定义 Base URL / API Key / Model Name / Header / System Prompt / Temperature。
- 协议：OpenAI Compatible SSE、Claude 原生 SSE、Gemini 原生 SSE。
- 预设：OpenAI / Claude / Gemini / DeepSeek / Kimi / Qwen / GLM。
- 本地数据：Room + DataStore。
- 安全：API Key 使用 Android Keystore AES-GCM，本地密文不参与云备份。
- 分享：支持 Android `ACTION_SEND`，可接收微信/QQ/浏览器系统分享文本和网页链接。
- 知识库：网页、笔记、文本文件、聊天记录；保留 `VectorStore` 扩展接口用于后续 RAG。

## 构建环境

- JDK 17
- Gradle 8.13
- Android Gradle Plugin 8.13.2
- Kotlin 2.3.21
- KSP 2.3.12
- compileSdk / targetSdk 36
- minSdk 26（Android 8.0+）

## 构建 APK

Android Studio 打开项目后使用 JDK 17 / SDK 36，执行：

```bash
./gradlew :app:assembleDebug
```

Debug APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

仓库 CI 使用 `.github/workflows/android.yml`，会自动安装 Gradle 8.13 和 Android SDK 36 后构建并上传 APK artifact。
