package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Mounted input ownership ends immediately when a screen opens or the rider exits. */
@EventBusSubscriber(modid=StarfallBlueprint.ID,value=Dist.CLIENT)
public final class OrbitalClient {
    static int hiddenPilotFrames;static float maxCheckedCameraDistance;static boolean fireHeld,lockHeld;private static int selectedWeapon;private static PeregrineEntity lastVehicle,cameraVehicle;private static float cameraDistance=11;
    private static final java.util.Map<Integer,PeregrineEngineSound> engines=new java.util.HashMap<>();
    private static final IKeyConflictContext FLIGHT=new IKeyConflictContext(){public boolean isActive(){var m=Minecraft.getInstance();return m.screen==null&&m.player!=null&&m.player.getVehicle() instanceof PeregrineEntity;}public boolean conflicts(IKeyConflictContext c){return c==this;}};
    private static KeyMapping key(String id,int value){return new KeyMapping("hj.key."+id,FLIGHT,InputConstants.Type.KEYSYM,value,"hj.keys");}
    public static final KeyMapping ENTER=new KeyMapping("hj.key.enter",net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F,"hj.keys"),LEFT=key("roll_left",GLFW.GLFW_KEY_Q),RIGHT=key("roll_right",GLFW.GLFW_KEY_E),BOOST=key("boost",GLFW.GLFW_KEY_LEFT_CONTROL);
    public static void keys(RegisterKeyMappingsEvent e){for(var k:java.util.List.of(ENTER,LEFT,RIGHT,BOOST))e.register(k);}
    private static boolean physical(KeyMapping k){return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(),k.getKey().getValue());}
    private static boolean piloting(){var m=Minecraft.getInstance();return m.player!=null&&m.screen==null&&m.player.getVehicle() instanceof PeregrineEntity;}
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre e){
        if(!piloting())return;if(e.getButton()==GLFW.GLFW_MOUSE_BUTTON_LEFT){fireHeld=e.getAction()!=GLFW.GLFW_RELEASE;e.setCanceled(true);}else if(e.getButton()==GLFW.GLFW_MOUSE_BUTTON_RIGHT){lockHeld=false;e.setCanceled(true);}
    }
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered e){if(piloting()){e.setCanceled(true);e.setSwingHand(false);}}
    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent e){
        if(!piloting()||e.getScrollDeltaY()==0)return;var m=Minecraft.getInstance();var f=(PeregrineEntity)m.player.getVehicle();if(lastVehicle!=f){lastVehicle=f;selectedWeapon=f.weapon();}
        selectedWeapon=Math.floorMod(selectedWeapon+(e.getScrollDeltaY()>0?-1:1),3);PacketDistributor.sendToServer(new OrbitalNetworking.Weapon(selectedWeapon));e.setCanceled(true);m.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(HaijiaSounds.SELECT.get(),1.2f,.25f));
    }
    @SubscribeEvent public static void camera(CalculateDetachedCameraDistanceEvent e){if(Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.getVehicle() instanceof PeregrineEntity f){if(cameraVehicle!=f){cameraVehicle=f;cameraDistance=11;}cameraDistance=net.minecraft.util.Mth.lerp(.12f,cameraDistance,f.boosting()?14:11);e.setDistance(cameraDistance);if(Boolean.getBoolean("starfall.haijiaFlightCheck"))maxCheckedCameraDistance=Math.max(maxCheckedCameraDistance,e.getDistance());}else cameraVehicle=null;}
    @SubscribeEvent public static void keyEvent(InputEvent.Key e){var m=Minecraft.getInstance();if(m.player==null||m.screen!=null)return;if(m.player.getVehicle() instanceof PeregrineEntity){while(m.options.keyDrop.consumeClick()){}while(m.options.keySwapOffhand.consumeClick()){}if(e.getKey()==RIGHT.getKey().getValue()||e.getKey()==LEFT.getKey().getValue())while(m.options.keyInventory.consumeClick()){} }else if(e.getKey()==ENTER.getKey().getValue()&&!m.level.getEntitiesOfClass(PeregrineEntity.class,m.player.getBoundingBox().inflate(5)).isEmpty()){while(m.options.keySwapOffhand.consumeClick()){} }}
    @SubscribeEvent public static void movement(MovementInputUpdateEvent e){if(e.getEntity().getVehicle() instanceof PeregrineEntity)e.getInput().shiftKeyDown=false;}
    @SubscribeEvent public static void engineTick(ClientTickEvent.Post e){
        var m=Minecraft.getInstance();
        engines.entrySet().removeIf(entry->{if(m.level==null||entry.getValue().isStopped()){m.getSoundManager().stop(entry.getValue());return true;}return false;});
        if(m.level==null||m.player==null||m.isPaused())return;
        for(var entity:m.level.entitiesForRendering())if(entity instanceof PeregrineEntity plane&&plane.getControllingPassenger()!=null&&plane.energy()>0&&plane.distanceToSqr(m.player)<80*80&&!engines.containsKey(plane.getId())){
            var sound=new PeregrineEngineSound(plane);engines.put(plane.getId(),sound);m.getSoundManager().play(sound);
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Pre e){
        var m=Minecraft.getInstance();if(m.player==null){fireHeld=lockHeld=false;lastVehicle=null;return;}
        while(ENTER.consumeClick())if(m.screen==null&&!m.isPaused())PacketDistributor.sendToServer(new OrbitalNetworking.Flight(1024));
        if(!(m.player.getVehicle() instanceof PeregrineEntity f)){fireHeld=lockHeld=false;lastVehicle=null;return;}
        if(lastVehicle!=f){lastVehicle=f;selectedWeapon=f.weapon();fireHeld=lockHeld=false;}
        if(m.isPaused()||m.screen!=null)fireHeld=lockHeld=false;if(m.isPaused())return;
        int bits=0;if(m.screen==null){if(m.options.keyUp.isDown())bits|=1;if(m.options.keyDown.isDown())bits|=2;if(m.options.keyJump.isDown())bits|=4;if(physical(m.options.keyShift))bits|=8;if(physical(LEFT))bits|=16;if(physical(RIGHT))bits|=32;if(m.options.keyLeft.isDown())bits|=64;if(m.options.keyRight.isDown())bits|=128;if(fireHeld)bits|=PeregrineEntity.FIRE;if(BOOST.isDown())bits|=PeregrineEntity.BOOST;}
        m.options.keyShift.setDown(false);m.options.keySprint.setDown(false);PacketDistributor.sendToServer(new OrbitalNetworking.Flight(bits));
    }
    @SubscribeEvent public static void hidePilot(RenderPlayerEvent.Pre e){if(e.getEntity().getVehicle() instanceof PeregrineEntity){e.setCanceled(true);if(Boolean.getBoolean("starfall.haijiaFlightCheck"))hiddenPilotFrames++;}}
    @SubscribeEvent public static void hideHands(RenderHandEvent e){var p=Minecraft.getInstance().player;if(p!=null&&p.getVehicle() instanceof PeregrineEntity)e.setCanceled(true);}
    public static void hud(RegisterGuiLayersEvent e){e.registerAboveAll(StarfallBlueprint.id("hj_hud"),(g,d)->{
        var m=Minecraft.getInstance();if(m.player==null||m.options.hideGui||!(m.player.getVehicle() instanceof PeregrineEntity f))return;
        int accent=f.boosting()?0xffffa553:0xff8bdcff;
        int panelX=g.guiWidth()-132,panelY=g.guiHeight()-76;
        g.fill(panelX,panelY,panelX+124,panelY+68,0xc9112330);
        String[] icons={"laser","missile","bomb"};
        for(int i=0;i<3;i++){
            int sx=panelX+4+i*39;boolean selected=f.weapon()==i;
            if(selected){g.fill(sx,panelY+3,sx+37,panelY+25,0x803a6071);g.fill(sx,panelY+24,sx+37,panelY+26,accent);}
            BlackDogHudIcons.draw(g,icons[i],sx+2,panelY+6,selected?accent:0xff708797);
            g.drawString(m.font,i==0?"∞":Integer.toString(i==1?f.ammo():f.bombs()),sx+21,panelY+10,selected?0xffeffaff:0xff93a5b1,false);
        }
        BlackDogHudIcons.draw(g,"armor",panelX+5,panelY+30,0xff86dbb0);
        g.fill(panelX+25,panelY+36,panelX+57,panelY+40,0xff263e4a);
        g.fill(panelX+25,panelY+36,panelX+25+Math.round(32*f.health()/160),panelY+40,0xff86dbb0);
        BlackDogHudIcons.draw(g,"energy",panelX+64,panelY+30,0xff8bdcff);
        g.fill(panelX+84,panelY+36,panelX+116,panelY+40,0xff263e4a);
        g.fill(panelX+84,panelY+36,panelX+84+Math.round(32*f.energy()/2000000f),panelY+40,0xff8bdcff);
        BlackDogHudIcons.draw(g,"flight",panelX+5,panelY+49,accent);
        g.drawString(m.font,Long.toString(Math.round(f.getDeltaMovement().horizontalDistance()*72)),panelX+26,panelY+53,accent,false);
        BlackDogHudIcons.draw(g,"boost",panelX+64,panelY+49,f.boosting()?accent:0xff708797);
        g.fill(panelX+84,panelY+55,panelX+116,panelY+59,0xff263e4a);
        g.fill(panelX+84,panelY+55,panelX+84+Math.round(32*f.throttle()),panelY+59,accent);
        int x=g.guiWidth()/2,y=g.guiHeight()/2;
        g.fill(x-14,y,x-5,y+1,accent);g.fill(x+5,y,x+14,y+1,accent);g.fill(x,y-10,x+1,y-4,accent);
    });}
}
