# 岭川黑狗战甲首批验证 · 2026-10-29

## 实现范围

独立贝斯特创造栏，以及独立岭川黑狗创造栏；层相晶与陨相／下界合金复合的层相合金；五台真实2×2×2机器；16节点个人树状研发；八个基础部件、两级核心升级和12个能力模块；夜巡腕表整套展开／收回；装备槽锁定、核心负载与等级校验、原件回退、慢充与外部充电。现行玩法和数值见[指南](../LINGCHUAN_BLACKDOG.md)。

## 服务端

- Java 21.0.12、Minecraft 1.21.1、NeoForge 21.1.262；Gradle 8.14.3，离线缓存构建。`build`成功，产物`build/libs/starfall_blueprint-0.1.0.jar`。
- 使用项目既有`local-run-config.init.gradle`与准备后的官方BootstrapLauncher参数执行真实NeoForge服务端，避免缺失的DevLaunch额外下载；没有以模拟器替代游戏引擎。
- 最终 **243/243** 项必需GameTest通过，包含新增 **14/14** 岭川专项；其余229项覆盖既有公共工业、机器、能源物流、蓝图及贝斯特基础、整备、通行和诊断。完整输出见[GameTest日志](2026-10-29-lingchuan-blackdog/gametest.txt)。
- 新专项：个人研发前置、单项支付和重复点击；无需层相产品的起步；实际下界合金消耗、输出预留；部件制造知识；八部位完整性和物品存档；换件负载、返还旧件与保留电量；空装备槽及独立部件锁定、满背包收回；模块效果和收回清理；有限能源护盾；外壳共享能源及单机器掉落；火控投射物、真实扣电和冷却；采掘炮被基岩阻挡且不伤害其后实体；核心慢充、降级裁定；死亡知识复制及孤立外观件清理。
- FakePlayer原生忽略伤害，护盾专项通过真实NeoForge事件总线派发`LivingIncomingDamageEvent`，验证1000 XH只能抵消2点伤害、10点来袭仍剩8点；不把FakePlayer的免伤当作护盾效果。

## 资源与客户端

`tools/verify_resources.py`通过：937份JSON、全部PNG及本地模型／纹理引用。新增资产由`tools/generate_lingchuan_resources.py`确定性生成，已接入完整资源生成入口。层相定义单独保存在`assets/starfall_blueprint/lingchuan.json`，不覆盖原公共图册。

独立客户端目录`.tools/lingchuan-visual`实际加载NeoForge和全部新资源，在1280×800、GUI比例2、简体中文下渲染并截图；未打开或修改玩家存档。检查了模型缺纹理、部位标签、240像素高界面、研发已学／可学／未满足状态、清楚的全前置提示和跨分支连线。截图使用展示数据，不能替代真人多人游玩测试。

- [五台机器及22种部件](2026-10-29-lingchuan-blackdog/lingchuan-machines.png)
- [研发树和支付方式](2026-10-29-lingchuan-blackdog/lingchuan-research.png)
- [八部位组合与换件界面](2026-10-29-lingchuan-blackdog/lingchuan-assembly.png)

飞弹首批沿用原版大型火球弹体和爆炸规则；浮游炮为环绕能量投影及服务端定时射击。专用飞弹／浮游炮实体模型、真人飞行手感、多人PVP平衡和真人死亡／掉线／跨维度长时间游玩仍需后续验收。本轮没有宣称这些实机项目已经通过。

RE_BFL已同步国家配色与技术主线、黑狗专项、LC科技树、层相材料、能源概览和核心接口，明确旧ARM屏障主线及XC-ARM临时预算不再约束本次黑狗实现。原有公共PM-04工程板工艺保留，不能替代武装合金。
