#!/bin/sh
set -eu

TOOL_DIR="${TAIXU_TOOL_DIR:?missing TAIXU_TOOL_DIR}"
GRADLE_VERSION=8.14.2
for name in java javac gradle cmake ninja flutter dart rustc cargo rustdoc apksigner zipalign jarsigner keytool aapt aapt2 dexdump readelf objdump nm strings cxxfilt jadx apktool rg; do
    link="/opt/taixu/bin/$name"
    [ -L "$link" ] || continue
    target=$(readlink -f "$link" 2>/dev/null || true)
    case "$target" in
        "$TOOL_DIR"/*|/opt/taixu/tools/android/*|/opt/taixu/tools/rust/*|/opt/android-sdk/*|"/opt/gradle-$GRADLE_VERSION"/*|/opt/flutter/*) rm -f "$link" ;;
    esac
done
rm -rf "$TOOL_DIR" /opt/android-sdk "/opt/gradle-$GRADLE_VERSION" /opt/taixu/tools/android /opt/taixu/tools/rust /opt/flutter
if [ "$(readlink -f /opt/gradle 2>/dev/null || true)" = "/opt/gradle-$GRADLE_VERSION" ]; then
    rm -f /opt/gradle
fi
rm -f /root/.gradle/init.d/taixu-android-ndk.gradle
