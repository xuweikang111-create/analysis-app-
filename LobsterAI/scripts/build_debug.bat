@echo off
cd /d %~dp0\..
call gradlew.bat :app:assembleDebug --stacktrace
if errorlevel 1 exit /b %errorlevel%
echo APK: app\build\outputs\apk\debug\app-debug.apk