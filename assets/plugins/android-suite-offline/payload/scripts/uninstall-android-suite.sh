#!/bin/sh
set -eu

TOOL_DIR="${TAIXU_TOOL_DIR:?missing TAIXU_TOOL_DIR}"
rm -f /opt/taixu/bin/java /opt/taixu/bin/javac /opt/taixu/bin/gradle /opt/taixu/bin/cmake /opt/taixu/bin/ninja /opt/taixu/bin/flutter /opt/taixu/bin/dart
rm -rf "$TOOL_DIR"
rm -rf /opt/android-sdk /opt/gradle-* /opt/taixu/tools/android /opt/flutter
rm -f /root/.gradle/init.d/taixu-android-ndk.gradle
