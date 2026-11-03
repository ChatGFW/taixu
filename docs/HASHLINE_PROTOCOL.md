# Hashline 工具协议草案（v0）

> 状态：**M12 完成（空文件尾换行 + §11 边缘测）**。对标 [oh-my-pi / omp Hashline](https://blog.can.ac/2026/02/12/the-harness-problem/)。  
> 实现：锚点算法、ops 编辑、SSH/检索挂锚、闸门、跨启动计数、设置观测、legacy 默认关、`0:00` 文件头哨兵。  
> 目标：让模型用**内容哈希锚点**表达局部修改，少复述原文、少撞空白、过期锚点 fail-closed。  
> 非目标：不引入新顶层工具名、不替换 `write` 全量覆写、不要求模型复述 `oldText`。

---

## 0. 一句话

`read`（及带行号的检索命中）在每行前挂上稳定短哈希；`edit` 用这些锚点声明「改哪几行、改成什么」；应用前按磁盘现文重算哈希，任一锚点失配则整次拒绝，并提示重新 `read`。

---

## 1. 动机与现状缺口

| 现状 | 问题 |
| :--- | :--- |
| `edit(path, oldText, newText)` + Exact→LineTrimmed→BlockAnchor | 模型仍须复述 `oldText`；空白/缩进偏差靠降级策略兜底，弱模型仍常失败并烧重试 token |
| `read` 分页有范围头，但正文**无行号、无内容指纹** | 模型只能靠记忆或再猜字符串定位 |
| `expected_sha256` 只服务产物核验 | 普通源码编辑没有「行级新鲜度」 |

Hashline 把「定位」从「复述内容」改成「引用上次看到的行指纹」。

---

## 2. 设计原则

1. **扩展现有工具面**：仍叫 `read` / `edit`；用参数形态区分 hashline 与 legacy `oldText`，避免再占一个 schema 槽位。
2. **Fail-closed**：任一锚点与磁盘现文不符 → 整次 `edit` 失败，不写盘、不半应用。
3. **锚点不可伪造为免审通道**：hashline 编辑仍走既有 PLAN / 审批 / Checkpoint / 脱敏；与 `oldText` 同级。
4. **与行号解耦校验**：行号仅辅助人类与模型阅读；**唯一信任源是 content hash**（文件被插入/删除后行号漂移，hash 仍可判陈旧）。
5. **双轨（legacy 默认关）**：带 `ops` 走 Hashline；`oldText`/`newText` 仅在设置开启后走 `TextReplacerEngine`。二者互斥。
6. **远程/本地同一契约**：经 `SessionExecutionEnvironments.files` 的读/写语义一致；远程未实现时明确失败（与现有文件工具一致）。

---

## 3. 行锚点（Line Anchor）

### 3.1 定义

对文件按**逻辑行**切分后，第 `n` 行（1 起始）的锚点写作：

```text
{n}:{h}
```

- `n`：十进制行号，无前导零，范围 `1..lineCount`。
- `h`：该行 **规范化内容** 的短哈希，小写十六进制，**固定 2 字符**（v0）。

示例：`12:a3`、`1:0e`。

### 3.2 规范化（hash 输入）

对原始行字节串（UTF-8 解码后的 Kotlin/Java `String` 行）做：

1. 去掉行尾 `\r`（兼容 CRLF 文件在 `split('\n')` 后残留的 CR）。
2. **不** trim 行首缩进、**不**折叠内部空白——缩进是内容的一部分。
3. 空行的规范化结果是空串 `""`。
4. 哈希输入 = 规范化后的 Unicode 码点序列按 UTF-8 编码的字节。

> 与 omp 博文一致：模型不需要复述空白；锚点已绑定「当时看到的那一行」。

### 3.3 哈希算法（v0）

```text
h = hex2( FNV-1a-32(utf8(normalized_line)) & 0xFF )
```

- 使用 **FNV-1a 32-bit**，取低 8 bit，格式化为 **固定 2 位**小写 hex（`00`–`ff`）。
- 碰撞概率：同文件内对编辑定位足够；**不以抗碰撞为安全边界**——安全性靠「失配则拒绝」+ 工作区权限，不靠哈希不可预测性。
- 算法标识写入读结果头：`hash=fnv1a16`（历史命名；实现为低 8 bit），便于日后升级（见 §10）。

伪代码：

```kotlin
fun lineHash(normalized: String): String {
    var hash = 0x811c9dc5u
    for (b in normalized.toByteArray(Charsets.UTF_8)) {
        hash = hash xor b.toUByte().toUInt()
        hash *= 0x01000193u
    }
    return (hash and 0xFFu).toString(16).padStart(2, '0')
}
```

### 3.4 切行规则（与现网 `WorkspaceFileAccess` 对齐）

- 按 `\n` 切分；若全文以 `\n` 结尾，最后空段不计入 `lineCount`（与现 `totalLines` 计算一致）。
- 二进制 / 非 UTF-8：`read` 保持现行为（失败或走图片通道）；**不对二进制发锚点**。
- 单行超长：仍计入一行；hash 覆盖整行规范化内容（若单行极大导致输出膨胀，靠现有 `limit` 分页，不在 v0 做列窗）。

---

## 4. `read` 输出契约

### 4.1 何时挂锚点

| 条件 | 行为 |
| :--- | :--- |
| 文本 `read`（含 offset/limit 分页） | **默认**输出 Hashline 正文（见 §4.2） |
| `query` 检索命中行 | 命中行带锚点；路径与行号格式见 §4.4 |
| 图片 / `expected_sha256` 产物核验失败 | 不挂锚点 |
| 调用方显式 `anchors=false` | 兼容旧式「无行号正文」（迁移期保留，默认 `true`） |

小文件无参全文读取：同样挂锚点（改变现状「直接 dump 原文」——这是有意的协议升级）。

### 4.2 正文格式

范围头（扩展现有 `rangeHeader`）：

```text
[文件 /workspace/src/Main.kt 共 120 行，当前显示第 10-25 行；hash=fnv1a16；内容未完，继续读取请用 offset=26]
10:a3|fun main() {
11:f1|    println("hi")
12:0e|}
...
```

规则：

- 每行：`{n}:{h}|{raw_line_without_newline}`
- `|` 后是**原始行文本**（已去尾 `\r`，但保留缩进）；**不**把 `|` 再转义——若原文含 `|`，原样保留（解析锚点只看第一个 `|`）。
- 模型编辑时**不得**把 `{n}:{h}|` 前缀写回文件；该前缀仅存在于工具输出通道。

### 4.3 元数据（可选，给宿主 UI / 测试，不进模型上下文）

`ToolResult.metadata` 可增加：

| key | 含义 |
| :--- | :--- |
| `hashline` | `"fnv1a16"` |
| `anchor_first` / `anchor_last` | 本页首末锚点字符串，如 `10:a3` |
| `content_sha256` | 可选：整文件 SHA-256，便于与 Checkpoint 对账 |

模型可见文本仍以 §4.2 为准。

### 4.4 检索命中

`read(path, query=...)` 命中行建议：

```text
src/Main.kt:11:f1|    println("hi")
```

即 `{relpath}:{n}:{h}|{line}`。目录检索多文件时每行一条；仍受 `max_results` 约束。

---

## 5. `edit` 输入契约（Hashline 模式）

### 5.1 互斥

一次调用只能是下列之一：

| 模式 | 必填 | 禁止同时出现 |
| :--- | :--- | :--- |
| **legacy** | `path`, `oldText`, `newText` | `ops` |
| **hashline** | `path`, `ops`（非空数组） | `oldText`, `newText` |

混用 → 参数错误，不读盘。

### 5.2 Schema（草案）

```json
{
  "type": "object",
  "properties": {
    "path": { "type": "string" },
    "oldText": { "type": "string", "description": "legacy：精确/容错替换的旧文本" },
    "newText": { "type": "string", "description": "legacy：新文本" },
    "ops": {
      "type": "array",
      "description": "hashline：按数组顺序应用的锚点操作；整批原子提交",
      "minItems": 1,
      "maxItems": 64,
      "items": {
        "type": "object",
        "properties": {
          "op": {
            "type": "string",
            "enum": ["replace", "insert_after", "delete"]
          },
          "start": {
            "type": "string",
            "description": "起始锚点 n:h；replace/delete 必填",
            "pattern": "^[1-9][0-9]{0,6}:[0-9a-f]{2}$"
          },
          "end": {
            "type": "string",
            "description": "结束锚点（含）；省略则=start；须与 start 同行号序且 end.n >= start.n",
            "pattern": "^[1-9][0-9]{0,6}:[0-9a-f]{2}$"
          },
          "after": {
            "type": "string",
            "description": "insert_after：插入到该锚点行之后；文件开头用 0:00",
            "pattern": "^(0:00|[1-9][0-9]{0,6}:[0-9a-f]{2})$"
          },
          "content": {
            "type": "string",
            "description": "replace / insert_after 的新文本；可含多行；不要带 n:h| 前缀。delete 禁止带 content"
          }
        },
        "required": ["op"]
      }
    }
  },
  "required": ["path"]
}
```

工具描述建议改为：

> 优先：先 `read` 拿到 `n:h|` 行，再用 `ops` 按锚点修改（不必复述旧文本）。锚点过期会失败，须重新 `read`。也支持 legacy `oldText`/`newText`（须唯一匹配）。

### 5.3 操作语义

设当前磁盘行列表为 `L[1..N]`，每行有现算锚点 `A(i)`。

#### `replace`

- 需要 `start`；`end` 默认 `start`。
- 校验：`A(start.n)==start` 且 `A(end.n)==end`，且 `start.n <= end.n`。
- 效果：删除闭区间 `[start.n, end.n]` 的行，插入 `content` 按 `\n` 切成的行（`content` 末尾多余单个 `\n` 不产生额外空行，与 legacy 尾随换行习惯一致）。
- `content == ""`：等价于删除该区间（允许；与显式 `delete` 同效）。

#### `insert_after`

- 需要 `after`；禁止 `start`/`end`/`content` 缺失。
- 校验：`A(after.n)==after`（哨兵除外）。
- 效果：在 `after.n` 之后插入 `content` 切分出的行。
- 特殊：`after` 允许为 `0:00`（`HashlineAnchors.FILE_HEAD`）表示「文件开头之前插入」；不校验磁盘行内容，空文件也可写入。仅可用于 `insert_after.after`；`replace`/`delete` 的 `start`/`end` 禁止 `0:00`。
- 空原文件：若任一 `content` 以换行结束，写出结果保留文件尾换行（不因此多出空行）；非空原文件仍继承原文件是否以 `\n` 结尾。

#### `delete`

- 需要 `start`；`end` 默认 `start`；禁止 `content`。
- 校验同 `replace` 区间。
- 效果：删除闭区间行。

### 5.4 多 op 原子性与重叠

1. **校验阶段**（只读）：按磁盘现文校验**全部** op 的锚点；任一路失败 → 整批失败。
2. **应用阶段**：将所有 op **翻译为基于「校验快照行号」的区间编辑**，按 `start.n` **降序**应用（从文件尾改到头），避免行号漂移。
3. **重叠拒绝**：任意两 op 的目标行区间（`insert_after` 的目标视为 `after.n` 后的空缝，不与邻行区间重叠冲突，除非另一 op 删除了 `after` 行）若行号区间相交 → 参数错误。  
   - `insert_after` 与 `replace/delete`：若后者区间包含 `after.n`，视为冲突。
4. 单次 `ops.length` ≤ 64；单次 `content` 总 UTF-8 字节计入现有 `MAX_WRITE_BYTES` / `MAX_EDIT_BYTES` 预算。

### 5.5 换行风格

写出时：若原文件任一现存行以 `\r` 结尾（CRLF），新插入行使用 `\r\n`；否则 `\n`。不在 v0 做「按邻行推断缩进」——`content` 必须自带目标缩进（模型从 `read` 可见缩进）。

---

## 6. 失败与纠错文案

| 错误码（对内） | 模型可见要点 |
| :--- | :--- |
| `HASHLINE_STALE` | 列失配锚点；要求立刻 `read` 同 path（建议带同一 offset 窗口）；禁止改用猜测 `oldText` 盲试同一处 |
| `HASHLINE_BAD_ANCHOR` | 格式非法 / 行号越界 |
| `HASHLINE_OVERLAP` | ops 区间冲突 |
| `HASHLINE_MIXED_MODE` | 同时传了 `ops` 与 `oldText`（结构化失败，不抛异常） |
| `HASHLINE_EMPTY_OPS` | `ops` 为空数组 |
| `HASHLINE_MISSING_ARGS` | 既无 `ops` 也无 `oldText`/`newText` |
| `HASHLINE_OPS_DISABLED` | 设置关闭了 ops 编辑 |
| `HASHLINE_LEGACY_DISABLED` | 设置关闭了 legacy 编辑 |
| legacy 失败 | 保持现有「唯一匹配 / 未找到」+ 反思提示 |

失败码同时写入 `metadata.hashline_error` 与 `ToolResult.errorCode`；`withRecoveryAdvice` 对 STALE 强制建议 `read`，且 `retrySameArguments=false`。

`HASHLINE_STALE` 示例：

```text
锚点已过期：期望 11:f1，磁盘现为 11:9c（文件自上次 read 后已变化）。
请重新 read("/workspace/src/Main.kt", offset=1, limit=80) 获取新锚点后再 edit。
整次修改未写入。
```

---

## 7. 与现有子系统的接缝

| 子系统 | 行为 |
| :--- | :--- |
| **审批 / PLAN** | `edit` 名不变；风险分级仍按写工具；审批摘要 / 状态栏 / 步骤头 / Diff 卡展示「Hashline N ops」 |
| **Checkpoint / rewind** | 仍走 `snapshots.before/after`；hashline 成功写入与 legacy 相同 |
| **TurnChangeCards** | 不改；仍按写工具触碰路径出卡片 |
| **ToolCallLoopDetector** | ✅ 同 path 连续 `HASHLINE_STALE`（默认 ≥2，含改 ops 盲试）拦截并强制 `read`；成功 read 同 path 清零 |
| **ContextWindowPolicy** | `edit` 输出预留不变；成功回执保持短文本 + `DIFF_STAT`，diff 仍只进 metadata |
| **ToolRoundDispatcher** | hashline `edit` 仍属 mutation，与其它写工具互斥 |
| **远程 Files** | ✅ 走 `readTextForEdit` + `write`（SSH 已实现）；未实现环境返回明确失败文案 |
| **MCP Harness 写工具** | `allowWriteTools` 门控不变；schema 同步扩展 |
| **TextReplacerEngine** | 仅 legacy 路径；hashline **不**走模糊匹配（锚点已是精确定位） |

---

## 8. 成功回执

```text
已修改 /workspace/src/Main.kt（匹配策略：hashline，操作 2，替换区间 3 行）
DIFF_STAT: +5 -3
```

`metadata.strategy = "hashline"`；`metadata.hashline_ops` = 操作条数；`metadata.diff` 仍为 Unified Diff（供前端）。

---

## 9. 迁移与开关

### 9.1 阶段

| 阶段 | 行为 |
| :--- | :--- |
| **M0 草案** | 本文档；单测可先落地纯函数 `HashlineAnchors`（hash / 格式化 / 应用） |
| **M1** | ✅ 本地：`read` 默认挂锚；`edit` 接受 `ops`；legacy 并行；工具 schema 已更新 |
| **M2** | ✅ SSH `read` 对齐锚点；检索命中带锚；`tools.md` 强调优先 ops |
| **M3** | ✅ 闸门 + DataStore + **跨启动累计计数** + **设置页「Hashline 编辑」三开关**；**legacy 默认仍开启**（默认关闭待观测；计数启发式可提示关闭） |
| **M4** | ✅ `ToolCallLoopDetector`：同 path 连续 `HASHLINE_STALE`（即使 ops 不同）强制先 `read`；失败 metadata 对齐 `HASHLINE_*` |
| **M5** | ✅ 审批摘要 / `ToolStatusDescriber` / 步骤头 / Diff 卡展示「Hashline N ops」与 ops 预览 |
| **M6** | ✅ 成功回执对齐协议（`strategy`/`hashline_ops` metadata + 替换区间文案）；`MIXED_MODE`/`EMPTY_OPS` 结构化失败 |
| **M7** | ✅ `hashline_error` → `ToolResult.errorCode`；Hashline 专用 `ToolRecoveryAdvice`（STALE 强制 read） |
| **M8** | ✅ 设置页观测提示可「一键关闭 legacy」；搜索文案改为跨启动累计 |
| **M9** | ✅ **legacy 默认关闭**（DataStore / `HashlinePolicy.DEFAULT` / 工具文案）；设置可手动开启 |
| **M10** | ✅ 反向观测：`legacy_disabled` 计数展示；弱模型仍狂发 oldText 且 ops 未跑通时软提示「一键临时开启 legacy」 |
| **M11** | ✅ `insert_after.after=0:00` 文件头哨兵（含空文件）；schema / tools 文案同步 |
| **M12** | ✅ 空文件经 `0:00` 写入时保留 content 尾换行意图；补齐 §11 边缘单测（同行哈希 / 多行 replace / 无尾换行 / backend 空文件） |

### 9.2 设置项

路径：设置 → Agent 执行与上下文 → **Hashline 编辑**（`HashlineSettingsBlock`）；设置搜索关键词 `hashline` / `行锚` / `oldText`。

| 键 | AgentPreferences | 默认 | 作用 |
| :--- | :--- | :--- | :--- |
| `agent_hashline_read_anchors_default` | `hashlineReadAnchorsDefault` | `true` | `read` 未显式传 `anchors` 时是否挂锚 |
| `agent_hashline_edit_ops_enabled` | `hashlineEditOpsEnabled` | `true` | 是否接受 `edit` 的 `ops` |
| `agent_hashline_legacy_edit_enabled` | `hashlineLegacyEditEnabled` | `false` | 是否接受 `oldText`/`newText`（未写键视为关闭） |

观测：同区块展示累计 `HashlineEditStats`（DataStore `hashline_edit_stats_v1`，跨启动保留；刷新 / 清零，含 `legacy_disabled` 被拒次数）；`shouldSuggestDisableLegacy()` 在 ops 够稳且 legacy 仍失败（且用户已手动开启）时给出软提示与一键关闭；`shouldSuggestEnableLegacy()` 在 legacy 关闭、模型仍大量被拒且 ops 未明显跑通时给出软提示与一键临时开启。**不自动改偏好**。失败结果 metadata / `errorCode` 可带 `HASHLINE_*`。

升级说明：从未写过该键的安装在 M9 起默认拒绝 legacy；曾显式开启/关闭的用户保留其键值。

不在 v0 做 per-model 强制——角色路由成熟后再绑。

---

## 10. 版本与升级

- 协议版本字符串：`hashline/0`（写入范围头 `hash=fnv1a16` 即隐含 v0）。
- 若升级到 3–4 hex 或换算法：新 `hash=` 值；**旧锚点一律 STALE**；不要求跨版本兼容。
- 会话压缩后历史里的旧 `read` 正文可能含过期锚点——这是预期的；模型必须以**本回合新鲜 read** 为准（可在工具描述强调）。

---

## 11. 测试清单（实现时必绿）

纯函数 / 无 API Key：

1. 空文件、单行无尾换行、CRLF、仅 `\n` 空行、行内含 `|`。
2. 同内容不同行号 → 同 `h` 不同 `n`；改一行空白 → `h` 变。
3. `replace` 单行 / 多行；`delete`；`insert_after`；多 op 降序应用。
4. 交错重叠 → `HASHLINE_OVERLAP`；错 hash → `HASHLINE_STALE`；不写盘。
5. 与 legacy 互斥参数。
6. 审批 / checkpoint 路径：hashline 成功触发 before/after；失败不触 after。
7. 分页 `read` 的锚点行号与绝对行号一致（不是页内 1..k）。

---

## 12. 明确不做（v0）

- 新工具名 `hash_edit` / `apply_hashline`
- 列级锚点、AST 级锚点（留给日后 `ast_edit`）
- 模糊锚点恢复（「hash 近邻」）——与 fail-closed 冲突
- 把锚点写进工作区文件或 git
- 在 `base`/shell 输出里自动注入锚点
- Windows 路径混用：仍统一 `/workspace/...` 展示

---

## 13. 实现落点（供开工）

| 组件 | 建议 |
| :--- | :--- |
| `runtime/environment/HashlineAnchors.kt` | hash、格式化、解析、`apply(ops)` 纯函数（本地/SSH 共用） |
| `harness/text/HashlineEditArgs.kt` | 工具 JSON → ops |
| `WorkspaceFileAccess` / `SshExecutionFiles.read` | 挂锚点正文；`anchors=false` 旁路 |
| `WorkspaceToolBackend` | 解析 `ops` vs legacy；`readTextForEdit` + apply + write |
| `WorkspaceTextSearch` | 命中行 `path:n:h|excerpt` |
| `ProviderClient.TOOLS` / `prompts/system/tools.md` | schema 与优先 ops 说明 |
| `HashlinePolicy` / DataStore / `HashlineEditStats` (+Codec) | 闸门、偏好键、跨启动累计计数 |
| 测试 | `HashlineAnchorsTest`、`WorkspaceToolBackendTest`、`WorkspaceTextSearchTest` |

---

## 14. 示例

**read**

```text
[文件 /workspace/hello.js 共 3 行，当前显示第 1-3 行；hash=fnv1a16]
1:a3|function hello() {
2:f1|  return "world";
3:0e|}
```

**edit（把 return 改掉）**

```json
{
  "path": "/workspace/hello.js",
  "ops": [
    {
      "op": "replace",
      "start": "2:f1",
      "content": "  return \"hashline\";"
    }
  ]
}
```

**edit（在末尾前插入一行）**

```json
{
  "path": "/workspace/hello.js",
  "ops": [
    {
      "op": "insert_after",
      "after": "2:f1",
      "content": "  // ok"
    }
  ]
}
```

**edit（文件开头插入 / 空文件写入）**

```json
{
  "path": "/workspace/hello.js",
  "ops": [
    {
      "op": "insert_after",
      "after": "0:00",
      "content": "// header\n"
    }
  ]
}
```

若此时磁盘第 2 行已变，两次都返回 `HASHLINE_STALE`，磁盘不变。
