MOZHE Kimi Work macOS Apple Silicon 3.2.18 兼容插件 v1.0.13

目标版本
- Kimi: 3.2.18
- Build: 3.2.18.6
- Electron: 43.7.3
- 官方 app.asar SHA256:
  6eb027db9d23b1c7f5246c1cc8f15bd92340febb422bc09e5d7309449469d6d0

用途
- 为 Kimi Work 3.2.18 增加本地 Relay 路由兼容。
- Relay 只允许 127.0.0.1，格式：http://127.0.0.1:<端口>/v1
- KWORK_MODEL_RELAY_BASE 已设置时，Work Gateway 与 Daimon 配置写入均使用本地 Relay。
- Relay 已配置但地址错误或端口未监听时，启动脚本拒绝启动，避免意外走其它路由。
- 不修改 Kimi 登录凭据，不绕过账号认证或计费机制。
- 不删除 Daimon、Gateway、WebBridge / Kimi 浏览器扩展资源。

安装
1. 官方 Kimi 3.2.18 安装在 /Applications/Kimi.app。
2. 双击 install.command。
3. 双击 configure-relay.command 配置本地 Relay。
4. 使用 launch-with-relay.command 启动 Kimi。

回滚
- 双击 uninstall.command。

问题收集
- 双击 diagnose.command，在桌面生成 MOZHE_Kimi_318_Diagnose_*.zip。

重要
- 严格校验官方 3.2.18 app.asar。未知或二次修改版本会拒绝安装。
- 只有明确设置本地 Relay 时补丁才生效。未设置时保留 Kimi 原行为。
- 本地 Relay 模式采用 fail-closed，不自动回退官方地址。
- macOS 可能因应用重新签名而再次请求部分系统权限。
