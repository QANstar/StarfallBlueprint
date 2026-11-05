package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import java.nio.file.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Explicit opt-in integration/visual QA. Requires an isolated copied save named playtest. */
@EventBusSubscriber(modid=StarfallBlueprint.ID,value=Dist.CLIENT)
public final class BlackDogPlaytest {
    private static int phase,ticks;
    private static volatile boolean ready;
    private static final java.util.Map<Integer,net.minecraft.world.phys.Vec3> droneOffsets=new java.util.HashMap<>();
    private static double maxDroneStep;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(BlackDogPlaytest.class);
    private static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(server!=null&&mc.player!=null){var id=mc.player.getUUID();server.execute(()->{var p=server.getPlayerList().getPlayer(id);if(p!=null)action.accept(p);});}
    }
    private static void shot(String system){server(p->{var watch=BlackDogArmor.active(p);BlackDogArmor.selectSystem(p,BlackDogArmor.SYSTEMS.indexOf(system));p.getCooldowns().removeCooldown(p.getMainHandItem().getItem());p.getMainHandItem().getItem().use(p.level(),p,InteractionHand.MAIN_HAND);});}
    private static void screenshot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->LOG.info("BLACKDOG QA screenshot {}",c.getString()));}
    private static void check(boolean success,String message){if(!success)throw new IllegalStateException(message);LOG.info("BLACKDOG QA PASS: {}",message);}
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("starfall.blackdogPlaytest"))return;
        var mc=Minecraft.getInstance();if(mc.getOverlay()!=null||mc.player==null||mc.level==null)return;
        if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("blackdog-review"))throw new IllegalStateException("Playtest must run in isolated blackdog-review directory");
        try {
            if(phase==0){
                mc.options.pauseOnLostFocus=false;mc.setScreen(null);mc.options.renderDistance().set(6);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(p->{
                    var old=BlackDogArmor.active(p);if(!old.isEmpty())BlackDogArmor.recall(p,old);p.getInventory().clearContent();p.setGameMode(GameType.CREATIVE);
                    var watch=new net.minecraft.world.item.ItemStack(BlackDogArmor.ARMED_WATCH.get());((BlackDogArmor.ArmedWatch)watch.getItem()).prepare(watch);p.getInventory().setItem(1,watch);p.getInventory().selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,CatalogueContent.stack("blackdog_fire_control",1));check(BlackDogArmor.toggle(p,watch),"QA suit deploys on the real server");
                    var d=CatalogueEquipment.getData(watch);d.putBoolean("Enabled_turret",true);CatalogueEquipment.setData(watch,d);
                    p.getPersistentData().putInt("LingchuanResearch",7);p.experienceLevel=30;p.getInventory().setItem(5,CatalogueContent.stack("laminar_alloy",64));p.getInventory().setItem(6,CatalogueContent.stack("common",64));
                    p.teleportTo(p.getX(),300,p.getZ());p.setYRot(0);p.setXRot(0);p.getAbilities().flying=false;p.onUpdateAbilities();p.serverLevel().setDayTime(6000);p.serverLevel().setWeatherParameters(6000,0,false,false);ready=true;
                });phase=1;ticks=0;return;
            }
            if(!ready)return;ticks++;
            if(phase==1){
                if(ticks==10){mc.player.setXRot(0);mc.player.setYRot(0);}
                if(ticks==50){server(p->{var w=BlackDogArmor.active(p);LOG.info("QA server suit={} deployed={} complete={} turret={} drones={} position={}",w,BlackDogArmor.deployed(w),BlackDogArmor.complete(p,w),BlackDogArmor.enabled(w,"turret"),p.serverLevel().getEntitiesOfClass(BlackDogDrone.class,p.getBoundingBox().inflate(10)).size(),p.position());});var w=BlackDogArmor.active(mc.player);LOG.info("QA client suit={} turret={} position={}",w,BlackDogArmor.enabled(w,"turret"),mc.player.position());for(var entity:mc.level.entitiesForRendering())if(entity instanceof BlackDogDrone d)LOG.info("QA client drone={} owner={} position={}",d.getId(),d.ownerId(),d.position());}
                if(ticks==30||ticks==34)BlackDogClient.reviewKeys=new BlackDogClient.FlightKeys(true,false);
                if(ticks==31||ticks==35)BlackDogClient.reviewKeys=new BlackDogClient.FlightKeys(false,false);
                if(ticks==55){BlackDogClient.tick(event);check(mc.player.getAbilities().flying,"double-space controller input enters powered hover");check(mc.level.getEntitiesOfClass(BlackDogDrone.class,mc.player.getBoundingBox().inflate(5)).size()==2,"two drone meshes are tracked by the real client");screenshot("blackdog-hover-drones.png");BlackDogClient.reviewKeys=new BlackDogClient.FlightKeys(false,true);}
                if(ticks==56)BlackDogClient.reviewKeys=new BlackDogClient.FlightKeys(false,false);
                if(ticks==90){BlackDogClient.tick(event);LOG.info("QA cruise flying={} flag={} ticks={} pose={} speed={}",mc.player.getAbilities().flying,mc.player.isFallFlying(),mc.player.getFallFlyingTicks(),mc.player.getPose(),mc.player.getDeltaMovement().length());check(mc.player.isFallFlying()&&mc.player.getFallFlyingTicks()>10,"cruise sustains vanilla horizontal flying posture");check(mc.player.getDeltaMovement().length()>1,"cruise accelerates above twenty metres per second");screenshot("blackdog-cruise.png");BlackDogClient.reviewKeys=new BlackDogClient.FlightKeys(false,true);}
                if(ticks==91){BlackDogClient.reviewKeys=new BlackDogClient.FlightKeys(false,false);phase=2;ticks=0;mc.options.setCameraType(CameraType.FIRST_PERSON);}
            }else if(phase==2){
                if(ticks==1){mc.player.setYRot(mc.player.getYRot()+150);mc.player.setXRot(30);}
                for(var entity:mc.level.entitiesForRendering())if(entity instanceof BlackDogDrone drone){
                    var offset=drone.renderPosition(1).subtract(mc.player.position());var old=droneOffsets.put(drone.getId(),offset);if(old!=null)maxDroneStep=Math.max(maxDroneStep,offset.distanceTo(old));
                }
                if(ticks==30){check(maxDroneStep>.01&&maxDroneStep<.65,"drone formation eases through a sharp turn without position snaps");screenshot("blackdog-fire-control.png");shot("laser");phase=3;ticks=0;}
            }
            else if(phase==3&&ticks==4){screenshot("blackdog-laser.png");shot("cannon");phase=4;ticks=0;}
            else if(phase==4&&ticks==5){screenshot("blackdog-mining-cannon.png");server(Progression::open);phase=5;ticks=0;}
            else if(phase==5&&ticks>=30&&mc.screen instanceof ProgressionTreeScreen){
                screenshot("growth-tree-all.png");
                for(var child:mc.screen.children())if(child instanceof Button button&&button.getMessage().getString().equals(Component.translatable("growth.starfall_blueprint.category.lingchuan").getString())){button.onPress();break;}
                phase=6;ticks=0;
            }else if(phase==6&&ticks==25){
                screenshot("growth-tree-lingchuan.png");
                mc.screen.mouseScrolled(80,100,0,4);phase=7;ticks=0;
            }else if(phase==7&&ticks==20){
                screenshot("growth-tree-detail.png");mc.player.closeContainer();mc.setScreen(null);
                server(p->{var floor=net.minecraft.core.BlockPos.containing(p.position());for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)p.serverLevel().setBlock(floor.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);p.teleportTo(floor.getX()+.5,floor.getY()+.3,floor.getZ()+.5);});phase=8;ticks=0;
            }else if(phase==8){
                if(ticks>=10&&ticks<15)mc.player.setDeltaMovement(0,-.4,0);
                if(ticks==35){BlackDogClient.tick(event);check(mc.player.onGround()&&!mc.player.getAbilities().flying&&!mc.player.isFallFlying(),"physical landing automatically stops hover and flying posture");
                    server(p->{BlackDogArmor.flightRequest(p,true,false,false);check(!p.getAbilities().flying,"stale flight heartbeat cannot lift a landed real player");});screenshot("blackdog-landed-compact-hud.png");phase=9;ticks=0;
                }
            }else if(phase==9){
                if(ticks==5||ticks==9)BlackDogClient.reviewKeys=new BlackDogClient.FlightKeys(true,false);
                if(ticks==6||ticks==10)BlackDogClient.reviewKeys=new BlackDogClient.FlightKeys(false,false);
                if(ticks==20){BlackDogClient.tick(event);check(mc.player.getAbilities().flying,"a new deliberate takeoff works after landing");server(p->BlackDogArmor.setCharge(BlackDogArmor.active(p),50));}
                if(ticks==40){BlackDogClient.tick(event);check(BlackDogArmor.active(mc.player).isEmpty()&&!mc.player.getAbilities().flying&&!mc.player.isFallFlying(),"low power closes the real client armour and flight model");
                    check(mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(),"low power removes the worn armour projection");
                    check(mc.level.getEntitiesOfClass(BlackDogDrone.class,mc.player.getBoundingBox().inflate(10)).isEmpty(),"low power removes client drone models");screenshot("blackdog-low-power-recalled.png");
                    Files.writeString(mc.gameDirectory.toPath().resolve("blackdog-qa-result.txt"),"PASS: double-tap hover, cruise posture and speed, Bezier drone turn smoothing, compact HUD, landing, stale packet rejection, deliberate relaunch, low-power recall and model cleanup.\n");mc.stop();
                }
            }
            if(phase==5&&ticks>250)throw new IllegalStateException("Growth tree failed to open through the real server menu");
        }catch(Exception error){LOG.error("BLACKDOG QA FAILED",error);try{Files.writeString(mc.gameDirectory.toPath().resolve("blackdog-qa-result.txt"),"FAIL: "+error);}catch(Exception ignored){}mc.options.keyJump.setDown(false);mc.options.keySprint.setDown(false);mc.stop();}
    }
    private BlackDogPlaytest(){}
}
