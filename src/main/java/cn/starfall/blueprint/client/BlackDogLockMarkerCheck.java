package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import java.nio.file.Files;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in packet/render smoke test in the existing isolated review save only. */
@EventBusSubscriber(modid=StarfallBlueprint.ID,value=Dist.CLIENT)
public final class BlackDogLockMarkerCheck {
    private static int phase,ticks,total;
    private static volatile boolean ready;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(BlackDogLockMarkerCheck.class);
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);LOG.info("LOCK MARKER QA PASS: {}",message);}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->LOG.info("LOCK MARKER screenshot {}",c.getString()));}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("starfall.blackdogLockMarkerCheck"))return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("blackdog-review"))throw new IllegalStateException("Marker QA requires isolated blackdog-review directory");
        try{
            if(++total>600)throw new IllegalStateException("Marker QA timed out in phase "+phase);
            if(phase==0){
                mc.options.pauseOnLostFocus=false;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.hideGui=false;mc.setScreen(null);
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();server.execute(()->{
                    var p=server.getPlayerList().getPlayer(id);var previous=BlackDogArmor.active(p);if(!previous.isEmpty())BlackDogArmor.recall(p,previous);
                    p.getInventory().clearContent();p.setGameMode(GameType.CREATIVE);
                    for(int x=-8;x<=8;x++)for(int z=-8;z<=16;z++)p.level().setBlock(new BlockPos(x,125,z),Blocks.SMOOTH_QUARTZ.defaultBlockState(),3);
                    for(var e:p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new net.minecraft.world.phys.AABB(-8,126,-8,8,132,16),e->e!=p))e.discard();
                    for(var e:p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(-8,125,-8,8,140,16)))e.discard();
                    p.teleportTo(0,126,0);p.setYRot(0);p.setXRot(4);p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    var watch=new net.minecraft.world.item.ItemStack(BlackDogArmor.ARMED_WATCH.get());((BlackDogArmor.ArmedWatch)watch.getItem()).prepare(watch);
                    p.getInventory().setItem(1,watch);p.getInventory().setItem(0,CatalogueContent.stack("blackdog_fire_control",1));p.getInventory().selected=0;BlackDogArmor.toggle(p,watch);BlackDogArmor.selectSystem(p,BlackDogArmor.SYSTEMS.indexOf("swarm"));
                    var cow=EntityType.COW.create(p.level());cow.setNoAi(true);cow.setInvulnerable(true);cow.setPos(0,126,9);p.level().addFreshEntity(cow);
                    p.serverLevel().setDayTime(6000);p.serverLevel().setWeatherParameters(6000,0,false,false);p.inventoryMenu.broadcastChanges();ready=true;
                });phase=1;ticks=0;return;
            }
            if(!ready)return;ticks++;
            mc.player.setYRot(0);mc.player.setXRot(4);
            if(phase==1&&ticks>40){mc.options.keyUse.setDown(true);phase=2;ticks=0;}
            else if(phase==2&&ticks>=6&&BlackDogLockMarker.active()){
                shot("blackdog-target-locking.png");phase=3;ticks=0;
            }else if(phase==3&&ticks==25){
                check(BlackDogLockMarker.active(),"real server packet keeps the local marker active while aiming");
                check(mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class,mc.player.getBoundingBox().inflate(16)).stream().noneMatch(e->e.isCurrentlyGlowing()),"target has no shared glowing flag");
                shot("blackdog-target-locked.png");mc.options.keyUse.setDown(false);phase=4;ticks=0;
            }else if(phase==4&&ticks==15){
                check(!BlackDogLockMarker.active(),"releasing right-click clears the client marker");shot("blackdog-target-released.png");
                Files.writeString(mc.gameDirectory.toPath().resolve("blackdog-lock-marker-result.txt"),"PASS: real server-to-owner packet, local marker rendering, no shared glow, release clears marker; locking/locked/released screenshots captured.");
                mc.stop();phase=5;
            }
        }catch(Exception error){
            LOG.error("LOCK MARKER QA failed",error);try{Files.writeString(mc.gameDirectory.toPath().resolve("blackdog-lock-marker-result.txt"),"FAIL: "+error);}catch(Exception ignored){}
            mc.options.keyUse.setDown(false);mc.stop();
        }
    }
    private BlackDogLockMarkerCheck(){}
}
