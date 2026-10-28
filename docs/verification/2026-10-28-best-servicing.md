# 贝斯特野外整备扩展：2026-10-28

## 本轮实现

个人学习增加第三级野外整备，沿用原存档字段与死亡复制；保留原菜单操作0–4，新增5学习、6装配便携整备包、7水桶冷却。整备包须人工装配，无公共制造配方；主手持包、副手持受损设备，消耗普通钢1和包耐久1恢复200工务耐久。整备包原版耐久32、不可堆叠、无电热数据。工位冷却消耗水桶1、返空桶1、降热400，到400及以下解除过热锁。

## 实际验证

环境：Windows、Java 21.0.12.1、Minecraft 1.21.1、NeoForge 21.1.262、Gradle 8.14.3。

- Gradle `build` 的compileJava、processResources、jar、assemble、check及build任务完成；没有独立JUnit测试。出现现有ClientSetup弃用警告，无编译错误。
- 2026-10-28 15:38:33真实NeoForge服务端只运行新增3项GameTest，3/3通过，耗时524ms；Gradle退出0，`BUILD SUCCESSFUL in 21s`。
- 资源生成链build_atlas.py → sync_catalogue.py → generate_catalogue_resources.py各执行一次，verify_best_resources.py检查通过。当前304项物品、245条配方、170项图鉴；专业成品不进入公共配方。
- 生成器检查整备包原版耐久32、stack1、无动力设备电热字段、三级前置、原创32×32透明贴图、模型和中英语言键，以及冷却元数据。

专项测试位于`src/main/java/cn/starfall/blueprint/test/BestServicingGameTests.java`，独立命名空间`starfall_blueprint_servicing`：

1. `fieldResearchAndManualAssemblyArePersonalAndAtomic`：不能跳过二级知识、重复学习不扣费、满背包装配失败保留材料、腾位后装配成功并精确扣料。
2. `waterCoolingReturnsContainerAndPreservesEquipment`：热800降至400解锁、水桶返空桶；保留电量、磨损及自定义数据；零热与知识不足均不扣水。
3. `portableServicingUsesSteelAndKitWithoutChangingHeatOrEnergy`：未学习不消耗；正常维修耗钢和包耐久并恢复200；保留电量、热量与过热锁；最后一次使用完成维修后包耗尽；完好设备不耗料。

保存的服务端日志：`2026-10-28-best-servicing-server.log`。离线环境下Yggdrasil公钥请求报UnknownHostException，但服务端正常完成全部3项测试。

## 复现

Java 21环境下可从仓库执行：

```powershell
.\gradlew.bat build
.\gradlew.bat -PgameTestNamespaces=starfall_blueprint_servicing runGameTestServer
```

Gradle配置支持`gameTestNamespaces`属性；不指定时包含原命名空间与新增整备命名空间。实际专项运行使用本机临时init脚本选定相同命名空间；随后校验正式属性能够生成运行参数。

## 范围与限制

本轮没有重跑生成稳定性、客户端穿戴、菜单视觉或多人实机验收。首批216/216为历史记录，不计作本扩展验收。第一次启动时项目配置覆盖临时筛选，误启动原有216项套件，发现后终止；该次没有完整结果，不计通过数量。随后修正筛选，只运行上述3项并通过。
