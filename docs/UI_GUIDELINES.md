# 太墟 UI 规范（Compose / miuix）

Compose / miuix 相关的全部约定与踩坑约束。改 `feature/*/src/main/java/.../ui/` 或 `feature/components` / `feature/theme` 下任何文件之前先读本文件。

产品级 UX（聊天过程链、抽屉导航、窗口三态业务行为等）仍在 [`ARCHITECTURE_RULES.md`](ARCHITECTURE_RULES.md) §3；其余架构约束见仓库根 [`AGENTS.md`](../AGENTS.md)。

上游参考：[Mishka ui-guidelines](https://github.com/YuKongA/Mishka/blob/main/docs/ui-guidelines.md)。下列条目已映射到太墟组件与包名；**勿照抄 Mishka 路径 / ProxyScreen / 代理 IP 等业务特例**。

---

## 组件与形状

- 所有 UI 走 miuix（或其 `feature/components` / `feature/miuix` 包装）。禁止引入 `androidx.compose.material3`。**优先直接用库原件**（见 [组件库](https://compose-miuix-ui.github.io/miuix/zh_CN/components/)）：搜索用 `InputField` / `SearchBar`；顶栏更多用 `WindowIconDropdownMenu`；设置下拉用 `WindowDropdownPreference` / `WindowSpinnerPreference`；角标用 `Badge`；底栏抽屉用 `WindowBottomSheet`；弹窗用 `WindowDialog` / `RuntimeAlertDialog`。仅在需要太墟语义别名或跨页一致性时保留 `Runtime*` 薄包装，禁止再手搓 Material 式 `ExposedDropdownMenu` / 自绘搜索胶囊 / 自绘更多菜单。
- 容器用 `RuntimeCard`；按钮用 `RuntimeButton` / `RuntimeFilledTonalButton` / `RuntimeOutlinedButton` / `RuntimeTextButton` / `RuntimeIconButton`；开关与进度用 `RuntimeSwitch` / `Runtime*ProgressIndicator`。
- 返回图标走 `RuntimeIconName.Back` → `MiuixIcons.Back`（见 `RuntimeMiuixIcons`）。工具栏 / 输入区 / 侧栏圆形操作对齐 miuix `IconButton`：**40dp** 底 + 约 **20dp** 图标；禁止 Material 48dp touch-target 或库默认 **60dp FAB** 撑大实心底；新建入口优先顶栏 `actions` / `WindowIconDropdownMenu`。
- **自定义形状用 squircle modifier**（`top.yukonga.miuix.kmp.squircle.*`）。非 miuix 组件的手搓形状**不要**用 `RoundedCornerShape` 做 clip/background。按性能选：
  - 非点击纯色背景 → `squircleBackground`（无 offscreen layer，**不要再 clip**）
  - 图片 / 必须裁剪 → `squircleClip`（一个 offscreen layer）
  - 可点击 → `squircleSurface` + `.clickable{}`（涟漪裁进圆角）；条件可点击时退化为 `squircleBackground`
- 卡片圆角经 `atMostCardRadius` **不得超过 16dp**；描边只保留表示选中 / 错误 / 运行中的实色边（`stateOnly()`），灰色/半透明描边一律丢掉。

---

## 页面骨架

- 设置类 / 列表二级页优先复用 [`RuntimeSettingsScaffold`](../feature/components/src/main/java/top/wkbin/taixu/ui/components/RuntimeSettingsScaffold.kt)（内部已是 Scaffold + 大标题 `RuntimeTopBar(scrollBehavior)` + LazyColumn）。
- 手写列表页时 LazyColumn 必须挂 `.runtimeListScrollEffects(scrollBehavior.nestedScrollConnection)`（等价于 `.scrollEndHaptic().overScrollVertical().nestedScroll(...)`；nestedScroll **必须在 overscroll 内侧**，否则下拉无法先展开大标题）。
- 内容水平边距走 `runtimeContentPadding(...)`（默认水平 12dp）；卡片间距用 `RuntimeSpacing.cardGap`（12dp）。**TextField 表单**不包 Card，直接同样的水平 padding。
- 末尾保留导航栏可达：`RuntimeSettingsScaffold` 已把 scaffold 底 inset 并进 `contentPadding.bottom`；手写页用 `runtimeScrollContentPadding` 或末项 `Spacer` + `navigationBarsPadding()`，**不要**给二级页签名塞 `bottomPadding: Dp`（靠末尾 Spacer / contentPadding 自适应）。聊天根页与自适应外壳持有侧栏 / rail 时由外壳透传 inset，勿在子页再硬编码底栏高度。
- 首个 item 是 Card / 表单时，大标题 Scaffold 已用 `RuntimeSpacing.pageTop` 补顶距；手写页若顶栏无额外 top，首张 Card 前可加 `Spacer(12.dp)`。`RuntimeSectionTitle` / miuix `SmallTitle` 开头**不加**额外顶 Spacer（标题自带上下边距）。

---

## 顶栏毛玻璃

- 每个 Scaffold 各自一份 backdrop：`val backdrop = rememberRuntimeBackdrop()`（API&lt;33 为 null）。
- TopAppBar / 相关 bar 用 [`RuntimeBlurredBar`](../feature/components/src/main/java/top/wkbin/taixu/ui/components/RuntimeBlurredBar.kt) 包裹；bar 色 `runtimeBarColor(backdrop)`（有 backdrop 时 Transparent，否则 surface）。
- 滚动内容追加 `.runtimeBackdropSource(backdrop)`（内部 `layerBackdrop`）。嵌套页面各自一份 backdrop，嵌套 `layerBackdrop` OK。
- 搜索页若有顶栏动画，动画背景色与 bar 色同一源，避免实色盖住毛玻璃。

---

## 宽屏与缺口

- 断点只读 `LocalRuntimeWidthClass` / `rememberRuntimeWidthClass()` / `widthClassOf()`（600dp / 840dp）。**严禁**再散落 `maxWidth >= xxx.dp`。
- **内容居中**：`runtimeContentPadding` / `runtimeContentGutter` 把内容限到 `RuntimeContentMaxWidth = 840.dp`。**LazyColumn / LazyGrid 保持全宽**（两侧无滚动死区），只把 gutter 写进 `contentPadding`。**不要**用 layout modifier 压缩 LazyColumn 节点宽度。
- 宽屏导航：中间态 / 展开态走 `RuntimeAdaptiveNavigationScaffold` + `RuntimeNavigationRail`；rail 吸收起始侧 inset，内容区按脚手架既有 `consumeWindowInsets` / 末端 systemBars∪displayCutout 处理。
- 大标题列表在宽屏仍走 `RuntimeSettingsScaffold` / `RuntimeTopBar`；关于页等有 hero 视差的屏幕若固定 SmallTopAppBar，**不要**再套会复制大标题的大标题壳。
- **横屏缺口**：miuix `Scaffold` 不自动 padding 内容。二级页手写 LazyColumn 时，在 `fillMaxSize()` 后补水平 `displayCutout ∪ navigationBars`（竖屏为 0）；顶栏由自身 inset 处理。`RuntimeSettingsScaffold` 已消费 scaffold 起止 padding；新写二级页勿漏水平缺口。主聊天 / 自适应外壳内容已居中在缺口内侧的，无需重复。

---

## 分组卡片（滚动性能）

- `LazyColumn` 里禁止 `item { Card { 多行 Preference } }`——整卡一次性组合，行多时滚动 / 展开卡顿。
- 设置列表用 [`PreferenceSurface(first, last)`](../feature/settings/src/main/java/top/wkbin/taixu/ui/settings/PersonalizationComponents.kt)：每行独立 lazy item，首/末段 `squircleSurface` 分角，中间段直角拼接，语义对齐 miuix Card（`runtimeCardColor()` + 16dp）。有圆角的段**必须**经 `squircleSurface`（fill+clip），否则段内 clickable 的方角涟漪会溢出。
- 条件行用 `buildList` / 按 index 算 `first`/`last`。拆分本身不加 item 动画；需要展开动画时在 item 内 `Modifier.animateItem(...)`，**placement spec 不能长期为 null**（否则下方组硬跳）。
- **不适用拆分**：纯静态说明卡、带 textureBlur / 视差的关于页 hero 卡，保持单 `item { Card }`。关于页内容 Column 用 `heightIn(min = 视口高)`，**不要** `fillParentMaxHeight()`（钉死一屏后横屏矮视口会裁切且无法滚）。

---

## Dialog / BottomSheet / 下拉

- **长内容 Dialog**：`WindowDialog` 不限 content 高度。`RuntimeAlertDialog` / miuix `AlertDialog` 包装已用 `heightIn(max ≈ 窗口高×0.8)` + 正文 `weight(1f, fill = false)`；手写 `WindowDialog` 时同样包一层，滚动区可滚、按钮作非加权子项钉底。
- **选项列表 Dialog**（入口行弹出、内含若干操作行）：`insideMargin` 水平 0（让 `ArrowPreference` 全出血），垂直约 24dp 补 title 顶距。弹窗打开期间入口行设 `holdDownState`（MIUI 惯例）；「先关自身再弹下一层」的操作行不设。
- **单选弹窗**优先 `WindowDropdownDialog` / `WindowDropdownPreference` + `DropdownDefaults.dialogDropdownColors()`，点选即生效、底部只留取消。构造 `DropdownEntry` 的 `remember` **key 不能含 onSelect**（用 `rememberUpdatedState` 接住）。
- **BottomSheet**：`WindowBottomSheet` / 太墟 `ModalBottomSheet` 的默认 insets **实际主要是 `imePadding()`**（名称易误导）；水平边距由调用方或 `insideMargin` 负责。内容根节点要自己补 **底部** `systemBars ∪ displayCutout`（`systemBars` 已含 captionBar——漏掉就是小窗贴边的原因）。两处刻意排除：① 不含 ime（避免双倍底距）；② 不含水平方向（sheet 底对齐且限宽居中，单侧缺口 inset 会把内容推偏——与全宽二级页水平缺口不同）。内容高度随列表增减会硬跳时，用与 sheet 入场同参的高度过渡（miuix `folmeSpring`），别自己凑 spring。

---

## 颜色、状态与收集

- 合法颜色源仅 `RuntimeTheme.colorScheme.*` / `MiuixTheme.colorScheme.*` 与 `semanticColors()`（`SemanticColors`：success / warning / info / agent / code / terminal / categorical）。**禁止屏幕里散落 `Color(0xFF...)`**（终端主题预览等「展示色板本身」的例外须集中在专用 preview 文件）。
- 所有屏幕 Flow 用 `collectAsStateWithLifecycle()`，不用 compose runtime 的 `collectAsState`。

---

## Compose 性能铁律

- **强跳过友好**：UI 投影用的 `data class` 标 `@Immutable`；大集合字段用 `ImmutableList` / `ImmutableMap` / `PersistentSet`，且**从 Repository / Provider 生产端就是该类型**，不要在 UI 末端才转——中途裸 `List` 作 composable 参数会让整树不可 skip。
- **帧率级 State 不能在组合期读**：滚动折叠比例、动画进度等，用 `derivedStateOf` 包成 `Dp`/`Float` 再 `by` 解包会把失效抬到整个 restart scope。一律以 `State<T>` 或 `() -> T` 透传，在 **layout / draw** 阶段读（自定义 `layout{}`、`graphicsLayer{}`、`onDrawBehind{}`）。`Modifier.padding(每帧值)` / `rotate` / `scale` / `alpha` 的值形式包装同样是组合期读，合并进已有 `graphicsLayer{}`。只有夹成布尔或离散档后才可在组合期读。
- **持续动画在不可见时必须停**：由 alpha / 可见性门控帧循环；判定放在 draw 或 `snapshotFlow { visible }.first { it }`，**不能**在组合期 `by` 解包每帧值。
- **高频采样动画**（若做折线 / 用量条）：用单调递增序号作 `Animatable` 绝对目标并 `animateTo(seq)`，**禁止** `snapTo(0)+animateTo(1)` 相对进度（重组早于动画启动的那一帧会双向抖动）。滑入进度在 draw 阶段读 State。

---

## 卡片内铺满绘制（若新增水印 / 图表层）

miuix Card / `squircleSurface` 已 clip，子内容不必再套 `squircleClip`：

1. `insideMargin = 0` + 内层 `Box(fillMaxSize)`，文字进带 16dp padding 的 Column，绘制层才能铺满。
2. 绘制层用 `Modifier.matchParentSize()`（**不参与测量**），卡片高度仍由文字决定。
3. 绘制层自身 `clipToBounds()`，避免滑动动画画出左右边界。

同行多卡共用同一套水印尺寸 / 浓度常量，否则高度或透明度会错开。
