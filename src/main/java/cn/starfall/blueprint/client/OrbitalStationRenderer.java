package cn.starfall.blueprint.client;
import cn.starfall.blueprint.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.phys.Vec3;

/** Held-terminal service overlay, with persistent topology synchronized by the server. */
public final class OrbitalStationRenderer implements BlockEntityRenderer<OrbitalBlockEntity> {
    public OrbitalStationRenderer(BlockEntityRendererProvider.Context c){}
    @Override public boolean shouldRenderOffScreen(OrbitalBlockEntity b){return true;}
    @Override public int getViewDistance(){return 96;}
    @Override public void render(OrbitalBlockEntity b,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var m=Minecraft.getInstance();if(b.getBlockState().getValue(OrbitalBlock.PART)!=0||b.kind()!=0||m.player==null||!(OrbitalTerminalItem.usable(m.player.getMainHandItem(),m.player)||OrbitalTerminalItem.usable(m.player.getOffhandItem(),m.player)))return;
        var v=buffers.getBuffer(RenderType.lightning());var origin=new Vec3(.5,2.7,.5);int color=b.energy.getEnergyStored()>0?0x82dfff:0xf5a04b;
        BlackDogBeamRenderer.ring(v,pose,origin,new Vec3(1,0,0),new Vec3(0,0,1),8,.012,color,.35f);
        for(var pos:b.links)if(b.getLevel().hasChunkAt(pos)){var end=Vec3.atCenterOf(pos.subtract(b.getBlockPos())).add(0,2.2,0);BlackDogBeamRenderer.tube(v,pose,origin,end,.016,color,.65f,4);
            double progress=(b.getLevel().getGameTime()+partial)%40/40;var point=origin.lerp(end,progress);BlackDogBeamRenderer.tube(v,pose,point,point.add(end.subtract(origin).normalize().scale(.5)),.055,0xffffff,.9f,6);}
    }
}
