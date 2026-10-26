# 机器升级槽原生客户端界面验收

验收日期：2026-10-26（Asia/Shanghai）。

历史记录：以下截图对应当日较早的固定四／五格版本。当前升级栏已改为动态追加与分页；本记录保留当时结果，当前构建及定向测试见[动态升级槽验收](growing-upgrades-acceptance.txt)，本轮未更新客户端截图。

使用现有 `IndustryVisualCheck` fixture，在两个独立游戏目录中启动真实 Minecraft 1.21.1 / NeoForge 21.1.262 客户端。使用已构建的模组 classes/resources，没有修改 Java 源码或构建配置，没有打开或创建玩家世界。启动脚本位于 `.tools/run_machine_upgrades_visual.ps1`；启动参数为现有 `build/moddev/clientRunVmArgs.txt` 与 `clientRunProgramArgs.txt`，另外指定 `--width 1280 --height 720`。仅验收目录的选项设为中文、GUI scale 2，并跳过首次无障碍引导。

最终运行结果：

- 整备台：`-Dstarfall.visualCheck=true -Dstarfall.unifiedPreparationVisualCheck=true`；客户端于16:43:10自动退出，Java/脚本退出码0。
- 自动合成台：`-Dstarfall.visualCheck=true -Dstarfall.autocrafterRecipePreviewVisualCheck=true`；客户端于16:43:18自动退出，Java/脚本退出码0。
- 两项后台任务输出均已收集，没有剩余相关客户端任务。

最终截图（1280×720）：

- [整备台](prep/screenshots/unified-preparation-interface.png)：左侧五个升级槽完整显示，省能接头仅在左栏显示一次；原料、成品、燃料、附件、液体及完整玩家物品栏均可见，没有重叠或裁切。
- [自动合成台配方A](autocrafter/screenshots/autocrafter-recipe-a.png)：左侧四个升级槽完整显示，右上成品样品槽保留；右侧木棍工作台配方预览和库存翻页按钮完整显示。
- [自动合成台配方B](autocrafter/screenshots/autocrafter-recipe-b.png)：同一原生客户端切换至第二个木棍配方的fixture截图。

日志：`prep/client.log`、`autocrafter/client.log`，以及对应 `logs/latest.log`。环境为 Oracle Java 21.0.12.1、AMD Radeon 780M、OpenGL 4.6。

声音设备在本环境不可用，日志记录 `Failed to open OpenAL device` 后客户端自动禁用声音与音乐；贴图、中文字体、界面渲染及自动截图正常，最终客户端退出码均为0。该环境提示不属于升级槽代码失败。

先前854×480试拍因GUI逻辑高度不足发生上下裁切，最终截图已由1280×720完整窗口重新生成并覆盖。此前使用JAVA_TOOL_OPTIONS的提示被PowerShell包装器误作NativeCommandError；最终使用直接-D参数运行，真实退出码如上。

目视范围仅覆盖现有整备台和自动合成台fixture；实际网络操作、Shift堆叠与批次锁定由本轮服务端GameTests验证。没有新增quarry界面fixture。
