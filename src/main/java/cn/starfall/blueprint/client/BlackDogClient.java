package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import java.util.List;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Optical cloak conceals the deployed solid armour as well as the player model. */
@EventBusSubscriber(modid=StarfallBlueprint.ID,value=Dist.CLIENT)
public final class BlackDogClient {
    @SubscribeEvent public static void shield(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;var mc=Minecraft.getInstance();if(mc.level==null)return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();var buffer=buffers.getBuffer(net.minecraft.client.renderer.RenderType.lightning());float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for(var player:mc.level.players()){
            var chest=player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);if(!(chest.getItem() instanceof BlackDogArmor.Projection)||!CatalogueEquipment.getData(chest).getBoolean("ShieldActive")||player.distanceToSqr(camera)>1600)continue;
            // A faint exterior shell keeps first-person visibility clear; impacts have a separate bright pulse.
            if(player==mc.player&&mc.options.getCameraType().isFirstPerson())continue;
            pose.pushPose();Vec3 point=player.getPosition(partial).subtract(camera).add(0,1,0);pose.translate(point.x,point.y,point.z);
            BlackDogBeamRenderer.sphere(buffer,pose,Vec3.ZERO,1.15+Math.sin((player.tickCount+partial)*.06)*.025,0xeb587b,.12f);pose.popPose();
        }
        buffers.endBatch(net.minecraft.client.renderer.RenderType.lightning());
    }
    private static final KeyMapping NEXT=new KeyMapping("key.starfall_blueprint.blackdog_next",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_R,"key.categories.starfall_blueprint");
    private static final KeyMapping PREVIOUS=new KeyMapping("key.starfall_blueprint.blackdog_previous",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V,"key.categories.starfall_blueprint");
    private static final KeyMapping[] DIRECT=new KeyMapping[BlackDogArmor.SYSTEMS.size()];
    static {for(int i=0;i<DIRECT.length;i++)DIRECT[i]=new KeyMapping("key.starfall_blueprint.blackdog_"+BlackDogArmor.SYSTEMS.get(i),KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_UNKNOWN,"key.categories.starfall_blueprint");}
    private static boolean jumping,sprinting,hovering,cruise;
    private static boolean wasAirborne;
    private static int jumpTime=-100,ticks,heartbeat,takeoffUntil;
    private static java.util.UUID suit;
    private static JetSound jet;
    private static java.util.UUID hudSuit;
    private static String hudSelection="";
    private static int hudState,hudFlight,flashUntil,flashSlot;
    private static Component flashText=Component.empty();
    private static long lastShutdown;
    private static int shutdownUntil;
    record FlightKeys(boolean jump,boolean cruise){}
    static FlightKeys reviewKeys;
    public static void keys(RegisterKeyMappingsEvent e){e.register(NEXT);e.register(PREVIOUS);for(var key:DIRECT)e.register(key);}
    public static void layers(RegisterGuiLayersEvent e){e.registerAboveAll(StarfallBlueprint.id("blackdog_fire_control"),(g,t)->hud(g));}
    private static void select(int action){var mc=Minecraft.getInstance();if(mc.player!=null&&mc.screen==null&&BlackDogArmor.holdingTerminal(mc.player))PacketDistributor.sendToServer(new BlackDogNetworking.Control(action));}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        while(NEXT.consumeClick())select(-1);while(PREVIOUS.consumeClick())select(-2);for(int i=0;i<DIRECT.length;i++)while(DIRECT[i].consumeClick())select(i);
        if(mc.player==null||mc.level==null){reset();return;}
        var player=mc.player;var watch=BlackDogArmor.active(player);var data=CatalogueEquipment.getData(watch);
        boolean capable=!watch.isEmpty()&&BlackDogArmor.has(watch,"flight")&&BlackDogArmor.charge(watch)>=100&&player.isAlive()&&!player.isPassenger()&&!player.isSpectator();
        if(!capable){
            if(suit!=null){player.getAbilities().flying=false;player.stopFallFlying();player.setPose(Pose.STANDING);if(mc.getConnection()!=null)PacketDistributor.sendToServer(new BlackDogNetworking.Flight(false,false,false));}
            reset();return;
        }
        var identity=data.hasUUID("SuitId")?data.getUUID("SuitId"):null;
        if(!java.util.Objects.equals(identity,suit)){reset();suit=identity;hovering=data.getBoolean("Hovering");}
        if(mc.isPaused())return;ticks++;
        boolean jump=mc.screen==null&&mc.options.keyJump.isDown(),sprint=mc.screen==null&&mc.options.keySprint.isDown();boolean changed=false,takeoff=false;
        if(Boolean.getBoolean("starfall.blackdogPlaytest")&&reviewKeys!=null){jump=reviewKeys.jump();sprint=reviewKeys.cruise();}
        if(jump&&!jumping){if(ticks-jumpTime<=7){hovering=!hovering;cruise=false;changed=true;jumpTime=-100;takeoff=hovering;wasAirborne=false;takeoffUntil=ticks+8;if(hovering&&player.onGround())player.setDeltaMovement(player.getDeltaMovement().add(0,.45,0));}else jumpTime=ticks;}
        if(hovering&&!player.onGround())wasAirborne=true;
        if(hovering&&player.onGround()&&(wasAirborne||ticks>takeoffUntil)){
            hovering=cruise=wasAirborne=false;changed=true;jumpTime=-100;player.stopFallFlying();player.setPose(Pose.STANDING);
        }
        if(sprint&&!sprinting&&hovering){cruise=!cruise;changed=true;}
        jumping=jump;sprinting=sprint;
        if(cruise&&(player.horizontalCollision||player.isInWater()||mc.screen!=null||BlackDogArmor.charge(watch)<350)){cruise=false;changed=true;}
        player.getAbilities().flying=hovering&&!cruise;
        if(hovering){
            Vec3 target;
            if(cruise){target=player.getLookAngle().scale(2.6);player.startFallFlying();player.setPose(Pose.FALL_FLYING);}
            else {
                if(player.isFallFlying()){player.stopFallFlying();player.setPose(Pose.STANDING);}
                double forward=mc.screen==null?(mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0):0;
                double sideways=mc.screen==null?(mc.options.keyLeft.isDown()?1:0)-(mc.options.keyRight.isDown()?1:0):0;
                double yaw=Math.toRadians(player.getYRot());target=new Vec3(-Math.sin(yaw)*forward+Math.cos(yaw)*sideways,(jump?1:0)-(mc.screen==null&&mc.options.keyShift.isDown()?1:0),Math.cos(yaw)*forward+Math.sin(yaw)*sideways);
                if(target.lengthSqr()>1)target=target.normalize();target=target.scale(.48);
            }
            player.setDeltaMovement(player.getDeltaMovement().lerp(target,cruise?.09:.18));player.fallDistance=0;
            if(jet==null||jet.isStopped()){jet=new JetSound();mc.getSoundManager().play(jet);}
        }else if(player.isFallFlying()){player.stopFallFlying();player.setPose(Pose.STANDING);}
        if(changed||++heartbeat>=4){heartbeat=0;PacketDistributor.sendToServer(new BlackDogNetworking.Flight(hovering,cruise,takeoff));}
    }
    private static void reset(){jumping=sprinting=hovering=cruise=wasAirborne=false;jumpTime=-100;ticks=heartbeat=takeoffUntil=0;suit=null;}
    private static final class JetSound extends AbstractTickableSoundInstance {
        JetSound(){super(SoundEvents.ELYTRA_FLYING,SoundSource.PLAYERS,RandomSource.create());looping=true;delay=0;volume=.1f;}
        @Override public void tick(){var p=Minecraft.getInstance().player;if(p==null||!hovering||suit==null){stop();return;}x=p.getX();y=p.getY();z=p.getZ();float speed=(float)p.getDeltaMovement().length();volume=Math.min(.7f,.12f+speed*.22f);pitch=Math.min(1.8f,.65f+speed*.3f);}
    }
    private static Component text(String key,Object... args){return Component.translatable("lc."+key,args);}
    private static void fit(GuiGraphics g,Component value,int x,int y,int width,int color){var font=Minecraft.getInstance().font;float scale=Math.min(1,width/(float)Math.max(1,font.width(value)));g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.drawString(font,value,0,0,color,false);g.pose().popPose();}
    private static void hud(GuiGraphics g){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;var player=mc.player;var watch=BlackDogArmor.active(player);
        if(watch.isEmpty()){
            if(hudSuit!=null)for(int i=0;i<player.getInventory().getContainerSize();i++){
                var carried=player.getInventory().getItem(i);if(!(carried.getItem() instanceof BlackDogArmor.Watch))continue;var saved=CatalogueEquipment.getData(carried);
                if(saved.hasUUID("SuitId")&&hudSuit.equals(saved.getUUID("SuitId"))&&saved.getLong("ShutdownAt")>0&&saved.getLong("ShutdownAt")!=lastShutdown){lastShutdown=saved.getLong("ShutdownAt");shutdownUntil=player.tickCount+40;}
            }
            if(player.tickCount<shutdownUntil){
                var notice=text("power_shutdown");int w=mc.font.width(notice)+12,x=mc.getWindow().getGuiScaledWidth()-w-4,y=mc.getWindow().getGuiScaledHeight()/2-7;
                g.fill(x,y,x+w,y+15,0xdd171b26);g.fill(x,y,x+2,y+15,0xffe47b65);g.drawString(mc.font,notice,x+6,y+4,0xffffb39b,false);
            }
            return;
        }
        String selection=BlackDogArmor.selectedSystem(watch);int state=0;for(int i=0;i<BlackDogArmor.SYSTEMS.size();i++)if(BlackDogArmor.enabled(watch,BlackDogArmor.SYSTEMS.get(i)))state|=1<<i;
        int flight=cruise?2:hovering?1:0,now=player.tickCount;var data=CatalogueEquipment.getData(watch);var identity=data.hasUUID("SuitId")?data.getUUID("SuitId"):null;
        if(!java.util.Objects.equals(identity,hudSuit)||!selection.equals(hudSelection)||state!=hudState){
            flashText=selection.isEmpty()?text("hud_armor"):text("system."+selection);
            flashSlot=0;for(String system:BlackDogArmor.SYSTEMS){if(system.equals(selection))break;if(BlackDogArmor.has(watch,system))flashSlot++;}
            if(List.of("turret","shield","cloak").contains(selection))flashText=flashText.copy().append(" · ").append(text(BlackDogArmor.enabled(watch,selection)?"hud_on":"hud_off"));
            flashUntil=now+24;
        }else if(flight!=hudFlight){flashText=text(flight==2?"hud_cruise":flight==1?"hud_hover":"hud_landed");flashUntil=now+24;flashSlot=systemCount(watch)-1;}
        hudSuit=identity;hudSelection=selection;hudState=state;hudFlight=flight;
        float scale=.65f;int panelHeight=panelHeight(watch),x=mc.getWindow().getGuiScaledWidth()-25,y=(int)((mc.getWindow().getGuiScaledHeight()-panelHeight*scale)/2);
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);
        renderPanel(g,0,0,watch,player.getCooldowns().getCooldownPercent(CatalogueContent.item("blackdog_fire_control"),0));g.pose().popPose();
        if(now<flashUntil){
            int labelWidth=Math.min(120,mc.font.width(flashText)+10),labelX=x-labelWidth-4,labelY=y+(int)((4+Math.max(0,flashSlot)*26)*scale)+2;
            g.fill(labelX,labelY,labelX+labelWidth,labelY+12,0xd5111723);fit(g,flashText,labelX+5,labelY+2,labelWidth-10,0xffe6e9ed);
        }
    }
    private static int systemCount(ItemStack watch){return Math.max(1,(int)BlackDogArmor.SYSTEMS.stream().filter(a->BlackDogArmor.has(watch,a)).count()+(BlackDogArmor.has(watch,"flight")?1:0));}
    private static int panelHeight(ItemStack watch){return systemCount(watch)*26+18;}
    static void renderPanel(GuiGraphics g,int x,int y,ItemStack watch,float cooldown){
        var mc=Minecraft.getInstance();int height=panelHeight(watch),slot=0;String selected=BlackDogArmor.selectedSystem(watch);
        g.fill(x,y,x+32,y+height,0xd8101520);g.fill(x+31,y,x+32,y+height,0xff654050);
        for(String system:BlackDogArmor.SYSTEMS){if(!BlackDogArmor.has(watch,system))continue;
            boolean toggle=List.of("turret","shield","cloak").contains(system),on=toggle&&BlackDogArmor.enabled(watch,system),chosen=selected.equals(system);
            int color=switch(system){case "laser"->0xffff7288;case "missile"->0xffffae64;case "cannon"->0xff7ed9f3;case "turret"->0xffeed080;case "shield"->0xff79bff4;default->0xffc49eec;};
            iconSlot(g,x+3,y+4+slot++*26,system,color,chosen,on,toggle?0:cooldown);
        }
        if(BlackDogArmor.has(watch,"flight"))iconSlot(g,x+3,y+4+slot*26,"flight",cruise?0xffffc276:0xff88d5cb,false,hovering,0);
        else if(slot==0)iconSlot(g,x+3,y+4,"armor",0xffb0becd,false,true,0);
        double fraction=BlackDogArmor.charge(watch)/(double)Math.max(1,BlackDogArmor.capacity(watch));int barHeight=height-20,barColor=fraction<.15?0xffed795c:0xff79c8da;
        g.fill(x+27,y+4,x+30,y+4+barHeight,0xff2e3949);g.fill(x+27,y+4+barHeight-(int)(barHeight*fraction),x+30,y+4+barHeight,barColor);
        for(int i=13;i<barHeight;i+=13)g.fill(x+27,y+4+i,x+30,y+5+i,0x77202b39);
        String percent=Math.round(fraction*100)+"%";g.drawString(mc.font,percent,x+16-mc.font.width(percent)/2,y+height-11,barColor,false);
    }
    private static void iconSlot(GuiGraphics g,int x,int y,String system,int color,boolean selected,boolean on,float cooldown){
        g.fill(x-1,y-1,x+21,y+24,selected?0xffed92a5:on?0xff59988d:0xff344252);g.fill(x,y,x+20,y+23,selected?0xff3c293b:0xff1b2634);
        boolean toggle=List.of("turret","shield","cloak","flight").contains(system);
        BlackDogHudIcons.draw(g,system,x+2,y+2,toggle&&!on?0xff708092:color);
        if(cooldown>0){g.fill(x+2,y+2+(int)(16*(1-cooldown)),x+18,y+18,0xb0101721);g.fill(x+2,y+21,x+2+(int)(16*(1-cooldown)),y+22,0xffefb86a);}
        if(on)g.fill(x+14,y+18,x+18,y+21,0xff88e6b4);
        if(selected)g.fill(x+7,y-2,x+13,y,0xfff2ccd5);
    }
    public static void render(RenderPlayerEvent.Pre event){
        var player=event.getEntity();var watch=BlackDogArmor.active(player);
        if(player.isInvisible()&&BlackDogArmor.deployed(watch)&&BlackDogArmor.has(watch,"cloak"))event.setCanceled(true);
    }
    private BlackDogClient(){}
}
