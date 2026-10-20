package cn.starfall.blueprint.test;

import cn.starfall.blueprint.ReinforcedSmithingRecipe;
import cn.starfall.blueprint.StarfallBlueprint;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class RecipeSyncGameTests {
    @GameTest(template = "empty")
    public static void loginRecipePacketRoundTrip(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager().getRecipes();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ClientboundUpdateRecipesPacket.STREAM_CODEC.encode(buffer, new ClientboundUpdateRecipesPacket(recipes));
            var decoded = ClientboundUpdateRecipesPacket.STREAM_CODEC.decode(buffer);
            h.assertTrue(decoded.getRecipes().size() == recipes.size(), "All login recipes must survive network sync");
            h.assertTrue(!buffer.isReadable(), "Recipe packet must be fully consumed");
            var smithing = decoded.getRecipes().stream()
                    .filter(recipe -> recipe.id().equals(ResourceLocation.withDefaultNamespace("netherite_pickaxe_smithing")))
                    .findFirst().orElseThrow().value();
            h.assertTrue(smithing instanceof ReinforcedSmithingRecipe, "Client must receive the reinforced smithing recipe");
            ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
            pickaxe.set(StarfallBlueprint.REINFORCED.get(), true);
            pickaxe.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE, 1873);
            pickaxe.setDamageValue(120);
            var input = new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                    pickaxe, new ItemStack(Items.NETHERITE_INGOT));
            var result = ((ReinforcedSmithingRecipe) smithing).assemble(input, h.getLevel().registryAccess());
            h.assertTrue(result.is(Items.NETHERITE_PICKAXE) && result.getMaxDamage() == 2437
                    && result.getDamageValue() == 157, "Synced recipe must preserve reinforced smithing behavior");
            h.succeed();
        } finally {
            buffer.release();
        }
    }
}
