package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import cn.starfall.blueprint.EsperNetworking.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in actual-client mesh fixture. Not a substitute for an in-world playtest. */
@EventBusSubscriber(modid=StarfallBlueprint.ID,value=Dist.CLIENT)
public final class EsperVisualCheck {
    private static int ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if(!Boolean.getBoolean("starfall.esperVisualCheck"))return;
        var m=Minecraft.getInstance();if(m.getOverlay()!=null)return;
        if(!m.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("esper-rework-review"))throw new IllegalStateException("Use isolated esper-rework-review directory");
        if(ticks==0) {
            if(!(m.screen instanceof TitleScreen))return;
            m.options.guiScale().set(2);m.resizeDisplay();m.setScreen(new Preview());ticks=1;
        } else if(++ticks==35)Screenshot.grab(m.gameDirectory,"esper-effects.png",m.getMainRenderTarget(),c->
                org.slf4j.LoggerFactory.getLogger(EsperVisualCheck.class).info("ESPER screenshot {}",c.getString()));
        else if(ticks==55)m.stop();
    }
    private static final class Preview extends Screen {
        Preview(){super(Component.literal("白湾自治邦 · 自定义特效渲染检查"));}
        @Override public void renderBackground(GuiGraphics g,int x,int y,float p){}
        @Override public void render(GuiGraphics g,int x,int y,float p) {
            g.fill(0,0,width,height,0xff09131e);
            g.drawString(font,title,18,14,0xffd8f6ff);
            String[] labels={"蓝色闪电 / 分叉与亮芯","崩坏球 / 碎裂边界","空间斩 / 目标局部连续切线","屏障 / 规则格挡双环"};
            for(int i=0;i<4;i++) {
                int col=i%2,row=i/2,cw=(width-42)/2,ch=(height-82)/2;
                int left=16+col*(cw+10),top=38+row*(ch+10);
                g.fill(left,top,left+cw,top+ch,0xff112234);
                g.drawString(font,labels[i],left+10,top+10,0xff8dcfff);
                g.flush();RenderSystem.enableDepthTest();
                g.pose().pushPose();g.pose().translate(left+cw*.5,top+ch*.62,180);
                float scale=Math.min(cw/12F,(ch-25)/5F);g.pose().scale(scale,-scale,scale);
                var out=g.bufferSource().getBuffer(RenderType.debugStructureQuads());
                Pattern pattern=switch(i){case 0->Pattern.LIGHTNING;case 1->Pattern.SINGULARITY;case 2->Pattern.TARGET_SLASH;default->Pattern.GUARD_MELEE;};
                Vec3 center=i==0?new Vec3(-5,-.4,0):new Vec3(0,0,-1);
                Vec3 normal=i==0?new Vec3(1,.12,0).normalize():new Vec3(0,0,1);
                EsperFractureRenderer.preview(new Fracture(pattern,center,normal,i==0?10:i==1?2.3F:2,24,778899),out,g.pose().last().pose(),.31F);
                g.flush();g.pose().popPose();RenderSystem.disableDepthTest();
            }
            g.drawString(font,"实际客户端渲染器 · 固定帧几何检查；实战动态效果仍需游戏内体验",18,height-19,0xff799bb9);
            var skills = java.util.List.of(Esper.Skill.SLASH, Esper.Skill.COLLAPSE, Esper.Skill.VOLLEY, Esper.Skill.BARRIER);
            var slots = skills.stream().map(skill -> new EsperNetworking.Slot(skill.ordinal(), 1,
                    skill == Esper.Skill.COLLAPSE ? 32 : 0)).toList();
            EsperClient.drawHud(g, new EsperNetworking.Hud(true, Esper.School.SPACE.ordinal(), 86, 140, 0, slots), 0, 2);
        }
    }
    private EsperVisualCheck(){}
}
