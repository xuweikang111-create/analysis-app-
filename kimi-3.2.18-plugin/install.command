#!/bin/bash
set -euo pipefail

APP="/Applications/Kimi.app"
STATE="$HOME/Library/Application Support/MOZHE-KimiWork"
HERE="$(cd "$(dirname "$0")" && pwd)"
PAYLOAD="$HERE/payload"

EXPECTED_VERSION="3.2.18"
EXPECTED_BUILD="3.2.18.6"
OFFICIAL_ASAR_SHA="6eb027db9d23b1c7f5246c1cc8f15bd92340febb422bc09e5d7309449469d6d0"
PATCHED_ASAR_SHA="__PATCHED_ASAR_SHA__"

fail() { echo "[MOZHE] $*" >&2; exit 1; }

[ -d "$APP" ] || fail "未找到 /Applications/Kimi.app"
[ -f "$APP/Contents/Resources/app.asar" ] || fail "未找到 app.asar"

VER="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$APP/Contents/Info.plist" 2>/dev/null || true)"
BUILD="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$APP/Contents/Info.plist" 2>/dev/null || true)"
[ "$VER" = "$EXPECTED_VERSION" ] || fail "版本不匹配：检测到 $VER，需要 $EXPECTED_VERSION"
[ "$BUILD" = "$EXPECTED_BUILD" ] || fail "Build 不匹配：检测到 $BUILD，需要 $EXPECTED_BUILD"

CURRENT_SHA="$(shasum -a 256 "$APP/Contents/Resources/app.asar" | awk '{print $1}')"
if [ "$CURRENT_SHA" = "$PATCHED_ASAR_SHA" ]; then
  echo "[MOZHE] 已安装 3.2.18 兼容补丁。"
  exit 0
fi
[ "$CURRENT_SHA" = "$OFFICIAL_ASAR_SHA" ] || fail "app.asar 不是官方 3.2.18 原版，拒绝覆盖：$CURRENT_SHA"

mkdir -p "$STATE"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$STATE/backup-$STAMP"
mkdir -p "$BACKUP"

echo "[MOZHE] 正在备份..."
cp -p "$APP/Contents/Resources/app.asar" "$BACKUP/app.asar"
cp -p "$APP/Contents/Info.plist" "$BACKUP/Info.plist"
cp -p "$APP/Contents/MacOS/Kimi" "$BACKUP/Kimi"
if [ -f "$APP/Contents/_CodeSignature/CodeResources" ]; then
  mkdir -p "$BACKUP/_CodeSignature"
  cp -p "$APP/Contents/_CodeSignature/CodeResources" "$BACKUP/_CodeSignature/CodeResources"
fi
printf '%s\n' "$BACKUP" > "$STATE/active-backup"

rollback() {
  echo "[MOZHE] 安装失败，正在回滚..."
  cp -p "$BACKUP/app.asar" "$APP/Contents/Resources/app.asar" || true
  cp -p "$BACKUP/Info.plist" "$APP/Contents/Info.plist" || true
  cp -p "$BACKUP/Kimi" "$APP/Contents/MacOS/Kimi" || true
  if [ -f "$BACKUP/_CodeSignature/CodeResources" ]; then
    mkdir -p "$APP/Contents/_CodeSignature"
    cp -p "$BACKUP/_CodeSignature/CodeResources" "$APP/Contents/_CodeSignature/CodeResources" || true
  fi
}
trap rollback ERR

osascript -e 'tell application "Kimi" to quit' >/dev/null 2>&1 || true
sleep 2

cp -p "$PAYLOAD/app.asar" "$APP/Contents/Resources/app.asar"
cp -p "$PAYLOAD/Info.plist" "$APP/Contents/Info.plist"

NEW_SHA="$(shasum -a 256 "$APP/Contents/Resources/app.asar" | awk '{print $1}')"
[ "$NEW_SHA" = "$PATCHED_ASAR_SHA" ] || fail "补丁文件写入校验失败"

xattr -dr com.apple.quarantine "$APP" >/dev/null 2>&1 || true

echo "[MOZHE] 正在重新签名 Kimi.app..."
codesign --force --sign - --preserve-metadata=identifier,entitlements,requirements,flags "$APP"
codesign --verify --deep --strict "$APP"

trap - ERR
echo
echo "[MOZHE] 安装完成。"
echo "[MOZHE] Relay 格式：http://127.0.0.1:<端口>/v1"
echo "[MOZHE] Relay 模式采用 fail-closed，不自动回退官方地址。"
echo "请运行 configure-relay.command，然后使用 launch-with-relay.command 启动 Kimi。"
