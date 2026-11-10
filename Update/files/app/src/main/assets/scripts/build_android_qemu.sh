#!/bin/sh
# TaiXu x86_64 compatibility build. This script must run inside the dedicated
# QEMU guest selected by LinuxRuntime; it is never used by the normal ARM64 path.
set -e

PROJECT_PATH="${1:-.}"
TASK="${2:-assembleDebug}"
COMPAT_ROOT=/opt/taixu/compat/x86_64
# JDK 目录名随客机镜像升级而变（jdk-17 / jdk-21 …），写死版本号会在镜像换代后直接报缺少 java。
# 取最高正式版（过滤 rc/beta/alpha/preview/snapshot）；客机为 x86_64 环境，无需额外架构过滤。
JAVA_HOME=$(ls -d "$COMPAT_ROOT"/jdk-*/ 2>/dev/null | grep -Eiv 'rc|beta|alpha|preview|snapshot' | sort -V | tail -n 1 | sed 's#/$##' || true)
[ -n "$JAVA_HOME" ] || { echo "==> [TaiXu QEMU Build] ❌ $COMPAT_ROOT 下未找到可用 JDK"; exit 127; }
GRADLE_HOME=$(ls -d "$COMPAT_ROOT"/gradle-*/ 2>/dev/null | grep -Eiv 'rc|beta|alpha|preview|snapshot' | sort -V | tail -n 1 | sed 's#/$##' || true)
ANDROID_HOME="$COMPAT_ROOT/android-sdk"
# Build-Tools 版本由客机镜像决定，随镜像升级而变；写死版本号会让镜像升级后直接判"缺少 Build-Tools"。
# 取最高稳定版（过滤 rc/beta/alpha/preview/snapshot），与 taixu-build.sh 的 ARM64 分支保持同一口径。
BUILD_TOOLS_DIR=$(ls -d "$ANDROID_HOME"/build-tools/*/ 2>/dev/null | grep -Eiv 'rc|beta|alpha|preview|snapshot' | sort -V | tail -n 1 | sed 's#/$##' || true)
[ -n "$BUILD_TOOLS_DIR" ] || { echo "==> [TaiXu QEMU Build] ❌ $ANDROID_HOME/build-tools 下未找到可用 Build-Tools"; exit 127; }

echo "==> [TaiXu QEMU Build] x86_64 兼容环境预检"
[ "$(uname -m)" = "x86_64" ] || { echo "==> [TaiXu QEMU Build] ❌ 当前并非 x86_64 QEMU Guest"; exit 126; }
[ -x "$JAVA_HOME/bin/java" ] || { echo "==> [TaiXu QEMU Build] ❌ 缺少 $JAVA_HOME/bin/java"; exit 127; }
java_lib=$(find "$JAVA_HOME" \( -type f -o -type l \) -name libjvm.so -print -quit 2>/dev/null || true)
[ -n "$java_lib" ] || { echo "==> [TaiXu QEMU Build] ❌ 未找到 JVM 原生库 (libjvm.so)"; exit 126; }
machine=$(od -An -t x1 -j 18 -N 2 "$java_lib" 2>/dev/null | tr -d '[:space:]')
[ "$machine" = "3e00" ] || { echo "==> [TaiXu QEMU Build] ❌ JDK 不是 x86_64 ELF: $JAVA_HOME/bin/java"; exit 126; }
for binary in "$BUILD_TOOLS_DIR/aapt2"; do
    [ -x "$binary" ] || { echo "==> [TaiXu QEMU Build] ❌ 缺少 $binary"; exit 127; }
    machine=$(od -An -t x1 -j 18 -N 2 "$binary" 2>/dev/null | tr -d '[:space:]')
    [ "$machine" = "3e00" ] || { echo "==> [TaiXu QEMU Build] ❌ 主机工具不是 x86_64 ELF: $binary"; exit 126; }
done
ls "$ANDROID_HOME"/platforms/android-*/android.jar >/dev/null 2>&1 || { echo "==> [TaiXu QEMU Build] ❌ 缺少 Android Platform（$ANDROID_HOME/platforms/android-*/android.jar）"; exit 127; }   # TAIXU_PLATFORM_GLOB
[ -f "$BUILD_TOOLS_DIR/lib/d8.jar" ] || { echo "==> [TaiXu QEMU Build] ❌ 缺少 $BUILD_TOOLS_DIR/lib/d8.jar"; exit 127; }

export JAVA_HOME ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$COMPAT_ROOT/cache/gradle"
export PATH="$JAVA_HOME/bin:$GRADLE_HOME/bin:$ANDROID_HOME/platform-tools:$BUILD_TOOLS_DIR:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
mkdir -p "$GRADLE_USER_HOME"

LOCAL_PROPERTIES="$PROJECT_PATH/local.properties"
TMP_PROPERTIES="${LOCAL_PROPERTIES}.taixu-qemu.tmp"
if [ -f "$LOCAL_PROPERTIES" ]; then
    sed -e '/^[[:space:]]*sdk\.dir[[:space:]]*=/d' -e '/^[[:space:]]*ndk\.dir[[:space:]]*=/d' "$LOCAL_PROPERTIES" > "$TMP_PROPERTIES"
else
    : > "$TMP_PROPERTIES"
fi
printf 'sdk.dir=%s\n' "$ANDROID_HOME" >> "$TMP_PROPERTIES"
mv -f "$TMP_PROPERTIES" "$LOCAL_PROPERTIES"

cd "$PROJECT_PATH"
EXTRA_ARGS="--console=plain --info --stacktrace --no-daemon --max-workers=2 -Dorg.gradle.native=false -Dorg.gradle.internal.http.connectionTimeout=30000 -Dorg.gradle.internal.http.socketTimeout=60000 -Pandroid.builder.sdkDownload=false -Pandroid.aapt2FromMavenOverride=$BUILD_TOOLS_DIR/aapt2"
[ "${TAIXU_OFFLINE:-0}" = "1" ] && EXTRA_ARGS="$EXTRA_ARGS --offline"
echo "==> [TaiXu QEMU Build] 使用 x86_64 JDK/SDK 执行 $TASK；APK ABI 仍由项目配置决定"
if [ -n "$GRADLE_HOME" ] && [ -d "$GRADLE_HOME/lib" ]; then
    exec "$JAVA_HOME/bin/java" -Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC -Dfile.encoding=UTF-8 -Dorg.gradle.appname=gradle -Dorg.gradle.installation.dir="$GRADLE_HOME" -classpath "$GRADLE_HOME/lib/*" org.gradle.launcher.GradleMain "$TASK" $EXTRA_ARGS
elif [ -f ./gradle/wrapper/gradle-wrapper.jar ]; then
    exec "$JAVA_HOME/bin/java" -Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC -Dfile.encoding=UTF-8 -classpath ./gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain "$TASK" $EXTRA_ARGS
else
    echo "==> [TaiXu QEMU Build] ❌ 未找到 Gradle（$COMPAT_ROOT/gradle-*）或项目 Wrapper"
    exit 127
fi
