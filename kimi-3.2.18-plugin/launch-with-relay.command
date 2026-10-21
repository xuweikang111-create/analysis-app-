#!/bin/bash
set -euo pipefail

APP="/Applications/Kimi.app"
STATE="$HOME/Library/Application Support/MOZHE-KimiWork"
CONF="$STATE/relay-base"
LOG="$STATE/launcher.log"

[ -d "$APP" ] || { echo "未找到 /Applications/Kimi.app"; exit 1; }

BASE="$KWORK_MODEL_RELAY_BASE"
if [ -z "$BASE" ] && [ -f "$CONF" ]; then
  BASE="$(cat "$CONF")"
fi
if [ -z "$BASE" ]; then
  echo "尚未配置 Relay。先运行 configure-relay.command。"
  exit 1
fi

if ! printf '%s' "$BASE" | grep -Eq '^http://127\.0\.0\.1:[0-9]{1,5}/v1/?$'; then
  echo "Relay 格式不合法：$BASE"
  exit 1
fi
BASE="$(printf '%s' "$BASE" | sed 's#/$##')"

PORT="$(printf '%s' "$BASE" | sed -E 's#^http://127\.0\.0\.1:([0-9]+)/v1$#\1#')"
if command -v nc >/dev/null 2>&1; then
  if ! nc -z 127.0.0.1 "$PORT" >/dev/null 2>&1; then
    echo "[MOZHE] 本地 Relay 端口 $PORT 当前未监听，拒绝启动。"
    exit 1
  fi
fi

EXE="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$APP/Contents/Info.plist")"
BIN="$APP/Contents/MacOS/$EXE"
[ -x "$BIN" ] || { echo "Kimi 主程序不可执行：$BIN"; exit 1; }

osascript -e 'tell application "Kimi" to quit' >/dev/null 2>&1 || true
sleep 2
mkdir -p "$STATE"

echo "[MOZHE] Relay: $BASE"
echo "[MOZHE] 启动 Kimi..."
KWORK_MODEL_RELAY_BASE="$BASE" nohup "$BIN" >>"$LOG" 2>&1 &
disown || true
echo "[MOZHE] 已启动。日志：$LOG"
