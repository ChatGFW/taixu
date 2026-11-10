#!/bin/sh
# 把 Update/ 当前内容推送到独立归档分支（不动工作区、不动 main、不 force）
#
# 用法：
#   sh Update/sync-archive.sh                    # 推送到 archive/update-2026-11
#   sh Update/sync-archive.sh -m "说明"          # 自定义提交信息
#   BR=archive/其它名字 sh Update/sync-archive.sh # 换分支名
#
# 为什么用临时 GIT_INDEX_FILE：
#   Update/ 被 .git/info/exclude 排除在 main 之外。在 main 上直接 add 会污染暂存区；
#   临时 index 只把 Update/ 写成一棵树，再 commit-tree -p 旧归档提交接上去，
#   全程不触碰工作区、HEAD 和 main 的 index。
#
# 退出码：0 = 已推送或本就不需要推送；1 = 前置条件不满足 / 推送失败
set -eu

BR="${BR:-archive/update-2026-11}"
MSG=""
# 默认与上一条归一化：归档分支始终只有单条提交（内容才是存档本体，迭代过程不必留痕）。
# 需要看演变过程时用 --keep-history 回到追加模式。
SQUASH=1
while [ $# -gt 0 ]; do
    case "$1" in
        -m)
            [ $# -ge 2 ] || { echo "错误：-m 后面缺提交信息" >&2; exit 1; }
            MSG="$2"
            shift 2
            ;;
        --keep-history)
            SQUASH=0
            shift
            ;;
        *)
            echo "错误：未知参数 $1（可用：-m <说明> / --keep-history）" >&2
            exit 1
            ;;
    esac
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

[ -d Update ] || { echo "错误：$ROOT 下没有 Update/" >&2; exit 1; }
git rev-parse --git-dir >/dev/null 2>&1 || { echo "错误：$ROOT 不是 git 仓库" >&2; exit 1; }
git remote get-url origin >/dev/null 2>&1 || { echo "错误：没有 origin remote" >&2; exit 1; }

HEAD_BEFORE="$(git rev-parse HEAD)"
# 本地没有归档分支时（换机/新 clone，恢复流程只建了 FETCH_HEAD），先从 origin 取回，
# 否则会当成新分支造出无父提交，推送必然被判非快进。
if ! git rev-parse --verify "refs/heads/$BR" >/dev/null 2>&1; then
    echo "本地没有 $BR，尝试从 origin 取回以保留历史…"
    if git fetch -q origin "refs/heads/$BR:refs/heads/$BR" 2>/dev/null; then
        echo "  已取回：$(git rev-parse --short "refs/heads/$BR")"
    else
        echo "  请提示：远端也没有该分支或网络不可用，将按新分支创建"
    fi
fi

IDX="${TMPDIR:-/tmp}/taixu-archive-idx.$$"
trap 'rm -f "$IDX"' EXIT INT TERM
GIT_INDEX_FILE="$IDX"
export GIT_INDEX_FILE

git read-tree --empty
git add -f Update
TREE="$(git write-tree)"
COUNT="$(git ls-tree -r --name-only "$TREE" | wc -l | tr -d ' ')"

OLD_REF=""
if git rev-parse --verify "refs/heads/$BR" >/dev/null 2>&1; then
    OLD_REF="$(git rev-parse "refs/heads/$BR")"
    if [ "$TREE" = "$(git rev-parse "refs/heads/$BR^{tree}")" ]; then
        if [ "$SQUASH" = 0 ] || [ "$(git rev-list --count "refs/heads/$BR")" = 1 ]; then
            echo "Update/ 与 $BR 内容一致（$COUNT 个文件），无需推送。"
            exit 0
        fi
        echo "内容一致，但 $BR 还有 $(git rev-list --count "refs/heads/$BR") 条提交，将压成单条。"
    fi
    if [ "$SQUASH" = 1 ]; then
        # 归一化：无父提交。先留一个回滚点，避免压掉后无从找回上一版
        git update-ref refs/backup/archive-last "$OLD_REF"
        PARENT=""
    else
        # 故意不加引号：展开成 "-p <sha>" 两个词
        PARENT="-p $OLD_REF"
    fi
else
    PARENT=""
    echo "提示：本地没有 $BR，将创建新分支"
fi

[ -n "$MSG" ] || MSG="docs(update): 存档更新 $(date '+%Y-%m-%d %H:%M')"
COMMIT="$(git -c user.name=taixu -c user.email=taixu@local commit-tree "$TREE" $PARENT -m "$MSG")"
git update-ref "refs/heads/$BR" "$COMMIT"
# 后续不再需要临时索引；必须解除，否则 git status 以空 index 为准，会把全部跟踪文件当成「已删除」报出几千条
unset GIT_INDEX_FILE

echo "新归档提交: $(git rev-parse --short "$COMMIT")  ($COUNT 个文件)"
if [ "$SQUASH" = 1 ] && [ -n "$OLD_REF" ]; then
    # 归一化必然重写历史，但必须先确认远端仍停在 $OLD_REF，否则说明别处推过，放弃以免误覆盖
    PUSH_LIMIT="--force-with-lease=refs/heads/$BR:$OLD_REF"
    echo "推送 $BR（归一化，force-with-lease 锁定 $OLD_REF）..."
else
    PUSH_LIMIT=""
    echo "推送 $BR ..."
fi
if ! git push $PUSH_LIMIT origin "$BR"; then
    echo "推送失败（网络或远端已被更新）。提交已落在本地 ref，网络恢复后重试，或先 git fetch origin $BR 再重跑：" >&2
    echo "    git push origin $BR" >&2
    exit 1
fi

REMOTE="$(git ls-remote origin "refs/heads/$BR" 2>/dev/null | awk '{print substr($1,1,8)}' || true)"
echo
echo "完成，核对："
echo "  远端 $BR: ${REMOTE:-查询失败（网络）}"
echo "  本地 $BR: $(git rev-parse --short "refs/heads/$BR")"
if [ "$HEAD_BEFORE" = "$(git rev-parse HEAD)" ]; then
    echo "  HEAD 未变动（$HEAD_BEFORE），main 未被触碰"
fi
echo "  工作区改动: $(git status --porcelain | wc -l | tr -d ' ') 条"
