# GitHub 发布说明 — v1.1.1

目标仓库：`xuweikang111-create/analysis-app-`

推荐发布结构：

```text
analysis-app-/
├── 现有 main 内容（保持不动）
├── LobsterAI/                 # 本 Android 工程
└── .github/workflows/
    └── lobster-ai-android.yml # github-repo-root 中已准备好的工作流
```

推荐分支：`lobster-ai-v2`。

## 当前阻塞

GitHub 连接读取正常，但所有写入调用均返回：

```text
403 Resource not accessible by integration
```

并且当前 GitHub App installation 列表为空。这意味着账号连接存在，但 App 尚未获得目标仓库写权限。

给 GitHub App 授权 `xuweikang111-create/analysis-app-` 后，可以直接继续执行：

1. 创建 `lobster-ai-v2` 分支；
2. 上传 `LobsterAI/` 全部源码；
3. 上传 `.github/workflows/lobster-ai-android.yml`；
4. 触发 GitHub Actions；
5. 下载 `LobsterAI-v1.1.1-debug` Artifact 中的 APK。