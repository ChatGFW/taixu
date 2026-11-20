#!/bin/sh
# Build shower-server.jar (classes.dex) for app_process.
# 依赖：JDK 17+ 的 javac/jar、任一 android.jar（隐藏 API 全走反射，无需框架 stub）、build-tools 的 d8。
# 可用环境变量覆盖：ANDROID_JAR / D8 / JAVAC / JAR。
set -e
root=$(cd "$(dirname "$0")" && pwd)

ANDROID_JAR=${ANDROID_JAR:-$(ls -d /opt/android-sdk/platforms/android-[0-9]* 2>/dev/null | sort -V | tail -n 1)/android.jar}
D8=${D8:-$(ls -d /opt/android-sdk/build-tools/[0-9]* 2>/dev/null | grep -Eiv 'rc|beta|alpha|preview|snapshot' | sort -V | tail -n 1)/d8}
JAVAC=${JAVAC:-javac}
JAR=${JAR:-jar}

classes="$root/build/classes"
dexDir="$root/build/dex"
outJar="$root/../showerclient/src/main/assets/shower-server.jar"

test -f "$ANDROID_JAR" || { echo "android.jar 不存在: $ANDROID_JAR" >&2; exit 1; }
test -x "$D8" || { echo "d8 不存在: $D8" >&2; exit 1; }

rm -rf "$classes" "$dexDir"
mkdir -p "$classes" "$dexDir"

"$JAVAC" -encoding UTF-8 --release 17 -classpath "$ANDROID_JAR" -d "$classes" \
    $(find "$root/src" -name '*.java')
"$D8" --min-api 26 --output "$dexDir" $(find "$classes" -name '*.class')
test -f "$dexDir/classes.dex" || { echo "classes.dex missing" >&2; exit 1; }

(cd "$dexDir" && "$JAR" cf "$outJar" classes.dex)
ls -l "$outJar"
