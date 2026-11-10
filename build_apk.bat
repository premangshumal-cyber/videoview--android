@echo off
echo ===================================================
echo Building KinePulse Debug APK (app-debug.apk)...
echo ===================================================
if exist gradlew.bat (
    call gradlew.bat assembleDebug
) else (
    call gradle assembleDebug
)
echo.
echo Done! Your installable APK is located at:
echo app\build\outputs\apk\debug\app-debug.apk
pause
