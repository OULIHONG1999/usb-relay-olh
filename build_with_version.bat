@echo off
REM ========================================
REM USB Relay - Auto Version and Build
REM ========================================
echo.
echo ========================================
echo USB Relay - 自动版本更新和构建
echo ========================================
echo.

cd relay-host\USBRelay

echo [步骤 1/3] 更新版本号...
powershell -ExecutionPolicy Bypass -File .\update_version.ps1 patch
if errorlevel 1 (
    echo 版本更新失败！
    pause
    exit /b 1
)
echo.

echo [步骤 2/3] 清理旧的构建文件...
call gradlew.bat clean
if errorlevel 1 (
    echo 清理失败！
    pause
    exit /b 1
)
echo.

echo [步骤 3/3] 构建Android应用...
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo 构建失败！
    pause
    exit /b 1
)
echo.

echo ========================================
echo 构建成功！
echo ========================================
echo.

REM Get version from version.properties
for /f "tokens=2 delims==" %%a in ('findstr "VERSION_MAJOR" version.properties') do set MAJOR=%%a
for /f "tokens=2 delims==" %%a in ('findstr "VERSION_MINOR" version.properties') do set MINOR=%%a
for /f "tokens=2 delims==" %%a in ('findstr "VERSION_PATCH" version.properties') do set PATCH=%%a

echo APK位置: app\build\outputs\apk\debug\app-debug.apk
echo 版本号: %MAJOR%.%MINOR%.%PATCH%
echo.

echo 是否安装到设备？(Y/N)
set /p INSTALL_CHOICE=
if /i "%INSTALL_CHOICE%"=="Y" (
    echo.
    echo 正在安装...
    adb install -r app\build\outputs\apk\debug\app-debug.apk
    if errorlevel 1 (
        echo 安装失败！请确保设备已连接。
    ) else (
        echo 安装成功！
    )
)

echo.
pause
