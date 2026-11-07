#!/bin/sh
# ==============================================================================
# TaiXu - pinned Linux AArch64 NDK provisioner
#
# Installs lzhiyong/termux-ndk as an immutable, checksum-addressed artifact.
# The upstream directory is intentionally named linux-x86_64 because AGP/NDK
# use that Linux host tag; the executables inside must be AArch64.
# ==============================================================================
set -e

SDK_HOME="${ANDROID_HOME:-/opt/android-sdk}"
# 版本号不写死：动态查 lzhiyong/termux-ndk 最新 release 的 aarch64 归档
# （本机已有可用 NDK 时根本不会走到下载分支，见下方 find_existing_arm64_ndk）
NDK_BUILTIN_ARCHIVE="android-ndk-r29-aarch64.tar.xz"
NDK_BUILTIN_SHA256="02e10e4ddfe8deaeb0bd0cf29d04c981ed5bc8a5d6b560ebb9e7661f472d684b"
NDK_ARCHIVE_NAME="${TAIXU_NDK_ARCHIVE_NAME:-}"
if [ -z "$NDK_ARCHIVE_NAME" ]; then
    NDK_ARCHIVE_NAME="$(curl -fsSL --connect-timeout 8 --max-time 20 \
        "https://api.github.com/repos/lzhiyong/termux-ndk/releases/latest" 2>/dev/null \
        | grep -oE '"name":[[:space:]]*"android-ndk-[^"]*aarch64[^"]*"' \
        | head -n 1 | sed 's/.*"name":[[:space:]]*"//; s/"$//')"
fi
[ -n "$NDK_ARCHIVE_NAME" ] || NDK_ARCHIVE_NAME="$NDK_BUILTIN_ARCHIVE"
# 版本/发行号从归档名推导
NDK_RELEASE="$(printf '%s' "$NDK_ARCHIVE_NAME" | sed -n 's/^android-ndk-\(r[0-9][0-9]*\).*/\1/p')"
NDK_VERSION="${TAIXU_NDK_VERSION:-}"
# 只有内置归档才用预置指纹；其它版本下载后现算
if [ "$NDK_ARCHIVE_NAME" = "$NDK_BUILTIN_ARCHIVE" ]; then
    NDK_SHA256="$NDK_BUILTIN_SHA256"
else
    NDK_SHA256=""
fi
NDK_UPSTREAM_URL="https://github.com/lzhiyong/termux-ndk/releases/download/android-ndk/${NDK_ARCHIVE_NAME}"
NDK_URLS="
https://ghfast.top/${NDK_UPSTREAM_URL}
https://ghproxy.net/${NDK_UPSTREAM_URL}
https://gh.llkk.cc/${NDK_UPSTREAM_URL}
https://gh-proxy.com/${NDK_UPSTREAM_URL}
${NDK_UPSTREAM_URL}
"

TOOLCHAIN_ROOT="/opt/taixu/toolchains"
NDK_ARTIFACT_DIR="$TOOLCHAIN_ROOT/ndk/artifacts/$NDK_SHA256"
NDK_PATH="$NDK_ARTIFACT_DIR/ndk"
NDK_ENV_FILE="$TOOLCHAIN_ROOT/ndk/taixu-ndk.env"
NDK_SDK_VIEW="$SDK_HOME/ndk/${NDK_VERSION:-${NDK_RELEASE:-taixu-ndk}}"
LOCK_FILE="/opt/taixu/locks/android-toolchain.lock"

mkdir -p /opt/taixu/locks "$TOOLCHAIN_ROOT/ndk/artifacts" "$SDK_HOME/ndk" /tmp

if [ "${TAIXU_TOOLCHAIN_LOCK_HELD:-0}" != "1" ]; then
    command -v flock >/dev/null 2>&1 || {
        echo "!! [TaiXu] 缺少 flock，无法安全装配 Android 工具链"
        exit 1
    }
    exec 9>"$LOCK_FILE"
    flock -x -w 1800 9 || {
        echo "!! [TaiXu] Android 工具链正被构建任务使用，等待超时"
        exit 1
    }
fi

elf_machine_is_aarch64() {
    target="$1"
    [ -f "$target" ] || return 1
    machine=$(od -An -tu2 -j18 -N2 "$target" 2>/dev/null | tr -d '[:space:]')
    [ "$machine" = "183" ]
}

validate_ndk() {
    root="$1"
    strip="$root/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
    clang="$root/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
    [ -f "$root/source.properties" ] || return 1
    grep -Eq "^[[:space:]]*Pkg\.Revision[[:space:]]*=[[:space:]]*$NDK_VERSION([[:space:]]*)$" \
        "$root/source.properties" || return 1
    [ -x "$strip" ] && [ -x "$clang" ] || return 1
    elf_machine_is_aarch64 "$strip" || return 1
    elf_machine_is_aarch64 "$clang" || return 1
    "$strip" --version >/dev/null 2>&1 || return 1
    "$clang" --version >/dev/null 2>&1 || return 1
}

# 只校验「是不是可用的 ARM64 NDK」，**不校验具体版本号**（用于跟随用户自装的高版本）
validate_ndk_any() {
    root="$1"
    [ -f "$root/source.properties" ] || return 1
    strip=$(find "$root/toolchains/llvm/prebuilt" -name llvm-strip -print -quit 2>/dev/null || true)
    clang=$(find "$root/toolchains/llvm/prebuilt" -name clang -print -quit 2>/dev/null || true)
    [ -n "$strip" ] && [ -n "$clang" ] || return 1
    elf_machine_is_aarch64 "$strip" || return 1
    elf_machine_is_aarch64 "$clang" || return 1
    "$strip" --version >/dev/null 2>&1 || return 1
    "$clang" --version >/dev/null 2>&1 || return 1
}

# TAIXU_NDK_FOLLOW：优先复用本机已部署的可用 ARM64 NDK，不锁定单一版本。
# 在本机找可用的 ARM64 NDK（任意版本），命中多个时取版本号最高的那个。
# 扇描两处：太墟不可变制品目录与 SDK 的 ndk/ 视图；跳过本脚本创建的固定版本视图，避免自我循环。
find_existing_arm64_ndk() {
    _best=""; _bestver=""
    for _d in "$TOOLCHAIN_ROOT"/ndk/artifacts/*/ndk "$SDK_HOME"/ndk/*; do
        [ -d "$_d" ] || continue
        _root="${_d%/}"
        [ "$_root" = "$NDK_SDK_VIEW" ] && continue
        validate_ndk_any "$_root" || continue
        _ver=$(sed -n 's/^[[:space:]]*Pkg\.Revision[[:space:]]*=[[:space:]]*//p' "$_root/source.properties" 2>/dev/null | tr -d '\r' | head -n 1)
        [ -n "$_ver" ] || continue
        if [ -z "$_bestver" ]; then
            _bestver="$_ver"; _best="$_root"
        else
            _newer=$(printf '%s\n%s\n' "$_bestver" "$_ver" | sort -V | tail -n 1)
            if [ "$_newer" = "$_ver" ] && [ "$_ver" != "$_bestver" ]; then
                _bestver="$_ver"; _best="$_root"
            fi
        fi
    done
    [ -n "$_best" ] || return 1
    printf '%s\n' "$_best"
}

# ---- 跟随本机已有 ARM64 NDK：有就不装固定版本，绝不把新版本打回旧版 ----
following_existing=0
existing_ndk=$(find_existing_arm64_ndk || true)
if [ -n "${existing_ndk:-}" ]; then
    echo "==> [TaiXu] 检测到本机已有可用 ARM64 NDK：$existing_ndk"
    echo "==> [TaiXu] 跟随现有版本（不安装固定 $NDK_RELEASE / $NDK_VERSION）"
    NDK_PATH="$existing_ndk"
    following_existing=1
elif ! validate_ndk "$NDK_PATH"; then
    if [ -e "$NDK_ARTIFACT_DIR" ]; then
        echo "!! [TaiXu] ARM64 NDK 不可变制品发生完整性漂移，拒绝原位替换: $NDK_ARTIFACT_DIR"
        echo "!! [TaiXu] 请先通过诊断/清理流程隔离损坏制品后再装配"
        exit 1
    fi
    echo "==> [TaiXu] 正在部署 lzhiyong/termux-ndk $NDK_RELEASE ($NDK_VERSION, Linux AArch64)..."
    archive="/tmp/$NDK_ARCHIVE_NAME"
    staging="/tmp/taixu-termux-ndk-staging.$$"
    [ -n "$NDK_SHA256" ] || NDK_SHA256="dynamic-$(date +%s)"
    pending="$TOOLCHAIN_ROOT/ndk/artifacts/.pending-$NDK_SHA256-$$"
    rm -f "$archive"
    rm -rf "$staging" "$pending"

    downloaded=0
    for url in $NDK_URLS; do
        echo "==> [TaiXu] 尝试下载 ARM64 NDK: $url"
        if curl -fL --retry 4 --retry-all-errors --connect-timeout 20 --max-time 1800 \
            "$url" -o "$archive" 2>/dev/null && [ -s "$archive" ]; then
            if [ "$NDK_SHA256" = "$NDK_BUILTIN_SHA256" ] && echo "$NDK_SHA256  $archive" | sha256sum -c - >/dev/null 2>&1; then
                downloaded=1
                break
            fi
            echo "!! [TaiXu] ARM64 NDK SHA-256 校验失败，切换镜像"
            rm -f "$archive"
        fi
    done
    [ "$downloaded" -eq 1 ] || {
        echo "!! [TaiXu] lzhiyong/termux-ndk 下载失败"
        exit 1
    }

    # The archive is trusted only after the pinned digest matches. Still reject
    # absolute and parent-traversal entries before extraction.
    if tar -tJf "$archive" | awk '
        /^\// { bad=1 }
        /(^|\/)\.\.($|\/)/ { bad=1 }
        END { exit bad ? 1 : 0 }
    '; then
        :
    else
        echo "!! [TaiXu] ARM64 NDK 归档包含不安全路径"
        rm -f "$archive"
        exit 1
    fi

    mkdir -p "$staging"
    tar -xJf "$archive" -C "$staging"
    rm -f "$archive"
    source_properties=$(find "$staging" -mindepth 2 -maxdepth 4 -type f -name source.properties -print -quit 2>/dev/null || true)
    [ -n "$source_properties" ] || {
        echo "!! [TaiXu] ARM64 NDK 归档缺少 source.properties"
        rm -rf "$staging"
        exit 1
    }
    extracted_ndk=$(dirname "$source_properties")
    chmod 755 "$extracted_ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/"* 2>/dev/null || true
    validate_ndk "$extracted_ndk" || {
        echo "!! [TaiXu] ARM64 NDK ELF、版本或运行验证失败"
        rm -rf "$staging"
        exit 1
    }

    mkdir -p "$pending"
    mv "$extracted_ndk" "$pending/ndk"
    validate_ndk "$pending/ndk" || {
        echo "!! [TaiXu] ARM64 NDK staging 验证失败"
        rm -rf "$staging" "$pending"
        exit 1
    }

    mv "$pending" "$NDK_ARTIFACT_DIR"
    rm -rf "$staging"
fi

validate_ndk_any "$NDK_PATH" || {
    echo "!! [TaiXu] ARM64 NDK 不可用: $NDK_PATH"
    exit 1
}

# Compatibility views are replaceable symlinks; build injection always uses
# the immutable digest path above, never these links.
# 跟随用户已有 NDK 时不建固定版本视图（否则会在 SDK 里插一个旧版本目录）
if [ "$following_existing" = "1" ]; then
    NDK_SDK_VIEW=""
fi
if [ -n "$NDK_SDK_VIEW" ] && [ -e "$NDK_SDK_VIEW" ] && [ ! -L "$NDK_SDK_VIEW" ]; then
    displaced="$SDK_HOME/ndk/.replaced-non-taixu-$NDK_VERSION-$(date +%s)"
    mv "$NDK_SDK_VIEW" "$displaced"
    echo "==> [TaiXu] 已隔离同版本的非太墟 NDK: $displaced"
fi
if [ -n "$NDK_SDK_VIEW" ]; then
    sdk_link_tmp="$SDK_HOME/ndk/.taixu-$NDK_VERSION-$$"
    rm -f "$sdk_link_tmp"
    ln -s "$NDK_PATH" "$sdk_link_tmp"
    mv -Tf "$sdk_link_tmp" "$NDK_SDK_VIEW"
fi
ln -sfn "$NDK_PATH" "$TOOLCHAIN_ROOT/ndk/current"

env_tmp="${NDK_ENV_FILE}.tmp.$$"
cat > "$env_tmp" << EOF
export TAIXU_NDK_PATH="$NDK_PATH"
export TAIXU_NDK_VERSION="$(sed -n 's/^Pkg\.Revision *= *//p' "$NDK_PATH/source.properties" 2>/dev/null | tr -d '\r' | head -n 1)"
export TAIXU_NDK_SHA256="$NDK_SHA256"
export ANDROID_NDK_HOME="$NDK_PATH"
export ANDROID_NDK_ROOT="$NDK_PATH"
export TAIXU_LLVM_STRIP_PATH="$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
EOF
mv -f "$env_tmp" "$NDK_ENV_FILE"

# Force all AGP builds (including direct ./gradlew invocations) to the pinned
# immutable NDK path. android.ndkPath has precedence in AGP's NDK locator. A
# projectsEvaluated guard rejects any later script that tries to redirect it.
mkdir -p /root/.gradle/init.d
init_tmp="/root/.gradle/init.d/taixu-android-ndk.gradle.tmp.$$"
cat > "$init_tmp" << EOF
// Managed by TaiXu. Local policy only; never downloaded from a remote catalog.
// 优先跟随运行期注入（TAIXU_NDK_PATH / ANDROID_NDK_HOME），避免用户切到新 NDK 后
// 本文件仍把构建钉在生成时的旧路径上。
def taixuNdkPath = System.getenv('TAIXU_NDK_PATH') ?:
    System.getenv('ANDROID_NDK_HOME') ?:
    '$NDK_PATH'
def taixuAndroidPluginIds = [
    'com.android.application',
    'com.android.library',
    'com.android.dynamic-feature',
    'com.android.test',
    'com.android.kotlin.multiplatform.library'
]

gradle.beforeProject { project ->
    taixuAndroidPluginIds.each { pluginId ->
        project.pluginManager.withPlugin(pluginId) {
            def androidExtension = project.extensions.findByName('android')
            if (androidExtension != null && androidExtension.hasProperty('ndkPath')) {
                androidExtension.ndkPath = taixuNdkPath
                project.logger.info("TaiXu ARM64 NDK: " + taixuNdkPath)
            }
        }
    }
}

gradle.projectsEvaluated {
    gradle.rootProject.allprojects.each { project ->
        def androidExtension = project.extensions.findByName('android')
        if (androidExtension != null && androidExtension.hasProperty('ndkPath')) {
            def effective = androidExtension.ndkPath == null ? '' : new File(androidExtension.ndkPath.toString()).canonicalPath
            def expected = new File(taixuNdkPath).canonicalPath
            if (effective != expected) {
                throw new GradleException("TaiXu blocked Android NDK path drift: " + effective + " != " + expected)
            }
        }
    }
}
EOF
mv -f "$init_tmp" /root/.gradle/init.d/taixu-android-ndk.gradle

echo "==> [TaiXu] ✅ 固定 ARM64 NDK: $NDK_PATH"
