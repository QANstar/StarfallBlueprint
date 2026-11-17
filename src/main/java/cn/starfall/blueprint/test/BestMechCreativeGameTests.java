package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("starfall_blueprint_mech_three")
@PrefixGameTestTemplate(false)
public final class BestMechCreativeGameTests {
    @GameTest(template = "combat_arena")
    public static void creativePresetsDeployAndDriveWithoutKnowledge(GameTestHelper h) {
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++)
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        var profile = new GameProfile(UUID.randomUUID(), "CreativeMechTest");
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        p.setGameMode(GameType.CREATIVE);
        var origin = h.absolutePos(new BlockPos(1, 1, 1));
        p.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5, 0, 0);
        var floor = h.absolutePos(new BlockPos(5, 0, 5));
        try {
            for (String type : List.of(BestMechProgression.ENGINEERING, BestMechProgression.COMBAT, BestMechProgression.UNIVERSAL)) {
                int mask = type.equals(BestMechProgression.ENGINEERING) ? 7 : type.equals(BestMechProgression.COMBAT) ? 56 : 448;
                var ordinary = CatalogueContent.stack(BestMechProgression.itemKey(type), 1);
                h.assertTrue(BestMechItem.energy(ordinary).getEnergyStored() == 0, type + " ordinary item starts empty");
                var stack = BestMechItem.creativeStack(type);
                var tag = BestMechItem.state(stack);
                h.assertTrue(BestMechProgression.type(stack).equals(type) && stack.getCount() == 1, type + " uses its own item");
                h.assertTrue(BestMechProgression.mask(tag) == mask, type + " has every compatible upgrade");
                h.assertTrue(BestMechItem.energy(stack).getEnergyStored() == BestMechItem.energy(stack).getMaxEnergyStored()
                        && tag.getFloat("Health") == BestMechProgression.maxHealth(type, tag), type + " starts at full energy and health");
                h.assertTrue(!tag.hasUUID("MechId"), type + " preset must not carry a fixed identity");
                h.assertTrue(!BestMechModules.sanitize(tag.getString(BestMechModules.STATE)).isEmpty()
                        && !BestMechCores.read(tag).isEmpty() && !BestMechLegs.read(tag).isEmpty(), type + " has valid utility, core and legs");
                if (!type.equals(BestMechProgression.COMBAT))
                    h.assertTrue(!BestMechArms.read(tag).isEmpty(), type + " has a valid engineering arm");
                if (type.equals(BestMechProgression.ENGINEERING))
                    h.assertTrue(BestMechBodies.REINFORCED.equals(BestMechBodies.read(tag)), "engineering has reinforced body");
                h.assertTrue(BestMechItem.energy(ordinary).getEnergyStored() == 0, type + " ordinary item remains uncharged after preset creation");
                p.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5, 0, 0);
                p.setItemInHand(InteractionHand.MAIN_HAND, stack);
                var result = stack.getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false)));
                h.assertTrue(result.consumesAction(), type + " creative player deploys without knowledge");
                var live = h.getLevel().getEntitiesOfClass(BestMechEntity.class, new AABB(floor).inflate(5), e -> !e.isRemoved());
                h.assertTrue(live.size() == 1, type + " deployment creates exactly one mech");
                var mech = live.getFirst();
                try {
                    h.assertTrue(mech.platformType().equals(type) && mech.upgradeMask() == mask
                            && mech.energy() == mech.capacity() && mech.getHealth() == mech.getMaxHealth(), type + " deployed entity retains type, upgrades and full balances");
                    p.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                    h.assertTrue(mech.interact(p, InteractionHand.MAIN_HAND).consumesAction() && p.getVehicle() == mech,
                            type + " creative player mounts without knowledge");
                    mech.setOnGround(true);
                    int before = mech.energy();
                    p.setPlayerInput(0, 1, false, false);
                    mech.tick();
                    h.assertTrue(mech.energy() < before, type + " creative player can drive without knowledge");
                } finally { p.stopRiding(); mech.discard(); }
            }
        } finally { p.stopRiding(); p.getInventory().clearContent(); }
        h.succeed();
    }
}
