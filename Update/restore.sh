#!/bin/sh
# 把 Update/files/ 下的改造重放回源码树
#
# 用法：
#   cd <仓库根目录> && sh Update/restore.sh          # 默认：安全模式（逐文件判定，不盲覆盖）
#
# 为什么默认不是直接 cp -f 覆盖：
#   Update/files 是「我方改造完成时」的整文件快照。上游作者后来又改了同一个文件时，
#   直接 cp -f 会把作者的新改动整段抹掉（含对方新写的硬编码、改名的常量、挪位的函数）。
#   所以覆盖前对每个文件判定：
#
#     状态          当前工作区 vs 基线               vs 我方快照        动作
#     ------------------------------------------------------------------------------
#     ALREADY_OK    已与快照一致                  一致              跳过（已是目标状态）
#     SAFE          与基线一致                    不同              覆盖（上游没动过，等价于补丁干净应用）
#     CONFLICT      与基线不同                    不同              拒绝覆盖，打印「上游改了什么 + 我方改动点」
#     NEW_ABSENT    基线无此文件且当前不存在      —                 落地（我方新增文件）
#     NEW_CONFLICT  基线无此文件但当前已存在      不同              拒绝覆盖，打印差异
#
# 基线（我方改造前的最后一个上游提交）有两个来源，按顺序自动选择：
#   1) git 里的 FORK_BASE 提交（默认 745c82d9）
#   2) Update/base/ 整文件快照——同步上游、历史被重建导致基线提交不存在时用这份
#      （由 `git diff 745c82d9 HEAD` 的基线侧导出，与 Update/files 一一对应）
#
# 环境变量：
#   FORK_BASE=<commit>   覆盖默认基线提交
#   FORCE=1              跳过判定，强制整文件覆盖（会丢上游改动，慎用）
#
# 退出码：0 = 全部已是目标状态或已安全重放；2 = 有文件冲突待人工/补丁处理
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

FORK_BASE="${FORK_BASE:-745c82d9}"
PATCH="Update/docs/fork-patches/0001-dynamic-toolchain-paths.patch"

if [ ! -d Update/files ]; then
    echo "错误：找不到 Update/files 目录" >&2
    exit 1
fi

if [ "${FORCE:-0}" = "1" ]; then
    echo "警告：FORCE=1，跳过判定，整文件覆盖（上游改动会丢失）" >&2
    n=0
    while IFS= read -r f; do
        rel="${f#Update/files/}"
        mkdir -p "$(dirname "$rel")"
        cp -f "$f" "$rel"
        echo "  forced: $rel"
        n=$((n + 1))
    done <<EOF
$(find Update/files -type f | sort)
EOF
    echo
    echo "改造文件已强制还原到 $ROOT（$n 个）"
    exit 0
fi

# ---------- 基线来源：优先 git 提交，退化到 Update/base/ 快照 ----------
if git rev-parse --verify "${FORK_BASE}^{commit}" >/dev/null 2>&1; then
    BASE_MODE="git"
elif [ -d Update/base ] && [ -n "$(find Update/base -type f 2>/dev/null | head -1)" ]; then
    BASE_MODE="snapshot"
    echo "提示：基线提交 $FORK_BASE 不在本地仓库（历史被重建过？），改用 Update/base/ 快照判定。"
else
    echo "错误：既没有基线提交 $FORK_BASE，也没有 Update/base/ 快照，无法判定上游是否改动过。" >&2
    echo "  任选其一：" >&2
    echo "    FORK_BASE=<commit> sh Update/restore.sh" >&2
    echo "    git apply --3way $PATCH      # 补丁方式，能保住双方改动" >&2
    exit 1
fi

base_has() {   # 0=基线存在该文件，1=基线无（我方新增）
    if [ "$BASE_MODE" = "git" ]; then
        git cat-file -e "${FORK_BASE}:$1" 2>/dev/null
    else
        [ -f "Update/base/$1" ]
    fi
}

base_hash() {
    if [ "$BASE_MODE" = "git" ]; then
        git rev-parse "${FORK_BASE}:$1" 2>/dev/null
    else
        git hash-object -- "Update/base/$1" 2>/dev/null
    fi
}

base_show() {
    if [ "$BASE_MODE" = "git" ]; then
        git show "${FORK_BASE}:$1" 2>/dev/null
    else
        cat "Update/base/$1" 2>/dev/null
    fi
}

# 我方在该文件上的改动点（从补丁里摘 hunk 头，hunk 头的尾段是所属函数/上下文）
our_hunks() {
    [ -f "$PATCH" ] || return 0
    awk -v target="b/$1" '
        /^diff --git / { inblk = (index($0, target) > 0); next }
        inblk && /^@@/  { print }
    ' "$PATCH" | head -6
}

# 上游相对基线的改动（只打印前若干行，够定位即可）
their_change() {
    if [ -f "$1" ]; then
        base_show "$1" | diff -u - "$1" 2>/dev/null | sed -n '1,14p'
    fi
}

echo "基线：$FORK_BASE（来源：$BASE_MODE$([ "$BASE_MODE" = "snapshot" ] && echo '，Update/base/' || true)）"
echo

STAT="$(mktemp)"
trap 'rm -f "$STAT"' EXIT
: > "$STAT"

while IFS= read -r f; do
    rel="${f#Update/files/}"
    snap_hash="$(git hash-object -- "$f" 2>/dev/null || echo '')"

    if [ -f "$rel" ]; then
        cur_hash="$(git hash-object -- "$rel" 2>/dev/null || echo 'MISSING')"
    else
        cur_hash="MISSING"
    fi

    if base_has "$rel"; then
        base_hash_val="$(base_hash "$rel")"

        if [ "$cur_hash" = "$snap_hash" ]; then
            echo "  ALREADY_OK: $rel  （已是改造后状态）"
            echo "already" >> "$STAT"
        elif [ "$cur_hash" = "$base_hash_val" ]; then
            mkdir -p "$(dirname "$rel")"
            cp -f "$f" "$rel"
            echo "  restored  : $rel  （上游未改动，覆盖安全）"
            echo "ok" >> "$STAT"
        else
            echo "  CONFLICT  : $rel"
            echo "              上游改过这个文件，当前内容既非基线也非我方快照 → 拒绝覆盖"
            echo "              ── 上游相对基线的改动（前 14 行）──"
            their_change "$rel" | sed 's/^/                /'
            echo "              ── 我方在该文件的改动点（补丁 hunk）──"
            if [ -n "$(our_hunks "$rel")" ]; then
                our_hunks "$rel" | sed 's/^/                /'
            else
                echo "                （补丁里没有该文件的 hunk，可能需重新生成补丁）"
            fi
            echo "              处理：按上面的 hunk 在新代码里重新定位改动点，不要整文件覆盖"
            echo "skip:$rel" >> "$STAT"
        fi
    else
        if [ ! -e "$rel" ]; then
            mkdir -p "$(dirname "$rel")"
            cp -f "$f" "$rel"
            echo "  restored  : $rel  （我方新增文件，落地）"
            echo "ok" >> "$STAT"
        elif [ "$cur_hash" = "$snap_hash" ]; then
            echo "  ALREADY_OK: $rel  （已是改造后状态）"
            echo "already" >> "$STAT"
        else
            echo "  CONFLICT  : $rel"
            echo "              我方新增的文件已存在但内容不同（上游可能也新增了同名文件），拒绝覆盖"
            echo "              ── 当前文件 vs 我方快照（前 14 行）──"
            diff -u "$f" "$rel" 2>/dev/null | sed -n '1,14p' | sed 's/^/                /'
            echo "skip:$rel" >> "$STAT"
        fi
    fi
done <<EOF
$(find Update/files -type f | sort)
EOF

already_n="$(grep -c '^already$' "$STAT" || true)"
ok_n="$(grep -c '^ok$' "$STAT" || true)"
skip_n="$(grep -c '^skip:' "$STAT" || true)"

echo
echo "处理完毕：已是目标状态 $already_n 个，已还原 $ok_n 个，冲突跳过 $skip_n 个。"
if [ "$skip_n" -gt 0 ]; then
    echo
    echo "  冲突文件清单："
    grep '^skip:' "$STAT" | sed 's/^skip:/    /'
    echo
    echo "  两种处理路径："
    echo "    A. 补丁三方合并（能保住双方改动，冲突处留 >>>>>>> 标记）："
    echo "         git apply --3way $PATCH"
    echo "    B. 逐个按上面打印的「我方 hunk」在新代码里重新定位改动点，改完跑："
    echo "         sh Update/check.sh"
fi
echo "  随后按 Update/CHECKLIST.md 逐条核验改造点是否仍成立"
if [ "$skip_n" -gt 0 ]; then
    exit 2
fi
exit 0
