# 养龙虾 AI v1.1.1

这是一次“可安装优先”的修复版本源码交付，重点解决上一轮测试壳无法正常使用、图标不正确和正式工程构建链不清晰的问题。

## 修复与改进

- 正式原生 Compose 工程版本升级到 `1.1.1`（versionCode 3）。
- Launcher Activity 与 Android 12+ exported 配置复核。
- 启动主题、硬件加速、软键盘 resize、Android 13+ Back 回调配置补齐。
- Launcher 图标替换为浅灰底黑色乌鸦方向，补齐 legacy/adaptive/round icon。
- GitHub Actions 固定 JDK 17 + Android SDK 36 + Gradle 8.13。
- CI 同时执行 `assembleDebug` 和 `lintDebug`，成功后上传 APK Artifact。
- 增加项目自检脚本和 Windows/Linux 构建脚本。
- 保留原有 AI 伙伴、养成、聊天、模型配置、系统分享、网页摘要、知识库和本地安全存储功能。

## 重要说明

此前的 `LobsterAI_Test_v1.1.0.apk` 只是极简 WebView 安装测试壳，并非该原生 Android 工程的编译产物，现已废弃。

本执行容器缺少 Android SDK 且无法联网拉取 Gradle/Google Android 依赖，因此这里不能冒充已经成功执行 `assembleDebug`。仓库中的 CI 已准备好在具有 Android SDK 的 GitHub Runner 上真实构建 APK。