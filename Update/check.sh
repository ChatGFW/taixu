#!/bin/sh
# 改造点自动核验（只检查改造过的部分，不碰未改造代码）
# 用法：cd <仓库根目录> && sh Update/check.sh
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 1

PASS=0
FAIL=0
WARN=0

# expect <描述> <实际值> <期望> [mode]
expect() {
    desc="$1"; actual="$2"; want="$3"; mode="${4:-eq}"
    ok=0
    case "$mode" in
        eq)     [ "$actual" = "$want" ] && ok=1 ;;
        ge)     [ "${actual:-0}" -ge "$want" ] 2>/dev/null && ok=1 ;;
        zero)   [ "${actual:-0}" -eq 0 ] 2>/dev/null && ok=1 ;;
        empty)  [ -z "$actual" ] && ok=1 ;;
    esac
    if [ "$ok" = 1 ]; then
        printf '  \033[32mPASS\033[0m %s\n' "$desc"; PASS=$((PASS+1))
    else
        printf '  \033[31mFAIL\033[0m %s  (实际=%s 期望=%s)\n' "$desc" "$actual" "$want"; FAIL=$((FAIL+1))
    fi
}

S=assets/plugins/android-suite-offline/payload/scripts/install-android-suite.sh
NDK=app/src/main/assets/scripts/setup_termux_ndk.sh
S=assets/plugins/android-suite-offline/payload/scripts/install-android-suite.sh
UN=assets/plugins/android-suite-offline/payload/scripts/uninstall-android-suite.sh
VS=assets/plugins/android-suite-offline/payload/scripts/verify-android-suite.sh
CORE=app/src/main/assets/scripts/setup_android_core.sh
FLU=app/src/main/assets/scripts/setup_flutter.sh
TG=app/src/main/assets/scripts/template_gradlew.sh
TB=app/src/main/assets/scripts/taixu-build.sh
GP=assets/plugins/android-suite-offline/payload/config/gradle.properties
MF=assets/plugins/android-suite-offline/manifest.json
DES=core/model/src/main/java/top/wkbin/taixu/core/model/DevEnvironmentSuite.kt
REP=runtime/src/main/java/top/wkbin/taixu/runtime/doctor/EnvironmentRepairer.kt
PRE=runtime/src/main/java/top/wkbin/taixu/runtime/build/BuildEnvironmentPreflight.kt
WSV=feature/workspace/src/main/java/top/wkbin/taixu/ui/workspace/WorkshopSettingsViewModel.kt
ASM=tools/src/main/java/top/wkbin/taixu/core/tools/AndroidSdkManager.kt
TI=runtime/src/main/java/top/wkbin/taixu/runtime/doctor/ToolchainInspector.kt
ST=core/model/src/main/java/top/wkbin/taixu/core/model/SandboxToolchain.kt
PBS=core/model/src/main/java/top/wkbin/taixu/core/model/PluginBundleScripts.kt

cnt() { n=$(grep -c "$2" "$1" 2>/dev/null); echo "${n:-0}"; }
# 固定串计数：模式含 [ ] * . 等字面字符时用这个，避免 BRE 转义差异
cntf() { n=$(grep -cF "$2" "$1" 2>/dev/null); echo "${n:-0}"; }

echo "=== A. 脚本层：版本号按实际安装探测 ==="
expect "A1 安装器 Gradle 版本按归档推导" "$(cnt "$S" 'GRADLE_VERSION=$(printf')" 1
expect "A2 Build-Tools 版本读 source.properties" "$(cntf "$S" 'source.properties')" 2 ge
expect "A3 安装器维护 0-current 稳定软链" "$(cnt "$S" 'build-tools/0-current')" 1
expect "A4 卸载按实际版本目录执行" "$(cnt "$UN" 'gradle-\\*')" 1 ge
expect "A5 验签脚本不写死平台版本" "$(cnt "$VS" 'platforms/android-34')" 0 zero
expect "A6 核心脚本平台检查按通配" "$(cntf "$CORE" 'platforms/android-[0-9]*')" 2
expect "A7 核心脚本维护 0-current 稳定软链" "$(cnt "$CORE" 'build-tools/0-current')" 1
expect "A8 全局 gradle 配置走稳定入口" "$(cnt "$GP" 'build-tools/0-current/aapt2')" 1
expect "A9 插件清单 Gradle 入口稳定" "$(cntf "$MF" '/opt/gradle"')" 1
expect "A10 gradlew 模板含版本探测函数" "$(cnt "$TG" '_tx_pick_gradle')" 2 ge
expect "A11 构建脚本按实际安装探测" "$(cnt "$TB" 'sort -V | tail -n 1')" 2 ge
expect "A12 Flutter 平台检查按通配" "$(cntf "$FLU" 'platforms/android-[0-9]*')" 1

echo "=== B. Kotlin 层：版本号按实际安装探测 ==="
expect "B1 组件检查命令按实际安装探测" "$(cntf "$DES" 'platforms/android-[0-9]*')" 2
expect "B2 组件检查不写死 Build-Tools 版本" "$(cnt "$DES" 'build-tools/35.0.0')" 0 zero
expect "B3 环境修复 profile 动态探测" "$(cnt "$REP" 'Pkg\\.Revision')" 1 ge
expect "B4 预检脚本平台与工具动态" "$(cnt "$PRE" '_tx_bt')" 2 ge
expect "B5 预检 QEMU 兼容层仍用固定制品" "$(cnt "$PRE" 'COMPAT_ROOT')" 2 ge
expect "B6 工作台默认草稿不内置版本" "$(cnt "$WSV" 'gradle-8.14.2')" 0 zero
expect "B7 SDK 识别按实际 Build-Tools 目录" "$(cnt "$ASM" 'build-tools')" 2 ge
expect "B8 SDK 验证脚本按动态软链" "$(cnt "$ASM" '_tx_bt')" 1 ge

echo "=== C. 工具链探测与清理 ==="
expect "C1 探测候选支持通配展开" "$(cntf "$TI" "contains('*')")" 1
expect "C2 aapt2 与 apksigner 候选通配" "$(cnt "$ST" 'build-tools/\\*')" 2
expect "C3 清理白名单允许数字版本" "$(cnt "$PBS" 'gradle-')" 1 ge

echo "=== D. 恢复通道（同步上游后必看）==="
PATCH=Update/docs/fork-patches/0001-dynamic-toolchain-paths.patch
RS=Update/restore.sh
RDM=Update/README.md
expect "D1 补丁文件存在"          "$(test -f "$PATCH" && echo 1 || echo 0)" 1
expect "D2 补丁段数 = files 文件数" "$(cnt "$PATCH" '^diff --git' | tr -d ' ')" "$(find Update/files -type f | wc -l | tr -d ' ')"
expect "D3 restore 四态判定"     "$(grep -c 'CONFLICT\|ALREADY_OK' "$RS")" 4 ge
expect "D4 restore 有 FORCE 开关" "$(grep -c 'FORCE' "$RS")" 1 ge
expect "D5 README 首选打补丁"     "$(grep -c '方式 1（推荐）：打补丁' "$RDM")" 1
expect "D6 基线快照存在"        "$(test -d Update/base && echo 1 || echo 0)" 1
expect "D7 base+新增 = files 总数" "$(( $(find Update/base -type f | wc -l) + $(grep -c . Update/BASE-ABSENT.txt) ))" "$(find Update/files -type f | wc -l | tr -d ' ')"
expect "D8 restore 可退化到 base 快照" "$(grep -c 'Update/base' "$RS")" 3 ge
expect "D9 restore 冲突退出码 2"   "$(grep -c 'exit 2' "$RS")" 1
# D10 校验补丁覆盖的文件集与快照文件集一一对应（不依赖当前树状态，同步上游后仍成立）
_t1="$(mktemp)"; _t2="$(mktemp)"
grep '^diff --git' "$PATCH" | sed 's|^diff --git a/||;s| b/.*||' | sort -u > "$_t1"
(cd Update/files && find . -type f | sed 's|^\./||' | sort) > "$_t2"
expect "D10 补丁文件集 = 快照文件集" "$(diff "$_t1" "$_t2" | wc -l | tr -d ' ')" 0
rm -f "$_t1" "$_t2"
SA=Update/sync-archive.sh
expect "D11 归档同步脚本语法通过" "$(sh -n "$SA" 2>/dev/null && echo 1 || echo 0)" 1
expect "D12 脚本用临时索引不污染 main" "$(grep -c 'GIT_INDEX_FILE' "$SA")" 3 ge
expect "D13 不含裸 --force"        "$(grep -c -- '--force[^-]' "$SA")" 0
expect "D15 归一化用 force-with-lease" "$(grep -c -- '--force-with-lease' "$SA")" 1 ge
expect "D16 默认归一化（SQUASH=1）"  "$(grep -c '^SQUASH=1' "$SA")" 1
expect "D14 Update 不被 main 跟踪" "$(git ls-files Update | wc -l | tr -d ' ')" 0
# D17：补丁必须能在干净上游树上完整应用（含二进制段）。
# 这是原有盲区——D2/D10 只比对文本层的「段数/文件集」，补丁若因二进制文件缺
# full index 行而整体 apply 失败，两项仍全绿。实测解包 FORK_BASE 树再 git apply --check。
FORK_BASE="${FORK_BASE:-$(grep -m1 '^FORK_BASE=' Update/restore.sh | sed 's/.*:-\([0-9a-f]\{7,40\}\).*/\1/')}"
_pt=$(mktemp -d 2>/dev/null || true)
[ -n "${_pt:-}" ] || _pt="/tmp/tx-patch-check-$$"
mkdir -p "${_pt}"
_rc=1
if [ -n "${FORK_BASE:-}" ] && git rev-parse --verify "${FORK_BASE}^{commit}" >/dev/null 2>&1; then
    git archive "$FORK_BASE" 2>/dev/null | tar -x -C "${_pt}" 2>/dev/null
    ( cd "${_pt}" && git init -q >/dev/null 2>&1 && git add -A >/dev/null 2>&1 \
        && git apply --check "$ROOT/$PATCH" >/dev/null 2>&1 ) && _rc=0
fi
rm -rf "${_pt}"
expect "D17 补丁可在干净上游树应用（含二进制段）" "$_rc" 0

echo "=== E. 语法自检 ==="
bad=0
for f in $(find Update/files -name '*.sh' 2>/dev/null) app/src/main/assets/bin/taixu-build-guard; do
    [ -f "$f" ] || continue
    sh -n "$f" 2>/dev/null || { printf '  \033[31mFAIL\033[0m shell 语法: %s\n' "$f"; bad=$((bad+1)); }
done
for f in $(find Update/files -name '*.kt' 2>/dev/null); do
    python3 - "$f" <<'PY' || bad=$((bad+1))
# 括号平衡两档判定：先数原文，不平衡时再剥离字符串/注释复核。
# 正则字符类（如 [= (:]）与提示文案里的括号是字面字符，不是语法括号，不剥离会误报。
import sys
def strip(s):
    out = []; i = 0; n = len(s)
    while i < n:
        if s.startswith('"""', i):
            j = s.find('"""', i + 3); i = n if j < 0 else j + 3; continue
        c = s[i]
        if c in ('"', "'"):
            i += 1
            while i < n:
                if s[i] == '\\': i += 2; continue
                if s[i] == c: i += 1; break
                i += 1
            continue
        if s.startswith('//', i):
            j = s.find('\n', i); i = n if j < 0 else j; continue
        if s.startswith('/*', i):
            j = s.find('*/', i + 2); i = n if j < 0 else j + 2; continue
        out.append(c); i += 1
    return ''.join(out)
def d(t):
    return (t.count('{') - t.count('}'), t.count('(') - t.count(')'), t.count('[') - t.count(']'))
f = sys.argv[1]
s = open(f, encoding='utf-8').read()
if d(s) == (0, 0, 0) or d(strip(s)) == (0, 0, 0):
    sys.exit(0)
print('  \033[31mFAIL\033[0m 括号不平衡: %s raw=%s code=%s' % (f, d(s), d(strip(s))))
sys.exit(1)
PY
done
if [ "$bad" -eq 0 ]; then
    printf '  \033[32mPASS\033[0m E 语法自检全部通过\n'; PASS=$((PASS+1))
else
    FAIL=$((FAIL+bad))
fi

echo "=== G. 改造语义特征（逐文件回归哨兵）==="
# features.txt 每行：<路径>\t<new|contain|sha>\t<特征>
# 用途：守住「文件还在、内容却被上游同步覆盖回基线」这种静默回退。
# new=新增文件必须存在；sha=二进制内容必须一致；contain=必须包含改造特征串。
_gbad=0; _gn=0
_tab=$(printf '\t')
while IFS="$_tab" read -r _gp _gm _gpat; do
    [ -n "$_gp" ] || continue
    case "$_gp" in \#*) continue ;; esac
    _gn=$((_gn+1))
    case "$_gm" in
        new) [ -f "$_gp" ] || { _gbad=$((_gbad+1)); printf '  \033[31mFAIL\033[0m G 新增文件缺失: %s\n' "$_gp"; } ;;
        sha) [ "$(sha256sum "$_gp" 2>/dev/null | cut -d' ' -f1)" = "$_gpat" ] || { _gbad=$((_gbad+1)); printf '  \033[31mFAIL\033[0m G 内容不符: %s\n' "$_gp"; } ;;
        *)   { [ -f "$_gp" ] && grep -qF -- "$_gpat" "$_gp" 2>/dev/null; } || { _gbad=$((_gbad+1)); printf '  \033[31mFAIL\033[0m G 特征丢失: %s\n' "$_gp"; } ;;
    esac
done < Update/features.txt
expect "G 改造语义特征全部命中（$_gn 项）" "$_gbad" 0

echo
echo "────────────────────────────────"
printf '通过 %s / 失败 %s\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ] && echo "改造点全部就绪" || echo "有改造点不兼容，需修复（只修上面 FAIL 的项）"
exit 0
