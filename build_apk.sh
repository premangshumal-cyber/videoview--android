#!/usr/bin/env bash
set -e
echo "Building KinePulse Debug APK..."
if [ -f "./gradlew" ]; then
    ./gradlew assembleDebug
else
    gradle assembleDebug
fi
echo "APK ready at: app/build/outputs/apk/debug/app-debug.apk"
