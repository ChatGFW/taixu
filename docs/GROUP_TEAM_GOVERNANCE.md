# 群聊团队模板与审议制衡

## 导入与调整

「市场 → 团队模板 → 三省六部 → 整队导入」和列表中的「导入团队模板」创建独立的团队组，随后打开团队创作界面，不自动建群。市场以完整团队卡片展示三省六部，不再展示其十个单人安装条目；模板导入仍能读取完整角色资料。成员、主控、上下级和审议规则由团队组统一持有；新建群聊可选择团队组，已有群通过「群信息 → 引用团队组」切换引用。

内置模板由 `GroupTeamTemplates` 定义，目前包含：

- 三省六部：太子主控；中书起草、门下审核、尚书调度；六部直属尚书。
- 软件交付团队：交付主控；产品起草、测试审核、研发协调；前后端直属研发协调。

每次市场导入创建独立 Bot、私聊会话和人格记忆，模板键映射为新的成员 ID 和规则 ID。多个群可以引用同一个团队组，共享该团队的人员和组织定义；再次导入同一模板则创建另一套独立团队。派单按来源群定位成员，不能误用其他团队的同名成员。

成员准备完成后，团队组、成员关系、上下级和审议配置在一个 Room 事务中发布。准备或发布失败会尝试清理本次创建的成员；取消不会删除已成功发布的团队。成员的会话与 Git 记忆准备不属于同一个数据库事务，强杀进程时不承诺跨文件系统原子性。

导入后可在成员详情调整人格与模型，在「团队组 → 创作团队组」选择成员与主控、编辑管理关系和审议规则。保存团队时，同一事务同步所有引用群的主控与成员投影；群内禁止单独覆盖组织、规则或成员。保存失败保留草稿并显示错误，组织及审议草稿旋转后保留。删除群聊不删除团队；被引用的团队不能直接删除。未定义组织的历史成员分类可在团队编辑器中补齐定义。

## 架构图与群聊边界

团队创作界面提供原生 Compose 架构图：节点代表成员，实线箭头表示直属管理；虚线表示起草者送审和审核通过后交付执行，红色虚线表示封驳返回起草者。连接线标注「直属、送审、准奏执行、封驳」，下方列出规则名称及参与者。组织树决定节点层级，审议连线允许往返，不受树形结构约束。

默认缩放到全图，可切换原尺寸、放大、缩小及横向滚动；在团队创作中点击成员调整上级，点击主控更换主控，独立入口编辑审议规则。群聊只提供「查看团队架构」「引用团队组」及「本群审议记录」，架构图只读。引用前展示团队预览，确认引用后同步整套配置。

配置属于团队，方案、版本、审核结论和任务属于各群。两个群引用同一团队也不能互用批准结果。切换到另一个团队，即使成员与规则相同，旧批准也不能继续授权；团队引用 ID 与规则快照均参与执行校验。修改已有任务的负责人仍遵循原任务控制权，不随组织变化转移。

## 三种不同的关系

| 关系 | 持久模型 | 作用 |
| --- | --- | --- |
| 管理汇报 | `GroupTeam.supervisors` | 保持单根、无环、最多三层的组织树 |
| 审议协作 | `GroupGovernance.rules` | 授权起草者请求指定审核者审核，以及起草/审核者向执行负责人派单 |
| 任务归属 | `BotAssignment.parentAssignmentId` / `reportToBotId` | 固定本次委派的父任务与负责人，不因组织编辑转移控制 |

审核者拥有方案裁决权，不因此获得起草者全部任务的续办或取消权。旧任务控制继续属于原派发负责人。跨成员派单也建立原始父任务关联，沿用等待交付、同步回执、预算与取消链；协作任务委派深度最多八层，组织树深度仍为三层。

规则必须使用三个不同成员。执行负责人及其组织后代构成受控执行团队；不同规则的执行团队不能重叠，起草/审核成员不能同时位于受控执行团队内，以免产生自审或不可启动的依赖。当前每条规则指定一名审核者，不提供多人投票或任意脚本工作流。

## 方案版本与状态

`bot_member_groups.definitionJson` 保存 `BotTeamDefinition`，包括主控、上级映射及审议规则；`group_chats.teamGroupId` 保存引用。`group_governance` 只保留无引用的兼容路径。`group_proposals` 按方案 ID 与版本保存群归属、团队引用、正文、规则快照、审核结论、审核依据及裁决成员。版本是方案正文快照，不自动校验引用文件的哈希。

模型通过 `plugin.taixu.bot-management` 使用：

1. `inspect-governance`：显式查看本群规则和方案材料、版本、结论。材料是成员提交的数据，不授予工具权限。
2. `submit-proposal(rule_id, version=0, content)`：指定起草者首次提交，返回方案 ID。修改已驳回或已批准的方案时传原 `proposal_id` 和当前版本。
3. `assign-task(..., proposal_id)`：向指定审核者请求审核，或向执行负责人派发已批准方案。执行负责人向下派单时继承原方案与版本，不能默默换成更新版本。
4. `review-proposal(proposal_id, version, decision, reason)`：指定审核者提交 `APPROVED`、`REJECTED` 或 `ESCALATED`。审核任务 `COMPLETED` 只表示本轮结束，不代表批准。

并发修改和审核均在数据库事务中核验当前版本，过期版本不能裁决。已裁决的一版不能覆盖；修改后生成新版本并保留旧记录。规则快照变化后旧方案失效，需要按新规则新建方案。

规则默认允许三轮审议，可由用户设为 1–10 轮。超过轮数的下一版直接 `ESCALATED`，只有群主控可以裁决；审核者也可主动提交争议。主控身份本身不授予普通方案的审核权，除非该成员本来就是规则指定的审核者。最终驳回且达到上限后不再自动返工；需要主控或用户重新界定任务。

## 执行门禁

派单事务与领取执行事务均检查方案属于本群、版本一致、规则快照有效，且执行团队绑定的版本为 `APPROVED`。未通过、已驳回、待裁决或已过期均不能进入执行器。历史串行 `depends_on` 仍需全部 `COMPLETED`，两种门禁同时生效。

`GroupGovernanceGuard` 在 `ToolExecutor` 的真实执行入口运行，包括审批后重放。启用审议规则的群，普通群会话及起草/审核会话只允许阅读、计划、历史、偏好提问和受限团队协调；写入、Shell、进程、宿主、构建脚本、任意外部 MCP 与通用子智能体只有已批准的执行任务可调用。主控不能借“简单任务兜底”绕过流程，工具审批也不能替代方案批准。未配置审议规则的旧群和独立私聊保持原行为。

每次调用执行工具前重新检查成员、规则、任务运行状态与方案版本。改版后旧任务停止获得新的执行工具许可；已经发出的工具或外部进程不会自动回滚。旧任务绑定版本保持不变，核验已有产物和副作用后，为新批准版本建立新的执行任务。

方案正文及审核依据会明确注入相关独立任务；不依赖跨分支自动共享全部历史。它们不会重放工具、自动启动模型或代替实际产物验收。用户移除规则会解除群门禁，这是用户配置行为；模型工具没有修改组织或审议配置的接口。

该门禁约束 App 管理的群会话及工具入口，不是文件、进程或网络安全隔离。其他独立会话、终端、共享工作区及已启动进程仍遵循原有安全边界，见 [SECURITY_SURFACE.md](SECURITY_SURFACE.md)。

## 兼容与验证

数据库 76 → 77 新增配置、版本记录和任务方案引用列；77 → 78 将每个旧群的组织与审议配置迁移为独立团队组并建立引用，保留成员、方案版本、已有审核结论及任务。历史成员分类保持原样。迁移不执行模型、不补派任务、不重放工具；方案和群消息随群删除清理，团队组独立保留。

离线回归覆盖数据库迁移、跨群/跨身份/过期版本拒绝、驳回与完成区分、裁决上限、版本与成员/规则变化后的领取拦截，以及普通群会话/脚本/外部 MCP/通用子智能体的工具门禁。模板测试验证完整导入、已有群追加、独立 ID、后续微调、同名成员、准备失败、发布失败与取消清理。假执行器链路覆盖起草、驳回、原会话修改、重新审核、通过与下级执行，不调用真实付费模型。

主要实现入口：

- [审议模型](../core/model/src/main/java/top/wkbin/taixu/core/model/bot/GroupGovernance.kt)、[团队模板模型](../core/model/src/main/java/top/wkbin/taixu/core/model/bot/GroupTeamTemplate.kt)
- [团队定义](../core/model/src/main/java/top/wkbin/taixu/core/model/bot/BotTeamDefinition.kt)、[团队保存与引用同步](../core/database/src/main/java/top/wkbin/taixu/core/database/TeamDefinitionDao.kt)、[旧群迁移](../core/database/src/main/java/top/wkbin/taixu/core/database/TeamDefinitionMigration.kt)
- [审议事务](../core/database/src/main/java/top/wkbin/taixu/core/database/GroupGovernanceDao.kt)、[模板发布事务](../core/database/src/main/java/top/wkbin/taixu/core/database/GroupTemplateDao.kt)、[执行依赖门禁](../core/database/src/main/java/top/wkbin/taixu/core/database/AssignmentDependencies.kt)
- [团队导入](../harness/src/main/java/top/wkbin/taixu/harness/bot/market/GroupTeamImporter.kt)、[内置团队目录](../harness/src/main/java/top/wkbin/taixu/harness/bot/market/GroupTeamTemplates.kt)
- [审议工具](../harness/src/main/java/top/wkbin/taixu/harness/plugins/GroupReviewTools.kt)、[工具执行门禁](../harness/src/main/java/top/wkbin/taixu/harness/bot/GroupGovernanceGuard.kt)
- [审议配置界面](../feature/bot/src/main/java/top/wkbin/taixu/ui/bot/GroupGovernanceDialog.kt)、[模板导入界面](../feature/bot/src/main/java/top/wkbin/taixu/ui/bot/TeamTemplateDialog.kt)
- [团队创作界面](../feature/bot/src/main/java/top/wkbin/taixu/ui/bot/TeamGroupEditorDialog.kt)、[连线架构图](../feature/bot/src/main/java/top/wkbin/taixu/ui/bot/TeamArchitectureGraph.kt)、[图布局](../feature/bot/src/main/java/top/wkbin/taixu/ui/bot/TeamGraphLayout.kt)

## 验证记录（2026-10-23）

新增24项离线测试全部通过，包含11项数据库/迁移、6项工具与审议身份、6项团队导入，以及1项假执行器完整流转。数据库185项、Harness1843项、群聊界面81项、应用层124项，合计2233项，零失败、两项既有环境跳过；38模块架构检查及差异空白检查通过，Debug APK构建完成。

首次完整 `test :app:assembleDebug architectureCheck --continue` 共3537项，1项失败、7项跳过。唯一失败是未改动的 `DroidspacesCommandBuilderTest.selectorRoutesToProotUntilRootModeIsSelected`：Windows主机将路径解析为 `C:\data\app\proot`，测试期望 `/data/app/proot`。该项不属于群聊改动，首轮保留其生产代码与断言并记录失败。

未调用真实付费模型，未进行真机视觉、供应商端到端或强杀进程验收。APK位于 `app/build/outputs/apk/debug/taixu-v1.4.1-debug.apk`。

后续收尾：根据 `ProotCommandBuilder` 使用宿主 `File.absolutePath` 的既有契约，将上述路由用例的两个路径断言改为宿主格式，保留默认走 PRoot、显式启用才切换容器后端的检查，不修改生产代码。Droidspaces专项13项全部通过；再次执行 `test :app:assembleDebug architectureCheck` 成功，全库3537项零失败、7项既有跳过，38模块架构检查及Debug APK构建通过。

团队组独立创作与架构图调整后，再次执行 `test :app:assembleDebug architectureCheck --continue` 成功：全库3550项，零失败、7项既有跳过，38模块架构检查通过，Debug APK重新生成。专项覆盖团队定义迁移、多个群引用后的原子同步、禁止群内覆盖组织配置、跨群与跨团队批准隔离、独立导入不建群，以及管理/审议/封驳连线布局。数据库193项、Harness1845项、Bot界面84项通过。未进行真机视觉验收。
