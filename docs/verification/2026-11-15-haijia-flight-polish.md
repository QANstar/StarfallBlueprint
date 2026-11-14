# 海隼飞机操作、特效与音效修复验证

- 激光实体覆盖完整束线的裁剪盒，并采用独立512格可见距离，避免细小实体的默认距离使拉远镜头后光束消失。光束、命中光环与导弹明亮尾焰不依赖粒子设置；导弹另有逐段烟火轨迹。
- Q/E读取物理按下状态，避免打开背包时遗留E键状态；左右滚转对称，松键主动回正，地面清零。
- HUD改为右下角124×68图标面板，显示武器、弹药、机体、电量、速度与推力；取消文字说明及锁定进度。
- 左键开火当刻，由服务端寻找192格内、半角12°视锥中的可见目标。无需右键和等待，松开开火清除候选，已发射导弹独立追踪原目标。炸弹保留重力投放。
- 新增专用引擎、强推力、激光、导弹、投弹及切换武器事件，复用游戏内置音频并提供中英字幕。引擎单实例循环与平滑混音，退出／下机／断电后停止；武器仅在成功发射时播放。实际爆炸继续使用原生爆炸声音。

构建通过。31项服务端测试通过，包含首刻捕捉、导弹跟踪与冷却、对称滚转与回正、远距离光束可见性。实际客户端检查覆盖遗留E状态、滑跑、开火、滚轮、飞行、隐藏驾驶员、镜头及音频。

实际客户端通过结果（音效检查记录已创建的OpenAL声音通道，而非仅检查播放调用）：

```
PASS: runway taxi and rotation, real mouse/wheel events and packets, immediate fire-to-target acquisition, wide laser, guided missile, gravity bomb, held afterburner; turn=-102.65625, peak horizontal=3.1998711521911316, max client step=4.524463518121039, hidden pilot frames=933, camera distance=13.999996, actual audio channels=[hj_boost, hj_missile, hj_laser, hj_weapon_select, hj_bomb_release, hj_engine], engine frames=175
```
