# 基础产线实现说明

## 锁定环境

Minecraft 1.21.1、NeoForge 21.1.262、ModDevGradle 2.0.148、Gradle 8.14.3、Java 21。MOD ID为`starfall_blueprint`；客户端界面在Dist.CLIENT入口注册，共享机器逻辑不引用客户端类。

## 权威设定映射

| 代码内容 | RE_BFL来源 |
| --- | --- |
| 首段范围、质量账、状态与恢复 | `05_开发准备/09_公共科技开发方案.md` |
| PC-R01时间、水、能耗与L01基础线 | `04_游戏设定/07_基础产线与机器合成.md` |
| 整备台3铁1铜4圆石、燃料320kXH | `04_游戏设定/08_机器与常规部件配方.md` |
| CB-01至04用量、20%耐久收益 | `04_游戏设定/04_合金与材料形式/06_公共材料规格与用量.md` |
| 同源原矿、G/I、Q0与普通采收I上限 | `04_游戏设定/01_矿物设定/02_稀有度与公共品位标准.md` |

## 数据与状态

`material_batch`为持久化且网络同步的物品组件。质量量值采用整数毫M；晶料单件10,000毫M、陨相组分9,000毫M。原矿与晶料都写入`common`，无免费改路线入口。当前单源材料以`material=meteor_phase`标识共同来源；高级多组分账不在本阶段。

原矿G12样例：输入1,000,000毫M、陨相120,000毫M；输出10份晶料，共100,000毫M、陨相90,000毫M；尾砂900,000毫M、陨相30,000毫M。250mB工艺水随尾砂保留，不能从本版尾砂免费提取回水。

整备台5个库存槽：原矿、煤/木炭、晶料、尾砂、接头。水量4000mB，能源使用64位XH，燃料余量不换成第二种FE库存。当前只实现燃料供能；尚无外部电力输入接口。

开工：验证原矿组件、出料兼容与容量、水、可用燃料；扣除一块矿与250mB水，把本批晶料/尾砂存为预留输出。工作200刻，每刻扣200XH，接头为180XH。库存与批次写入同一方块实体NBT；完成时恰好一次合并两类输出并清空预留。中途换接头不会重算已锁定的批次功率。缺燃料或输出背压保留进度，区块卸载没有追赶计时。

拆移：机器掉落物含`BLOCK_ENTITY_DATA`，包括库存、水、能源、进度及预留输出，不同时散落内部库存。原矿没有生存可放置方块物品，精准采集也不掉可放置矿块，避免重新放置采掘重抽品质；品质与份数按世界种子、矿坐标及所用时运等级确定，同条件重复计算稳定，无时运保持原输出。寻矿不强加载区块。

### 时运采收规则（本轮服务端回归通过）

普通与深层陨相矿执行同一规则。将所用时运等级截断为L=0..3；L=0直接掉1份，不额外抽取数量随机数；L>0时按原版矿物公式`max(1, random.nextInt(L + 2))`确定份数，I/II/III最多2/3/4份且允许仍为1份。

| L | G6概率 | G12概率 | G24概率 |
| --- | --- | --- | --- |
| 0 | 65% | 30% | 5% |
| 1 | 55% | 35% | 10% |
| 2 | 45% | 40% | 15% |
| 3 | 35% | 45% | 20% |

时运的品质收益只提高品位档位概率，普通采收仍为I30..40，不提高完整度、纯度或加工路线，不替代后续低冲击工具。每份原矿仍为1000M，整备台每份的水、能源、晶料与尾砂账及加工守恒规则不变。矿块自然密度不改；采收产出预算须计入各时运等级的份数及品位收益。

耐久升级：新上限为旧上限×6/5向下取整，新已损耐久为旧损耗比例×新上限向上取整。附魔和其他组件随原工具复制，写入`reinforced`标记阻止重复应用。铁砧材料修理与单件砂轮去附魔已通过服务端交互测试；其他修理/合并组合仍待验证。

## 验证记录

2026-10-20：完整构建通过；45份JSON、全部PNG及本地模型/贴图引用校验通过；真实NeoForge服务端11项GameTest全部通过，配方与进度数据成功加载。

| 用例 | 通过的行为 |
| --- | --- |
| oneCoalEightBatches | 一煤8批、80份晶料、2000mB水消耗、8批尾砂 |
| lowGradeConservesMass | G6%出5份；干量与陨相组分守恒、I扣2 |
| connectorSavesTenPercent | 8批升级加工消耗288kXH，余32kXH |
| dryAndBlockedMachineDoesNotConsume | 缺水/开工满仓不扣料，在制堵塞暂停且不耗能，恢复后只出一批 |
| reloadResumesReservedBatch | 第73刻保存恢复，后127刻完成，不重复出料 |
| qualityAndUpgradeCrafting | 配方实际加载、Q0接收、低I拒绝、镐耐久250→300且损伤100→120、禁止二次加固 |
| automationPorts | 顶矿/底出料边界、附件保护、流体模拟不改变库存、4000mB上限 |
| machineItemKeepsInFlightBatch | 实际机器掉落物重新放置后恢复第73刻，并且只结算一次 |
| rainNeedsRealWeatherAndOpenSky | 晴天0、雨天100mB/秒、顶棚阻止集雨、满罐停收 |
| upgradingTankPreservesStoredWater | 已有2500mB的罐升级为集雨器后仍为2500mB |
| collectorFeedsBenchAndHonorsBackpressure | 经真实能力接口向下供水100mB，接收端满后保留剩余900mB |

本机官方Maven的额外启动配置与DevLaunch下载不可用，测试使用已下载的**同版本官方userdev包原始config.json**生成启动参数，再通过官方BootstrapLauncher直接启动，未替换Minecraft/NeoForge实现。可用[已准备环境启动脚本](../tools/run_prepared_gametests.ps1)复现直接启动。标准联网环境仍可使用`gradlew runGameTestServer`；本地启动摘录见[验证证据](verification/2026-10-20-gametest.txt)。

服务端公共密钥联网请求在沙箱中失败，GameTest不需要玩家认证，11项测试仍完成。测试没有宣称客户端渲染、正常玩家联机、20种子密度或2/5分钟游玩目标已经验收。

## 交互回归扩展（2026-10-20，23/23通过）

新增12项服务端GameTest，连同原11项在真实NeoForge服务端全部通过。最终执行`build runGameTestServer -x downloadAssets`成功，退出码0；下表为本轮新增的已通过用例。控制台摘录见[本轮测试证据](verification/2026-10-20-interaction-gametest.txt)。

| 用例 | 核验内容 |
| --- | --- |
| connectorChangeOnlyAffectsNextBatchAcrossSave | 第73刻安装/移除接头并保存恢复，当前批次仍分别使用40k/36kXH预算 |
| fuelStarvationResumesWithoutReservingTwice | 仅2000XH时加工10刻后暂停，保存恢复不重复扣矿/水，补木炭完成剩余190刻 |
| shieldUpgradePreservesComponentsAndRoundsDamageUp | 盾牌耐久336→403、损伤1→2、命名保留、输入不变、拒绝二次加固 |
| survivalBucketsAtPreparationBench | 生存注/抽水、潜行要求、堆叠空桶守恒、空罐/不足一桶/满罐/不足整桶空位拒绝 |
| survivalBucketsAtWaterTank | 普通常压罐同样的整桶边界与物品守恒 |
| survivalBucketOffhandAndSneakRouting | 副手填/抽水、主手物品保护、堆叠空桶守恒、整备台潜行空桶可达及潜行水桶维持原版路由 |
| ordinaryTankActualDropRetainsWaterWhenPlaced | 实际破坏生成唯一掉落物并重新放置，恢复2500mB且能实际抽水 |
| vanillaHoppersDriveTwoContinuousBatches | 原版漏斗真实刻驱动进矿/进煤/双出料，两批20晶料+2尾砂、500mB与80kXH消耗，40刻空闲不重复 |
| reinforcedPickSurvivesVanillaAnvilMaterialRepair | 强化镐经原版铁砧材料修理，耐久上限/标记/命名保留，扣材料和经验 |
| reinforcedShieldSurvivesVanillaAnvilMaterialRepair | 强化盾牌经原版铁砧木板修理，属性保留与结算 |
| reinforcedPickSurvivesSingleItemGrindstoneDisenchantment | 强化镐经单件砂轮去附魔，保留上限/损伤/标记/命名 |
| reinforcedShieldSurvivesSingleItemGrindstoneDisenchantment | 强化盾牌经单件砂轮去附魔，保留上限/损伤/标记/命名 |

修理测试仅覆盖铁砧材料修理与砂轮单件去附魔；双工具合并、工作台修理、下界合金锻造及其他模组兼容仍待验证。此轮同时修复Windows Gradle包装脚本吞掉失败退出码的问题，已用无效Java启动参数确认包装脚本返回非零状态。

桶测试全部通过`ServerPlayerGameMode.useItemOn`进入真实服务端交互链。原版桶默认不绕过潜行使用，原实现因此无法进入整备台抽水回调；现通过`RightClickBlock`仅对潜行空桶与整备台启用方块处理，并尊重其他监听器明确拒绝的交互。主副手均有覆盖。满罐/不足整桶空位拒绝时，非潜行主手的默认方块交互会消费操作，阻止桶继续使用；副手拒绝和潜行水桶保留原版PASS路由，不声称全场景阻止倒水。

本轮已下载官方DevLaunch和同版本开发包，通过标准Gradle任务启动；本机镜像配置与域名映射仅用于依赖可达性。无图形GameTest跳过826MB客户端资产下载，提供资产索引17的本地引用供运行任务配置校验，未用于客户端验收。首次测试目录缺少server.properties和公共密钥联网失败均为非致命启动日志，全部测试完成并正常关闭。

## 时运采收回归（2026-10-20，26/26通过）

主代理执行`build runGameTestServer -x downloadAssets`成功，13:09:57服务端报告`All 26 required tests passed :)`，`BUILD SUCCESSFUL in 21s`，退出码0。本轮新增3项`OreHarvestGameTests`，连同历史23项全部通过；此前11项与23项记录保持原样。见[本轮时运测试证据](verification/2026-10-20-fortune-gametest.txt)。

| 用例 | 通过的行为 |
| --- | --- |
| fortuneImprovesOreYieldAndGrade | 1024个坐标逐一检查L0..3，普通/深层一致、同点重算一致、各级样本增产提品；无时运保持原结果，I与所用工具不变 |
| oreRequiresCorrectToolAndSilkCannotCreatePlaceableOre | 采收要求正确工具，精准采集不能产出可放置矿块 |
| fortuneAboveThreeUsesTierThreeBudget | 超过III的附魔等级使用III档预算 |

每份1000M的加工守恒仍由原有加工回归覆盖。采收产出预算须计入时运份数与品位收益；此次样本回归不替代资源预算校准，矿块自然密度参数保持原值，20种子密度校准仍未完成。

## 储水与雨水补给

常压罐与集雨器都以同一水方块实体保存4000mB库存，仅接受普通水。新造空罐不送水，带水罐升级继承真实`BLOCK_ENTITY_DATA`。桶交互以1000mB为整桶单位；存档和掉落打包实际水量。

集雨器逐刻用`Level.isRainingAt`检查真实雨、天空、遮挡与生物群系，每刻5mB；不离线补算。每20刻向正下方普通水接口提供至多100mB，仅扣除对方实际接收量；满仓保留原水。该出口是本地重力供水，不冒充尚未实现的共享流体管网。
