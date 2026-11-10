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
RUST=app/src/main/assets/scripts/setup_rust.sh
JADX=app/src/main/assets/scripts/setup_jadx.sh
CORE=app/src/main/assets/scripts/setup_android_core.sh
DES=core/model/src/main/java/top/wkbin/taixu/core/model/DevEnvironmentSuite.kt
REP=runtime/src/main/java/top/wkbin/taixu/runtime/doctor/EnvironmentRepairer.kt
TPR=core/model/src/main/java/top/wkbin/taixu/core/model/ToolchainPathResolver.kt

cnt() { n=$(grep -c "$2" "$1" 2>/dev/null); echo "${n:-0}"; }

echo "=== A. 脚本层：不写死版本号 + 架构过滤 ==="
expect "A1 pick_archive 存在"        "$(cnt "$S" 'pick_archive()')" 1
expect "A2 归档引用不写死版本"       "$(grep -E 'ARCHIVES/[a-z-]+-[0-9]' "$S" 2>/dev/null | wc -l | tr -d ' ')" 0 zero
expect "A3 预发布正则含 canary"      "$(cnt "$S" 'canary\[0-9\]\*')" 1
expect "A4 NDK 版本动态查询"         "$(cnt "$NDK" 'termux-ndk/releases/latest')" 1
expect "A5 Rust 版本动态查询"        "$(cnt "$RUST" 'channel-rust-stable.toml')" 1
expect "A6 JADX 版本动态查询"        "$(cnt "$JADX" 'skylot/jadx/releases/latest')" 1
expect "A7 build-tools 跟随已装"     "$(cnt "$CORE" 'TAIXU_BUILD_TOOLS_VERSION')" 1
expect "A8 四处不降级保护"           "$(cnt "$S" '不降级保护')" 4
# 入口包装器：按已安装目录动态扫描版本，不能把版本号写进 exec 行（否则装上新版后仍指向旧目录）
W1=app/src/main/assets/bin/smali
W2=app/src/main/assets/bin/baksmali
W3=app/src/main/assets/bin/apktool
expect "A9 smali/baksmali 入口动态扫描" "$(grep -c 'sort -V | tail -n 1' "$W1" "$W2" 2>/dev/null | awk -F: '{s+=$2} END{print s+0}')" 2
expect "A10 apktool 入口动态扫描"     "$(cnt "$W3" 'sort -V | tail -n 1')" 1
expect "A11 入口脚本不写死版本号"   "$(cat "$W1" "$W2" "$W3" 2>/dev/null | grep -E 'exec .*(smali-[0-9]|apktool_[0-9])' | wc -l | tr -d ' ')" 0 zero

echo "=== B. 环境检查 / 修复：不再打回旧版 ==="
expect "B1 环境检查动态探测"         "$(cnt "$DES" 'sort -V | tail -n 1')" 2 ge
expect "B2 环境检查不硬编码 35.0.0"  "$(cnt "$DES" 'build-tools/35.0.0')" 0 zero
expect "B3 修复环境 profile 动态"    "$(cnt "$REP" '_tx_latest_dir')" 1 ge
expect "B4 Node 仅缺失时安装"        "$(cnt "$REP" 'NODE_BIN')" 2 ge
expect "B5 Node LTS 动态查询"        "$(cnt "$REP" 'nodejs.org/dist/index.json')" 1

echo "=== C. 工具链路径解析 ==="
if [ -f "$TPR" ]; then
    printf '  \033[32mPASS\033[0m C1 解析器文件存在\n'; PASS=$((PASS+1))
else
    printf '  \033[31mFAIL\033[0m C1 解析器文件缺失\n'; FAIL=$((FAIL+1))
fi
expect "C2 架构常量 TARGET_ARCH"     "$(cnt "$TPR" 'TARGET_ARCH')" 1 ge

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

echo "=== F. MCP 响应体积阈值（2026-11-01 改造）==="
MRS="harness/src/main/java/top/wkbin/taixu/harness/mcp/McpResponseSizeLimiter.kt"
expect "F1 内联阈值 5MB"        "$(cnt "$MRS" 'DEFAULT_MAX_INLINE_BYTES = 5L')" 1
expect "F2 落盘阈值 16MB"       "$(cnt "$MRS" 'DEFAULT_MAX_SPILL_BYTES = 16L')" 1
expect "F3 硬限 16MB"           "$(cnt "$MRS" 'DEFAULT_HARD_LIMIT_BYTES = 16L')" 1
expect "F4 落盘目录 128MB"      "$(cnt "$MRS" 'MAX_SPILL_DIR_BYTES = 128L')" 1

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

echo
echo "────────────────────────────────"
printf '通过 %s / 失败 %s\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ] && echo "改造点全部就绪" || echo "有改造点不兼容，需修复（只修上面 FAIL 的项）"
exit 0
