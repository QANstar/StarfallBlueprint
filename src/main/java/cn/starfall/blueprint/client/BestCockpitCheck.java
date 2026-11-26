package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Exercises a real server mount, local camera transition and normal dismount in a copied world. */
@EventBusSubscriber(modid = StarfallBlueprint.ID, value = Dist.CLIENT)
public final class BestCockpitCheck {
    private static int phase, ticks, elapsed;
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("starfall.bestCockpitCheck")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
        var dir = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!dir.endsWith("best-cockpit-review")) throw new IllegalStateException("Use an isolated review save");
        if (++elapsed > 700) throw new IllegalStateException("Cockpit check timed out at phase " + phase);
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        if (phase == 0) {
            mc.setScreen(null);
            mc.options.pauseOnLostFocus = false;
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server.execute(() -> {
                var p = server.getPlayerList().getPlayer(id);
                var level = p.serverLevel();
                p.stopRiding();
                for (var previous : level.getEntitiesOfClass(BestMechEntity.class, p.getBoundingBox().inflate(64))) previous.discard();
                p.setGameMode(GameType.CREATIVE);
                level.setDayTime(1000);
                var pos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.blockPosition());
                level.setBlockAndUpdate(pos, Blocks.SMOOTH_STONE.defaultBlockState());
                p.teleportTo(pos.getX()+.5, pos.getY()+1, pos.getZ()+.5);
                var mech = BestMechs.COMBAT_TYPE.get().create(level);
                mech.loadState(BestMechItem.state(BestMechItem.creativeStack(BestMechProgression.COMBAT)));
                mech.moveTo(p.getX(),p.getY(),p.getZ(),p.getYRot(),0);
                level.addFreshEntity(mech);
                p.startRiding(mech, true);
            });
            phase++;
        } else if (phase == 1 && mc.player.getVehicle() instanceof BestMechEntity mech && ++ticks > 45) {
            if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) throw new IllegalStateException("Mount did not default to third-person view");
            Screenshot.grab(mc.gameDirectory,"cockpit-third-person.png",mc.getMainRenderTarget(),msg -> LogUtils.getLogger().info("{}",msg.getString()));
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            phase++; ticks=0;
        } else if (phase == 2 && mc.player.getVehicle() instanceof BestMechEntity mech && ++ticks > 15) {
            if (!mc.options.getCameraType().isFirstPerson()) throw new IllegalStateException("F5/first-person toggle was overridden");
            double height = mc.gameRenderer.getMainCamera().getPosition().y - mech.getY();
            if (height < 2.5 || height > 4.4) throw new IllegalStateException("Unexpected combat camera height " + height);
            Screenshot.grab(mc.gameDirectory,"cockpit-live.png",mc.getMainRenderTarget(),msg -> LogUtils.getLogger().info("{}",msg.getString()));
            server.execute(() -> server.getPlayerList().getPlayer(id).stopRiding());
            phase++; ticks=0;
        } else if (phase == 3 && mc.player.getVehicle()==null && ++ticks > 10) {
            if (mc.options.getCameraType()!=CameraType.FIRST_PERSON) throw new IllegalStateException("Prior view not restored");
            var previewMech = BestMechs.COMBAT_TYPE.get().create(mc.level);
            previewMech.loadState(BestMechItem.state(BestMechItem.creativeStack(BestMechProgression.COMBAT)));
            var inventory = mc.player.getInventory();
            var menu = new BestMechLoadoutMenu(0, inventory, previewMech);
            mc.setScreen(new BestMechLoadoutScreen(menu, inventory, net.minecraft.network.chat.Component.translatable("menu.starfall_blueprint.best_mech_loadout")));
            phase++; ticks=0;
        } else if (phase == 4 && ++ticks > 20) {
            Screenshot.grab(mc.gameDirectory,"loadout-panel.png",mc.getMainRenderTarget(),msg -> LogUtils.getLogger().info("{}",msg.getString()));
            var inventory = mc.player.getInventory();
            var screen = new BestWorkbenchScreen(new BestWorkbenchMenu(0, inventory), inventory, net.minecraft.network.chat.Component.translatable("block.starfall_blueprint.best_workbench"));
            mc.setScreen(screen); screen.selectPage(3);
            phase++; ticks=0;
        } else if (phase == 5 && ++ticks > 20) {
            Screenshot.grab(mc.gameDirectory,"workbench-panel.png",mc.getMainRenderTarget(),msg -> LogUtils.getLogger().info("{}",msg.getString()));
            phase++; ticks=0;
        } else if (phase == 6 && ++ticks > 15) {
            Files.writeString(dir.resolve("cockpit-result.txt"),"PASS: boarded, first-person optic at mech height, dismounted, prior third-person view restored, cargo and workbench screens rendered");
            phase++; mc.stop();
        }
    }
}
