# Delivery Report — 养龙虾 AI v1.1.1

## 本轮修复

- 版本升级：`versionCode = 3`，`versionName = 1.1.1`。
- 修正启动 Manifest：Launcher Activity、`exported=true`、硬件加速、软键盘 resize 与 Android 13+ 返回回调配置。
- 重做 Launcher 图标资源：浅灰底 + 黑色乌鸦剪影，覆盖 legacy/adaptive/round icon 引用。
- 启动主题补齐 `windowBackground`，避免启动阶段黑屏/空白预览。
- `gradle-wrapper.properties` 固定 Gradle 8.13 与官方 distribution SHA-256；项目当前 `gradle-wrapper.jar` 仍是早期 bootstrap，因此正式 CI 不依赖该 JAR，而由 `gradle/actions/setup-gradle` 直接安装固定版本。
- GitHub Actions 固定 JDK 17、Android SDK 36、Gradle 8.13；执行 `assembleDebug` 与 `lintDebug`，成功后上传 APK Artifact。
- 增加 Windows/Linux 本地构建脚本和工程自检脚本。

## 已实现的主要功能

- Jetpack Compose + Material 3 手机端 UI
- 首页 AI 伙伴/龙虾养成状态
- 多龙虾、多会话、本地上下文
- Room + DataStore 本地持久化
- OpenAI Compatible API + SSE 流式聊天
- Claude/Gemini 原生适配入口
- 自定义 Base URL / API Key / Model / Header / System Prompt
- Android Keystore AES-GCM 加密 API Key
- 消息停止、重生成、编辑、删除、复制、导入导出
- 微信/QQ/浏览器系统分享链接接收
- URL 抓取、正文清洗、长文分段、AI 摘要
- 本地知识库与 VectorStore/RAG 预留接口

## 当前静态自检结果

- Kotlin 源文件：31
- Kotlin 代码：约 4,000 行
- XML/TOML 解析错误：0
- `scripts/verify_project.py`：PASS
- `git diff --check`：PASS
- TODO/FIXME/NotImplementedError/“伪代码/自行实现”占位：0
- Manifest Launcher / 图标 / 版本号检查：PASS

## 构建验证状态

当前执行容器只有 JDK，没有 Android SDK/Gradle 安装；尝试运行项目 Gradle Wrapper 时，容器 DNS 无法访问 Gradle/Google Android 依赖服务器，因此本环境无法诚实完成 `assembleDebug`。

仓库中的 GitHub Actions 已配置为真正执行：

```bash
gradle :app:assembleDebug --stacktrace --no-daemon
gradle :app:lintDebug --stacktrace --no-daemon
```

成功后的 APK 为：

```text
app/build/outputs/apk/debug/app-debug.apk
```

如果工程放在已有仓库的 `LobsterAI/` 子目录，则路径为：

```text
LobsterAI/app/build/outputs/apk/debug/app-debug.apk
```

Artifact 名称：`LobsterAI-v1.1.1-debug`。

## GitHub 发布状态

GitHub 账号：`xuweikang111-create`

目标仓库：`xuweikang111-create/analysis-app-`

计划：不覆盖现有 `main` 项目，创建 `lobster-ai-v2` 分支，并把 Android 项目放入 `LobsterAI/` 子目录。

已实际尝试：

1. 创建 `lobster-ai-v2` 分支 → 403 `Resource not accessible by integration`
2. 在 `main` 创建 `LobsterAI/PUBLISH_STATUS.md` → 403 `Resource not accessible by integration`

仓库元数据确认当前 GitHub 用户本人对仓库拥有 admin/push 权限，但 ChatGPT GitHub App 的 installation 列表为空，因此连接器只能读公开仓库，不能执行写入。需要给 GitHub App 授权 `xuweikang111-create/analysis-app-` 后才能继续直接发布。

## 关于之前的测试 APK

`LobsterAI_Test_v1.1.0.apk` 是一个极简 WebView 安装壳，不是本原生 Compose 工程的真实编译产物，已判定废弃，不应继续安装或分发。