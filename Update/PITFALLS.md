# 踩坑记录（Pitfalls）—— 下次直接照做，别再踩

按「坑 → 现象 → 根因 → 正确做法」记录，每条都有实证。

---

## 坑 1 ★★★ 架构守卫拦 CI：单文件 ≤ 400 行

**现象**

```
> Task :architectureCheck FAILED
AboutCommunityScreen.kt 共 490 行，超过棘轮基线 476（只许缩减不许上涨）
```

注意：**Kotlin 编译已经过了**，是架构守卫在编译之后把它拦下的。

**根因**

项目自带 `architectureCheck` Gradle 任务（`build.gradle.kts`），读 `architecture-policy.json`，检查 4 项：

| 检查项 | 规则 |
|---|---|
| 模块依赖白名单 | 只能依赖允许的模块 |
| 依赖无环 | 模块间不能循环依赖 |
| import 黑名单 | 禁止跨层 import |
| **文件尺寸棘轮** | 单文件 ≤ 400 行；存量超限登记在 `.architecture-baseline.json`，**只许缩减不许上涨** |

`architectureBaselineSync` 只能登记**存量**文件，**新文件超限会被拒绝登记**。

**正确做法**

写新功能时就按 400 行预留拆分，不要等 CI 报错：

| 类型 | 拆法 |
|---|---|
| 数据模型 + 大清单 | 模型类（data class + enum）+ 目录对象（清单常量） |
| ViewModel 过大 | 把**无状态动作**抽成独立类（下载/解压/写文件），**状态推进留在 ViewModel** |
| 序列化/编解码 | 抽成 `XxxCodec` object |
| 大页面 | 对话框抽成 `XxxDialogs.kt`，入口行抽成 `XxxEntry.kt` |
| 棘轮文件（如 AboutCommunityScreen 476 行） | 新增内容必须抽到别处，保证行数不涨 |

---

## 坑 2 ★★★ 机械切割拆文件 → 引用私有成员，必编译失败

**现象**

把 `install()` 整段从 ViewModel 切到新类后，新类里出现：

```kotlin
patch(spec.id) { ... }   // ViewModel 的私有方法
_state.value.rows...     // ViewModel 的私有字段
```

→ `Unresolved reference`，一堆编译错误。

**根因**

`install()` 看着像「安装逻辑」，实际是**紧耦合 ViewModel 状态**的编排代码，整体搬走必然炸。

**正确做法**

拆之前先 `grep` 目标函数引用了哪些外部符号：

```sh
grep -nE "_state|patch\(|loadSpecs|private fun" 目标文件
```

- 有状态引用 → **只抽无状态的纯动作**（下载、解压、拼脚本、写文件），状态推进留在原类
- 拆分后必须再 `grep` 一次，确认新文件里零状态引用

---

## 坑 3 ★★★ Kotlin 原始字符串里的 `$变量` 会被当模板

**现象**

```
e: ToolchainPathResolver.kt:91:55 Unresolved reference 'ANDROID_HOME'.
> Task :core:model:compileKotlin FAILED
```

**根因**

注释里写了 `② $ANDROID_HOME/ndk/<ver>`，但这段注释在 `"""` 原始字符串内部（shell 脚本模板），
Kotlin 把 `$ANDROID_HOME` 当成模板变量插值，而这个符号不存在。

**正确做法**

| 场景 | 写法 |
|---|---|
| 想输出 shell 变量给 shell 展开 | `${'$'}VAR` |
| 只是注释/文档文字，不需要 `$` | 直接删掉 `$` |
| 确实要 Kotlin 常量插值（如 `CACHE_DIR`） | `$CACHE_DIR`（常量存在就合法） |

**另一条同源约束**：这些模板用 `sh -c '...'` 包裹时，raw string 内**不能出现单引号**（会提前闭合外层引号），
需要引用时用 `-name "*Java*/bin/java"` 这类双引号或直接不用引号。

**自检命令**（写完必跑）：

```sh
grep -nE '\$[A-Z][A-Z_]{2,}' 你的文件.kt | grep -vE "\\\$\{'\\\$'\}"
```

---

## 坑 4 ★ 提交前必须检查「漏文件」

**现象**

某次只提交了 ViewModel 和 Screen，漏了同一次改动里的被调用方（client / 探针 / 测试）→
调用方传了参数但被调用方没这个参数 → **编译失败**。

**正确做法**

```sh
git status --porcelain          # 看全部改动
git diff --cached --name-only   # 提交前核对暂存清单
```

按模块成对提交：改了调用方就要提交被调用方；改了探针字符串就要同步对应测试断言。

---

## 坑 5 ★ 本地编译验证不了，不代表代码有问题

**现象**

本地 `./gradlew` 报：

```
Cannot find a Java installation matching: {languageVersion=17}
```

**根因**

项目 toolchain 要求的 JDK 主版本与本机安装的不一致（或 `java.installations.paths` 未指向）——
这是**环境缺失**，不是代码错误。

**正确做法**

- 用 CI 验证（`workflow_dispatch` 触发 `release.yml`）
- 或本地补装对应 JDK 后加 `-Porg.gradle.java.installations.paths=/usr/lib/jvm/<jdk 目录>`
- 另：`--offline` 模式会因缺插件缓存报 `Plugin [id: '...'] was not found`，同样是环境问题

---

## 快速自检清单（每次改完必跑）

```sh
# 1. 文件行数 ≤ 400（棘轮文件不超基线）
for f in <改动文件>; do echo "$(wc -l < $f) $f"; done

# 2. Kotlin raw string 里的裸 $变量
grep -nE '\$[A-Z][A-Z_]{2,}' <文件> | grep -vE "\\\$\{'\\\$'\}"

# 3. 拆分后的新文件不应引用原类私有成员
grep -nE '_state|patch\(' <拆出的文件>

# 4. 括号平衡
python3 -c "s=open('<文件>').read(); print(s.count('{')-s.count('}'), s.count('(')-s.count(')'))"

# 5. 提交前核对暂存清单
git diff --cached --name-only

# 6. 工具链版本号不得写死（坑 9）
rg -n '/opt/(gradle|android-sdk/build-tools)-[0-9]' --glob '*.kt' --glob '*.sh' --glob '!Update/**'
rg -n 'File\("/(opt|usr|etc|root)' --glob '*.kt'
```

---

## 坑 6 ★★ 从归档分支恢复 `Update/` 后直接 `reset --hard` → 存档被删

**现象**

```sh
git fetch --depth 1 origin archive/update-2026-11
git checkout FETCH_HEAD -- Update/     # 91 个文件恢复成功
git reset --hard origin/main           # ← Update/ 整个消失
```

**根因**

`git checkout <ref> -- <path>` 不只写工作区，还把文件写进 index；`reset --hard` 以 index 为准重置工作区，
于是刚恢复的文件被视为「该删」。

**正确做法**

恢复后立刻解除暂存，让 `Update/` 回到未跟踪状态，之后 `reset --hard` / `checkout` 都不再波及：

```sh
git checkout FETCH_HEAD -- Update/
git reset -- Update/
```

实证：解除暂存前 `reset --hard` → 文件数 0；解除后 → 91 个全部仍在。

---

## 坑 7 ★★ 新 clone 的仓库没有 `Update/` 忽略规则

**现象**：恢复后 `git status` 出现 91 条 `?? Update/...`，`git add -A` / `git clean -fd` 会顺手把存档带进提交或删掉。

**根因**：`.git/info/exclude` 是本地文件，不随 clone/fetch 传递；原工作区当初手工加过 `Update/`，新 clone 没有。

**正确做法**

```sh
echo 'Update/' >> .git/info/exclude
```

实证：补规则前 `git status` 91 条，补后 0 条。

---

## 坑 8 ★★ 用 write/edit 重写脚本会丢掉执行位，别用 `./脚本` 调用

**现象**：`chmod +x Update/sync-archive.sh` 后 `ls -l` 是 `-rwx------`，但只要再用 write/edit 改它一次，
权限就回到 `-rw-------`；git 里记录的是 `100644`，clone 回来也不是可执行。

**根因**：这类文件重写工具是整文件覆写，不保留原权限位；沙箱 umask 又使新文件默认 600。
阶段内 `git add` 抓到的就是当时的权限，于是 mode 一路是 100644。

**正确做法**

- 一律用 `sh Update/<脚本>.sh` 调用，不写 `./Update/<脚本>.sh`，也不依赖可执行位（README、CHECKLIST 已统一）
- 断言不写「文件可执行」，改写「能不能被 sh 解析」：
  ```sh
  expect "D11 归档同步脚本语法通过" "$(sh -n "$SA" 2>/dev/null && echo 1 || echo 0)" 1
  ```

实证：修正前在全新 clone 里 `check.sh` 报 `36/37`（D11 挂）；改成 `sh -n` 判定后 `37/37`。

---

## 坑 9 ★★★ 工具链版本号一律动态探测（内核规则，勿再问）

**规则本身**

任何工具链路径都不能写死版本号：`gradle-8.14.2`、`build-tools/35.0.0`、`android-34`、`ndk 29.0.x` 都是错的。
用户机器上的实际版本随时会变，写死等于把代码钉在某个瞬间。

四个环节都要动态，缺一处就会「打回旧版」或「删不掉」：

| 环节 | 写死后的后果 |
| :--- | :--- |
| 安装 / provision | 反复重下旧版制品 |
| 环境检查 | 把新版本判成「不合规」 |
| 修复 / 重生成 profile | 把新版环境重写成旧版 |
| **卸载删除清单** | 写死旧版删不到；写死当前版等于把升级绑死在代码里 |

卸载这一条尤其容易想反：写死旧版本号看着「安全」，实际效果是「只有被写死的那个会被删，其它版本反而不在删除范围内」——
用户装的是新版，卸载却删不掉任何东西。正确做法是删「实际存在的那个」，而不是删「代码里记着的那个」。

**探测必须在「能看到目标路径」的进程里做**

这是最容易翻车的地方：`purgePaths` / 安装清单最终由 **PRoot 沙箱内的 shell** 执行，
而 Kotlin 代码跑在 **App 进程**，进程内根本看不到沙箱的 `/opt`。

```kotlin
// ✗ 错误：App 进程 listFiles 沙箱路径，恒为 null → 卸载一条 rm 都不生成
File("/opt").listFiles()

// ✓ 正确：Kotlin 只做白名单校验，通配交给沙箱 shell 在卸载执行期展开
purgePaths = listOf("/opt/gradle-*")
steps += "rm -rf -- /opt/gradle-[0-9]*"
```

通配用 `[0-9]*` 而不是裸 `*`：`/opt/gradle-9.9.0` 命中，`/opt/gradle-evil` 不命中。

**标准探测写法（POSIX sh）**

```sh
_ver=$(ls -d /opt/gradle-[0-9]* 2>/dev/null \
    | sed 's|^/opt/gradle-||' \
    | grep -E '^[0-9]+(\.[0-9]+)+$' \
    | sort -t. -k1,1n -k2,2n -k3,3n \
    | tail -n 1)
test -z "$_ver" || TARGET="/opt/gradle-$_ver"
```

取不到就 `INFO ... action=skip`，不要回退到任何写死的旧版本号。

**允许固定的例外（只有两类）**

- `sha256` 绑定具体制品的下载配方（如 `tools.json` 里 x86_64 QEMU 兼容层，改动态会导致校验失败）
- 离线归档里只有唯一一份的包（如 `setup_android_core.sh` 的 `platform-34-ext7_r03.zip`）

**自检**

```sh
# 源码里不应出现写死的工具链版本路径
rg -n '/opt/(gradle|android-sdk/build-tools)-[0-9]' --glob '*.kt' --glob '*.sh' --glob '!Update/**'
# Kotlin 不应直接枚举沙箱路径
rg -n 'File\("/(opt|usr|etc|root)' --glob '*.kt'
```

实证：这两条命令在改造后均为空（仅 `ToolchainPathResolver.kt` 的历史注释提及旧版本号）。
