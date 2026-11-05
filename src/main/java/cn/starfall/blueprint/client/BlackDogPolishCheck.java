package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import java.nio.file.Files;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Isolated real creative inventory proxy, physical grip and restrained shield visual regression. */
@EventBusSubscriber(modid=StarfallBlueprint.ID,value=Dist.CLIENT)
public final class BlackDogPolishCheck {
    private static int phase,ticks;
    private static volatile boolean ready;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(BlackDogPolishCheck.class);
    private static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(server!=null&&mc.player!=null){var id=mc.player.getUUID();server.execute(()->{try{var p=server.getPlayerList().getPlayer(id);if(p!=null)action.accept(p);}catch(Exception e){fail(e);}});}}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);LOG.info("POLISH QA PASS: {}",message);}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->LOG.info("POLISH QA screenshot {}",c.getString()));}
    private static void fail(Exception e){LOG.error("POLISH QA failed",e);try{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("blackdog-polish-result.txt"),"FAIL: "+e);}catch(Exception ignored){}Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("starfall.blackdogPolishCheck"))return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("blackdog-review"))throw new IllegalStateException("Polish QA requires isolated blackdog-review directory");
        try{
            if(phase==0){mc.options.pauseOnLostFocus=false;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setScreen(null);server(p->{
                var previous=BlackDogArmor.active(p);if(!previous.isEmpty())BlackDogArmor.recall(p,previous);p.getInventory().clearContent();p.setGameMode(GameType.CREATIVE);
                for(int x=-8;x<12;x++)for(int z=-8;z<14;z++)p.level().setBlock(new BlockPos(x,125,z),Blocks.SMOOTH_QUARTZ.defaultBlockState(),3);
                p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);p.teleportTo(2,126,-3);p.setYRot(0);p.setXRot(0);p.getAbilities().flying=false;p.onUpdateAbilities();
                var watch=new ItemStack(BlackDogArmor.ARMED_WATCH.get());((BlackDogArmor.ArmedWatch)watch.getItem()).prepare(watch);p.getInventory().setItem(1,watch);p.getInventory().setItem(0,CatalogueContent.stack("blackdog_fire_control",1));p.getInventory().selected=0;
                p.getInventory().setItem(5,new ItemStack(Items.APPLE,7));p.getInventory().setItem(6,CatalogueContent.stack("blackdog_watch",1));p.getInventory().setItem(7,new ItemStack(Items.DIRT,11));
                check(BlackDogArmor.toggle(p,watch),"fresh suit deploys with unrelated items in aliased inventory indices");p.serverLevel().setDayTime(6000);p.serverLevel().setWeatherParameters(6000,0,false,false);ready=true;
            });phase=1;ticks=0;return;}
            if(!ready)return;ticks++;
            if(phase==1&&ticks==45){mc.player.setYRot(0);mc.player.setXRot(0);var screen=new CreativeModeInventoryScreen(mc.player,mc.player.connection.enabledFeatures(),false);mc.setScreen(screen);int x=(mc.getWindow().getGuiScaledWidth()-195)/2+182,y=(mc.getWindow().getGuiScaledHeight()-136)/2+150;screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);phase=2;ticks=0;
            }else if(phase==2&&ticks==30){
                check(mc.screen instanceof CreativeModeInventoryScreen,"real creative inventory opens");var screen=(CreativeModeInventoryScreen)mc.screen;check(screen.isInventoryOpen(),"creative personal inventory tab selected");BlackDogArmor.lockSlots(screen.getMenu());
                String[] armor={"head","chest","legs","feet"};for(int i=0;i<4;i++){var slot=screen.getMenu().slots.get(5+i);check(slot.getItem().is(CatalogueContent.item("blackdog_armor_"+armor[i])),"creative displayed slot maps to actual "+armor[i]+" projection");check(!slot.mayPickup(mc.player),"creative projection extraction remains locked");}
                var armorSlot=screen.getMenu().slots.get(5);int ax=(mc.getWindow().getGuiScaledWidth()-195)/2+armorSlot.x+8,ay=(mc.getWindow().getGuiScaledHeight()-136)/2+armorSlot.y+8;screen.mouseClicked(ax,ay,0);screen.mouseReleased(ax,ay,0);check(screen.getMenu().getCarried().isEmpty()&&armorSlot.getItem().is(CatalogueContent.item("blackdog_armor_head")),"clicking a creative armour proxy neither crashes nor extracts its projection");
                check(mc.player.getInventory().getItem(5).is(Items.APPLE)&&mc.player.getInventory().getItem(5).getCount()==7,"unrelated hotbar apples remain unchanged");shot("blackdog-creative-slots-fixed.png");phase=3;ticks=0;
            }else if(phase==3&&ticks==10){mc.player.closeContainer();mc.setScreen(null);mc.player.setYRot(0);mc.player.setXRot(0);phase=4;ticks=0;
            }else if(phase==4&&ticks==25){shot("blackdog-fire-grip-first-person.png");server(p->BlackDogWeapons.shieldImpact(p,p.position().add(0,1,4)));phase=5;ticks=0;
            }else if(phase==5&&ticks==4){boolean own=false;for(var entity:mc.level.entitiesForRendering())if(entity instanceof BlackDogBeam beam&&beam.style()==3&&beam.shieldOwner()==mc.player.getId())own=true;check(own,"first-person shield impact carries the local player's synchronized identity");shot("blackdog-shield-impact-first-person.png");mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);phase=6;ticks=0;
            }else if(phase==6&&ticks==25){shot("blackdog-fire-grip-third-person.png");server(p->BlackDogWeapons.shieldImpact(p,p.position().add(0,1,4)));phase=7;ticks=0;
            }else if(phase==7&&ticks==4){shot("blackdog-shield-local-ripple.png");phase=8;ticks=0;
            }else if(phase==8&&ticks==20){server(p->{for(var slot:java.util.List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))check(p.getItemBySlot(slot).getItem() instanceof BlackDogArmor.Projection,"server armour contains projections only");});Files.writeString(mc.gameDirectory.toPath().resolve("blackdog-polish-result.txt"),"PASS: real creative inventory mapping, four locked projection slots, unrelated items preserved, physical grip and first/third-person shield screenshots.");mc.stop();phase=9;}
        }catch(Exception e){fail(e);}
    }
    private BlackDogPolishCheck(){}
}
