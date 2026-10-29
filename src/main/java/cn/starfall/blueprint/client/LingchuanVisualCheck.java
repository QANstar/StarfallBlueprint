package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import java.util.Arrays;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Explicit isolated client QA: actual registered armour model, canvas interaction and component UI. */
public final class LingchuanVisualCheck {
    private static int phase,ticks;
    private static LingchuanScreen research;
    private static final String[] SHOTS={"","blackdog-3d-armor.png","research-canvas-detail.png","research-canvas-overview.png","research-canvas-pan-zoom.png","blackdog-components.png"};
    public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        if(phase==0){if(!(mc.screen instanceof TitleScreen))return;mc.setScreen(new ModelGallery());phase=1;ticks=0;return;}
        if(++ticks==35)Screenshot.grab(mc.gameDirectory,SHOTS[phase],mc.getMainRenderTarget(),m->{});
        if(ticks!=50)return;
        if(phase==5){mc.stop();return;}
        if(phase==1){
            var data=new SimpleContainerData(6);data.set(0,4);data.set(1,500000&65535);data.set(2,500000>>>16);data.set(3,3);
            var inv=new Inventory(null);research=new LingchuanScreen(new LingchuanMenu(0,inv,new SimpleContainer(27),data),inv,Component.translatable("block.starfall_blueprint.lc_research_station"));
            mc.setScreen(new Preview(research));research.researchCanvas().focus(4);
        }else if(phase==2)research.researchCanvas().overview();
        else if(phase==3){
            double sx=research.getGuiLeft()+160,sy=research.getGuiTop()+140;var canvas=research.researchCanvas();double wx=canvas.worldX(sx),wy=canvas.worldY(sy);
            research.mouseScrolled(sx,sy,0,3);
            if(Math.abs(canvas.worldX(sx)-wx)>1e-6||Math.abs(canvas.worldY(sy)-wy)>1e-6)throw new IllegalStateException("Research zoom lost cursor anchor");
            research.mouseClicked(sx,sy,0);research.mouseDragged(sx+54,sy+22,0,54,22);research.mouseReleased(sx+54,sy+22,0);
            if(Math.abs(canvas.worldX(sx)-(wx-54/canvas.zoom()))>1e-6)throw new IllegalStateException("Research drag did not update the viewport");
            canvas.focus(12);System.out.println("PASS: research wheel zoom anchor, mouse drag and node focus");
        }else if(phase==4){
            var data=new SimpleContainerData(6);data.set(0,2);data.set(1,500000&65535);data.set(2,500000>>>16);data.set(3,65535);data.set(5,1);
            var stock=new SimpleContainer(27);var inv=new Inventory(null);var watch=CatalogueContent.stack("blackdog_watch",1);var d=reviewData(3,3);d.putInt("Energy",123456);CatalogueEquipment.setData(watch,d);stock.setItem(8,watch);
            stock.setItem(10,CatalogueContent.stack("blackdog_component_flight",1));stock.setItem(11,CatalogueContent.stack("blackdog_component_shield",1));
            mc.setScreen(new Preview(new LingchuanScreen(new LingchuanMenu(0,inv,stock,data),inv,Component.translatable("block.starfall_blueprint.lc_armor_assembler"))));
        }
        phase++;ticks=0;
    }
    private static CompoundTag reviewData(int tier,int mask){var d=new CompoundTag();int[] tiers=new int[8];Arrays.fill(tiers,tier);d.putIntArray("ChassisTiers",tiers);d.putInt("Abilities",mask);d.putInt("CoreTier",tier);return d;}
    private static final class Preview extends Screen {
        private final LingchuanScreen screen;
        Preview(LingchuanScreen screen){super(Component.empty());this.screen=screen;}
        @Override protected void init(){screen.init(minecraft,width,height);}
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xff101219);screen.render(g,mx,my,partial);}
    }
    private static final class ModelGallery extends Screen {
        private BlackDogArmorModel model;
        ModelGallery(){super(Component.literal("BLACK DOG / MODULAR EXOSUIT"));}
        @Override protected void init(){
            var root=minecraft.getEntityModels().bakeLayer(BlackDogArmorModel.LAYER);
            var original=new net.minecraft.client.model.HumanoidModel<net.minecraft.world.entity.LivingEntity>(root);
            var projection=CatalogueContent.stack("blackdog_armor_chest",1);CatalogueEquipment.setData(projection,reviewData(3,4095));
            var registered=net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(projection).getHumanoidArmorModel(null,projection,EquipmentSlot.CHEST,original);
            if(!(registered instanceof BlackDogArmorModel actual))throw new IllegalStateException("Custom armour model extension was not registered");model=actual;
        }
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xff11151e);g.fill(0,0,width,34,0xff252332);g.fill(0,33,width,34,0xffa2485c);g.drawString(font,title,15,12,0xffefc9d2,false);
            int cell=width/3;
            for(int i=0;i<3;i++){
                int x=i*cell;g.fill(x+8,46,x+cell-8,height-18,0xff1c2230);g.drawString(font,Component.literal(i==0?"I / 基础基底":i==1?"III / 组件挂载 · 正面":"III / 推进器 · 背面"),x+17,58,0xffd4adb9,false);
                float scale=Math.min(cell*.65f,(height-100)/2.4f);g.flush();com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
                com.mojang.blaze3d.systems.RenderSystem.setShaderLights(new org.joml.Vector3f(.4f,-.7f,-.6f).normalize(),new org.joml.Vector3f(-.5f,-.4f,.7f).normalize());
                g.pose().pushPose();g.pose().translate(x+cell/2f,height-42,200);g.pose().scale(scale,scale,scale);g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(i==2?22:155));g.pose().translate(0,-1.5,0);
                model.preview(reviewData(i==0?1:3,i==0?0:4095));
                var consumer=g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(StarfallBlueprint.id("textures/models/armor/blackdog_exosuit.png")));
                model.renderToBuffer(g.pose(),consumer,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);g.flush();g.pose().popPose();com.mojang.blaze3d.platform.Lighting.setupForFlatItems();com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
            }
        }
    }
    private LingchuanVisualCheck(){}
}
