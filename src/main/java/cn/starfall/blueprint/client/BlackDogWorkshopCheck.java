package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import java.nio.file.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in real server/client wiring and display QA, restricted to the isolated review save. */
@EventBusSubscriber(modid=StarfallBlueprint.ID,value=Dist.CLIENT)
public final class BlackDogWorkshopCheck {
    private static int phase,ticks;
    private static volatile boolean ready;
    private static BlockPos root;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(BlackDogWorkshopCheck.class);
    private static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(server!=null&&mc.player!=null){var id=mc.player.getUUID();server.execute(()->{try{var player=server.getPlayerList().getPlayer(id);if(player!=null)action.accept(player);}catch(Exception e){fail(e);}});}}
    private static void check(boolean success,String message){if(!success)throw new IllegalStateException(message);LOG.info("WORKSHOP QA PASS: {}",message);}
    private static IndustryBlockEntity station(net.minecraft.server.level.ServerPlayer player,int offset){return (IndustryBlockEntity)player.level().getBlockEntity(root.offset(offset,0,0));}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->LOG.info("WORKSHOP QA screenshot {}",c.getString()));}
    private static void fail(Exception e){LOG.error("WORKSHOP QA failed",e);try{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("blackdog-workshop-result.txt"),"FAIL: "+e);}catch(Exception ignored){}Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("starfall.blackdogWorkshopCheck"))return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("blackdog-review"))throw new IllegalStateException("Workshop QA requires isolated blackdog-review directory");
        try{
            if(phase==0){
                mc.options.pauseOnLostFocus=false;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.renderDistance().set(6);mc.setScreen(null);
                server(p->{
                    var old=BlackDogArmor.active(p);if(!old.isEmpty())BlackDogArmor.recall(p,old);p.getInventory().clearContent();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();
                    p.getPersistentData().putInt("LingchuanResearch",65535);root=new BlockPos(0,120,0);
                    for(int x=-4;x<15;x++)for(int z=-7;z<6;z++)p.level().setBlock(new BlockPos(x,119,z),Blocks.SMOOTH_QUARTZ.defaultBlockState(),3);
                    String[] kinds={"lc_armor_assembler","lc_armor_charger","lc_parts_fabricator"};
                    for(int i=0;i<3;i++){
                        var pos=root.offset(i*5,0,0);var block=CatalogueContent.BLOCKS.get(kinds[i]).get();var state=block.defaultBlockState().setValue(IndustryBlock.FACING,Direction.NORTH);p.level().setBlock(pos,state,3);block.setPlacedBy(p.level(),pos,state,p,ItemStack.EMPTY);station(p,i*5).clearContent();station(p,i*5).setPower(2000000);
                    }
                    var bases=java.util.stream.IntStream.range(0,8).mapToObj(i->CatalogueContent.stack(Lingchuan.baseKey(i,3),1)).toList();var watch=BlackDogArmor.build(bases);BlackDogArmor.setCharge(watch,123456);station(p,0).setItem(8,watch);
                    p.getInventory().setItem(2,CatalogueContent.stack("blackdog_component_flight",1));p.getInventory().setItem(3,CatalogueContent.stack("laminar_alloy",64));p.getInventory().setItem(4,CatalogueContent.stack("laminar_crystal",64));p.getInventory().setItem(5,CatalogueContent.stack("board",64));
                    var full=new ItemStack(BlackDogArmor.ARMED_WATCH.get());((BlackDogArmor.ArmedWatch)full.getItem()).prepare(full);BlackDogArmor.setCharge(full,400000);station(p,5).setItem(8,full);
                    p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);p.teleportTo(3.0,121,-5);p.setYRot(0);p.setXRot(8);p.serverLevel().setDayTime(6000);p.serverLevel().setWeatherParameters(6000,0,false,false);p.openMenu(station(p,0));ready=true;
                });phase=1;ticks=0;return;
            }
            if(!ready)return;ticks++;
            if(phase==1&&ticks>=45&&mc.screen instanceof LingchuanScreen screen){shot("workshop-assembly-before.png");screen.reviewConnect(4);phase=2;ticks=0;}
            else if(phase==2&&ticks==30){
                check(mc.screen instanceof LingchuanScreen,"assembly menu remains open after drag");var be=(IndustryBlockEntity)mc.level.getBlockEntity(root);check((be.blackDogDisplay.getInt("Abilities")&(1<<4))!=0,"world display receives connected flight component");
                server(p->{check(BlackDogArmor.has(station(p,0).getItem(8),"flight")&&p.getInventory().getItem(2).isEmpty()&&station(p,0).getPower()==1995000,"real canvas drag consumed one owned component and exact power");});shot("workshop-assembly-connected.png");((LingchuanScreen)mc.screen).reviewDetach(4);phase=3;ticks=0;
            }else if(phase==3&&ticks==30){
                server(p->{check(!BlackDogArmor.has(station(p,0).getItem(8),"flight")&&station(p,0).getItem(10).is(CatalogueContent.item("blackdog_component_flight")),"right click detaches into real staging inventory");p.openMenu(station(p,5));});phase=4;ticks=0;
            }else if(phase==4&&ticks==40){check(mc.screen instanceof LingchuanScreen,"charger screen opens");var be=(IndustryBlockEntity)mc.level.getBlockEntity(root.offset(5,0,0));check(Integer.bitCount(be.blackDogDisplay.getInt("Abilities"))==12,"charger synchronizes complete watch configuration");shot("workshop-charger.png");server(p->{p.teleportTo(7,121,-5);p.openMenu(station(p,10));});phase=5;ticks=0;
            }else if(phase==5&&ticks>=35&&mc.screen instanceof LingchuanScreen screen){screen.reviewProduct();phase=6;ticks=0;
            }else if(phase==6&&ticks==15){shot("workshop-fabricator.png");for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().equals(Component.translatable("lc.workshop.manufacture"))){b.onPress();break;}phase=7;ticks=0;
            }else if(phase==7&&ticks==30){server(p->{check(station(p,10).getItem(9).is(CatalogueContent.item("blackdog_component_night")),"filtered manufacture produces the selected real component");p.teleportTo(6,121,-7.5);});mc.player.closeContainer();mc.setScreen(null);mc.player.setYRot(0);mc.player.setXRot(8);phase=8;ticks=0;
            }else if(phase==8&&ticks==40){shot("workshop-machines.png");mc.player.setYRot(-45);phase=9;ticks=0;
            }else if(phase==9&&ticks==20){shot("workshop-machines-angle.png");server(p->{var be=station(p,0);be.clearContent();for(int i:new int[]{0,1,2,6})be.setItem(i,CatalogueContent.stack(Lingchuan.baseKey(i,i==6?2:3),1));be.setItem(10,CatalogueContent.stack("blackdog_component_flight",1));p.teleportTo(3,121,-5);p.openMenu(be);});phase=10;ticks=0;
            }else if(phase==10&&ticks>=35&&mc.screen instanceof LingchuanScreen screen){check(java.util.Arrays.stream(screen.getMenu().preview().getIntArray("ChassisTiers")).filter(i->i>0).count()==4,"unfinished assembly displays only its four placed chassis parts");shot("workshop-assembly-partial.png");Files.writeString(mc.gameDirectory.toPath().resolve("blackdog-workshop-result.txt"),"PASS: real canvas drag, server item/power transaction, detach, synchronized gantry, partial assembly and complete charging model, filtered manufacture, screenshots.");mc.stop();phase=11;}
        }catch(Exception e){fail(e);}
    }
    private BlackDogWorkshopCheck(){}
}
