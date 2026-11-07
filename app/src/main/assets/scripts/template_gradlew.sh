#!/bin/sh
# ==============================================================================
# TaiXu (LinuxAIRuntime) - Self-Adaptive Gradle Wrapper
# ==============================================================================
DIR="$(cd "$(dirname "$0")" && pwd)"

# 优先加载插件装配期固化的环境变量
if [ -f /etc/profile.d/taixu-android.sh ]; then
    . /etc/profile.d/taixu-android.sh
fi

JAVA_BIN=$(which java 2>/dev/null || ls /usr/lib/jvm/*/bin/java 2>/dev/null | head -n 1 || true)
if [ -n "$JAVA_BIN" ] && [ -x "$JAVA_BIN" ]; then
    export JAVA_HOME=${JAVA_HOME:-$(dirname $(dirname $(readlink -f "$JAVA_BIN" 2>/dev/null || echo "$JAVA_BIN")))}
    export PATH="$JAVA_HOME/bin:$PATH"
    JAVA_EXEC="$JAVA_BIN"
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
    JAVA_EXEC="$JAVA_HOME/bin/java"
else
    JAVA_EXEC=$(which java 2>/dev/null || echo "java")
fi

# TAIXU_GRADLEW_DYNAMIC：指向旧版固定目录的分支已移除，
# 版本只由下一行动态探测决定；若被 APK 覆盖还原，登录期补丁会按本标记重新剥离。
# 动态探测本机 Gradle 安装目录（取 /opt/gradle-* 最高正式版），不写死版本号
TX_GRADLE_DIR="$(ls -d /opt/gradle-*/ 2>/dev/null | grep -Eiv 'rc|beta|alpha|preview|snapshot' | sort -V | tail -n 1 | sed 's#/$##')"

if [ -f "$DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
    exec "$JAVA_EXEC" -jar "$DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
elif [ -n "$TX_GRADLE_DIR" ] && [ -d "$TX_GRADLE_DIR/lib" ]; then
    exec "$JAVA_EXEC" -Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC \
        -Dorg.gradle.appname=gradle \
        -Dorg.gradle.installation.dir="$TX_GRADLE_DIR" \
        -Dorg.gradle.native=false \
        -classpath "$TX_GRADLE_DIR/lib/*" \
        org.gradle.launcher.GradleMain "$@"
elif [ -n "$TX_GRADLE_DIR" ] && [ -x "$TX_GRADLE_DIR/bin/gradle" ]; then
    exec "$TX_GRADLE_DIR/bin/gradle" "$@"
elif [ -x /usr/local/bin/gradle ]; then
    exec /usr/local/bin/gradle "$@"
elif command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
else
    exec /usr/bin/gradle "$@"
fi
