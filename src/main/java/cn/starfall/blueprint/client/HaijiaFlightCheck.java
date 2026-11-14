package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import java.nio.file.Files;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.glfw.GLFW;

/** Real input-event, packet, runway, weapon and camera check in an isolated copied save only. */
@EventBusSubscriber(modid=StarfallBlueprint.ID,value=Dist.CLIENT)
public final class HaijiaFlightCheck {
    private static int phase,ticks,total,movingFrames;private static volatile boolean ready;
    private static Vec3 previous;private static double maxStep,peakSpeed;private static float beforeYaw,turn;
    private static boolean badMotion;
    private static boolean sawBeam,sawMissile,sawBomb,sawLock,sawBoost,mouseCaptured,scrollCaptured;
    private static final java.util.Set<String> playedSounds=java.util.concurrent.ConcurrentHashMap.newKeySet();
    @SubscribeEvent public static void sound(net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent event){
        if(Boolean.getBoolean("starfall.haijiaFlightCheck")){var id=event.getSound().getLocation();if(id.getNamespace().equals(StarfallBlueprint.ID))playedSounds.add(id.getPath());}
    }
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(HaijiaFlightCheck.class);
    private static void mouse(int button,boolean pressed){var event=new InputEvent.MouseButton.Pre(button,pressed?GLFW.GLFW_PRESS:GLFW.GLFW_RELEASE,0);OrbitalClient.mouse(event);mouseCaptured|=event.isCanceled();}
    private static void scroll(){var m=Minecraft.getInstance();int hotbar=m.player.getInventory().selected;var event=new InputEvent.MouseScrollingEvent(0,-1,false,false,false,0,0);OrbitalClient.scroll(event);scrollCaptured|=event.isCanceled()&&hotbar==m.player.getInventory().selected;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("starfall.haijiaFlightCheck"))return;var m=Minecraft.getInstance();if(m.level==null||m.player==null||m.getOverlay()!=null)return;
        if(!m.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("haijia-flight-review"))throw new IllegalStateException("Flight check requires isolated review directory");
        try{
            if(++total>1200)throw new IllegalStateException("Flight check timed out");
            if(phase==0){m.options.pauseOnLostFocus=false;m.options.setCameraType(CameraType.THIRD_PERSON_BACK);m.setScreen(null);var server=m.getSingleplayerServer();var id=m.player.getUUID();server.execute(()->{
                var p=server.getPlayerList().getPlayer(id);p.stopRiding();p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();
                for(var e:p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class,new net.minecraft.world.phys.AABB(-100,100,-100,100,250,200)))e.discard();
                for(var e:p.serverLevel().getEntitiesOfClass(PeregrineEntity.class,new net.minecraft.world.phys.AABB(-100,100,-100,100,250,200)))e.discard();
                for(int x=-4;x<=4;x++)for(int z=-8;z<=180;z++){p.level().setBlock(new BlockPos(x,125,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);for(int y=126;y<=145;y++){var pos=new BlockPos(x,y,z);if(!p.level().getBlockState(pos).isAir())p.level().setBlock(pos,Blocks.AIR.defaultBlockState(),3);}}
                p.teleportTo(0,126,0);p.setYRot(0);p.setXRot(0);var item=Haijia.stack("hj_creative_fighter",1);PeregrineItem.prepareCreative(item);var f=Haijia.FIGHTER.get().create(p.level());f.unpack(CatalogueEquipment.getData(item));f.setPos(0,126,0);f.setYRot(0);f.setOnGround(true);p.level().addFreshEntity(f);p.startRiding(f);
                var target=EntityType.COW.create(p.level());target.setNoAi(true);target.setNoGravity(true);target.setInvulnerable(true);target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);target.setPos(0,130,70);p.level().addFreshEntity(target);
                p.serverLevel().setDayTime(6000);p.serverLevel().setWeatherParameters(6000,0,false,false);ready=true;
            });phase=1;return;}
            if(!ready||!(m.player.getVehicle() instanceof PeregrineEntity f))return;ticks++;
            m.options.keyUp.setDown(ticks<145);m.options.keyJump.setDown(ticks>=45&&ticks<90);m.options.keyLeft.setDown(ticks>=80&&ticks<110);OrbitalClient.BOOST.setDown(ticks>=100&&ticks<120);
            if(ticks==1)OrbitalClient.RIGHT.setDown(true);if(ticks==30){if(Math.abs(f.roll())>.5)throw new IllegalStateException("Stale E mapping banks aircraft");OrbitalClient.RIGHT.setDown(false);}if(ticks==40)mouse(GLFW.GLFW_MOUSE_BUTTON_LEFT,true);if(ticks==54)mouse(GLFW.GLFW_MOUSE_BUTTON_LEFT,false);
            if(ticks==58)scroll();if(ticks==72)mouse(GLFW.GLFW_MOUSE_BUTTON_LEFT,true);if(ticks==82)mouse(GLFW.GLFW_MOUSE_BUTTON_LEFT,false);
            if(ticks==85){mouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT,false);scroll();}if(ticks==92)mouse(GLFW.GLFW_MOUSE_BUTTON_LEFT,true);if(ticks==96)mouse(GLFW.GLFW_MOUSE_BUTTON_LEFT,false);
            if(ticks==30&&f.getY()>126.15)throw new IllegalStateException("Aircraft left the ground without rotation");if(ticks==80&&f.getY()<127)throw new IllegalStateException("Aircraft never rotated off runway");
            if(ticks>=40&&ticks<=135){if(previous!=null){double distance=f.position().distanceTo(previous);maxStep=Math.max(maxStep,distance);if(distance>Math.max(2.8,f.getDeltaMovement().length()*1.8+.5))badMotion=true;if(distance>.02)movingFrames++;}previous=f.position();}
            peakSpeed=Math.max(peakSpeed,f.getDeltaMovement().horizontalDistance());sawLock|=f.lockProgress()>=10;sawBoost|=f.boosting();
            for(var entity:m.level.entitiesForRendering()){if(entity instanceof BlackDogBeam b&&b.style()==5)sawBeam=true;if(entity instanceof OrbitalStrike s){sawMissile|=s.style()==2;sawBomb|=s.style()==4;}}
            if(ticks==80)beforeYaw=f.getYRot();if(ticks==115)turn=Mth.wrapDegrees(f.getYRot()-beforeYaw);
            if(ticks==50||ticks==75||ticks==118||ticks==145)Screenshot.grab(m.gameDirectory,ticks==50?"haijia-laser-client.png":ticks==75?"haijia-missile-client.png":ticks==118?"haijia-afterburner-client.png":"haijia-flight-client.png",m.getMainRenderTarget(),c->LOG.info("FLIGHT screenshot {}",c.getString()));
            if(ticks==165){
                if(turn> -65)throw new IllegalStateException("Left rudder too weak: "+turn);
                if(badMotion||maxStep>8||movingFrames<70)throw new IllegalStateException("Motion continuity: max="+maxStep+", frames="+movingFrames);
                if(OrbitalClient.hiddenPilotFrames==0||OrbitalClient.maxCheckedCameraDistance<13.5)throw new IllegalStateException("Pilot/camera check failed");
                if(!mouseCaptured||!scrollCaptured||!sawLock||!sawBeam||!sawMissile||!sawBomb||!sawBoost||peakSpeed<1.8)throw new IllegalStateException("Combat input: mouse="+mouseCaptured+", wheel="+scrollCaptured+", lock="+sawLock+", laser="+sawBeam+", missile="+sawMissile+", bomb="+sawBomb+", boost="+sawBoost+", speed="+peakSpeed);
                var expected=java.util.Set.of("hj_engine","hj_boost","hj_laser","hj_missile","hj_bomb_release","hj_weapon_select");
                if(!playedSounds.containsAll(expected)||PeregrineEngineSound.checkedFrames<80)throw new IllegalStateException("Audio channels missing: "+playedSounds+", engine frames="+PeregrineEngineSound.checkedFrames);
                String result="PASS: runway taxi and rotation, real mouse/wheel events and packets, immediate fire-to-target acquisition, wide laser, guided missile, gravity bomb, held afterburner; turn="+turn+", peak horizontal="+peakSpeed+", max client step="+maxStep+", hidden pilot frames="+OrbitalClient.hiddenPilotFrames+", camera distance="+OrbitalClient.maxCheckedCameraDistance+", actual audio channels="+playedSounds+", engine frames="+PeregrineEngineSound.checkedFrames;
                Files.writeString(m.gameDirectory.toPath().resolve("haijia-flight-result.txt"),result);LOG.info(result);release();m.stop();phase=2;
            }
        }catch(Exception error){LOG.error("FLIGHT CHECK failed",error);try{Files.writeString(m.gameDirectory.toPath().resolve("haijia-flight-result.txt"),"FAIL: "+error);}catch(Exception ignored){}release();m.stop();}
    }
    private static void release(){var m=Minecraft.getInstance();m.options.keyUp.setDown(false);m.options.keyLeft.setDown(false);m.options.keyJump.setDown(false);OrbitalClient.BOOST.setDown(false);OrbitalClient.fireHeld=OrbitalClient.lockHeld=false;}
}
