package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in real screen registration and setting-packet regression, isolated from user saves. */
@EventBusSubscriber(modid=StarfallBlueprint.ID, value=Dist.CLIENT)
public final class ManualPlaytest {
    private static int phase, ticks;
    private static boolean esper, dog;
    private static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
        var mc=Minecraft.getInstance(); var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
        server.execute(()->action.accept(server.getPlayerList().getPlayer(id)));
    }
    private static void require(boolean value, String reason) { if(!value) throw new IllegalStateException(reason); }
    private static void shot(String name) {
        var mc=Minecraft.getInstance(); Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("starfall.manualPlaytest"))return;
        var mc=Minecraft.getInstance(); if(mc.getOverlay()!=null||mc.player==null||mc.level==null)return;
        require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("manual-review"),"Use an isolated manual-review directory");
        try {
            ticks++;
            if(phase==0) {
                mc.options.pauseOnLostFocus=false; mc.setScreen(null);
                server(p->{p.closeContainer();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(StarfallBlueprint.GUIDE.get()));
                    StarfallBlueprint.GUIDE.get().use(p.level(),p,InteractionHand.MAIN_HAND);});
                phase=1;ticks=0;
            } else if(phase==1 && ticks>30 && mc.screen instanceof ProgressionScreen screen) {
                require(ManualArticles.entries().stream().anyMatch(a->a.category().equals("camp")),"Camp articles must remain available");
                for(var article:ManualArticles.entries()) require(!(article.title().getString()+article.description().getString())
                        .matches("(?s).*(学习|研究解锁|缴纳|前置学习|[Ll]earn|[Uu]nlock).*"),"Study text in article "+article.id());
                shot("manual-articles.png");
                Button settings=null;
                for(var child:screen.children()) if(child instanceof Button button && button.getMessage().equals(ProgressionInventoryTab.text("category.settings"))) settings=button;
                require(settings!=null,"Personal settings must be present on the registered screen");settings.onPress();phase=2;ticks=0;
            } else if(phase==2 && ticks==20) {
                var screen=(ProgressionScreen)mc.screen;esper=screen.getMenu().allows(PlayerManual.ESPER);dog=screen.getMenu().allows(PlayerManual.BLACK_DOG);
                shot("manual-personal-settings.png");
                int clicked=0;
                for(var child:screen.children())if(child instanceof Button button && button.visible && button.getHeight()==22){button.onPress();clicked++;}
                require(clicked==2,"Exactly two personal toggles must be visible");phase=3;ticks=0;
            } else if(phase==3 && ticks==30) {
                var menu=((ProgressionScreen)mc.screen).getMenu();
                require(menu.allows(PlayerManual.ESPER)!=esper && menu.allows(PlayerManual.BLACK_DOG)!=dog,"Both setting packets must update synchronized state");
                mc.player.closeContainer();mc.setScreen(null);
                server(p->{p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(PlayerManual.ITEM.get()));PlayerManual.ITEM.get().use(p.level(),p,InteractionHand.MAIN_HAND);});
                phase=4;ticks=0;
            } else if(phase==4 && ticks>20 && mc.screen instanceof ProgressionScreen) {
                Files.writeString(mc.gameDirectory.toPath().resolve("manual-result.txt"),"PASS: both books open the registered manual; articles, visible settings and both real server toggles verified.");mc.stop();phase=5;
            }
            if(ticks>300)throw new IllegalStateException("Manual screen did not open");
        } catch(Exception error) {
            org.slf4j.LoggerFactory.getLogger(ManualPlaytest.class).error("MANUAL QA FAILED",error);
            try {Files.writeString(mc.gameDirectory.toPath().resolve("manual-result.txt"),"FAIL: "+error);}catch(Exception ignored){}
            mc.stop();
        }
    }
    private ManualPlaytest() {}
}
