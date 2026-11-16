# 无线充能、聚变核心与维护平台验证

2026-11-16，整包build及20项关键NeoForge服务端GameTest通过。执行：

```powershell
$env:GRADLE_USER_HOME='D:\Code\Workspace\.gradle'
.\gradlew.bat -I .tools/mirror.init.gradle build runGameTestServer '-PgameTestNamespaces=starfall_blueprint_mech_three,starfall_blueprint_mech_assembly,starfall_blueprint_mech_combat,starfall_blueprint_mech_support,starfall_blueprint_mech_power,starfall_blueprint_platform' --offline --console=plain
```

在上一轮13项机甲、组件、维护和战斗测试基础上，新增7项供能平台测试：无线独立槽与持久化账本；陌生主人、墙遮挡和真实平台到机甲512能量守恒及每刻限额；聚变拒绝普通煤、真实燃料扣除和产热、满电/隔离/磨损停机保留已付燃料；九格平台缺角停机；归海完整占地、越界和死亡拒绝；真实停机充电与钢材维修、能源守恒；平台模拟不扣能和共享20000每刻输出预算。

实际停机测试发现并修复了落地重力速度误判移动、充电提前耗尽维修能源的问题。平台会为待维修机体预留一次维修所需1000能量。新配方已通过服务端加载，修正了steel别名到实际ordinary_steel物品ID。

实现边界：平台为真实3×3方块结构，一次放置，机甲完整停稳后服务；平台主人须在线并通过机甲驾驶/归属校验。无线仅从同主人完整平台抽电，8格/视线/已加载区块，512 XH每刻；聚变仅归海，16000 XH每发电刻，1200刻每专用燃料芯，产热8，每100发电刻磨损1。隔离允许平台接触维护充电，但禁用无线与聚变运作。详细操作见三机指南。

尚未进行真人客户端外观、驾驶及多人联网验收；测试不代表完整回归。未提交或推送仓库，构建运行日志和缓存保留在忽略目录。
