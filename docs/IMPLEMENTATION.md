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

耐久升级：新上限为旧上限×6/5向下取整，新已损耐久为旧损耗比例×新上限向上取整。附魔和其他组件随原工具复制，写入`reinforced`标记阻止重复应用。铁砧材料修理与单件砂轮去附魔已通过服务端交互测试；双件合并与下界合金锻造本轮补齐，规则和验证见下节。

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

## 加固装备双件修理与锻造（2026-10-20，31/31通过）

- 工作台：覆盖原版`minecraft:repair_item`配方条目，使用继承原版`RepairItemRecipe`的序列化器。匹配、剩余耐久相加、5%额外修理、最大耐久取较大值、清除名称及普通附魔、保留诅咒仍由原版执行；只把任一输入的`reinforced`标记保留到结果，堵住上限保留但标记丢失后可再次加固的漏洞。普通物品行为不变。
- 砂轮：只修正上槽普通、下槽加固的同物品可修理双件组合，保留上槽名称与组件，最大耐久取两件较大值，合计绝对剩余耐久并增加上限的5%；加固标记继承下槽。去除普通附魔、保留诅咒、按诅咒种类重置累计修理成本，经验与输入消费交给原版。其他摆法及单件去附魔直接走原版；已有其他监听器结果不覆盖。
- 铁砧：保留原版左侧目标语义。加固放左侧则保留；普通放左、加固放右时，右侧连同升级作为供料消耗，结果为普通上限、无加固标记。名称、附魔、费用及累计修理惩罚均不重写。
- 锻造：覆盖原版`minecraft:netherite_pickaxe_smithing`条目，仍需要下界合金升级模板、钻石镐与下界合金锭。普通镐完全按原版；加固镐按新材质默认耐久计算一次20%，钻石1873→下界合金2437；已损耐久为`ceil(旧已损 × 2437 / 1873)`，保留名称、附魔和加固标记。源物品不变，不能二次加固。原版直接复制旧`MAX_DAMAGE`会使结果仍停在1873，此处修正为新材质预算。
- 只覆盖两个已有配方ID，避免另加同条件配方造成匹配顺序不确定；资源生成脚本同步维护。更高优先级数据包可替换这些条目。第三方修理/锻造配方与此前已丢失加固标记的旧物品迁移未纳入本轮。
- 游戏内手册新增修理与下界合金升级说明；客户端书页渲染仍待验收。

主代理执行`build runGameTestServer -x downloadAssets`（本机缓存、离线镜像配置）成功，13:28:46报告`All 31 required tests passed :)`，`BUILD SUCCESSFUL in 21s`，退出码0。47份JSON、全部PNG与引用静态校验通过。新增用例如下，原26项同时重跑通过。实际日志摘录见[修理与锻造测试证据](verification/2026-10-20-repair-gametest.txt)。

| 用例 | 已通过的行为 |
| --- | --- |
| grindstoneDoubleRepairPreservesUpgradeInEitherSlot | 铁镐/盾牌×4种加固顺序×6种诅咒排列，共48场景；非零损伤公式、顶槽名称与诅咒等级优先、修理成本、预览不变、仅消费一次、不能二次加固 |
| anvilDoubleRepairKeepsLeftTargetSemantics | 铁镐/盾牌×4种顺序，共8场景；左目标上限与标记、合计绝对剩余加12%、名称、附魔合并、实际经验扣费与消费 |
| craftingDoubleRepairKeepsUpgradeAndVanillaCurseRules | 真实CraftingMenu查配方并取出，共8场景；较大上限、5%修理、保留任一加固、名称/普通附魔清除、诅咒保留、多余材料拒绝与结算 |
| smithingReinforcedDiamondPickUsesNetheriteBase | 真实SmithingMenu，加固钻石1873/损伤120→下界合金2437/157；名称/附魔/诅咒保留、缺/错材料拒绝、预览不扣料、各消耗一件 |
| smithingOrdinaryDiamondPickRemainsVanilla | 普通镐仍为2031上限/损伤100，无加固标记；预览和消费保持原版 |

## 储水与雨水补给

常压罐与集雨器都以同一水方块实体保存4000mB库存，仅接受普通水。新造空罐不送水，带水罐升级继承真实`BLOCK_ENTITY_DATA`。桶交互以1000mB为整桶单位；存档和掉落打包实际水量。

集雨器逐刻用`Level.isRainingAt`检查真实雨、天空、遮挡与生物群系，每刻5mB；不离线补算。每20刻向正下方普通水接口提供至多100mB，仅扣除对方实际接收量；满仓保留原水。该出口是本地重力供水，不冒充尚未实现的共享流体管网。

## 寻矿与共享菜单回归（2026-10-20，39/39通过）

CB-04仍只扫描以玩家所在方块为中心、半径8格的闭球，先检查区块已加载再读方块，只反馈有无信号。抽出同一生产扫描函数用于精确球形和加载谓词验证；真实服务端另在已加载/未加载区块边缘执行扫描，确认相邻区块仍未加载、已加载区块数不增加。普通/深层矿均可检测，消息没有坐标、品位、参数或附加悬浮内容。

原版`ServerPlayerGameMode.useItem`本来就拦截冷却中的正常使用；本轮为`OreIndicatorItem.use`本身补上同一检查，使直接调用也不能重复扫描或重置冷却。主副手共用40刻冷却，前39刻反复尝试被拒，第40刻可用。`useItemOn`默认不扫描，后续独立`useItem`入口才扫描；不消耗或损坏指示片。

共享菜单测试使用两个生存FakePlayer、同一整备台的两个真实`PreparationMenu`，交错调用原版`clicked`处理。覆盖同时尝试取同一产物、右键分取后关闭界面返还光标、满背包/仅剩2份容量，以及竞争安装/移除单件接头。成品与尾砂数量及品质组件守恒，接头拆除不重算已锁定的36kXH批次，下一批恢复40kXH。本轮未发现需要修改整备台生产或菜单逻辑的问题。

| 新增用例 | 验证内容 |
| --- | --- |
| scanPredicateUsesSphereAndNeverReadsUnloadedPositions | 精确半径8格闭球；(8,0,0)包含、(6,6,0)排除；不可用位置不会进入方块读取谓词 |
| realServerUseChecksBothHandsMessagesAndFortyTickCooldown | 主副手、40刻冷却、直接调用守卫、普通/深层/无矿、方块使用后独立物品使用、消息与物品守恒 |
| realServerScanAtLoadedChunkEdgeDoesNotLoadNeighbor | 在准备区已加载边缘真实使用，邻区仍未加载且已加载区块数不增加 |
| twoMenusQuickMoveSettlesEachOutputOnce | 两个菜单交错快速取晶料/尾砂，只有先处理者获得对应产物，空转不重复结算 |
| twoMenusSplitPickupAndCloseConservesOutput | 10份晶料各取5份，关闭界面各返还5份，再次关闭不重复返还 |
| twoMenusConnectorChangesKeepReservedBatchBudget | 两人竞争安装仅占1件，另一人拆除；本批36k、下批40k，4件接头总量守恒 |
| partialViewerCapacityLeavesExactRemainder | 原有62份只接收2份，机器剩8份由另一人取走，合计72份 |
| fullViewerInventoryLeavesProductsForOtherViewer | 满背包快速取料无消耗，另一人可完整取走10份晶料和1份尾砂 |

`build runGameTestServer -x downloadAssets`在本机离线依赖缓存中成功执行；14:15:45报告`All 39 required tests passed :)`，构建29秒，退出码0。原31项一同回归通过；47份JSON、全部PNG与资源引用校验通过。见[寻矿与共享菜单测试证据](verification/2026-10-20-shared-scan-gametest.txt)。这些测试覆盖服务端逻辑与交错菜单结算，不代替双客户端网络同步、客户端提示渲染、多人压力、常规独服与完整进程重启验收。

## 剧情导向与独立工务交易（2026-10-20，42/42与独立重启通过）

后续实现依照[战争责任与主线剧情](../../RE_BFL/02_时间线和剧情设定/02_战争责任与主线剧情.md)与[任务与支线索引](../../RE_BFL/02_时间线和剧情设定/03_任务与支线索引.md)：MAIN-00折桥救援→MAIN-01白灯安置→本星调查。加工与技术是剧情中实际修复、供水、照明和运输行动的工具；不新增平行教学/练习清单、手册入课或免费包。手册仅作可选配方参考，不是强制剧情步骤。剧情NPC与任务运行时尚未实现，不能把设计顺序记为可玩成果。

`SupplyTerminalBlock`是生存可合成方块，不生成NPC实体。原版`minecraft:crafting_shaped`配方为`[" P ", "SSS", " S "]`，P纸、S圆石，共1纸4圆石。独立CB-05每份Q0晶料换1铁锭，无研究门槛；按实际交互手扣样，不重复处理副手。空手、手册和其他非交易交互不送物资。

`isQ0`精确条件为：`material=meteor_phase`、`dryMilliM=10000`、`9000 <= phaseMilliM <= dryMilliM`、`integrity >= 20`、`contamination=0`、`route=common`、`form=granules`。高完整度合格料可用，低于门槛、污染、错形态/路线或缺组件的同ID晶料均不放行。

`SupplyLedger`存于主世界SavedData，全世界所有玩家、终端、维度共享每天4笔；日期为主世界`floor(max(0, dayTime)/24000)`。只有新日期严格大于已记录日期才重置，回拨、返回已见日期、保存重载或重启不额外刷新；跳日不累计未用额度。交易先在背包副本模拟扣一份晶料，再向普通背包放铁，全部成功才提交并扣额度；失败保留材料与额度。主手最后一份耗尽腾出的普通背包槽可接铁；副手槽腾空不能用于收铁，奖励不进入护甲或副手槽。

本轮独立交易版本完成构建与真实服务端42/42回归：16:19:12报告`All 42 required tests passed :)`，测试耗时1.238秒，`BUILD SUCCESSFUL`耗时20秒、退出码0。原39项加3项SupplyGameTests，分别覆盖非交易终端空手/手册仅读不送物、Q0品质与背包事务、真实跨终端/玩家日期共享；51份JSON、全部PNG与资源引用校验通过。见[工务交易回归](verification/2026-10-20-public-supply-gametest.txt)。历史11/23/26/31/39项记录保留；客户端与多人压力按用户要求暂缓。

独立重启探针由`tools/verify_server_restart.py`启动同一隔离世界的两个真实JVM，`test/RestartVerification`仅在`starfall.verifyRestart=prepare/verify`显式属性启用。本轮在`run/restart-verification-1792484353139319500`实跑通过，两进程均正常保存关闭、成功标记齐全、退出码0：普通G12/I30原矿批次在进度73刻、306860XH、750mB水、剩余1笔交易时保存；第二JVM恢复后再运行127刻，得到284000XH、750mB水、10份I28晶料和1份尾砂。最后一笔额度仅结算一次，随后交易拒绝，空转20刻不复制产物。该探针验证机器批次与交易SavedData持久化、重启不补额度和产物不重复结算，不含课程、领取或收益归属；不扩大为所有机器、网络同步、真实玩家库存或死亡场景全覆盖。见[真实重启证据](verification/2026-10-20-server-restart.txt)。
