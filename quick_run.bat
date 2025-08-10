@echo off
echo ===================================
echo    Ranking App - Quick Run
echo ===================================
echo.

REM Set color
color 0A

REM Check if emulator is running
"C:\Users\ikizler1\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices | findstr "emulator-5554" >nul
if %errorlevel% neq 0 (
    echo [INFO] Emulator not detected. Starting emulator...
    start "Android Emulator" "C:\Users\ikizler1\AppData\Local\Android\Sdk\emulator\emulator.exe" -avd Medium_Phone_API_36
    echo [INFO] Waiting 45 seconds for emulator to boot...
    
    REM Show progress dots
    for /l %%i in (1,1,45) do (
        timeout /t 1 /nobreak >nul
        if %%i==15 echo [INFO] Still booting... ^(1/3^)
        if %%i==30 echo [INFO] Almost ready... ^(2/3^)
        if %%i==45 echo [INFO] Should be ready now! ^(3/3^)
    )
) else (
    echo [INFO] Emulator already running!
)

echo.
echo [INFO] Installing and launching app...

REM Install directly (skip build if it fails)
"C:\Users\ikizler1\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 install -r "app\build\outputs\apk\debug\app-debug.apk"
if %errorlevel% equ 0 (
    echo [SUCCESS] App installed successfully!
    echo [INFO] Launching app...
    "C:\Users\ikizler1\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 shell am start -n com.example.ranking/.MainActivity
    echo [SUCCESS] App is now running in emulator!
    echo.
    echo [TIP] Switch to emulator window to see your app
) else (
    echo [ERROR] Installation failed! 
    echo [TIP] Make sure the APK exists in app\build\outputs\apk\debug\
)

echo.
echo Press any key to exit...
pause >nul