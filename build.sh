#!/bin/bash
set -e

PROJECT_DIR="/Users/saurabh/.gemini/antigravity/scratch/spendwise-android"
cd "$PROJECT_DIR"

SDK_PATH="/Users/saurabh/Library/Android/sdk"
BUILD_TOOLS="$SDK_PATH/build-tools/36.1.0"
PLATFORM="$SDK_PATH/platforms/android-36.1/android.jar"
JAVAC="/opt/homebrew/opt/openjdk@17/bin/javac"
KEYTOOL="/opt/homebrew/opt/openjdk@17/bin/keytool"

echo "=== 1. Cleaning build directories ==="
rm -rf build gen bin compiled_res.zip initial.apk SpendWise-unaligned.apk SpendWise.apk
mkdir -p build gen bin/dex

echo "=== 2. Compiling Android Resources (aapt2 compile) ==="
"$BUILD_TOOLS/aapt2" compile --dir res -o compiled_res.zip

echo "=== 3. Linking Resources and generating R.java (aapt2 link) ==="
"$BUILD_TOOLS/aapt2" link \
    --manifest AndroidManifest.xml \
    -I "$PLATFORM" \
    --java gen \
    -o initial.apk \
    compiled_res.zip

echo "=== 4. Compiling Java Source Files (javac) ==="
"$JAVAC" -source 17 -target 17 \
    -cp "$PLATFORM" \
    -d bin \
    gen/com/spendwise/app/R.java \
    src/com/spendwise/app/*.java

echo "=== 5. Generating Dalvik Bytecode classes.dex (d8) ==="
"$BUILD_TOOLS/d8" \
    --lib "$PLATFORM" \
    --output bin/dex \
    bin/com/spendwise/app/*.class

echo "=== 6. Packaging classes.dex into APK ==="
zip -j -u initial.apk bin/dex/classes.dex

echo "=== 7. Zipaligning APK ==="
"$BUILD_TOOLS/zipalign" -f -p 4 initial.apk SpendWise-unaligned.apk

echo "=== 8. Creating Debug Keystore if needed ==="
if [ ! -f debug.keystore ]; then
    "$KEYTOOL" -genkeypair -v \
        -keystore debug.keystore \
        -storepass android \
        -alias androiddebugkey \
        -keypass android \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi

echo "=== 9. Signing APK with apksigner ==="
"$BUILD_TOOLS/apksigner" sign \
    --ks debug.keystore \
    --ks-pass pass:android \
    --ks-key-alias androiddebugkey \
    --key-pass pass:android \
    --out SpendWise.apk \
    SpendWise-unaligned.apk

echo "=== 10. Verifying APK Signature ==="
"$BUILD_TOOLS/apksigner" verify SpendWise.apk

echo "============================================="
echo "🎉 SUCCESS! SpendWise.apk generated successfully!"
ls -lh SpendWise.apk
echo "============================================="
