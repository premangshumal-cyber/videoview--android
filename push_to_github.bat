@echo off
echo ===============================================================
echo Pushing KinePulse to https://github.com/premangshumal-cyber/videoview--android
echo ===============================================================
git init
git branch -M main
git add .
git commit -m "Initial commit: Full KinePulse Android VideoView Player + APK CI"
git remote remove origin 2>nul
git remote add origin https://github.com/premangshumal-cyber/videoview--android.git
git push -u origin main --force
echo.
echo ===============================================================
echo DONE! GitHub is now compiling your APK in the cloud (~2 mins).
echo Open this link to download your APK directly to your phone:
echo https://github.com/premangshumal-cyber/videoview--android/releases
echo (or check https://github.com/premangshumal-cyber/videoview--android/actions)
echo ===============================================================
pause
