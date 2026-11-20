package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestMechItem;
import cn.starfall.blueprint.BestMechProgression;
import cn.starfall.blueprint.BestWorkbenchMenu;
import cn.starfall.blueprint.CatalogueContent;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in real Minecraft rendering only; no world, player, server or gameplay actions. */
public final class BestMechVisualCheck {
    private static int phase,ticks;
    private static final boolean UI_CHECK=Boolean.getBoolean("starfall.bestMechUiCheck");
    private static final String[] FILES=UI_CHECK
            ?new String[]{"workbench-mech-ready.png","workbench-mech-missing.png","workbench-mech-locked.png","workbench-base.png","mech-hud-alerts.png","mech-hud-stops.png"}
            :new String[]{"best-mechs-standard.png","best-mechs-equipped.png","best-mechs-items.png"};
    private BestMechVisualCheck() {}

    public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(mc.getOverlay()!=null)return;
        if(phase==0){
            if(!(mc.screen instanceof TitleScreen))return;
            mc.setScreen(UI_CHECK?uiScreen(1):new ModelPreview(false));phase=1;ticks=0;
        }else if(++ticks==30){
            Screenshot.grab(mc.gameDirectory,FILES[phase-1],mc.getMainRenderTarget(),message->
                    org.slf4j.LoggerFactory.getLogger(BestMechVisualCheck.class).info("Best visual screenshot: {}",message.getString()));
        }else if(ticks==50){
            if(phase==FILES.length){mc.stop();return;}
            phase++;ticks=0;
            mc.setScreen(UI_CHECK ? uiScreen(phase) : switch (phase) {
                case 2 -> new ModelPreview(true);
                default -> new ItemPreview();
            });
        }
    }
    private static Screen uiScreen(int phase){
        return phase<=4?workbench(phase-1):new HudPreview(phase==6);
    }
    private static Screen workbench(int sample){
        var inventory=new Inventory(null);
        String[] keys={"steel","load","conductor","board","steel_fix","head","sensor","sheath"};
        int[] counts={32,16,8,4,16,2,2,8};
        for(int i=0;i<keys.length;i++)inventory.setItem(i+1,CatalogueContent.stack(keys[i],counts[i]));
        if(sample==1){
            inventory.setItem(1,CatalogueContent.stack("steel",3));
            inventory.setItem(2,CatalogueContent.stack("load",4));
            inventory.setItem(7,net.minecraft.world.item.ItemStack.EMPTY);
        }
        var menu=new BestWorkbenchMenu(0,inventory);
        menu.setData(0,sample==2?2:3);for(int i=1;i<4;i++)menu.setData(i,sample==2?0:1);
        return new WorkbenchPreview(new BestWorkbenchScreen(menu,inventory,Component.translatable("block.starfall_blueprint.best_workbench")),sample);
    }
    private static void background(GuiGraphics g,int width,int height){
        g.fill(0,0,width,height,0xff15252e);
        g.fill(0,0,width,60,0xff213a43);
    }
    private static final class ModelPreview extends Screen {
        private static final String[] NAMES = {"SG-E / JUANLIU", "SG-C / JITUAN", "SG-X / GUIHAI"};
        private static final String[] ROLES = {"ENGINEERING", "COMBAT", "UNIVERSAL"};
        private static final String[] TYPES = {BestMechProgression.ENGINEERING, BestMechProgression.COMBAT, BestMechProgression.UNIVERSAL};
        private static final int[] ACCENTS = {0xffefba4f, 0xffdd7265, 0xff7be7e6};
        private final boolean equipped;
        private BestMechModel model;

        ModelPreview(boolean equipped) {
            super(Component.literal(equipped ? "BEST INDUSTRIES / FULL LOADOUT" : "BEST INDUSTRIES / WATER GOD PROGRAM"));
            this.equipped = equipped;
        }

        @Override protected void init() { model = new BestMechModel(minecraft.getEntityModels().bakeLayer(BestMechModel.LAYER)); }
        @Override public void renderBackground(GuiGraphics g, int mx, int my, float delta) {}

        @Override public void render(GuiGraphics g, int mx, int my, float delta) {
            g.fill(0, 0, width, height, 0xff101a24);
            g.drawString(font, title, 18, 19, 0xffecf2f4, false);
            g.drawString(font, equipped ? "INSTALLED HARDWARE / REAR THREE-QUARTER" : "CHASSIS LINEUP / FRONT THREE-QUARTER", 18, 35, 0xffa0b6c4, false);
            int cell = (width - 22) / 3;
            int floor = height - 67;
            float scale = Math.min(cell / 3.8f, (floor - 92) / 5.35f);
            var platforms = BestMechModel.Platform.values();
            for (int i = 0; i < platforms.length; i++) {
                int x = 11 + i * cell;
                int center = x + cell / 2;
                g.fill(x + 3, 56, x + cell - 3, height - 15, 0xff162634);
                g.fill(x + 3, 56, x + cell - 3, 58, ACCENTS[i]);
                g.drawString(font, NAMES[i], x + 13, 69, 0xffecf2f4, false);
                g.drawString(font, ROLES[i], x + 13, 83, ACCENTS[i], false);
                for (int y = floor; y > 100; y -= 20) g.fill(x + 10, y, x + cell - 10, y + 1, 0xff22394a);
                g.fill(x + 10, floor + 8, x + cell - 10, floor + 9, ACCENTS[i]);
                g.flush();
                RenderSystem.enableDepthTest();
                Lighting.setupFor3DItems();
                g.pose().pushPose();
                g.pose().translate(center, floor, 170);
                g.pose().scale(scale, scale, scale);
                g.pose().mulPose(Axis.XP.rotationDegrees(-9));
                g.pose().mulPose(Axis.YP.rotationDegrees(equipped ? -35 : 145));
                g.pose().translate(0, -1.5, 0);
                var item = equipped ? BestMechItem.creativeStack(TYPES[i]) : CatalogueContent.stack(BestMechProgression.itemKey(TYPES[i]), 1);
                if (equipped) {
                    var state = BestMechItem.state(item);
                    var combat = state.getCompound(cn.starfall.blueprint.BestMechCombatSystems.STATE);
                    model.posePreview(platforms[i], BestMechProgression.mask(state), cn.starfall.blueprint.BestMechArms.read(state),
                            cn.starfall.blueprint.BestMechLegs.read(state), cn.starfall.blueprint.BestMechBodies.read(state),
                            cn.starfall.blueprint.BestMechCores.read(state), state.getString(cn.starfall.blueprint.BestMechModules.STATE),
                            combat.getString("Weapon"), combat.getString("Drone"), state.getBoolean(cn.starfall.blueprint.BestMechPowerSystems.WIRELESS_STATE));
                } else {
                    model.posePreview(platforms[i], 0, i == 0 ? cn.starfall.blueprint.BestMechArms.DRILL : "", "", "", "", "", "", "");
                }
                model.render(g.pose(), g.bufferSource(), 15728880, OverlayTexture.NO_OVERLAY);
                g.flush();
                g.pose().popPose();
                Lighting.setupForFlatItems();
                RenderSystem.disableDepthTest();
                g.renderItem(item, x + 12, height - 43);
                g.drawString(font, equipped ? "DEPLOYMENT PRESET" : "PACKED CHASSIS", x + 35, height - 40, 0xffafc4cf, false);
            }
        }
    }
    private static final class ItemPreview extends Screen {
        private static final String[] LABELS = {"ENGINEERING / CANG LAN", "COMBAT / NU TAO", "UNIVERSAL / FINAL UPGRADES", "SUPPORT / FACILITIES"};
        private static final String[][] ITEMS = {
                {"best_mech", "best_canglan_engineering", "best_canglan_mobility", "best_canglan_endurance", "best_mech_drill_arm", "best_mech_heavy_excavator_arm", "best_engineering_confluence"},
                {"best_combat_mech", "best_nutao_fire_control", "best_nutao_armor", "best_nutao_sustained", "best_mech_machine_gun", "best_mech_explosive_launcher", "best_combat_confluence"},
                {"best_universal_mech", "best_zhenyuan_module", "best_tianpu_module", "best_changchuan_module", "best_mech_heavy_fusion_core", "best_mech_wireless_charger", "best_mech_laser"},
                {"best_mech_maintenance_drone", "best_mech_healing_drone", "best_mech_defense_drone", "best_mech_attack_drone", "best_mech_medical_supply", "best_workbench", "mech_maintenance_platform"}
        };

        ItemPreview() { super(Component.literal("BEST INDUSTRIES / PARTS CATALOGUE")); }
        @Override public void renderBackground(GuiGraphics g, int mx, int my, float delta) {}

        @Override public void render(GuiGraphics g, int mx, int my, float delta) {
            g.fill(0, 0, width, height, 0xff101a24);
            g.drawString(font, title, 18, 19, 0xffecf2f4, false);
            int rowHeight = (height - 60) / ITEMS.length;
            int gap = (width - 66) / ITEMS[0].length;
            for (int row = 0; row < ITEMS.length; row++) {
                int y = 42 + row * rowHeight;
                g.fill(18, y, width - 18, y + rowHeight - 6, 0xff162634);
                g.drawString(font, LABELS[row], 26, y + 6, 0xffa6cdd8, false);
                for (int i = 0; i < ITEMS[row].length; i++) {
                    String key = ITEMS[row][i];
                    var stack = key.equals("mech_maintenance_platform")
                            ? new net.minecraft.world.item.ItemStack(cn.starfall.blueprint.MechMaintenancePlatforms.ITEM.get())
                            : CatalogueContent.stack(key, 1);
                    int x = 33 + i * gap;
                    g.pose().pushPose();
                    g.pose().translate(x, y + 19, 0);
                    float scale = Math.max(1, Math.min(3.5f, (rowHeight - 28) / 16F));
                    g.pose().scale(scale, scale, 1);
                    g.renderItem(stack, 0, 0);
                    g.pose().popPose();
                }
            }
        }
    }

    private static final class WorkbenchPreview extends Screen {
        private final BestWorkbenchScreen preview;private final int sample;
        WorkbenchPreview(BestWorkbenchScreen preview,int sample){super(Component.literal("装配台视觉样例"));this.preview=preview;this.sample=sample;}
        @Override protected void init(){
            preview.init(minecraft,width,height);
            preview.selectPage(sample==3?0:3);
        }
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){}
        @Override public void render(GuiGraphics g,int mx,int my,float delta){
            background(g,width,height);preview.render(g,-100,-100,delta);
            String state=switch(sample){case 0->"机甲页 / 材料齐备";case 1->"机甲页 / 材料不足";case 2->"机甲页 / 知识未满足";default->"工务页 / 已学与目标状态";};
            g.drawString(font,"真实装配台界面 / "+state,16,12,0xffe5f2ed,false);
            g.drawString(font,"无世界 · 手工样例库存与个人知识 · 未执行学习、制造或维护",16,32,0xffa9c5c6,false);
        }
    }
    private static final class HudPreview extends Screen {
        private final boolean stops;
        HudPreview(boolean stops){super(Component.literal("机甲 HUD · 样例状态"));this.stops=stops;}
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){}
        private void panel(GuiGraphics g,int x,int y,String label,float hp,int energy,int heat,int wear,boolean isolated,boolean locked,boolean qualified,int request){
            g.drawString(font,label,x,y-14,0xffb6cfc8,false);
            BestMechClient.renderPanel(g,x,y,new BestMechClient.Snapshot(hp,100,energy,heat,wear,hp>0,isolated,locked,qualified,request));
        }
        @Override public void render(GuiGraphics g,int mx,int my,float delta){
            background(g,width,height);
            g.drawString(font,title,16,12,0xffe5f2ed,false);
            g.drawString(font,"复用实际 HUD · 无世界 / 非驾驶实机 · 以下均为手工样例数值",16,32,0xffa9c5c6,false);
            if(stops){
                panel(g,24,96,"A / 电池隔离",100,12000000,320,220,true,false,true,0);
                panel(g,width/2+8,96,"B / 磨损耗尽",55,12000000,320,2400,false,false,true,0);
                panel(g,24,284,"C / 驾驶知识不足",100,12000000,320,220,false,false,false,0);
                panel(g,width/2+8,284,"D / 移动电量不足",100,399,320,220,false,false,true,0);
            }else{
                panel(g,24,96,"A / 正常通电",100,12000000,320,220,false,false,true,0);
                panel(g,width/2+8,96,"B / 低电高热 + 机体受损",35,20000,950,1800,false,false,true,0);
                panel(g,24,284,"C / 已降至600但热锁仍在",60,12000000,600,1800,false,true,true,0);
                panel(g,width/2+8,284,"D / 请求等待（非成功反馈）",95,12000000,440,220,false,false,true,12);
            }
            g.drawString(font,"状态由真实绘制逻辑计算；没有模拟服务器响应或实际钻切。",16,height-28,0xffa9c5c6,false);
        }
    }
}
