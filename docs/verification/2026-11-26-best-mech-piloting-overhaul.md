# 贝斯特机甲驾驶体验与终端重构验证（2026-11-26）

实现与现行操作见 [BEST_THREE_MECHS.md](../BEST_THREE_MECHS.md)。

## 本轮改动摘要

- **P0 核心驾驶手感**：平滑移动惯性与常规行走热中性平衡（`BestMechModules` 基础冷却 `3/6`、移动产热 `1/2`，冲刺与高负荷作业才净升温）；步行腿跨步 `1.25`、巡航轮组 `1.0`；空格机械跳跃＋空中 12 刻推进跃升；解除涉水禁锢（涓流/归海 `0.85×` 水下推进力）；登机默认拉远第三人称（6.5/7.2/8.5 格）并开放 F5 自由切第一人称封闭驾驶舱；新增 `PilotInput` 持续输入包与鼠标左/右键按住连发，作业射程随眼高提升至 8 格（归海 10 格），创造模式免耗试挖与射击（保留 `BreakEvent` 保护），机甲键位接入 `MECH_PILOT` 冲突上下文并将友伤键调整为 `U`。
- **P1 HUD、选框、光束与音效**：新增 `BestMechSounds` 机甲音效体系、`BestMechOverlayRenderer`（3×3 区域作业线框与部署轮廓预览）、武器/僚机立体光束实体渲染及完整驾驶 HUD（速度/冲刺、冷却条、弹药计数、模式徽章与准星高亮）。
- **P2 统一机甲终端与后勤提速**：新增 `BestMechLoadoutMenu` / `BestMechLoadoutScreen`（驾驶 `I` 键或站立潜行空手右键打开，含 3D 模型旋转预览、8 硬件槽、3 成长强化位、实时货舱与一键隔离/维修/收纳）；直充/手持/无线/维护平台分别提速至 `20k / 50k / 10k / 100k XH/t`（平台缓存 `10M XH`）；部署自动清理草丛等可替换植被、放行掉落物与经验球并输出明确失败提示；工务装配台 10 个窄分页整合为 4 大分类。

## 编译与测试结果

- 使用本机 JDK `21.0.12.1` 零报错编译全部源码至 `build/classes/java/main`，同步资源并重新打包 `build/libs/starfall_blueprint-0.1.0.jar`（2,907,073 字节）。
- 在预备 NeoForge `21.1.262` 真实服务端 GameTest 运行时下，执行全部 14 个贝斯特命名空间（`starfall_blueprint_mech`, `starfall_blueprint_mech_legs`, `starfall_blueprint_mech_modules`, `starfall_blueprint_mech_body`, `starfall_blueprint_mech_arms`, `starfall_blueprint_heavy_drill`, `starfall_blueprint_area_excavator`, `starfall_blueprint_mech_loot`, `starfall_blueprint_mech_combat`, `starfall_blueprint_mech_support`, `starfall_blueprint_mech_power`, `starfall_blueprint_mech_three`, `starfall_blueprint_mech_assembly`, `starfall_blueprint_platform`）：
  - **P0 阶段**：`74 / 74` 全部通过（2.429 s）。
  - **P1 阶段**：`75 / 75` 全部通过（4.536 s）。
  - **P2 最终全量回归**：**`77 / 77` 全部通过**（2.667 s，退出码 0）。
- 新增 6 项专项测试覆盖：
  1. `sustainedWalkingNeverThermalLocksWhileSprintAndWorkHeatUp`（持续行走不热锁、冲刺正确升温与 boost 状态）
  2. `mechCanJumpOverTwoBlockLedgeAndWadeThroughWater`（跳跃＋短时推进跃升 ≥2 格与涉水前进）
  3. `reachScalesWithChassisEyeHeightAndPilotInputDrivesContinuousWork`（归海 8–10 格动态射程与持续输入包冷却连挖）
  4. `hudSyncsWeaponCooldownAmmoAndSwitchStates`（弹药、冷却与全部战斗/支援开关同步）
  5. `loadoutMenuInstallsRemovesComponentsAndPreservesAtomicSafety`（机甲终端打开、隔离解锁、槽位安装、货舱转出与一键收纳）
  6. `deployAllowsReplaceableFoliageAndRejectsFluidsCleanly`（草地/花朵自动清理部署与流体安全拒绝）

## 验证边界

- 服务端逻辑、物理位移、热平衡、权限与保护事件、统一终端事务及充能预算均经真实 NeoForge 服务端 GameTest 实跑验证。
- `BestCockpitCheck` 已同步更新为验证默认第三人称、F5 切第一人称、离舱恢复视角及 `BestMechLoadoutScreen` 界面渲染；本次在无图形会话环境下未启动独立 GUI 客户端截屏，客户端实机操控手感与多人联机验收待后续进行。
