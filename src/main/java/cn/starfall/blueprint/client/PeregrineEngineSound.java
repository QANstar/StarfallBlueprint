package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** One loop per occupied powered aircraft, smoothly mixed from idle to afterburner. */
final class PeregrineEngineSound extends AbstractTickableSoundInstance {
    private final PeregrineEntity plane;
    static int checkedFrames;
    PeregrineEngineSound(PeregrineEntity plane){
        super(HaijiaSounds.ENGINE.get(),SoundSource.PLAYERS,RandomSource.create());this.plane=plane;
        looping=true;delay=0;volume=.02f;pitch=.65f;updatePosition();
    }
    private void updatePosition(){x=plane.getX();y=plane.getY()+.5;z=plane.getZ();}
    @Override public void tick(){
        var mc=Minecraft.getInstance();
        if(plane.isRemoved()||mc.level!=plane.level()||plane.getControllingPassenger()==null||plane.energy()<=0){stop();return;}
        updatePosition();float speed=(float)Math.min(1,plane.getDeltaMovement().length()/3.3);
        float targetVolume=.10f+plane.throttle()*.25f+speed*.10f+(plane.boosting()?.12f:0);
        float targetPitch=.55f+plane.throttle()*.45f+speed*.20f+(plane.boosting()?.15f:0);
        volume=Mth.lerp(.1f,volume,targetVolume);pitch=Mth.lerp(.08f,pitch,targetPitch);
        if(Boolean.getBoolean("starfall.haijiaFlightCheck"))checkedFrames++;
    }
}
