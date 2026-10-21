#!/bin/bash
set -euo pipefail

APP="/Applications/Kimi.app"
STATE="$HOME/Library/Application Support/MOZHE-KimiWork"

[ -f "$STATE/active-backup" ] || { echo "[MOZHE] 没有找到可用备份。"; exit 1; }
BACKUP="$(cat "$STATE/active-backup")"
[ -d "$BACKUP" ] || { echo "[MOZHE] 备份目录不存在：$BACKUP"; exit 1; }

osascript -e 'tell application "Kimi" to quit' >/dev/null 2>&1 || true
sleep 2

cp -p "$BACKUP/app.asar" "$APP/Contents/Resources/app.asar"
cp -p "$BACKUP/Info.plist" "$APP/Contents/Info.plist"
cp -p "$BACKUP/Kimi" "$APP/Contents/MacOS/Kimi"
if [ -f "$BACKUP/_CodeSignature/CodeResources" ]; then
  mkdir -p "$APP/Contents/_CodeSignature"
  cp -p "$BACKUP/_CodeSignature/CodeResources" "$APP/Contents/_CodeSignature/CodeResources"
fi

rm -f "$STATE/active-backup"
echo "[MOZHE] 已恢复安装前文件。"
