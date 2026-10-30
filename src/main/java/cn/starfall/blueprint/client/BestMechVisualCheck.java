package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BestWorkbenchMenu;
import cn.starfall.blueprint.CatalogueContent;
import cn.starfall.blueprint.StarfallBlueprint;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderType;
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
            :new String[]{"mech-model.png","best-workbench-mech.png","mech-hud.png","best-workbench-base.png"};
    private BestMechVisualCheck() {}

    public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(mc.getOverlay()!=null)return;
        if(phase==0){
            if(!(mc.screen instanceof TitleScreen))return;
            mc.setScreen(UI_CHECK?uiScreen(1):new ModelPreview());phase=1;ticks=0;
        }else if(++ticks==30){
            Screenshot.grab(mc.gameDirectory,FILES[phase-1],mc.getMainRenderTarget(),message->
                    org.slf4j.LoggerFactory.getLogger(BestMechVisualCheck.class).info("Best visual screenshot: {}",message.getString()));
        }else if(ticks==50){
            if(phase==FILES.length){mc.stop();return;}
            phase++;ticks=0;
            mc.setScreen(UI_CHECK?uiScreen(phase):phase==2?workbench(0):phase==3?new HudPreview(false):workbench(3));
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
        private BestMechModel model;
        ModelPreview(){super(Component.literal("贝斯特 · 首台工程机甲 / 模型预览"));}
        @Override protected void init(){model=new BestMechModel(minecraft.getEntityModels().bakeLayer(BestMechModel.LAYER));}
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){}
        @Override public void render(GuiGraphics g,int mx,int my,float delta){
            background(g,width,height);
            g.drawString(font,title,16,12,0xffe5f2ed,false);
            g.drawString(font,"真实客户端模型与纹理 · 无世界 / 非驾驶实机 · 碰撞尺寸 1.8 × 3.2 格",16,32,0xffa9c5c6,false);
            String[] labels={"正面 / FRONT","四分之三 / 3-4 VIEW","背面 / BACK"};
            float[] angles={180,145,0};int cell=width/3;
            for(int i=0;i<3;i++){
                int x=i*cell;
                g.fill(x+8,76,x+cell-8,height-66,0xff263f49);
                g.drawCenteredString(font,labels[i],x+cell/2,88,0xffdfeee7);
                g.flush();RenderSystem.enableDepthTest();Lighting.setupFor3DItems();
                g.pose().pushPose();g.pose().translate(x+cell/2f,height*.58f,150);
                float scale=Math.min(cell/2.5f,(height-180)/3.6f);
                g.pose().scale(scale,scale,scale);
                g.pose().mulPose(Axis.XP.rotationDegrees(-8));g.pose().mulPose(Axis.YP.rotationDegrees(angles[i]));
                var vertices=g.bufferSource().getBuffer(RenderType.entityCutoutNoCull(StarfallBlueprint.id("textures/entity/best_engineering_mech.png")));
                model.renderToBuffer(g.pose(),vertices,15728880,OverlayTexture.NO_OVERLAY,-1);
                g.flush();g.pose().popPose();Lighting.setupForFlatItems();RenderSystem.disableDepthTest();
            }
            g.drawString(font,"固定工程钻臂 · 开放驾驶舱 · 当前原型静止姿态",16,height-48,0xffc4d8d4,false);
            g.drawString(font,"收纳物图标（真实物品资源）",16,height-28,0xffa9c5c6,false);
            g.renderItem(CatalogueContent.stack("best_mech",1),190,height-35);
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
