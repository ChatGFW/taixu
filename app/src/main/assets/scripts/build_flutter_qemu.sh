#!/bin/sh
# Flutter x86_64 host compatibility build; Android output remains arm64-v8a.
set -e

PROJECT_PATH="${1:-.}"
COMPAT_ROOT=/opt/taixu/compat/x86_64
FLUTTER_HOME="$COMPAT_ROOT/flutter"
# JDK 目录名随客机镜像升级而变（jdk-17 / jdk-21 …），写死版本号会在镜像换代后直接报缺少 java。
# 取最高正式版（过滤 rc/beta/alpha/preview/snapshot）；客机为 x86_64 环境，无需额外架构过滤。
JAVA_HOME=$(ls -d "$COMPAT_ROOT"/jdk-*/ 2>/dev/null | grep -Eiv 'rc|beta|alpha|preview|snapshot' | sort -V | tail -n 1 | sed 's#/$##' || true)
ANDROID_HOME="$COMPAT_ROOT/android-sdk"

[ "$(uname -m)" = "x86_64" ] || { echo "==> [TaiXu QEMU Flutter] ❌ 当前并非 x86_64 QEMU Guest"; exit 126; }
[ -x "$JAVA_HOME/bin/java" ] || { echo "==> [TaiXu QEMU Flutter] ❌ 缺少 $JAVA_HOME/bin/java"; exit 127; }
# Build-Tools / Gradle 版本由客机镜像决定，随镜像升级而变；写死版本号会让镜像升级后直接报"缺少工具"。
# 取最高稳定版（过滤 rc/beta/alpha/preview/snapshot）与 taixu-build.sh 的 ARM64 分支保持同一口径。
BUILD_TOOLS_DIR=$(ls -d "$ANDROID_HOME"/build-tools/*/ 2>/dev/null | grep -Eiv 'rc|beta|alpha|preview|snapshot' | sort -V | tail -n 1 | sed 's#/$##' || true)
[ -n "$BUILD_TOOLS_DIR" ] || { echo "==> [TaiXu QEMU Flutter] ❌ $ANDROID_HOME/build-tools 下未找到可用 Build-Tools"; exit 127; }
# QEMU 客机内的 Gradle 同样是随镜像升级的浮动版本，目录缺失时不应拼出空的 PATH 段。
QEMU_GRADLE_HOME=$(ls -d "$COMPAT_ROOT"/gradle-*/ 2>/dev/null | grep -Eiv 'rc|beta|alpha|preview|snapshot' | sort -V | tail -n 1 | sed 's#/$##' || true)
java_lib=$(find "$JAVA_HOME" \( -type f -o -type l \) -name libjvm.so -print -quit 2>/dev/null || true)
[ -n "$java_lib" ] || { echo "==> [TaiXu QEMU Flutter] ❌ 未找到 JVM 原生库 (libjvm.so)"; exit 126; }
machine=$(od -An -t x1 -j 18 -N 2 "$java_lib" 2>/dev/null | tr -d '[:space:]')
[ "$machine" = "3e00" ] || { echo "==> [TaiXu QEMU Flutter] ❌ JDK 不是 x86_64 ELF: $JAVA_HOME/bin/java"; exit 126; }
for binary in "$FLUTTER_HOME/bin/cache/dart-sdk/bin/dart" "$BUILD_TOOLS_DIR/aapt2"; do
    [ -x "$binary" ] || { echo "==> [TaiXu QEMU Flutter] ❌ 缺少 $binary"; exit 127; }
    machine=$(od -An -t x1 -j 18 -N 2 "$binary" 2>/dev/null | tr -d '[:space:]')
    [ "$machine" = "3e00" ] || { echo "==> [TaiXu QEMU Flutter] ❌ 主机工具不是 x86_64 ELF: $binary"; exit 126; }
done

export JAVA_HOME ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export FLUTTER_HOME
export PUB_CACHE="$COMPAT_ROOT/cache/flutter-pub"
export GRADLE_USER_HOME="$COMPAT_ROOT/cache/gradle"
export PUB_HOSTED_URL=https://pub.flutter-io.cn
export FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn
export ORG_GRADLE_PROJECT_android_aapt2FromMavenOverride="$BUILD_TOOLS_DIR/aapt2"
PATH="$FLUTTER_HOME/bin:$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$BUILD_TOOLS_DIR:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
if [ -n "$QEMU_GRADLE_HOME" ]; then PATH="$QEMU_GRADLE_HOME/bin:$PATH"; fi
export PATH
mkdir -p "$PUB_CACHE" "$GRADLE_USER_HOME"

cd "$PROJECT_PATH"
mkdir -p android
LOCAL_PROPERTIES=android/local.properties
TMP_PROPERTIES="${LOCAL_PROPERTIES}.taixu-qemu.tmp"
if [ -f "$LOCAL_PROPERTIES" ]; then
    sed -e '/^[[:space:]]*sdk\.dir[[:space:]]*=/d' -e '/^[[:space:]]*ndk\.dir[[:space:]]*=/d' -e '/^[[:space:]]*flutter\.sdk[[:space:]]*=/d' "$LOCAL_PROPERTIES" > "$TMP_PROPERTIES"
else
    : > "$TMP_PROPERTIES"
fi
printf 'sdk.dir=%s\nflutter.sdk=%s\n' "$ANDROID_HOME" "$FLUTTER_HOME" >> "$TMP_PROPERTIES"
mv -f "$TMP_PROPERTIES" "$LOCAL_PROPERTIES"

echo "==> [TaiXu QEMU Flutter] 拉取 Dart 依赖"
if [ "${TAIXU_OFFLINE:-0}" = "1" ]; then
    "$FLUTTER_HOME/bin/flutter" pub get --offline --verbose
else
    "$FLUTTER_HOME/bin/flutter" pub get --verbose
fi
echo "==> [TaiXu QEMU Flutter] 构建 Android ARM64 APK"
if [ "${TAIXU_OFFLINE:-0}" = "1" ]; then
    exec "$FLUTTER_HOME/bin/flutter" build apk --debug --target-platform android-arm64 --offline --verbose
else
    exec "$FLUTTER_HOME/bin/flutter" build apk --debug --target-platform android-arm64 --verbose
fi
