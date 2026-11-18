package cn.starfall.blueprint.client;

import cn.starfall.blueprint.Esper;
import cn.starfall.blueprint.StarfallBlueprint;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = StarfallBlueprint.ID, value = Dist.CLIENT)
public final class EsperClient {
    static final KeyMapping[] CAST = new KeyMapping[Esper.SLOTS];
    static {
        for (int i = 0; i < CAST.length; i++) CAST[i] = new KeyMapping("esper.key." + (i + 1),
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6 + i, "esper.name");
    }
    static void keys(RegisterKeyMappingsEvent event) { for (var key : CAST) event.register(key); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < CAST.length; i++) {
            boolean pressed = false;
            while (CAST[i].consumeClick()) pressed = true;
            if (pressed && mc.player != null && mc.screen == null && !mc.player.isSpectator())
                PacketDistributor.sendToServer(new Esper.Cast(i));
        }
    }
    private EsperClient() {}
}
