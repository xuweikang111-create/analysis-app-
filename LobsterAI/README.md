# 米奇 v1.2.0

原生 Android AI 伙伴客户端。Kotlin + Jetpack Compose + MVVM/Clean Architecture。

## v1.2.0

- APP 名称改为 **米奇**。
- Launcher 图标直接使用用户提供的原始乌鸦图片文件。
- 视觉默认开启：聊天支持选择图片，并按 OpenAI Compatible / Claude / Gemini 多模态协议发送。
- 新增思考开关：开启后向模型加入深度检查与推理摘要要求，不展示隐藏思维链。
- 模型自动发现：填写 API Base URL + API Key 后自动请求模型列表，也支持手动 Model Name。
- 自动智识库默认开启：每轮聊天完成后自动沉淀本地长期记忆。
- 后续聊天会从 MEMORY / NOTE / WEB 中做轻量相关性检索，并把相关记忆注入当前上下文。
- 保留多会话、流式输出、停止、重新生成、编辑、删除、复制、聊天导入导出、网页抓取与 AI 总结。
- API Key 继续使用 Android Keystore AES-GCM 本地加密保存。

## 构建环境

- JDK 17
- Gradle 8.13
- Android Gradle Plugin 8.13.2
- Kotlin 2.3.21
- Hilt 2.58
- compileSdk / targetSdk 36
- minSdk 26

GitHub Actions 会执行 `gradle :app:assembleDebug` 并上传 APK artifact。
