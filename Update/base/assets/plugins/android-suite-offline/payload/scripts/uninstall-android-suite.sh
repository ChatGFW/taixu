#!/bin/sh
set -eu

TOOL_DIR="${TAIXU_TOOL_DIR:?missing TAIXU_TOOL_DIR}"
rm -f /opt/taixu/bin/java /opt/taixu/bin/javac /opt/taixu/bin/gradle /opt/taixu/bin/cmake /opt/taixu/bin/ninja /opt/taixu/bin/flutter /opt/taixu/bin/dart
rm -rf "$TOOL_DIR"
rm -rf /opt/android-sdk /opt/gradle-8.14.2 /opt/taixu/toolchains/android /opt/flutter
rm -f /root/.gradle/init.d/taixu-android-ndk.gradle
