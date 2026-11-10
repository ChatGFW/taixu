# Update — 本仓库改造存档

本目录用于存放**我对 taixu 仓库的全部改造**，防止同步上游源码时被误删。

## 为什么放在这里

用户会经常同步上游源码（`git pull` / 重新 clone / 覆盖式更新），根目录的
`Progress.md`、`HookPoints.md`、`docs/fork-patches/` 容易被一起删掉。
把改造源码与记录集中到 `Update/` 后，只要这个目录还在，改造就不会丢。

## 目录结构

```
Update/
├── README.md              ← 本文件
├── MANIFEST.txt           ← 改造文件清单（相对路径 + 改造摘要）
├── CHECKLIST.md           ← 兼容性检查清单（只覆盖改造点，不碰未改造代码）
├── METHOD.md              ← 历史改造方法归档（时代一 MCP 阈值 / 时代二 工具链）
├── restore.sh             ← 一键把 files/ 还原回源码树
├── files/                 ← 改造后的完整源码文件（保持仓库相对路径）
│   ├── app/src/main/assets/...
│   ├── assets/plugins/android-suite-offline/...
│   ├── core/model/src/main/java/...
│   ├── feature/workspace/src/main/java/...
│   ├── harness/src/{main,test}/java/...
│   ├── runtime/src/{main,test}/java/...
│   └── tools/src/main/java/...
└── docs/
    ├── Progress.md        ← 改造全过程记录
    ├── HookPoints.md      ← 关键钩子点与回归检查命令
    └── fork-patches/
        └── 0001-dynamic-toolchain-paths.patch   ← 可 git apply 的统一补丁
```

## 三种恢复方式（按场景选）

### 方式 1（推荐）：打补丁 —— 能保留上游新改动

补丁是全部 52 项改造的真实 diff（基线 = `FORK_BASE`，默认 `745c82d9`），
同步上游后应用它，双方改动都保留，不会拿旧快照覆盖作者的新代码：

```sh
cd <仓库根目录>
git apply --3way Update/docs/fork-patches/0001-dynamic-toolchain-paths.patch
```

补丁件数与 `Update/files` 文件数一致（`check.sh` 的 D2/D10 会校验，不靠人眼数）：
基线已存在的 38 个文件出 diff，我方新增的 4 个（`bin/{apktool,baksmali,smali}`、`ToolchainPathResolver.kt`）
以新建文件形式包含，清单见 `BASE-ABSENT.txt`。

`--3way` 与上游改到同一处时留下 `<<<<<<< ours` / `>>>>>>> theirs` 冲突块，**双方改动都在里面**，
人工取舍后再 `git add`。刻意如此：宁可停下让人看，也不静默替人选一边。

### 方式 2：一键还原（带安全防护）

`restore.sh` 不是无条件 `cp -f`。它会对着基线逐文件判定，而基线有两个来源（自动选择）：

1. `git` 里的 `FORK_BASE` 提交（默认 `745c82d9`）；
2. `Update/base/` 整文件快照——当历史被重建、基线提交对象已不存在时自动退化到这份，
   所以判定不依赖 git 历史，只要 `Update/` 还在就能识别「上游到底改没改过这个文件」。

| 状态 | 含义 | 动作 |
|---|---|---|
| `ALREADY_OK` | 当前已与快照一致 | 跳过 |
| `restored` | 当前与基线一致（上游没动过） | 覆盖，安全 |
| `CONFLICT` | 当前既非基线也非快照（上游已改） | **拒绝覆盖**，提示改用补丁 |
| `NEW_*` | 基线无此文件 | 不存在则落地；已存在且不同则拒绝 |

```sh
cd <仓库根目录>
sh Update/restore.sh
```

出现 `CONFLICT` 时不会只丢一句提示，而是当场打印两边改动，供你在**新代码里重新定位**：

```
  CONFLICT  : app/src/main/assets/scripts/taixu-build.sh
              ── 上游相对基线的改动（前 14 行）──
              @@ -388,3 +388,4 @@   +# upstream hotfix …
              ── 我方在该文件的改动点（补丁 hunk）──
              @@ -31,11 +31,11 @@   （hunk 头尾带所属函数，便于定位）
```

退出码：`0` = 全部就绪/已安全重放；`2` = 有冲突待处理（脚本可感知，不会假装成功）。
确需强制覆盖（会丢上游改动）：`FORCE=1 sh Update/restore.sh`。

> 为什么不再默认整文件覆盖：实测可复现——上游曾修改 `setup_jadx.sh` 后，
> 旧版 `restore.sh` 的 `cp -f` 会把作者新增行静默抹掉；新版会拦下来。

### 方式 3：单文件对比

```sh
diff -u app/src/main/assets/scripts/setup_rust.sh \
        Update/files/app/src/main/assets/scripts/setup_rust.sh
```

## 同步上游后必须做的事（顺序固定）

```sh
# 0. 备份存档（Update/ 被 .git/info/exclude 忽略，不进 git，最容易被误删）
cp -a Update /tmp/Update.bak.$(date +%m%d-%H%M)

# 1. 回到自己仓库的最新源码（含已推送的改动）
git fetch origin && git reset --hard origin/main

# 2. 重放改造：逐文件判定，上游动过的文件不覆盖，只打印冲突点
sh Update/restore.sh          # 退出码 2 = 有冲突，按打印的 hunk 重新定位

# 3. 校验改造意图仍成立（39 项断言，含基线/补丁自洽）
sh Update/check.sh
```

1. 先 `restore.sh` 还原改造（上游改过的文件会停下来等你，不会硬写）
2. 再按 `CHECKLIST.md` 逐条核验改造点是否仍成立
3. **只核验清单里列出的改造点**——未改造的代码一律不动，交给上游开发者维护

## 改造原则（长期有效）

1. 只改**必须改**的地方，不顺手重构无关代码，降低与上游冲突的概率
2. 改造点必须可被 `grep` 精确定位（见 `MANIFEST.txt` 的定位关键词）
3. 每次改造同步更新 `MANIFEST.txt` / `CHECKLIST.md` / `Progress.md`
4. 脚本里不写死依赖版本号，一律动态获取（见 `Progress.md`「工具链版本动态化」一节）

---

## 关于工作目录改名（重要）

本存档**不依赖目录名**，可以安全改名。

### 为什么安全

| 依赖项 | 是否受改名影响 |
|---|---|
| `restore.sh` / `check.sh` | ❌ 不影响：用 `ROOT="$(cd "$(dirname "$0")/.." && pwd)"` 相对定位 |
| `Update/files/` 相对路径 | ❌ 不影响：全部是仓库内相对路径 |
| git 仓库（`.git`） | ❌ 不影响：跟着目录一起移动 |
| `origin` remote | ❌ 不影响：`https://github.com/ChatGFW/taixu.git` |
| Kotlin / Gradle / 构建脚本 | ❌ 不影响：实测零处硬编码目录名 |
| `.taixu-project.properties` | ❌ 不影响：只有 `type/source/importedAt`，无路径 |

**实测结论**：全仓库只有 5 个文件出现过目录名字面量，且都在文档示例与注释里，已统一改为 `<仓库根目录>`。

### 改名步骤

```sh
# 方式一：命令行
mv /workspace/<旧名> /workspace/太墟源码改造

# 方式二：在太墟工作区管理里重命名（推荐，会自动更新工作区绑定）
```

改名后验证：

```sh
cd /workspace/太墟源码改造
git status                       # 应正常显示分支
sh Update/check.sh               # 应 33/33 通过（A/B/C/D1-D10/E/F）
git remote -v                    # 应指向 ChatGFW/taixu
```

### 改名后唯一需要做的事

在太墟里重新选择一次工作区目录（让它指向新名字），之后一切照旧。

**不要**因为改名而重新 clone —— 那会丢掉 `Update/` 之外未推送的本地改动。

---

## 存档救援：`Update/` 被删 / 换机 / 重新 clone 之后

`Update/` 不进 `main`（被 `.git/info/exclude` 忽略），但整套存档已推到独立归档分支：

- 分支 `archive/update-2026-11`（孤立分支，**单一提交**；112 个文件 = 52 files + 48 base + 1 份补丁（含 52 段） + 11 份文档/脚本）
- 与 `main` 无父子关系（orphan），不影响 `main` 历史，也不会被 merge 进去

### 找回来（浅克隆也够，不需要完整历史）

```sh
git fetch --depth 1 origin archive/update-2026-11
git checkout FETCH_HEAD -- Update/
git reset -- Update/                          # 关键：解除暂存，让 Update/ 回到未跟踪
echo 'Update/' >> .git/info/exclude            # 新 clone 没有这条忽略规则，必预
```

### 两个实测出来的坑

| 坑 | 现象 | 原因 |
|---|---|---|
| 恢复后直接 `git reset --hard` | `Update/` 整个被删 | `git checkout <ref> -- path` 会把文件写进 index，`reset --hard` 会清掉它管辖范围的文件 |
| 新 clone 后 `git status` 一堆 `?? Update/` | 易被 `git clean -fd` 或误 `add` 带进 main | `.git/info/exclude` 是本地的，不随 clone 传递 |

顺序固定：**fetch → checkout → `git reset -- Update/` → 补 exclude**，做完再碰 `reset --hard` / `clean`。

### 同步上游后它会不会没

不会。`git reset --hard origin/main`、`git checkout`、`git switch` 只作用于当前分支与工作区，**不删远端分支**；
重新 `clone` 时浅克隆默认只拉 `main`（`--depth` 隐含 `--single-branch`），看不到归档分支属 git 正常行为，
分支仍在 GitHub 上，一条 fetch 就回来。唯一会丢的情况是主动删远端分支或删仓库。

### Update/ 改动后怎么更新存档

一条命令：`sh Update/sync-archive.sh`（内容未变会自动跳过；只动 `archive/*` 分支，不碰 `main`、不动工作区）

**默认行为是与上一条归一化**：每次更新都把归档分支重新压成单条提交（内容就是存档本体，迭代过程不留痕）。
因为会重写历史，推送用 `--force-with-lease` 锁定推送前的 sha，被人抢先推过就拒绝，不会误覆盖。
想保留演变过程时加 `--keep-history` 改回追加模式。被压掉的上一版存在本地 `refs/backup/archive-last`，可回滚。

```sh
sh Update/sync-archive.sh                      # 默认分支，归一化为单条提交
sh Update/sync-archive.sh -m "说明"            # 自定义提交信息
sh Update/sync-archive.sh --keep-history       # 追加一条，不压平
BR=archive/其它名字 sh Update/sync-archive.sh  # 换分支名
```

退出码：`0` = 已推送或无需推送；`1` = 前置条件不满足/推送失败（提交已落本地 ref，重试只需 `git push`）。
它内部等价于下面的手动步骤，保留供排查：

```sh
export GIT_INDEX_FILE=/tmp/idx.archive
rm -f "$GIT_INDEX_FILE"; git read-tree --empty; git add -f Update
C=$(git -c user.name=taixu -c user.email=taixu@local commit-tree "$T" -m "docs(update): 存档更新")
git update-ref refs/heads/archive/update-2026-11 "$C"
unset GIT_INDEX_FILE
git push --force-with-lease origin archive/update-2026-11
```

注意两点与脚本的差异：上面的 `commit-tree` **不带 `-p`**（即归一化）；想追加历史就加 `-p "$(git rev-parse archive/update-2026-11)"`，并去掉 push 的 `--force-with-lease`。
