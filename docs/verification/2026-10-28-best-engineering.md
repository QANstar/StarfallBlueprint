# 贝斯特轻型工程首批验证 · 2026-10-28

## 结果

Minecraft 1.21.1、NeoForge 21.1.262、Java 21.0.12.1、Gradle 8.14.3。完整构建及真实 NeoForge GameTest 服务端全量回归通过，退出码0。

实际服务端输出：

```text
[14:58:31] 216 GAME TESTS COMPLETE IN 18.84 s
[14:58:31] All 216 required tests passed :)
BUILD SUCCESSFUL in 40s
```

命令：`gradle build runGameTestServer --offline --init-script .tools/mirror.init.gradle --console=plain`。使用工作区 Gradle 缓存；测试 JVM 用 `-Djdk.net.hosts.file=D:/Code/Workspace/StarfallBlueprint/.tools/build-hosts` 避免联网公钥请求阻塞，认证服务出现 UnknownHostException 日志，但服务端测试正常启动并全部完成。原始本地日志为 `.tools/best-validation-final.log`。Gradle `test` 为 NO-SOURCE，以上结果来自实际启动的 GameTest 服务端。

全量通过后，仅将菜单提示中的“承载晶材／定向导体／钻头”统一为图册现名“承相结构嵌材／导相铜复材板／钻石磨头”，未改变事务或装备逻辑；最终再次完整 build 成功。

## 贝斯特专项覆盖

新增 BestEngineeringGameTests 共12项，已包含在216项内：

- 本人研究顺序、重复学习不扣费、玩家之间独立，以及死亡 Clone 事件转移研究。
- 骨架与钻切器的研究门槛、精确材料结算；满背包失败不扣料，留空后正常装配。
- 维修两装备只消耗钢材并恢复工务耐久，保留电量、温度、过热锁、名称及额外自定义数据。
- 实际装配台菜单拒绝非法按钮、远距离和已拆除工作台；关闭菜单不掉落背包物品。
- FakePlayer.gameMode.destroyBlock 真实生存采铁矿，每块仅扣256XH、120热量与1磨损；未学习、缺电、过热和磨损满时拒绝破坏。
- 热量1000进入停机，冷到402仍锁，400恢复。
- 注册物品能量能力模拟不写数据；两能匣共享512XH／刻接收上限，同刻重入为0，后续真实刻恢复预算；近满仅提取37XH并保持守恒。
- 地面疾跑15%临时助力不叠加，实际扣16XH；停跑、脱下和空电撤除属性。

## 资源与生成

- verify_resources.py：843份JSON、全部PNG及本地模型／纹理引用通过。
- verify_mc_catalogue.py：245条离散图册配方、17条原版映射通过。
- verify_best_resources.py：装配台九格材料、个人专用装配元数据、专业装备公共配方为零、两仓库名称／美术／模型一致通过。
- 完整生成链重复生成1378个图册和资源文件，字节稳定；64×32透明骨架穿戴纹理的腿部／腰部UV及同步副本检查通过。
- 当前canonical为303项物品、245条配方、169项图鉴；实际执行的定形公共工作台配方为114条。

首轮全量214/216，失败来自两处既有测试预期：CatalogueGameTests固定数量113未包含BEST-M01；PlantCultivationGameTests仍将隐藏旧升级槽当作可见槽。最小更新为114及当前独立电池槽／按需升级视图预期，保留实际配方执行、点击和加工中保护断言，随后全量通过。

## 范围

未启动原生客户端验收新菜单和穿戴效果，未做双客户端联机、压力或长时间生存游玩测试。纹理尺寸和UV静态检查不等于实机外观验收；FakePlayer及Clone事件覆盖不等于完整跨进程重连测试。原有阶段验证目录及证据保留。
