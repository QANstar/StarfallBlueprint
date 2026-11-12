package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Local billboard geometry: never modifies entity glow, teams or shared entity data. */
@EventBusSubscriber(modid=StarfallBlueprint.ID,value=Dist.CLIENT)
public final class BlackDogLockMarker {
    private static final RenderType TYPE=RenderType.debugStructureQuads();
    private static BlackDogNetworking.TargetLock state=BlackDogNetworking.TargetLock.empty();
    private static ClientLevel level;
    private static java.util.UUID viewer;
    private static long received;
    public static void receive(BlackDogNetworking.TargetLock update){
        var mc=Minecraft.getInstance();state=update;level=mc.level;viewer=mc.player==null?null:mc.player.getUUID();received=level==null?0:level.getGameTime();
    }
    public static boolean active(){
        var mc=Minecraft.getInstance();
        return mc.level!=null&&mc.level==level&&mc.player!=null&&mc.player.getUUID().equals(viewer)&&mc.player.isAlive()&&!mc.player.isSpectator()
            &&state.entityId()>=0&&mc.level.getGameTime()-received<=10&&mc.player.isUsingItem()
            &&mc.player.getUseItem().getItem() instanceof BlackDogArmor.FireControl&&BlackDogArmor.selectedSystem(BlackDogArmor.active(mc.player)).equals("swarm");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.level!=level||mc.player==null||mc.level==null||mc.level.getGameTime()-received>10){state=BlackDogNetworking.TargetLock.empty();level=null;viewer=null;}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||!active())return;
        var mc=Minecraft.getInstance();if(mc.options.hideGui)return;
        var entity=mc.level.getEntity(state.entityId());
        if(!(entity instanceof LivingEntity target)||!target.isAlive()||!target.getUUID().equals(state.target())||!mc.player.hasLineOfSight(target))return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var point=target.getPosition(partial).subtract(event.getCamera().getPosition());
        float width=(float)Math.max(target.getBbWidth()+.35,point.length()*.023),height=(float)Math.max(target.getBbHeight()+.35,point.length()*.03);
        float x=width*.5f,y=height*.5f,stroke=Math.max(.018f,width*.018f),arm=Math.min(width,height)*.25f;
        int ticks=Math.clamp(state.ticks(),0,BlackDogMissileLock.LOCK_TICKS),color=ticks==BlackDogMissileLock.LOCK_TICKS?0x85e9b6:0xffbb65;
        // Lift the lower brackets above the feet so terrain cannot bury them.
        var pose=event.getPoseStack();pose.pushPose();pose.translate(point.x,point.y+y+stroke*6+.08,point.z);pose.mulPose(event.getCamera().rotation());
        var buffers=mc.renderBuffers().bufferSource();var buffer=buffers.getBuffer(TYPE);
        for(int sx:new int[]{-1,1})for(int sy:new int[]{-1,1}){
            float left=sx<0?-x:x-arm,bottom=sy<0?-y:y-stroke;
            quad(buffer,pose,left,bottom,left+arm,bottom+stroke,color,1);
            left=sx<0?-x:x-stroke;bottom=sy<0?-y:y-arm;
            quad(buffer,pose,left,bottom,left+stroke,bottom+arm,color,1);
        }
        float barY=y+stroke*3;
        float filled=-x+width*ticks/BlackDogMissileLock.LOCK_TICKS;
        quad(buffer,pose,filled,barY,x,barY+stroke*2,0x182332,.8f);
        quad(buffer,pose,-x,barY,filled,barY+stroke*2,color,1);
        pose.popPose();buffers.endBatch(TYPE);
    }
    private static void quad(VertexConsumer out,PoseStack pose,float x0,float y0,float x1,float y1,int color,float alpha){
        var matrix=pose.last().pose();int a=(int)(alpha*255),r=color>>16&255,g=color>>8&255,b=color&255;
        out.addVertex(matrix,x0,y0,0).setColor(r,g,b,a);out.addVertex(matrix,x1,y0,0).setColor(r,g,b,a);
        out.addVertex(matrix,x1,y1,0).setColor(r,g,b,a);out.addVertex(matrix,x0,y1,0).setColor(r,g,b,a);
    }
    private BlackDogLockMarker(){}
}
