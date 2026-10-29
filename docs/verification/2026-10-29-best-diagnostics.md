# 贝斯特工务诊断与设备隔离：2026-10-29

## 本轮实现

工务诊断以个人`BestDiagnostics`独立保存，在原有三级研究之后分支学习；不要求轻载通行知识、不新增第四级研究，死亡复制保留。装配台新增动作13–16：学习、装配诊断器、隔离主手设备、接通主手设备；沿用库存副本校验后提交。材料不足、成品无处放置、重复学习或重复切换均不扣料、不改设备。

工务诊断器为不可堆叠、无耐久、无能量接口的普通工具，仅能由合格操作者手工装配。主手使用诊断器读取副手骨架或钻切器：电量／容量、热量／上限／解锁阈值、过热锁、剩余耐久、隔离状态及操作者技术条件并列呈现；骨架另列组件安装、档位与本人轻载知识。诊断不充电、不维修、不冷却、不消耗工具，也不初始化或修改被检查设备的自定义数据。

设备使用`BestBatteryIsolated`隔离标记。隔离时，充电能力的模拟与实际接收、能匣转入、疾跑助力、跨步助力和钻切均停止；已取得的充电接口也检查实时隔离状态。维修、工位冷却与自然冷却继续可用，并保留隔离标记。接通只清除隔离状态，不恢复电量或耐久、不清空热量或过热锁，也不改变组件、累计工作刻及其他元数据。缺失标记的旧设备仍视为已接通。

新增原创32×32透明PNG、双语语言键、图鉴和两仓库操作指南；诊断器及其专门装配不进入公共自动配方。菜单增加诊断页，保留原库存布局及原动作编号。

## 验证结果

环境：Windows 11、Java 21.0.12.1、Minecraft 1.21.1、NeoForge 21.1.262、Gradle 8.14.3。

- Gradle `build runGameTestServer`完成编译、资源处理、打包与校验，退出0；没有独立JUnit测试。现有ClientSetup弃用警告仍在，无编译错误。
- 2026-10-29 12:48:15真实NeoForge服务端运行诊断、整备、轻载三个命名空间，共13项测试，13/13通过，耗时590.2ms；包含本批新增5项、整备回归3项、轻载回归5项。`BUILD SUCCESSFUL in 26s`。
- 原始服务端日志保存在仓库根目录`2026-10-29-best-diagnostics-server.log`（本地运行资料，不提交）。
- 资源链build_atlas.py → sync_catalogue.py → generate_catalogue_resources.py各运行一次；静态验证通过，canonical现为306项物品、245条公共配方、172项图鉴。新增诊断器为32×32透明RGBA原创图标，无公共配方或能量接口。
- 静态复核后将工位主手提示与诊断副手提示分开，缩短分页按钮和英文页脚，生成器与双语资源一致同步；玩法逻辑未变，最终再次`build`成功，`BUILD SUCCESSFUL in 8s`，产物包含最终文案。
- 最终verify_best_resources.py与两仓库git diff --check通过。未重跑全量资源字节稳定性校验；Git仅报告既有LF/CRLF规范化提示。

测试源：`src/main/java/cn/starfall/blueprint/test/BestDiagnosticsGameTests.java`。

1. `diagnosticsLearningAndToolAssemblyArePersonalAndAtomic`：三级前置、独立个人知识、精确学习成本、死亡复制、满背包制造拒绝、精确制造成本、普通工具属性与无能量接口。
2. `handheldDiagnosticsAreReadOnlyAndRespectPersonalKnowledge`：未学习者拒绝、并列报告多个故障、骨架与个人知识状态、有效／无效／全新设备的严格只读检查。
3. `isolationAndReconnectionPreserveDeviceAndRequireKnowledge`：资格检查、重复动作拒绝、骨架和钻切器均可切换、只修改隔离字段、普通工具拒绝。
4. `isolationBlocksRealCapabilitiesAndBatteryTransfers`：隔离后已有接口立即拒绝模拟与实际接收、无写入和来源损失、接通后恢复512XH预算。
5. `isolatedDevicesStopWorkButRetainServicing`：立即清理跨步、阻断疾跑与零硬度钻切、工位维修／冷却、便携整备与自然冷却保留标记及其他字段。

## 运行方式与边界

常规环境：

```powershell
.\gradlew.bat -PgameTestNamespaces=starfall_blueprint_diagnostics,starfall_blueprint_servicing,starfall_blueprint_mobility build runGameTestServer
```

本机沿用已缓存依赖、现有镜像init脚本与专用hosts文件离线执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME='D:\Code\Workspace\.gradle'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.hosts.file=D:/Code/Workspace/StarfallBlueprint/.tools/build-hosts'
.\gradlew.bat --offline --no-daemon -I .tools/mirror.init.gradle -PgameTestNamespaces=starfall_blueprint_diagnostics,starfall_blueprint_servicing,starfall_blueprint_mobility build runGameTestServer
```

专用hosts文件使Minecraft服务公钥请求出现预期`UnknownHostException: api.minecraftservices.com`；不影响本机离线GameTest完成。

原首批216项服务端测试及各阶段历史结果按原记录保留。本轮验收范围为新增诊断与直接相邻的整备、轻载通行；原生客户端菜单、报告实际显示和多人实机仍待后续验收。
