#!/bin/bash
set -u

APP="/Applications/Kimi.app"
STATE="$HOME/Library/Application Support/MOZHE-KimiWork"
OUT="$HOME/Desktop/MOZHE_Kimi_318_Diagnose_$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

{
  echo "date=$(date)"
  sw_vers
  uname -a
  echo
  if [ -f "$APP/Contents/Info.plist" ]; then
    echo "version=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$APP/Contents/Info.plist" 2>/dev/null)"
    echo "build=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$APP/Contents/Info.plist" 2>/dev/null)"
  fi
  if [ -f "$APP/Contents/Resources/app.asar" ]; then
    shasum -a 256 "$APP/Contents/Resources/app.asar"
  fi
  echo
  echo "relay_config=$(cat "$STATE/relay-base" 2>/dev/null || true)"
  echo
  echo "port 10086:"
  lsof -nP -iTCP:10086 -sTCP:LISTEN 2>/dev/null || true
  echo
  echo "Kimi processes:"
  ps aux | grep -i '[K]imi' || true
} > "$OUT/system.txt" 2>&1

for D in "$HOME/Library/Logs/kimi-desktop" "$HOME/.kimi-work" "$HOME/.kimi-webbridge"; do
  if [ -d "$D" ]; then
    NAME="$(basename "$D")"
    mkdir -p "$OUT/$NAME"
    find "$D" -type f -mtime -2 -maxdepth 4 -print0 2>/dev/null | while IFS= read -r -d '' F; do
      cp -p "$F" "$OUT/$NAME/$(basename "$F")" 2>/dev/null || true
    done
  fi
done

[ -f "$STATE/launcher.log" ] && cp -p "$STATE/launcher.log" "$OUT/" || true
ditto -c -k --sequesterRsrc --keepParent "$OUT" "$OUT.zip"
rm -rf "$OUT"
echo "诊断包：$OUT.zip"
