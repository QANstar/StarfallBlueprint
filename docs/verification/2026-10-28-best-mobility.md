# 贝斯特轻载通行扩展：2026-10-28

## 本轮实现

轻载通行作为野外整备之后的独立个人知识，以`BestLightMobility`保存，死亡复制保留，原研究等级仍为0–3。装配台新增动作8–12：学习、制造跨步组件、安装主手骨架、切换档位、拆回组件；采用库存副本验证后提交，空间或材料不足不扣费。安装默认关闭跨步档；装拆与切档保留原电量、热量、过热锁、磨损、累计工作刻及其他自定义数据。

穿戴改装骨架且本人具备三级研究与轻载知识时，可开启持续地面跨步档：服务端STEP_HEIGHT临时增加0.4，原版0.6提升至1.0。每工作刻消耗24XH、发热4，与原自然冷却2和每100工作刻磨损1共用；站立待命照常计费。跨步档与疾跑助力互斥。潜行、疾跑、涉水、熔岩、骑乘、飞行、滑翔、离地、死亡、旁观以及装备／知识／资源条件不满足时不生效；只清理本功能自己的属性修正。

工位增加第二页，保留原库存布局；同步个人轻载知识。跨步组件为不可堆叠普通物品，无电量、热量或磨损，配有原创32×32透明PNG；不进入公共自动配方。

## 实际验证

环境：Windows、Java 21.0.12.1、Minecraft 1.21.1、NeoForge 21.1.262、Gradle 8.14.3。

- Gradle `build`完成compileJava、processResources、jar、assemble和check；没有独立JUnit测试。现有ClientSetup弃用警告仍在，无编译错误。
- 2026-10-28 16:13:57真实NeoForge服务端仅运行`starfall_blueprint_mobility`命名空间的本批5项GameTest，5/5通过，耗时594.1ms。Gradle退出0，`BUILD SUCCESSFUL in 25s`。
- 资源链build_atlas.py → sync_catalogue.py → generate_catalogue_resources.py各执行一次，verify_best_resources.py静态检查通过：305项物品、245条配方、171项图鉴。
- 静态规则覆盖独立知识、装配预算、组件非动力物品、组件安装拆回元数据、待命计费与互斥、双语语言键和透明贴图；保留既有野外整备检查。
- 两仓库git diff --check无空白错误。

测试源：`src/main/java/cn/starfall/blueprint/test/BestMobilityGameTests.java`；原始服务端日志：`2026-10-28-best-mobility-server.log`。

1. `mobilityLearningIsIndependentPersonalAndCloned`：三级前置、重复学习不扣料、独立个人知识、死亡复制，不引入第四等级。
2. `moduleAssemblyInstallationAndRemovalAreAtomic`：制造物理组件、安装消耗与默认关闭、拒绝重复安装、切档和数据保留、满背包拆回拒绝且不改设备、腾位后返还一个组件。
3. `strideChargesStandbyAndStopsAtEveryResourceBoundary`：待命24XH／热4、工作刻磨损、疾跑／潜行／飞行／离地和个人知识限制、低电量／过热／磨损停机、最后电量与热阈值即时清理、脱下仅清本功能修正、原疾跑模式仍有效。
4. `existingServicingPreservesInstalledHardwareAndMode`：原装配台维修、冷却和便携整备包保留组件模式、电量、累计工作刻和额外数据。
5. `actualCollisionClimbsOneBlockButRespectsFenceAndLowCeiling`：真实调用原版Entity.move碰撞逻辑，普通骨架被一格实心台阶挡住，开启跨步后可越过；1.5格栅栏和阻挡身体空间的低顶仍不可越过。

## 运行方式与验证边界

常规环境运行：

```powershell
.\gradlew.bat -PgameTestNamespaces=starfall_blueprint_mobility build runGameTestServer
```

本机使用已缓存依赖、现有镜像init脚本与专用hosts文件离线执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME='D:\Code\Workspace\.gradle'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.hosts.file=D:/Code/Workspace/StarfallBlueprint/.tools/build-hosts'
.\gradlew.bat --offline --no-daemon -I .tools/mirror.init.gradle -PgameTestNamespaces=starfall_blueprint_mobility build runGameTestServer
```

专用hosts文件使Minecraft服务公钥请求出现预期`UnknownHostException: api.minecraftservices.com`；不影响本机离线GameTest完成。本批没有重复首批全量216项或上批3项服务端测试，没有重复全资源字节稳定性测试；上述216/216和3/3仅为各批历史结果。原生客户端双页菜单、穿戴预测与多人实机尚未验收。
