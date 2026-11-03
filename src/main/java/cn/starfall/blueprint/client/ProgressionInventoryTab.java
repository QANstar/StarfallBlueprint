package cn.starfall.blueprint.client;

import cn.starfall.blueprint.ProgressionNetworking;
import cn.starfall.blueprint.StarfallBlueprint;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only inventory entry, following the inventory when its recipe book moves it. */
@EventBusSubscriber(modid = StarfallBlueprint.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ProgressionInventoryTab {
    private static final WeakHashMap<Screen, Button> TABS = new WeakHashMap<>();

    public static Component text(String key, Object... args) {
        return Component.translatable("growth.starfall_blueprint." + key, args);
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        // Restore the inventory menu before the server opens the remote growth menu.
        minecraft.player.closeContainer();
        PacketDistributor.sendToServer(new ProgressionNetworking.Open());
    }

    @SubscribeEvent public static void init(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen)
                && !(event.getScreen() instanceof CreativeModeInventoryScreen)) return;
        Button tab = Button.builder(text("tab"), button -> open()).bounds(0, 0, 48, 20)
                .tooltip(Tooltip.create(text("tab_hint"))).build();
        TABS.put(event.getScreen(), tab);
        position(event.getScreen(), tab);
        event.addListener(tab);
    }

    @SubscribeEvent public static void render(ScreenEvent.Render.Pre event) {
        Button tab = TABS.get(event.getScreen());
        if (tab != null) position(event.getScreen(), tab);
    }

    private static void position(Screen screen, Button tab) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) return;
        tab.setX(Math.max(2, Math.min(screen.width - tab.getWidth() - 2,
                container.getGuiLeft() + container.getXSize() + 2)));
        tab.setY(Math.max(2, Math.min(screen.height - tab.getHeight() - 2, container.getGuiTop() + 7)));
    }
}
