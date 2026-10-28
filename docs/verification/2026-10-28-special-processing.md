# 特殊加工机器定向验证 · 2026-10-28

新增自动酿造机、自动锻造台、自动切石机、自动高炉、自动烟熏炉。运行环境：Windows 11、Java 21.0.12.1、Minecraft 1.21.1、NeoForge 21.1.262。

## 生成与构建

依次执行RE_BFL的`04_游戏设定/产线图册/build_atlas.py`、程序仓库的`tools/sync_catalogue.py`、`tools/generate_catalogue_resources.py`，随后运行离线Gradle完整构建，退出码0：

```text
gradlew.bat --offline --init-script .tools/mirror.init.gradle build --console=plain
BUILD SUCCESSFUL in 9s
4 actionable tasks: 3 executed, 1 up-to-date
```

保留既有ClientSetup事件总线弃用警告。Gradle的`test`任务为NO-SOURCE；以下GameTest通过独立真实服务端启动执行，不以Gradle空测试任务代替验证。

产物：`build/libs/starfall_blueprint-0.1.0.jar`，1,497,948字节。
SHA-256：`53B31B3F85C0CDBC4A5DCCA02AABB11CC17A038226338F4AD72A191682228297`。

## 服务端定向验证

只运行`SpecialProcessingGameTests`的5项新用例；未重跑全量历史用例。

| 用例 | 检查内容 |
|---|---|
| cookingUsesDistinctRecipeTypesAndExposesOnlyWorkPorts | 高炉生铁、烟熏生牛肉的实际配方与经验，精确2000能耗；拒绝普通熔炼玻璃，自动化槽不含升级、电池和样品 |
| stonecuttingSampleBackpressureAndUpgradeSnapshotSurviveReload | 样品选石砖楼梯、满仓／无电不扣料，四个超频模块冻结10刻／1400能耗，NBT续作单次结算且保留样品 |
| smithingUpgradeUsesRealAssemblyAndPreservesEquipmentComponents | 下界合金升级调用真实组装，保留自定义名称与损伤；模板、装备、材料各消耗1 |
| smithingTrimUsesRegistryRecipeAndKeepsTrimComponent | 注册的纹饰配方生成真实TRIM装备组件 |
| brewingTransformsThreeBottlesAndReturnsDragonBreathContainer | 三瓶水酿粗制药水、三瓶喷溅转滞留药水，龙息返还玻璃瓶，跨NBT续作与总能量守恒 |

```text
[11:11:48] 5 GAME TESTS COMPLETE IN 490.8 ms
[11:11:48] All 5 required tests passed :)
exit code: 0
```

[原始服务端日志](2026-10-28-special-processing-gametest.txt)。临时运行目录首次启动缺少server.properties和离线认证公钥请求失败均由测试服务端记录，未影响测试执行及正常退出。

## 资源核对

SP-M01..05逐条核对9格定形配方、每格1件、合计与输入表一致、对应原版工作站的minecraft注册名、输出机器、配方加载入口、中英文名称。五台机器的物品／正面／运行正面PNG可解码，模型本地贴图引用存在。两仓`git diff --check`通过。

## 验证范围

本轮未启动原生客户端检查实际界面、未执行压力测试、全量历史回归或第三方配方包兼容验收。NBT验证是同进程保存／重新加载。供能、物流和升级复用现有基础设施；初始药水、酿造材料、烈焰粉、锻造模板和原料仍需要上游提供。
