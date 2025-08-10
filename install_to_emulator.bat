@echo off
echo Installing app to emulator...

REM Check if emulator is running
"C:\Users\ikizler1\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices | findstr "emulator-5554" >nul
if %errorlevel% neq 0 (
    echo Emulator not found! Starting emulator...
    start "Android Emulator" "C:\Users\ikizler1\AppData\Local\Android\Sdk\emulator\emulator.exe" -avd Medium_Phone_API_36
    echo Waiting for emulator to boot...
    timeout /t 45 /nobreak >nul
)

REM Try to build first
echo Building app...
gradlew.bat assembleDebug
if %errorlevel% equ 0 (
    echo Build successful! Installing to emulator...
) else (
    echo Build failed! Using existing APK...
)

REM Install the APK
"C:\Users\ikizler1\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 install -r "app\build\outputs\apk\debug\app-debug.apk"
if %errorlevel% equ 0 (
    echo Installation successful!
    echo Starting app...
    "C:\Users\ikizler1\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 shell am start -n com.example.ranking/.MainActivity
    echo Done! App is running in emulator.
) else (
    echo Installation failed!
)

echo.
echo Press any key to exit...
pause >nul