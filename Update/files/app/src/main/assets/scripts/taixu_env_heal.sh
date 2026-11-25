#!/bin/sh
# TaiXu 环境自愈脚本（幂等，永不返回非零）
#
# 背景：RootFS 更新会整体替换发行版，旧 rootfs 里 apt 装过的包、以及
# setup_android_core.sh 写出的 /etc/profile.d/taixu-android.sh 会一并消失，
# 表现为环境检查掉项：
#   -「核心基础工具链」缺 xz / file（apt 包没了）
#   -「Node.js 运行时」未检测到（libatomic.so.1 缺失，node 起不来；软链也没了）
#   -「Android 开发环境」未安装（探针首句 source profile.d 失败，整条命令直接中断）
# 本脚本按「缺什么补什么」处理，全部就绪时只做几次 stat，不产生任何副作用。

LOG_DIR="/opt/taixu/data"
LOG="$LOG_DIR/env-heal.log"

log() {
    [ -d "$LOG_DIR" ] || mkdir -p "$LOG_DIR" 2>/dev/null || return 0
    echo "$(date '+%F %T') $*" >>"$LOG" 2>/dev/null || true
}

# 1) 基础工具包：补回旧 rootfs 里装过、更新后丢失的 apt 包
missing=""
for pkg in xz-utils file libatomic1 ca-certificates; do
    dpkg -s "$pkg" >/dev/null 2>&1 || missing="$missing $pkg"
done
if [ -n "$missing" ]; then
    log "补装缺失包:$missing"
    # 新 rootfs 的 apt 索引可能是空的，先直接装，失败再更新索引重试一次
    if ! apt-get install -y -q --no-install-recommends $missing >>"$LOG" 2>&1; then
        log "首次安装失败，刷新索引后重试"
        apt-get update -q >>"$LOG" 2>&1 || log "apt-get update 失败（离线可忽略）"
        apt-get install -y -q --no-install-recommends $missing >>"$LOG" 2>&1 || log "补装失败:$missing"
    fi
fi

# 2) Node.js 软链：/opt/taixu 会随 rootfs 重建，bin 下的软链可能丢失
node_dir=$(ls -d /opt/taixu/tools/node-* 2>/dev/null | tail -1)
if [ -n "$node_dir" ] && [ -x "$node_dir/bin/node" ]; then
    for entry in node npm npx; do
        if [ ! -e "/opt/taixu/bin/$entry" ] && [ -e "$node_dir/bin/$entry" ]; then
            ln -sf "$node_dir/bin/$entry" "/opt/taixu/bin/$entry" && log "重建软链 /opt/taixu/bin/$entry"
        fi
    done
fi

# 3) Android SDK 入口软链（SDK 实体在 /opt/taixu 下，/opt/android-sdk 在 rootfs 里）
if [ ! -e /opt/android-sdk ] && [ -d /opt/taixu/tools/android-sdk ]; then
    ln -sfn /opt/taixu/tools/android-sdk /opt/android-sdk && log "重建软链 /opt/android-sdk"
fi

# 4) android-core 的 profile.d：缺失会让探针首句 `. /etc/profile.d/taixu-android.sh`
#    作为特殊内建命令直接中断整个 shell，被误判成「未安装 Android 环境」
if [ ! -f /etc/profile.d/taixu-android.sh ]; then
    java_home=$(dirname "$(dirname "$(readlink -f /opt/taixu/bin/java 2>/dev/null)")" 2>/dev/null)
    [ -d "$java_home" ] || java_home="/opt/taixu/tools/jdk21"
    gradle_home=$(ls -d /opt/taixu/tools/gradle-* 2>/dev/null | tail -1)
    [ -n "$gradle_home" ] || gradle_home="/opt/gradle-8.14"
    ndk_path="/opt/taixu/tools/ndk"
    ndk_ver=$(grep -m1 'Pkg.Revision' "$ndk_path/source.properties" 2>/dev/null | cut -d= -f2 | tr -d ' ')
    aapt2_path=$(readlink -f /opt/taixu/bin/aapt2 2>/dev/null)
    mkdir -p /etc/profile.d 2>/dev/null || true
    {
        echo '# TaiXu Android development environment (managed by android-core plugin)'
        echo "export JAVA_HOME=\"$java_home\""
        echo 'export ANDROID_HOME="/opt/android-sdk"'
        echo 'export ANDROID_SDK_ROOT="/opt/android-sdk"'
        echo "export GRADLE_HOME=\"$gradle_home\""
        echo "export TAIXU_AAPT2_PATH=\"$aapt2_path\""
        echo "export TAIXU_NDK_PATH=\"$ndk_path\""
        echo "export TAIXU_NDK_VERSION=\"$ndk_ver\""
        echo "export ANDROID_NDK_HOME=\"$ndk_path\""
        echo "export ANDROID_NDK_ROOT=\"$ndk_path\""
        echo 'export PATH="/opt/taixu/bin:$JAVA_HOME/bin:$GRADLE_HOME/bin:$PATH"'
        echo 'export _JAVA_OPTIONS="-Djava.security.egd=file:/dev/urandom"'
    } >/etc/profile.d/taixu-android.sh 2>/dev/null && log "重建 /etc/profile.d/taixu-android.sh"
    for rc_file in /root/.bashrc /etc/bash.bashrc; do
        [ -f "$rc_file" ] || continue
        grep -q 'taixu-android' "$rc_file" 2>/dev/null || \
            echo '. /etc/profile.d/taixu-android.sh 2>/dev/null || true' >>"$rc_file"
    done
fi

exit 0
