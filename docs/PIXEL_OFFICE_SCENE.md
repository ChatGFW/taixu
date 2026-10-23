# 像素办公室场景数据

`feature/bot` 的 `OfficeSceneSpec` 是工位、座位行为、互动对象和区域牌的共同入口。
默认实例清单在 `OfficeSceneDefaults.kt`；纯数据类型在 `OfficeSceneSpec.kt`。

## 新增和移动对象

- 新增工位：在 `defaultOfficeDesks()` 增加一个 `OfficeDeskSpec`，提供唯一 ID、位置和动画相位。
  底图、细节、人物座位、屏幕动画和可选阴影均由实例派生，无需修改 renderer 或分配策略。
  清单顺序决定工位分配优先级；动画相位独立于清单顺序。
- 移动工位：修改实例的 `x/y`。屏幕、座位、抽屉装饰和阴影均使用局部偏移。
  现有右侧工位保留原图的装饰差异，`drawers/shadow` 描述这些差异。
- 新增猫：在 `defaultOfficeCats()` 增加 `OfficeCatSpec`，指定 `SLEEPING/PERCHED` 风格。
  图像、尾巴动画、点击区和抚摸爱心共用实例坐标。ID 同时作为 Compose 测试标签和互动状态键。
- 新增咖啡机：在场景的 `coffeeMachines` 清单增加实例，指定不同 ID。
  机身、指示灯、蒸汽、点击区和冲泡反馈自动接入。
- 调整区域牌：修改 `defaultOfficeZones()` 中的锚点。文案资源和图标由 `kind` 决定。
- 调整其他座位：修改 `defaultOfficeSeats()`。`group` 决定分配池，`poses` 声明可用姿势，
  `roamOffset` 指定相对闲逛目标，`idleAction` 指定固定空闲动作。行为不依赖 ID 前缀或绝对坐标。

## 坐标与验证

实例使用原始房间坐标（192 × 448）。静态绘图按 3 倍像素生成，随后由 `PixelOfficeScene`
裁去空走道；Compose 在展示边界调用同一个 `y()` 转换，再应用相机平移与缩放。
不要将已转换的坐标写回实例。

`GroupCyberStudio` 将同一份场景传给座位分配与 `GroupPixelOffice`。
替换场景时重新分配人物、生成静态图并更新热点和牌匾；角色精灵生成保持独立。

场景构造时校验重复/空 ID、重叠座位、座位/屏幕/闲逛目标越界、热点越界与热点重叠。
热点重叠使用走道压缩后的坐标，匹配 Compose 实际命中范围。当前允许美术边缘裁切；
尚无完整的墙体、碰撞或寻路数据，校验不判断家具压墙、通道可达性或装饰物遮挡。

## 当前边界

工位、猫、咖啡机、全部座位及五块区域牌已经数据化。房间壳、沙发/会议桌/吧台等固定家具
及其装饰仍由现有绘图代码放置；修改这些固定家具时，仍需要同步调整关联座位。
任务板、目标板和固定环境效果也尚未迁入实例清单。

Canvas、Compose 覆盖层与程序化角色生成继续沿用。此阶段不引入图集导出、场景编辑器或热重载。

## 回归

运行 `:feature:bot:testDebugUnitTest --tests '*OfficeScene*Test' --tests '*PixelOffice*Test'`
以及 `--tests '*OfficeStandupTest' --tests '*GroupCyberStudioTest'`，并执行 `architectureCheck`。
测试覆盖迁移前的静态像素指纹、新增工位分配、逐工位移动、座位改名和闲逛、两类猫移动、
咖啡机独立反馈、非法场景拒绝，以及 Compose 中场景替换后的人物、热点和牌匾位置。
