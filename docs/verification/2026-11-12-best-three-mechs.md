# 三机组件制实现与验证（2026-11-12）

> 本文下方334项及旧机兼容验证为前轮历史记录。按用户后续要求，旧整机、兼容与迁移代码及对应测试已删除；当前验证以文末“无兼容精简版”记录为准。

环境：Windows 11、Oracle Java 21.0.12.1、NeoForge 21.1.262、Gradle 8.14.3。使用工作区既有GRADLE_USER_HOME和镜像初始化脚本解析离线依赖。

## 实现范围

- 涓流、激湍、归海三机，旧沧澜物品与实体ID保留兼容且不再公开制造。
- 六种可选强化、三种可并装最终强化、两类汇流总成、三类战斗基础件及实物配方。
- 工务装配台制造、安装、拆卸页；统一成长新增11节点，注册34项，退休旧进阶工程节点后33项可见。
- 双机融合消耗、组件与配装返还、生命比例、能源合计、燃料及计数、冷却、来源身份保留；失败整笔不变。
- 服务器直射武器，真实骑乘、机型知识、遮挡、伤害、电热、磨损和冷却检查；归海内建工程工具与独立火控键。
- 中英文名称、16项新物品定义/图标/模型、两种机体配色及可重复资源生成。

## 已执行检查

- 初次整包build成功。
- 新三机/装配/成长与现有工程模块、臂、机体、核心、轮组、掉落和区域作业共53项服务端GameTest通过。
- 首轮完整332项回归发现9项旧晋升及重型机预期/兼容问题，继续修复，不能将此轮记录视为全通过。
- 独立审查发现工程扩展与强化机体冲突、货舱降容丢货、纯战斗路线收纳权限不完整，已列入修复与专项回归。
- 资源脚本验证993份JSON、458份PNG及16项新增物品纹理引用；定义与两种语言文件重复生成字节一致。

## 最终结果

2026-11-12 12:52（Asia/Shanghai），执行以下命令成功：

```powershell
$env:GRADLE_USER_HOME='D:\Code\Workspace\.gradle'
.\gradlew.bat -I .tools/mirror.init.gradle build runGameTestServer --offline --console=plain
```

- 整包构建成功。
- 完整334项真实NeoForge服务端GameTest全部通过（约20.21秒测试阶段，整体40秒）。
- 新增强化Body＋工程扩展＋29M储能＋150HP部署收纳测试、货舱Slot9/18/26降容拒绝回滚测试通过。
- 旧heavy数值保持原速度和冷却，canonical工程机保留旧尺寸、货舱、能源、损伤和已付燃料；动作26组件交易测试替换旧整机晋升预期，状态保留断言继续执行。
- 独立静态复核确认强化Body兼容、货舱降容、纯战斗收纳权限三项修复关闭。
- 两仓库git diff --check通过，仅有行尾转换提示。

构建产物为[starfall_blueprint-0.1.0.jar](../../build/libs/starfall_blueprint-0.1.0.jar)。未进行真人客户端音画、操控和多人联机验收。三机共享现有模型骨架；专用吊运、医疗与团队治疗仍为后续范围，当前能力边界见[三机指南](../BEST_THREE_MECHS.md)。

## 无兼容精简版（当前结果）

按用户后续要求移除旧沧澜整机注册、物品定义、模型与纹理、LegacyHeavy及迁移状态、整机晋升helper、旧进阶研究和退休节点映射。当前只注册三种机甲，成长目录33项；九格工程臂仍作为实际组件配方和区域作业配件保留。

删除旧重型整机/晋升测试与场景，精简新流程测试中的旧知识、索引与兼容断言。旧约700行资源验证脚本改为当前资源与引用检查。本次只跑相关关键测试，不再执行上一轮完整回归。

2026-11-12 17:11（Asia/Shanghai）最终执行：

```powershell
python tools/verify_best_resources.py
$env:GRADLE_USER_HOME='D:\Code\Workspace\.gradle'
.\gradlew.bat -I .tools/mirror.init.gradle build runGameTestServer '-PgameTestNamespaces=starfall_blueprint_mech_assembly,starfall_blueprint_mech_three,starfall_blueprint_progression,starfall_blueprint_heavy_drill' --offline --console=plain
```

结果：整包构建成功；15项关键GameTest全部通过，覆盖合成扣料与失败回滚、组件拆装、成长支付、真实采掘和射击及区域工具。资源检查通过992份JSON、456份PNG和16项新物品引用。JAR条目检查确认没有旧整机资源、晋升helper及已删除的旧机测试类。heavy_drill命名空间为保留的九格工具臂测试，不注册额外机体。

两仓库现行文档同步为无旧机兼容的三机组件路线，旧版存档迁移不再提供；此前的334项结果仅保留为历史记录。构建包路径不变。
