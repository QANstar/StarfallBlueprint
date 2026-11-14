package cn.starfall.blueprint.client;
import cn.starfall.blueprint.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in real-client asset/menu render fixtures. Does not claim in-world flight verification. */
@EventBusSubscriber(modid=StarfallBlueprint.ID,value=Dist.CLIENT)
public final class HaijiaVisualCheck {
    private static int phase,ticks;
    private static final String[] FILES={"haijia-models.png","haijia-fighter-views.png","haijia-assembly.png","haijia-console.png","haijia-facilities.png"};
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!Boolean.getBoolean("starfall.haijiaVisualCheck"))return;var m=Minecraft.getInstance();if(m.getOverlay()!=null)return;
        if(!m.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("haijia-review"))throw new IllegalStateException("Haijia QA requires isolated haijia-review directory");
        if(phase==0){if(!(m.screen instanceof TitleScreen))return;m.options.guiScale().set(2);m.resizeDisplay();m.setScreen(new ModelPreview());phase=1;ticks=0;}
        else if(++ticks==30)Screenshot.grab(m.gameDirectory,FILES[phase-1],m.getMainRenderTarget(),c->org.slf4j.LoggerFactory.getLogger(HaijiaVisualCheck.class).info("HAIJIA screenshot {}",c.getString()));
        else if(ticks==50){if(phase==5){m.stop();return;}phase++;ticks=0;if(phase==5){m.setScreen(new FacilitiesPreview());return;}if(phase==2){m.setScreen(new ViewsPreview());return;}var inv=new Inventory(null);var menu=new OrbitalMenu(0,inv);menu.info[0]=phase==3?1:3;for(int i=1;i<=4;i++)menu.info[i]=i;for(int i=5;i<=8;i++)menu.info[i]=1;menu.info[9]=12000;menu.info[16]=20000;menu.info[18]=2;menu.info[19]=3;menu.info[20]=4;menu.info[21]=12;menu.info[10]=16;menu.info[14]=900;m.setScreen(new MenuPreview(new OrbitalScreen(menu,inv,Haijia.text("station"))));}
    }
    private static final class ModelPreview extends Screen {
        ModelPreview(){super(Component.literal("海岬联邦 · 轨道与航空"));}
        @Override public void renderBackground(GuiGraphics g,int x,int y,float p){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xff10223b);g.fill(0,0,width,4,0xfff4a14e);g.drawString(font,title,20,18,0xffdbeeff);g.drawString(font,"真实模型与资源渲染样例 · 无世界静态展示",20,36,0xff88bcdc);
            drawModel(g,width*.52f,height*.46f,Math.min(width/7.2f,height/4.0f),-24,145,false);
            String[] keys={"relay","assembler","launchpad","console","sat_charge","sat_laser","sat_kinetic","sat_weather","terminal"};
            for(int i=0;i<keys.length;i++){int x=20+i*(width-40)/keys.length;g.pose().pushPose();g.pose().translate(x,height-100,100);g.pose().scale(2,2,2);g.renderItem(Haijia.stack("hj_"+keys[i],1),0,0);g.pose().popPose();}
            g.drawString(font,"HF-01 海隼：后掠翼 / 双引擎 / 双激光炮 / 翼下导弹 / 可收放起落架",20,height-42,0xffdbeeff);
        }
    }
    private static void drawModel(GuiGraphics g,float x,float y,float scale,float pitch,float yaw,boolean gear){
        g.flush();RenderSystem.enableDepthTest();com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        g.pose().pushPose();g.pose().translate(x,y,180);g.pose().scale(scale,scale,scale);g.pose().mulPose(Axis.XP.rotationDegrees(pitch));g.pose().mulPose(Axis.YP.rotationDegrees(yaw));
        PeregrineModel.render(g.pose(),g.bufferSource().getBuffer(RenderType.entityCutoutNoCull(PeregrineRenderer.TEXTURE)),15728880,gear);
        g.flush();g.pose().popPose();com.mojang.blaze3d.platform.Lighting.setupForFlatItems();RenderSystem.disableDepthTest();
    }
    private static final class ViewsPreview extends Screen {
        ViewsPreview(){super(Component.literal("HF-01 海隼 · 轮廓检查"));}
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float p){}
        @Override public void render(GuiGraphics g,int mx,int my,float p){
            g.fill(0,0,width,height,0xff10223b);g.fill(0,0,width,4,0xfff4a14e);g.drawString(font,title,20,18,0xffdbeeff);
            float cell=(width-40)/3f;String[] labels={"俯视 · 后掠翼与翼身衔接","侧视 · 座舱与收放起落架","后视 · 双垂尾与双喷口"};
            for(int i=0;i<3;i++){float x=20+cell*i;g.fill((int)x+3,62,(int)(x+cell)-3,height-54,0xff142a43);g.drawCenteredString(font,labels[i],(int)(x+cell/2),76,0xffa9cce7);
                float scale=Math.min((cell-20)/(i==1?5.8f:4.6f),(height-180)/5.8f);
                drawModel(g,x+cell/2,height*.52f,scale,i==0?-90:i==1?0:-10,i==1?90:0,i==1);
            }
            g.drawString(font,"同一套实际模型 · 俯视与后视收起起落架，侧视展开起落架",20,height-30,0xffa9cce7);
        }
    }
    private static final class FacilitiesPreview extends Screen {
        FacilitiesPreview(){super(Component.literal("海岬设施 · 实际多方块模型"));}
        @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xff10223b);g.drawString(font,title,16,15,0xffdceeff);String[] names={"回声 2×2×3","织星 3×3×3","天阶 5×5×4","环港 3×2×2"};
            float cell=(width-32)/4f;
            for(int i=0;i<4;i++){String key=OrbitalStructure.KEYS.get(i);var d=OrbitalStructure.of(key);float cx=16+cell*(i+.5f);g.drawCenteredString(font,names[i],(int)cx,70,0xffa7d2e8);g.flush();RenderSystem.enableDepthTest();com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
                g.pose().pushPose();g.pose().translate(cx,height*.62,160);float scale=(cell-16)/(d.width+d.depth);g.pose().scale(scale,-scale,scale);g.pose().mulPose(Axis.XP.rotationDegrees(20));g.pose().mulPose(Axis.YP.rotationDegrees(215));g.pose().translate(-d.width*.5,0,-d.depth*.5);
                for(int part=0;part<d.parts();part++){var pos=d.offset(net.minecraft.core.Direction.NORTH,part);g.pose().pushPose();g.pose().translate(pos.getX(),pos.getY(),pos.getZ());var state=Haijia.BLOCKS.get(key).get().defaultBlockState().setValue(OrbitalBlock.PART,part);minecraft.getBlockRenderer().renderSingleBlock(state,g.pose(),g.bufferSource(),15728880,OverlayTexture.NO_OVERLAY);g.pose().popPose();}
                g.flush();g.pose().popPose();com.mojang.blaze3d.platform.Lighting.setupForFlatItems();RenderSystem.disableDepthTest();
            }
            g.drawString(font,"实际占地与模型碰撞一致 · 任意外壳交互 · 统一控制器与能源",16,height-35,0xffa7d2e8);
        }
    }
    private static final class MenuPreview extends Screen {
        private final OrbitalScreen preview;
        MenuPreview(OrbitalScreen p){super(Haijia.text("station"));preview=p;}
        @Override protected void init(){preview.init(minecraft,width,height);}
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xff10223b);preview.render(g,-100,-100,partial);g.drawString(font,"海岬工位界面 · 静态数据渲染检查",16,16,0xffdceeff);}
    }
}
