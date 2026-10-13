# 太墟（TaiXu）自定义迭代与 TaiXuDev 构建模块 (`feature:custom_iteration`)

本模块为 **太墟 · TaiXu** 量身打造的标准、无降级自迭代系统。让用户在手机上使用太墟的自主 AI Agent 进行太墟自身的二次开发、测试、云端 CI 构建，并向开源社区贡献代码。

---

## 一、模块架构与核心组件

```
feature/custom_iteration/
├── build.gradle.kts                        # Gradle 多模块依赖配置（对齐太墟规范）
├── README.md                               # 完整模块架构与接入文档
└── src/
    ├── main/
    │   ├── assets/
    │   │   ├── skills/
    │   │   │   └── taixu-custom-iteration/
    │   │   │       └── SKILL.md            # Agent 专有开发与 PR 规范 Skill
    │   │   └── templates/workflows/
    │   │       └── taixudev-build.yml      # GitHub Actions 云端构建 CI 模板（与 .github/ 逐字节一致）
    │   └── java/top/wkbin/taixu/
    │       ├── di/feature/custom/iteration/
    │       │   └── KoinModule.kt                 # Koin 依赖装配（Coordinator + ViewModel）
    │       ├── iteration/engine/
    │       │   ├── CustomIterationBootstrap.kt   # 自举引擎（工作区初始化/资产落盘/引导 Prompt 生成）
    │       │   ├── CustomIterationPaths.kt       # 运行时路径契约与 assets 读取抽象
    │       │   ├── TaiXuDevBuildCli.kt           # gh 命令模板与远程 URL 解析
    │       │   └── TaiXuDevBuildCoordinator.kt   # CI 调度与 APK 下载校验协调器
    │       └── ui/iteration/
    │           ├── CustomIterationScreen.kt      # Jetpack Compose UI 界面
    │           └── CustomIterationViewModel.kt   # Koin 状态管理 ViewModel
    └── test/java/top/wkbin/taixu/
        ├── iteration/engine/
        │   ├── CustomIterationBootstrapTest.kt   # 自举：落点/幂等/失败关闭
        │   └── TaiXuDevBuildCoordinatorTest.kt   # 调度：远程解析/轮询/校验/注入防护
        └── ui/iteration/
            └── CustomIterationViewModelTest.kt   # 状态机与真实路径契约
```

---

## 二、核心特性与设计规范（零降级）

1. **工作区物理隔离**：
   * 工作区宿主路径取 `RuntimePathManager.homeDir/custom_taixu`，该目录经 PRoot 绑定为 guest 的
     `/root`，因此沙盒内视角即 `~/custom_taixu`；开发行为完全不破坏运行中的宿主系统。
2. **专属 Agent Skill 约束 (`SKILL.md`)**：
   * 指导 AI Agent 严格遵守太墟的多模块 Kotlin DSL 规范、Material3 Compose 规范、协程设计及凭证安全规则。
   * Skill 随模块 assets 打包，自举时**自动落盘到三个真实扫描根**，无需用户手工复制：
     `<attachmentsDir>/skills/`（guest `/attachments/skills`）、
     `<workspaceDir>/skills/`（guest `/workspace/skills`）、
     以及 `~/custom_taixu/.agents/skills/`（项目级规范）。
   * 任一资产缺失或写不进去时**显式报错**，绝不给出「工作区就绪」的假象。
3. **GitHub Actions 云端 CI 构建 (`taixudev-build.yml`)**：
   * 免去手机端部署 Android SDK/NDK 的庞大体积开销；
   * 云端 Runner（`ubuntu-24.04` + Temurin JDK 17 + `platform-tools`）执行
     `TAIXU_DEV_BUILD=1 ./gradlew assembleDebug`，产物改名 `TaiXuDev-arm64-debug.apk` 并附
     `.sha256`，通过 `gh run watch --exit-status` / `gh run download -n taixudev-apk` 回传手机。
   * 构建目标仓库**运行时从迭代工作区的 `origin` remote 解析**：Fork 后自动指向主人账户，
     避免写死官方仓库导致 `workflow_dispatch` 403；URL 无法解析为 `owner/name` 时直接失败关闭。
4. **双包共存（Dual-Flavor）**：
   * 编译产物包名自动重命名为 `top.wkbin.taixu.dev`，应用名显示为 `TaiXuDev`；
   * 测试版与手机中的正式版太墟共存运行、互不覆盖。
5. **预置 Agent 引导 Prompt**：
   * 自举时按「当前 Fork 仓库」生成 6 步引导 Prompt（含设备码认证、免本地 SDK 策略、双包校验），
     交给 Chat 会话执行，构建/下载/提交全程可复现。
6. **开源 PR 交付闭环**：
   * 本地真机体验通过后，Agent 协助生成标准格式 PR 提交到 `wkbin/taixu:main`。

---

## 三、快速接入指南（集成到太墟主工程）

### 1. 注册 Gradle 子模块
在 `settings.gradle.kts` 中添加：
```kotlin
include(":feature:custom_iteration")
```

### 2. 添加导航路由
在 `TaiXuNavHost.kt` 中添加路由项：
```kotlin
composable("custom_iteration") {
    CustomIterationScreen(
        onBack = { navController.popBackStack() },
        onNavigateToChat = { prompt ->
            navController.navigate("chat?prompt=${Uri.encode(prompt)}")
        }
    )
}
```

### 3. 在设置或开发者中心添加入口
在 `SettingsScreen.kt` 或 `DeveloperScreen.kt` 中添加跳转入口按钮即可！
