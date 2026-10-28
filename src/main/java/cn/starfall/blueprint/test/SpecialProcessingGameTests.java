package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(StarfallBlueprint.ID)
@PrefixGameTestTemplate(false)
public final class SpecialProcessingGameTests {
    private static final BlockPos POS=new BlockPos(1,2,1);
    private static IndustryBlockEntity machine(GameTestHelper h,String kind){h.setBlock(POS,Blocks.AIR);h.setBlock(POS,CatalogueContent.BLOCKS.get(kind).get());return (IndustryBlockEntity)h.getBlockEntity(POS);}
    private static void ticks(IndustryBlockEntity be,int n){for(int i=0;i<n;i++)IndustryProcessing.tick(be);}
    private static int output(IndustryBlockEntity be,Item item){return be.stock.subList(9,13).stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();}

    @GameTest(template="empty")
    public static void cookingUsesDistinctRecipeTypesAndExposesOnlyWorkPorts(GameTestHelper h){
        String[] kinds={"auto_blast_furnace","auto_smoker"};Item[] inputs={Items.RAW_IRON,Items.BEEF},outputs={Items.IRON_INGOT,Items.COOKED_BEEF};
        for(int i=0;i<kinds.length;i++){
            var be=machine(h,kinds[i]);be.stock.set(0,new ItemStack(inputs[i]));be.setPower(2000);ticks(be,40);
            h.assertTrue(be.idle()&&output(be,outputs[i])==1&&be.getPower()==0&&be.experience>0,"Distinct cooking recipe settles with exact energy and XP: "+kinds[i]);
            be.stock.set(0,new ItemStack(Items.SAND));be.setPower(2000);ticks(be,1);
            h.assertTrue(be.idle()&&be.stock.get(0).getCount()==1&&be.getPower()==2000,"Cooking cannot fall back to ordinary furnace glass recipe");
            for(var side:Direction.values())for(int slot:be.workSlots(side))h.assertTrue(slot<13,"Battery, samples and upgrades stay outside automation");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void stonecuttingSampleBackpressureAndUpgradeSnapshotSurviveReload(GameTestHelper h){
        var be=machine(h,"auto_stonecutter");be.stock.set(0,new ItemStack(Items.STONE));be.setItem(19,new ItemStack(Items.STONE_BRICK_STAIRS));
        be.stock.set(16,CatalogueContent.stack("speed_1",4));be.setPower(2000);
        for(int i=9;i<13;i++)be.stock.set(i,new ItemStack(Items.DIAMOND,64));ticks(be,1);
        h.assertTrue(be.idle()&&be.stock.get(0).getCount()==1&&be.getPower()==2000,"Blocked outputs preserve stone and power before reservation");
        for(int i=9;i<13;i++)be.stock.set(i,ItemStack.EMPTY);be.setPower(0);ticks(be,1);
        h.assertTrue(be.idle()&&be.stock.get(0).getCount()==1,"Unpowered stonecutting preserves input");
        be.setPower(2000);ticks(be,1);h.assertTrue(!be.idle()&&be.processTicks==10&&be.processCost==1400,"Four speed modules reserve actual stonecutting budget");
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(saved,h.getLevel().registryAccess());ticks(be,9);
        h.assertTrue(be.idle()&&output(be,Items.STONE_BRICK_STAIRS)==1&&be.stock.get(19).is(Items.STONE_BRICK_STAIRS)&&be.getPower()==600,"Reload produces chosen item once and retains owned sample");h.succeed();
    }
    @GameTest(template="empty")
    public static void smithingUpgradeUsesRealAssemblyAndPreservesEquipmentComponents(GameTestHelper h){
        var be=machine(h,"auto_smith");var sword=new ItemStack(Items.DIAMOND_SWORD);sword.set(DataComponents.CUSTOM_NAME,Component.literal("Preserved blade"));sword.setDamageValue(37);
        be.stock.set(0,new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));be.stock.set(1,sword);be.stock.set(2,new ItemStack(Items.NETHERITE_INGOT));be.setPower(4000);ticks(be,80);
        var result=be.stock.subList(9,13).stream().filter(s->s.is(Items.NETHERITE_SWORD)).findFirst().orElse(ItemStack.EMPTY);
        h.assertTrue(be.idle()&&!result.isEmpty()&&result.getDamageValue()==37&&Component.literal("Preserved blade").equals(result.get(DataComponents.CUSTOM_NAME)),"Real smithing assembly preserves equipment data");
        h.assertTrue(be.stock.subList(0,3).stream().allMatch(ItemStack::isEmpty)&&be.getPower()==0,"One smithing operation consumes template, equipment and addition exactly once");h.succeed();
    }
    @GameTest(template="empty")
    public static void smithingTrimUsesRegistryRecipeAndKeepsTrimComponent(GameTestHelper h){
        var be=machine(h,"auto_smith");be.stock.set(0,new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE));be.stock.set(1,new ItemStack(Items.IRON_CHESTPLATE));be.stock.set(2,new ItemStack(Items.REDSTONE));be.setPower(4000);ticks(be,80);
        h.assertTrue(be.idle()&&be.stock.subList(9,13).stream().anyMatch(s->s.is(Items.IRON_CHESTPLATE)&&s.has(DataComponents.TRIM))&&be.getPower()==0,"Registered trim recipe creates its actual armor component");h.succeed();
    }
    @GameTest(template="empty")
    public static void brewingTransformsThreeBottlesAndReturnsDragonBreathContainer(GameTestHelper h){
        var be=machine(h,"auto_brewer");for(int i=0;i<3;i++)be.stock.set(i,PotionContents.createItemStack(Items.POTION,Potions.WATER));be.stock.set(3,new ItemStack(Items.NETHER_WART));be.stock.set(4,new ItemStack(Items.BLAZE_POWDER));be.setPower(16000);ticks(be,400);
        h.assertTrue(be.idle()&&output(be,Items.POTION)==3&&be.stock.subList(9,13).stream().filter(s->!s.isEmpty()).allMatch(s->s.getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY).is(Potions.AWKWARD)),"Real brewing transforms all three bottles into awkward potion");
        for(int i=9;i<13;i++)be.stock.set(i,ItemStack.EMPTY);for(int i=0;i<3;i++)be.stock.set(i,PotionContents.createItemStack(Items.SPLASH_POTION,Potions.AWKWARD));be.stock.set(3,new ItemStack(Items.DRAGON_BREATH));be.stock.set(4,new ItemStack(Items.BLAZE_POWDER));ticks(be,1);
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(saved,h.getLevel().registryAccess());ticks(be,400);
        h.assertTrue(be.idle()&&output(be,Items.LINGERING_POTION)==3&&output(be,Items.GLASS_BOTTLE)==1&&be.stock.subList(0,5).stream().allMatch(ItemStack::isEmpty)&&be.getPower()==0,"Reload settles three lingering potions plus one dragon breath bottle without duplicating ingredients");h.succeed();
    }
}
