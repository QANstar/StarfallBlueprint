package cn.starfall.blueprint.client;

import cn.starfall.blueprint.BlackDogArmor;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Optical cloak conceals the deployed solid armour as well as the player model. */
public final class BlackDogClient {
    public static void render(RenderPlayerEvent.Pre event){
        var player=event.getEntity();var watch=BlackDogArmor.active(player);
        if(player.isInvisible()&&BlackDogArmor.deployed(watch)&&BlackDogArmor.has(watch,"cloak"))event.setCanceled(true);
    }
    private BlackDogClient(){}
}
