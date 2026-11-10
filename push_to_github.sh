#!/usr/bin/env bash
set -e
echo "Pushing KinePulse to https://github.com/premangshumal-cyber/videoview--android ..."
git init
git branch -M main
git add .
git commit -m "Initial commit: Full KinePulse Android VideoView Player + APK CI"
git remote remove origin 2>/dev/null || true
git remote add origin https://github.com/premangshumal-cyber/videoview--android.git
git push -u origin main --force
echo "Done! Download your compiled APK at: https://github.com/premangshumal-cyber/videoview--android/releases"
