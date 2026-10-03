using Microsoft.Win32;
using System.Diagnostics;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace KworkMozheForce;

internal static class Program
{
    [STAThread]
    static void Main(string[] args)
    {
        Application.SetHighDpiMode(HighDpiMode.SystemAware);
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new MainForm(args.Contains("--background", StringComparer.OrdinalIgnoreCase)));
    }
}

public sealed class MainForm : Form
{
    const string AppName = "Kwork MOZHE Work Force v7";
    const string ProviderId = "mozhe-force";

    readonly string _appDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "KworkMozheForce");
    string StatePath => Path.Combine(_appDir, "state.json");
    string LogPath => Path.Combine(_appDir, "kwork-mozhe-v7.log");

    readonly TextBox _endpoint = new() { Text = "https://api.mozhe.world/v1" };
    readonly TextBox _apiKey = new() { UseSystemPasswordChar = true };
    readonly TextBox _model = new() { Text = "kimi-k3" };
    readonly Label _status = new();
    readonly Label _quotaValue = new() { Text = "未查询", AutoSize = false, TextAlign = ContentAlignment.MiddleLeft };
    readonly TextBox _log = new() { Multiline = true, ReadOnly = true, ScrollBars = ScrollBars.Vertical };
    readonly Button _enable = new() { Text = "启动 Kimi Work" };
    readonly Button _disable = new() { Text = "停止并恢复" };
    readonly Button _launch = new() { Text = "查看日志" };
    readonly Button _test = new() { Text = "测试 API 连接" };
    readonly Button _quota = new() { Text = "查询额度" };
    readonly NotifyIcon _tray = new();
    System.Threading.Timer? _watchdog;
    AppState _state = new();
    int _watchdogBusy;

    public MainForm(bool background)
    {
        Directory.CreateDirectory(_appDir);

        Text = "Kwork Quick - MOZHE";
        Width = 650;
        Height = 460;
        MinimumSize = new Size(650, 460);
        StartPosition = FormStartPosition.CenterScreen;
        Font = new Font("Microsoft YaHei UI", 9F);
        Icon = SystemIcons.Application;

        BuildUi();
        LoadState();

        _tray.Icon = SystemIcons.Shield;
        _tray.Text = "Kwork MOZHE v7";
        _tray.Visible = true;
        _tray.DoubleClick += (_, _) => ShowFromTray();

        Resize += (_, _) =>
        {
            if (WindowState == FormWindowState.Minimized)
                Hide();
        };

        FormClosing += (_, e) =>
        {
            if (e.CloseReason == CloseReason.UserClosing)
            {
                e.Cancel = true;
                Hide();
                _tray.ShowBalloonTip(1500, AppName,
                    _state.Enabled
                        ? "插件仍在后台强制接管。要恢复官方，请点击“关闭插件并恢复官方”。"
                        : "程序已缩小到托盘。",
                    ToolTipIcon.Info);
            }
        };

        if (_state.Enabled)
        {
            StartWatchdog();
            SetStatus(true, "强制接管已启用：主 Agent + 本地集群 Sub Agent 固定走 MOZHE");
        }
        else
        {
            SetStatus(false, "插件未启用，Kimi 使用原始配置");
        }

        if (background)
        {
            Shown += (_, _) => Hide();
        }
    }

    void BuildUi()
    {
        BackColor = Color.White;
        Text = "Kwork Quick - MOZHE";
        Width = 650;
        Height = 460;
        MinimumSize = new Size(650, 460);
        MaximumSize = new Size(650, 460);
        MaximizeBox = false;

        var title = new Label
        {
            Text = "Kimi Work 快捷接管",
            Font = new Font("Microsoft YaHei UI", 16F, FontStyle.Bold),
            ForeColor = Color.FromArgb(32, 32, 32),
            AutoSize = true,
            Left = 28,
            Top = 22
        };
        Controls.Add(title);

        var sub = new Label
        {
            Text = "Kimi Work → MOZHE → kimi-k3",
            Font = new Font("Microsoft YaHei UI", 9F),
            ForeColor = Color.DimGray,
            AutoSize = true,
            Left = 30,
            Top = 62
        };
        Controls.Add(sub);

        var line = new Label
        {
            BorderStyle = BorderStyle.Fixed3D,
            Left = 28,
            Top = 91,
            Width = 575,
            Height = 2
        };
        Controls.Add(line);

        AddLabel("API Key", 30, 116);
        _apiKey.SetBounds(115, 110, 487, 30);
        _apiKey.Font = new Font("Consolas", 10F);
        Controls.Add(_apiKey);

        var apiText = new Label
        {
            Text = "请求地址",
            AutoSize = true,
            Left = 30,
            Top = 160
        };
        Controls.Add(apiText);

        var apiValue = new Label
        {
            Text = "https://api.mozhe.world/v1",
            AutoSize = true,
            Left = 115,
            Top = 160,
            ForeColor = Color.FromArgb(55, 90, 160)
        };
        Controls.Add(apiValue);

        var quotaText = new Label
        {
            Text = "剩余额度",
            AutoSize = true,
            Left = 30,
            Top = 197
        };
        Controls.Add(quotaText);

        _quotaValue.SetBounds(115, 188, 350, 32);
        _quotaValue.Font = new Font("Microsoft YaHei UI", 10F, FontStyle.Bold);
        _quotaValue.ForeColor = Color.FromArgb(33, 99, 55);
        Controls.Add(_quotaValue);

        _quota.SetBounds(492, 186, 110, 34);
        Controls.Add(_quota);

        _test.SetBounds(30, 244, 130, 38);
        _enable.SetBounds(172, 244, 168, 38);
        _disable.SetBounds(352, 244, 130, 38);
        _launch.SetBounds(494, 244, 108, 38);
        Controls.AddRange(new Control[] { _test, _enable, _disable, _launch });

        var statusBox = new GroupBox
        {
            Text = "运行状态",
            Left = 30,
            Top = 300,
            Width = 572,
            Height = 86
        };
        _status.SetBounds(18, 27, 535, 44);
        _status.Font = new Font("Microsoft YaHei UI", 9.5F, FontStyle.Bold);
        statusBox.Controls.Add(_status);
        Controls.Add(statusBox);

        var foot = new Label
        {
            Text = "插件开启期间：本地 Work / 集群主 Agent / 本地 Sub Agent 强制走你的额度；只有点击“停止并恢复”才恢复官方配置。",
            Left = 30,
            Top = 401,
            Width = 572,
            Height = 34,
            ForeColor = Color.Firebrick,
            Font = new Font("Microsoft YaHei UI", 8.5F)
        };
        Controls.Add(foot);

        _log.Visible = false;
        _endpoint.Visible = false;
        _model.Visible = false;

        _enable.Click += async (_, _) => await EnableAsync();
        _disable.Click += async (_, _) => await DisableAsync();
        _launch.Click += (_, _) => OpenLog();
        _test.Click += async (_, _) => await TestEndpointAsync();
        _quota.Click += async (_, _) => await QueryQuotaAsync();
    }

    void AddLabel(string text, int x, int y)
    {
        Controls.Add(new Label { Text = text, AutoSize = true, Left = x, Top = y + 4 });
    }

    void LoadState()
    {
        try
        {
            if (File.Exists(StatePath))
            {
                _state = JsonSerializer.Deserialize<AppState>(File.ReadAllText(StatePath)) ?? new AppState();
                if (!string.IsNullOrWhiteSpace(_state.Endpoint)) _endpoint.Text = _state.Endpoint;
                if (!string.IsNullOrWhiteSpace(_state.Model)) _model.Text = _state.Model;

                if (_state.Enabled && string.IsNullOrWhiteSpace(_apiKey.Text))
                {
                    foreach (var h in _state.Homes)
                    {
                        var cfg = Path.Combine(h.Home, "config.toml");
                        var key = TryReadKey(cfg);
                        if (!string.IsNullOrWhiteSpace(key))
                        {
                            _apiKey.Text = key;
                            break;
                        }
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Log("读取状态失败: " + ex.Message);
        }
    }

    void SaveState()
    {
        try
        {
            Directory.CreateDirectory(_appDir);
            File.WriteAllText(StatePath,
                JsonSerializer.Serialize(_state, new JsonSerializerOptions { WriteIndented = true }),
                new UTF8Encoding(false));
        }
        catch (Exception ex)
        {
            Log("保存状态失败: " + ex.Message);
        }
    }

    async Task EnableAsync()
    {
        var endpoint = NormalizeEndpoint(_endpoint.Text);
        var key = _apiKey.Text.Trim();
        var model = _model.Text.Trim();

        if (!Uri.TryCreate(endpoint, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps)
        {
            MessageBox.Show("API Base URL 必须是有效的 HTTPS 地址。", AppName, MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return;
        }
        if (string.IsNullOrWhiteSpace(key))
        {
            MessageBox.Show("请输入你的 API Key。", AppName, MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return;
        }
        if (!Regex.IsMatch(model, @"^[A-Za-z0-9._:/-]{1,128}$"))
        {
            MessageBox.Show("Model ID 只允许字母、数字、点、下划线、冒号、斜杠和短横线。", AppName, MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return;
        }

        _endpoint.Text = endpoint;
        _enable.Enabled = false;
        try
        {
            await Task.Run(() =>
            {
                StopKimi();

                var homes = DetectHomes();
                if (homes.Count == 0)
                    throw new InvalidOperationException(
                        "没有找到 Kimi Work 的内嵌 kimi-code home。请先正常启动一次官方 Kimi Work 3.2.15，再回来开启接管。");

                if (!_state.Enabled)
                {
                    _state.Homes.Clear();
                    foreach (var home in homes)
                    {
                        Directory.CreateDirectory(home);
                        var cfg = Path.Combine(home, "config.toml");
                        var hs = CreateBackup(home, cfg);
                        _state.Homes.Add(hs);
                    }
                }
                else
                {
                    // 已经启用时允许更新 Key / endpoint / model，但保留第一次启用前的原始备份。
                    foreach (var home in homes)
                    {
                        if (!_state.Homes.Any(x => PathEquals(x.Home, home)))
                            _state.Homes.Add(CreateBackup(home, Path.Combine(home, "config.toml")));
                    }
                }

                foreach (var hs in _state.Homes)
                    ApplyForce(hs.Home, endpoint, key, model);

                _state.Enabled = true;
                _state.Endpoint = endpoint;
                _state.Model = model;
                SaveState();
                SetAutoStart(true);
            });

            StartWatchdog();
            SetStatus(true, "强制接管已启用，正在启动 Kimi Work…");
            Log("强制接管开启。已写入 default_model + 全模型重定向 + secondary_model.force。");
            LaunchKimi();
        }
        catch (Exception ex)
        {
            Log("开启失败: " + ex);
            MessageBox.Show(ex.Message, "开启失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
        finally
        {
            _enable.Enabled = true;
        }
    }

    async Task DisableAsync()
    {
        _disable.Enabled = false;
        try
        {
            StopWatchdog();

            await Task.Run(() =>
            {
                StopKimi();

                foreach (var hs in _state.Homes)
                {
                    try
                    {
                        var cfg = Path.Combine(hs.Home, "config.toml");
                        if (hs.HadConfig)
                        {
                            if (File.Exists(hs.BackupPath))
                                AtomicWrite(cfg, File.ReadAllText(hs.BackupPath));
                        }
                        else if (File.Exists(cfg))
                        {
                            File.Delete(cfg);
                        }
                    }
                    catch (Exception ex)
                    {
                        Log("恢复失败 " + hs.Home + ": " + ex.Message);
                    }
                }

                _state.Enabled = false;
                SaveState();
                SetAutoStart(false);
            });

            SetStatus(false, "插件已关闭，已恢复启用前的 Kimi 配置");
            Log("插件关闭并恢复官方配置。");
            LaunchKimi();
        }
        finally
        {
            _disable.Enabled = true;
        }
    }

    async Task TestEndpointAsync()
    {
        _test.Enabled = false;
        try
        {
            var endpoint = NormalizeEndpoint(_endpoint.Text);
            var key = _apiKey.Text.Trim();
            if (string.IsNullOrWhiteSpace(key))
                throw new InvalidOperationException("请先填写 API Key。");

            using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(12) };
            http.DefaultRequestHeaders.Authorization =
                new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", key);

            var url = endpoint.TrimEnd('/') + "/models";
            var resp = await http.GetAsync(url);
            var body = await resp.Content.ReadAsStringAsync();
            var preview = body.Length > 250 ? body[..250] + "…" : body;

            Log($"接口测试 GET /models -> {(int)resp.StatusCode} {resp.ReasonPhrase}");
            if (resp.IsSuccessStatusCode)
                {
                    MessageBox.Show("接口连接成功。\r\n\r\n" + preview, AppName, MessageBoxButtons.OK, MessageBoxIcon.Information);
                    await QueryQuotaAsync();
                }
            else
                MessageBox.Show($"接口返回 {(int)resp.StatusCode}。\r\n{preview}", AppName, MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
        catch (Exception ex)
        {
            Log("接口测试失败: " + ex.Message);
            MessageBox.Show(ex.Message, "接口测试失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
        finally
        {
            _test.Enabled = true;
        }
    }

    async Task QueryQuotaAsync()
    {
        _quota.Enabled = false;
        try
        {
            var key = _apiKey.Text.Trim();
            if (string.IsNullOrWhiteSpace(key))
                throw new InvalidOperationException("请先填写 API Key。");

            using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(12) };
            http.DefaultRequestHeaders.Authorization =
                new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", key);
            http.DefaultRequestHeaders.TryAddWithoutValidation("X-API-Key", key);

            var resp = await http.GetAsync("https://ai.mozhe.world/quota");
            var body = (await resp.Content.ReadAsStringAsync()).Trim();
            if (!resp.IsSuccessStatusCode)
            {
                _quotaValue.Text = $"查询失败 ({(int)resp.StatusCode})";
                _quotaValue.ForeColor = Color.Firebrick;
                Log($"额度查询失败 {(int)resp.StatusCode}: {body}");
                return;
            }

            var text = FormatQuota(body);
            _quotaValue.Text = text;
            _quotaValue.ForeColor = Color.FromArgb(33, 99, 55);
            Log("额度查询成功: " + text);
        }
        catch (Exception ex)
        {
            _quotaValue.Text = "查询失败";
            _quotaValue.ForeColor = Color.Firebrick;
            Log("额度查询失败: " + ex.Message);
        }
        finally
        {
            _quota.Enabled = true;
        }
    }

    static string FormatQuota(string body)
    {
        if (string.IsNullOrWhiteSpace(body)) return "接口返回为空";
        try
        {
            using var doc = JsonDocument.Parse(body);
            var root = doc.RootElement;
            string[] names = { "remaining", "quota", "balance", "left", "remaining_quota", "credits", "data" };
            foreach (var name in names)
            {
                if (root.ValueKind == JsonValueKind.Object && root.TryGetProperty(name, out var v))
                {
                    if (v.ValueKind == JsonValueKind.Number || v.ValueKind == JsonValueKind.String)
                        return v.ToString();
                    if (v.ValueKind == JsonValueKind.Object)
                    {
                        foreach (var inner in names)
                            if (v.TryGetProperty(inner, out var iv) &&
                                (iv.ValueKind == JsonValueKind.Number || iv.ValueKind == JsonValueKind.String))
                                return iv.ToString();
                    }
                }
            }
        }
        catch { }

        return body.Length > 80 ? body[..80] + "…" : body;
    }

    void OpenLog()
    {
        try
        {
            Directory.CreateDirectory(_appDir);
            if (!File.Exists(LogPath))
                File.WriteAllText(LogPath, "Kwork MOZHE v7 log" + Environment.NewLine, new UTF8Encoding(false));

            Process.Start(new ProcessStartInfo("notepad.exe", $"\"{LogPath}\"")
            {
                UseShellExecute = true
            });
        }
        catch (Exception ex)
        {
            MessageBox.Show(ex.Message, "打开日志失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    void StartWatchdog()
    {
        StopWatchdog();
        if (!_state.Enabled) return;

        _watchdog = new System.Threading.Timer(_ =>
        {
            if (Interlocked.Exchange(ref _watchdogBusy, 1) == 1) return;
            try
            {
                var endpoint = _state.Endpoint;
                var model = _state.Model;
                string? key = null;

                foreach (var hs in _state.Homes)
                {
                    var cfg = Path.Combine(hs.Home, "config.toml");
                    key ??= TryReadKey(cfg);
                }

                if (string.IsNullOrWhiteSpace(key))
                {
                    Ui(() => SetStatus(true, "接管开启，但未找到 MOZHE Key；请打开程序重新输入 Key 并再次开启"));
                    return;
                }

                foreach (var hs in _state.Homes)
                {
                    try
                    {
                        var cfg = Path.Combine(hs.Home, "config.toml");
                        var current = SafeRead(cfg);
                        if (!LooksForced(current, endpoint, model))
                        {
                            ApplyForce(hs.Home, endpoint, key, model);
                            Log("Watchdog 检测到配置变化，已重新强制接管: " + cfg);
                        }
                    }
                    catch (IOException)
                    {
                        // Kimi 正在短暂写入时跳过，本轮不报错；下轮重试，避免“文件被另一进程占用”的弹窗。
                    }
                    catch (UnauthorizedAccessException)
                    {
                        Ui(() => SetStatus(true, "接管开启，但配置文件权限不足，Watchdog 将继续重试"));
                    }
                    catch (Exception ex)
                    {
                        Log("Watchdog: " + ex.Message);
                    }
                }
            }
            finally
            {
                Interlocked.Exchange(ref _watchdogBusy, 0);
            }
        }, null, TimeSpan.FromSeconds(2), TimeSpan.FromSeconds(2));
    }

    void StopWatchdog()
    {
        try { _watchdog?.Dispose(); } catch { }
        _watchdog = null;
    }

    List<string> DetectHomes()
    {
        var result = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        var root = Path.Combine(appData, "kimi-desktop");

        void Add(string? p)
        {
            if (string.IsNullOrWhiteSpace(p)) return;
            try { result.Add(Path.GetFullPath(p)); } catch { }
        }

        Add(Path.Combine(root, "daimon-share", "daimon", "runtime", "kimi-code", "home"));

        var storage = Path.Combine(root, "daimon-storage.json");
        try
        {
            if (File.Exists(storage))
            {
                using var doc = JsonDocument.Parse(File.ReadAllText(storage));
                if (doc.RootElement.TryGetProperty("shareDir", out var s) && s.ValueKind == JsonValueKind.String)
                {
                    var share = s.GetString();
                    if (!string.IsNullOrWhiteSpace(share))
                        Add(Path.Combine(share, "daimon", "runtime", "kimi-code", "home"));
                }
            }
        }
        catch (Exception ex)
        {
            Log("解析 daimon-storage.json 失败: " + ex.Message);
        }

        // 仅保留已经存在或其父级 runtime 已存在的候选，避免误建完全无关目录。
        return result.Where(p =>
            Directory.Exists(p) ||
            Directory.Exists(Path.GetDirectoryName(p) ?? "")).ToList();
    }

    HomeState CreateBackup(string home, string cfg)
    {
        var had = File.Exists(cfg);
        var hash = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(home)))[..12];
        var dir = Path.Combine(_appDir, "backups");
        Directory.CreateDirectory(dir);
        var backup = Path.Combine(dir, $"{DateTime.Now:yyyyMMdd_HHmmss}_{hash}.config.toml");

        if (had)
            File.Copy(cfg, backup, true);
        else
            File.WriteAllText(backup, "# original config did not exist\r\n");

        Log("已备份: " + cfg + " -> " + backup);
        return new HomeState { Home = home, BackupPath = backup, HadConfig = had };
    }

    void ApplyForce(string home, string endpoint, string key, string model)
    {
        Directory.CreateDirectory(home);
        var cfg = Path.Combine(home, "config.toml");

        var current = File.Exists(cfg) ? SafeRead(cfg) : "";
        var forced = ForceConfig(current, endpoint, key, model);
        if (!string.Equals(current, forced, StringComparison.Ordinal))
            AtomicWrite(cfg, forced);
    }

    static string ForceConfig(string input, string endpoint, string key, string model)
    {
        input = input.Replace("\r\n", "\n").Replace("\r", "\n");

        var sections = ParseSections(input);
        var alias = $"{ProviderId}/{model}";
        var outSections = new List<ConfigSection>();

        foreach (var s in sections)
        {
            if (s.Header is null)
            {
                var root = new List<string>();
                bool hasDefault = false;

                foreach (var line in s.Lines)
                {
                    if (Regex.IsMatch(line, @"^\s*default_model\s*="))
                    {
                        root.Add($"default_model = \"{TomlEscape(alias)}\"");
                        hasDefault = true;
                    }
                    else if (Regex.IsMatch(line, @"^\s*default_provider\s*="))
                    {
                        root.Add($"default_provider = \"{ProviderId}\"");
                    }
                    else
                    {
                        root.Add(line);
                    }
                }

                if (!hasDefault)
                    root.Insert(0, $"default_model = \"{TomlEscape(alias)}\"");

                outSections.Add(new ConfigSection(null, root));
                continue;
            }

            var h = s.Header.Trim();

            if (IsSecondaryHeader(h) ||
                IsOurProviderHeader(h) ||
                IsOurModelHeader(h))
            {
                continue;
            }

            if (IsDirectModelHeader(h))
            {
                var lines = new List<string>();
                bool providerSeen = false, modelSeen = false;

                foreach (var line in s.Lines)
                {
                    if (Regex.IsMatch(line, @"^\s*provider\s*="))
                    {
                        lines.Add($"provider = \"{ProviderId}\"");
                        providerSeen = true;
                    }
                    else if (Regex.IsMatch(line, @"^\s*model\s*="))
                    {
                        lines.Add($"model = \"{TomlEscape(model)}\"");
                        modelSeen = true;
                    }
                    else
                    {
                        lines.Add(line);
                    }
                }

                if (!providerSeen) lines.Insert(0, $"provider = \"{ProviderId}\"");
                if (!modelSeen) lines.Insert(providerSeen ? 1 : 0, $"model = \"{TomlEscape(model)}\"");
                outSections.Add(new ConfigSection(s.Header, lines));
            }
            else
            {
                outSections.Add(s);
            }
        }

        outSections.Add(new ConfigSection($"[providers.\"{ProviderId}\"]", new List<string>
        {
            "type = \"openai\"",
            $"base_url = \"{TomlEscape(endpoint)}\"",
            $"api_key = \"{TomlEscape(key)}\""
        }));

        outSections.Add(new ConfigSection($"[models.\"{TomlEscape(alias)}\"]", new List<string>
        {
            $"provider = \"{ProviderId}\"",
            $"model = \"{TomlEscape(model)}\"",
            "max_context_size = 262144",
            "capabilities = [ \"thinking\", \"image_in\", \"tool_use\" ]"
        }));

        outSections.Add(new ConfigSection("[secondary_model]", new List<string>
        {
            $"default_model = \"{TomlEscape(alias)}\"",
            "force = true"
        }));

        var sb = new StringBuilder();
        foreach (var s in outSections)
        {
            if (s.Header is not null)
            {
                if (sb.Length > 0 && !sb.ToString().EndsWith("\n\n", StringComparison.Ordinal))
                    sb.Append('\n');
                sb.AppendLine(s.Header);
            }

            foreach (var line in s.Lines)
                sb.AppendLine(line);

            if (s.Header is not null)
                sb.AppendLine();
        }

        return sb.ToString().Replace("\n", Environment.NewLine);
    }

    static List<ConfigSection> ParseSections(string text)
    {
        var sections = new List<ConfigSection>();
        var current = new ConfigSection(null, new List<string>());
        sections.Add(current);

        foreach (var raw in text.Split('\n'))
        {
            var line = raw.TrimEnd('\r');
            if (Regex.IsMatch(line, @"^\s*\[[^\[].*\]\s*$") && !Regex.IsMatch(line, @"^\s*\[\["))
            {
                current = new ConfigSection(line.Trim(), new List<string>());
                sections.Add(current);
            }
            else if (Regex.IsMatch(line, @"^\s*\[\[.*\]\]\s*$"))
            {
                current = new ConfigSection(line.Trim(), new List<string>());
                sections.Add(current);
            }
            else
            {
                current.Lines.Add(line);
            }
        }
        return sections;
    }

    static bool IsSecondaryHeader(string h) =>
        Regex.IsMatch(h, @"^\[secondary_model(?:\.|\])");

    static bool IsOurProviderHeader(string h) =>
        Regex.IsMatch(h, @"^\[providers\.(?:\""mozhe-force\""|mozhe-force)(?:\.|\])");

    static bool IsOurModelHeader(string h) =>
        h.StartsWith("[models.\"mozhe-force/", StringComparison.OrdinalIgnoreCase);

    static bool IsDirectModelHeader(string h) =>
        Regex.IsMatch(h, @"^\[models\.(?:\""[^\""]+\""|[A-Za-z0-9_-]+)\]$");

    static bool LooksForced(string config, string endpoint, string model)
    {
        var alias = $"{ProviderId}/{model}";
        return config.Contains($"default_model = \"{alias}\"", StringComparison.Ordinal) &&
               config.Contains($"[providers.\"{ProviderId}\"]", StringComparison.Ordinal) &&
               config.Contains($"base_url = \"{endpoint}\"", StringComparison.Ordinal) &&
               config.Contains("[secondary_model]", StringComparison.Ordinal) &&
               Regex.IsMatch(config, @"(?m)^\s*force\s*=\s*true\s*$");
    }

    static string TryReadKey(string cfg)
    {
        try
        {
            if (!File.Exists(cfg)) return "";
            var text = SafeRead(cfg);
            var m = Regex.Match(text,
                @"(?ms)^\s*\[providers\.\""mozhe-force\""\]\s*(.*?)(?=^\s*\[|\z)");
            if (!m.Success) return "";

            var k = Regex.Match(m.Groups[1].Value, @"(?m)^\s*api_key\s*=\s*\""((?:\\.|[^\""])*)\""\s*$");
            return k.Success ? TomlUnescape(k.Groups[1].Value) : "";
        }
        catch { return ""; }
    }

    static string SafeRead(string path)
    {
        for (int i = 0; i < 4; i++)
        {
            try
            {
                using var fs = new FileStream(path, FileMode.Open, FileAccess.Read,
                    FileShare.ReadWrite | FileShare.Delete);
                using var sr = new StreamReader(fs, Encoding.UTF8, true);
                return sr.ReadToEnd();
            }
            catch (IOException) when (i < 3)
            {
                Thread.Sleep(120);
            }
        }
        return File.ReadAllText(path);
    }

    static void AtomicWrite(string path, string content)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var temp = path + ".kwork-v7.tmp";

        for (int i = 0; i < 6; i++)
        {
            try
            {
                File.WriteAllText(temp, content, new UTF8Encoding(false));
                File.Move(temp, path, true);
                return;
            }
            catch (IOException) when (i < 5)
            {
                try { if (File.Exists(temp)) File.Delete(temp); } catch { }
                Thread.Sleep(200 + i * 150);
            }
        }

        File.WriteAllText(temp, content, new UTF8Encoding(false));
        File.Move(temp, path, true);
    }

    void StopKimi()
    {
        foreach (var name in new[] { "Kimi" })
        {
            foreach (var p in Process.GetProcessesByName(name))
            {
                try
                {
                    Log($"关闭 Kimi PID={p.Id}");
                    p.Kill(true);
                    p.WaitForExit(3500);
                }
                catch (Exception ex)
                {
                    Log("关闭 Kimi 失败: " + ex.Message);
                }
                finally
                {
                    p.Dispose();
                }
            }
        }
        Thread.Sleep(700);
    }

    void LaunchKimi()
    {
        try
        {
            var exe = FindKimiExe();
            if (exe is null)
            {
                MessageBox.Show("没有自动找到 Kimi.exe。请手动启动官方 Kimi Work；强制配置仍会继续生效。",
                    AppName, MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            Process.Start(new ProcessStartInfo(exe) { UseShellExecute = true });
            Log("已启动: " + exe);
        }
        catch (Exception ex)
        {
            Log("启动 Kimi 失败: " + ex.Message);
            MessageBox.Show(ex.Message, "启动失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    string? FindKimiExe()
    {
        var local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        var pf = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        var pf86 = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);

        var candidates = new[]
        {
            Path.Combine(local, "Programs", "Kimi", "Kimi.exe"),
            Path.Combine(local, "Kimi", "Kimi.exe"),
            Path.Combine(local, "Programs", "kimi-desktop", "Kimi.exe"),
            Path.Combine(pf, "Kimi", "Kimi.exe"),
            Path.Combine(pf86, "Kimi", "Kimi.exe")
        };

        foreach (var c in candidates)
            if (File.Exists(c)) return c;

        try
        {
            using var k = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\App Paths\Kimi.exe");
            var v = k?.GetValue(null)?.ToString();
            if (!string.IsNullOrWhiteSpace(v) && File.Exists(v)) return v;
        }
        catch { }

        try
        {
            var programs = Path.Combine(local, "Programs");
            if (Directory.Exists(programs))
            {
                var hit = Directory.EnumerateFiles(programs, "Kimi.exe", SearchOption.AllDirectories)
                    .FirstOrDefault();
                if (hit is not null) return hit;
            }
        }
        catch { }

        return null;
    }

    void SetAutoStart(bool enabled)
    {
        try
        {
            using var key = Registry.CurrentUser.CreateSubKey(
                @"Software\Microsoft\Windows\CurrentVersion\Run", true);
            if (enabled)
            {
                var exe = Environment.ProcessPath ?? Application.ExecutablePath;
                key.SetValue("KworkMozheForceV7", $"\"{exe}\" --background");
            }
            else
            {
                key.DeleteValue("KworkMozheForceV7", false);
            }
        }
        catch (Exception ex)
        {
            Log("设置自启动失败: " + ex.Message);
        }
    }

    void SetStatus(bool enabled, string text)
    {
        Ui(() =>
        {
            _status.Text = (enabled ? "● " : "○ ") + text;
            _status.ForeColor = enabled ? Color.DarkGreen : Color.DimGray;
        });
    }

    void ShowFromTray()
    {
        Show();
        WindowState = FormWindowState.Normal;
        Activate();
    }

    void Log(string message)
    {
        var line = $"[{DateTime.Now:HH:mm:ss}] {message}";
        try
        {
            Directory.CreateDirectory(_appDir);
            File.AppendAllText(LogPath, line + Environment.NewLine, new UTF8Encoding(false));
        }
        catch { }

        Ui(() =>
        {
            _log.AppendText(line + Environment.NewLine);
            _log.SelectionStart = _log.TextLength;
            _log.ScrollToCaret();
        });
    }

    void Ui(Action action)
    {
        if (IsDisposed) return;
        if (InvokeRequired)
        {
            try { BeginInvoke(action); } catch { }
        }
        else
        {
            action();
        }
    }

    static string NormalizeEndpoint(string s) => s.Trim().TrimEnd('/');

    static string TomlEscape(string s) => s
        .Replace("\\", "\\\\")
        .Replace("\"", "\\\"")
        .Replace("\r", "\\r")
        .Replace("\n", "\\n");

    static string TomlUnescape(string s)
    {
        return s.Replace("\\n", "\n")
                .Replace("\\r", "\r")
                .Replace("\\\"", "\"")
                .Replace("\\\\", "\\");
    }

    static bool PathEquals(string a, string b)
    {
        try
        {
            return string.Equals(Path.GetFullPath(a).TrimEnd('\\'),
                                 Path.GetFullPath(b).TrimEnd('\\'),
                                 StringComparison.OrdinalIgnoreCase);
        }
        catch
        {
            return string.Equals(a, b, StringComparison.OrdinalIgnoreCase);
        }
    }
}

public sealed class AppState
{
    public bool Enabled { get; set; }
    public string Endpoint { get; set; } = "https://api.mozhe.world/v1";
    public string Model { get; set; } = "kimi-k3";
    public List<HomeState> Homes { get; set; } = new();
}

public sealed class HomeState
{
    public string Home { get; set; } = "";
    public string BackupPath { get; set; } = "";
    public bool HadConfig { get; set; }
}

public sealed record ConfigSection(string? Header, List<string> Lines);
