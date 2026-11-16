# 机甲维护与作战系统验证

2026-11-16，本轮扩展仍只注册涓流、激湍、归海三机。

新增四类维护组件、攻防无人机、五类武器和三类弹药、医疗物资及其独立物品资源与工作台配方。归海内建自修、激光、攻防无人机；维护通用槽、武器槽与战斗无人机槽分别管理。无人机以环绕粒子和作用轨迹显示，无独立飞行实体。

已执行：

```powershell
$env:GRADLE_USER_HOME='D:\Code\Workspace\.gradle'
.\gradlew.bat -I .tools/mirror.init.gradle build runGameTestServer '-PgameTestNamespaces=starfall_blueprint_mech_three,starfall_blueprint_mech_assembly,starfall_blueprint_mech_combat,starfall_blueprint_mech_support' --offline --console=plain
```

结果：整包构建成功，13项服务端GameTest通过。包括原有知识、射击遮挡、工程和武器独立动作、组件与部署、融合；新增真实货舱弹药扣除及冷却、配装状态校验、无人驾驶不获得免费防御、无维修物资不治疗、真实钢与能源支付、满血不扣费、排除中立动物、归海付费减伤和隔离禁用防御。

首轮发现并修复新配方错误引用steel（实际注册ID ordinary_steel），测试场景缺失及名称重复拼接；最终服务端日志无配方加载错误。旧测试手动射击中立动物的预期改为阻挡射线但不伤害；全能工程后射击测试改用敌对目标，保留实际伤害断言。

自动支持仅在合格驾驶员控制下运行；治疗友方玩家/队伍/驯养生物，维修友机。防御支援覆盖附近合法友方机甲，不提供玩家无敌或复活。爆炸不修改地形。真人客户端视觉、操控和多人联网尚未验收，本轮未做完整历史测试矩阵回归。
