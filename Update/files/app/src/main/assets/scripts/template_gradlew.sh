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


# 全局 Gradle 兜底：按本机实际安装目录取最高版本，不写死版本号
_tx_pick_gradle() {
    _gv=$(ls -d /opt/gradle-[0-9]* 2>/dev/null \
        | sed 's|^/opt/gradle-||' \
        | grep -E '^[0-9]+(\.[0-9]+)+$' \
        | sort -t. -k1,1n -k2,2n -k3,3n \
        | tail -n 1)
    [ -n "$_gv" ] && printf '%s' "/opt/gradle-$_gv"
}
_tx_gradle_home=$(_tx_pick_gradle)
if [ -f "$DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
    exec "$JAVA_EXEC" -jar "$DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
elif [ -n "$_tx_gradle_home" ] && [ -d "$_tx_gradle_home/lib" ]; then
    exec "$JAVA_EXEC" -Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC \
        -Dorg.gradle.appname=gradle \
        -Dorg.gradle.installation.dir="$_tx_gradle_home" \
        -Dorg.gradle.native=false \
        -classpath "$_tx_gradle_home/lib/*" \
        org.gradle.launcher.GradleMain "$@"
elif [ -n "$_tx_gradle_home" ] && [ -x "$_tx_gradle_home/bin/gradle" ]; then
    exec "$_tx_gradle_home/bin/gradle" "$@"
elif [ -x /usr/local/bin/gradle ]; then
    exec /usr/local/bin/gradle "$@"
elif command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
else
    exec /usr/bin/gradle "$@"
fi
