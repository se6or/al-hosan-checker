#!/bin/bash
# Build Rust library for Android (multiple architectures)
# Requires: cargo-ndk, Android NDK

set -e

echo "🔨 Building Rust core for Android..."

# Check for cargo-ndk
if ! command -v cargo-ndk &> /dev/null; then
    echo "❌ cargo-ndk not found. Installing..."
    cargo install cargo-ndk
fi

# Set NDK path (adjust as needed)
if [ -z "$ANDROID_NDK_HOME" ]; then
    if [ -n "$ANDROID_HOME" ]; then
        export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/26.1.10909125"
    else
        echo "❌ ANDROID_NDK_HOME or ANDROID_HOME not set"
        exit 1
    fi
fi

# Target directory in the Android project
JNI_DIR="../app/src/main/jniLibs"

# Clean previous builds
rm -rf "$JNI_DIR"
mkdir -p "$JNI_DIR/arm64-v8a"
mkdir -p "$JNI_DIR/armeabi-v7a"
mkdir -p "$JNI_DIR/x86_64"

# Build for arm64-v8a
echo "📦 Building for arm64-v8a..."
cargo ndk -t arm64-v8a build --release
cp target/aarch64-linux-android/release/libalhosan_core.so "$JNI_DIR/arm64-v8a/"

# Build for armeabi-v7a
echo "📦 Building for armeabi-v7a..."
cargo ndk -t armeabi-v7a build --release
cp target/armv7-linux-androideabi/release/libalhosan_core.so "$JNI_DIR/armeabi-v7a/"

# Build for x86_64
echo "📦 Building for x86_64..."
cargo ndk -t x86_64 build --release
cp target/x86_64-linux-android/release/libalhosan_core.so "$JNI_DIR/x86_64/"

echo "✅ Rust core built successfully!"
echo "📁 .so files placed in: $JNI_DIR"
ls -la "$JNI_DIR"/*/
