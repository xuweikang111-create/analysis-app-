#!/bin/bash
set -euo pipefail

STATE="$HOME/Library/Application Support/MOZHE-KimiWork"
mkdir -p "$STATE"

CURRENT=""
[ -f "$STATE/relay-base" ] && CURRENT="$(cat "$STATE/relay-base")"

echo "MOZHE Kimi Work 3.2.18 Relay 配置"
[ -n "$CURRENT" ] && echo "当前：$CURRENT"
printf "请输入本地 Relay，例如 http://127.0.0.1:8317/v1 : "
IFS= read -r BASE

if ! printf '%s' "$BASE" | grep -Eq '^http://127\.0\.0\.1:[0-9]{1,5}/v1/?$'; then
  echo "格式错误。只接受本机 127.0.0.1 Relay。"
  exit 1
fi

PORT="$(printf '%s' "$BASE" | sed -E 's#^http://127\.0\.0\.1:([0-9]+)/v1/?$#\1#')"
if [ "$PORT" -lt 1 ] || [ "$PORT" -gt 65535 ]; then
  echo "端口范围错误。"
  exit 1
fi

BASE="$(printf '%s' "$BASE" | sed 's#/$##')"
printf '%s\n' "$BASE" > "$STATE/relay-base"
chmod 600 "$STATE/relay-base"
echo "[MOZHE] 已保存：$BASE"
