package cn.starfall.blueprint.test;

import cn.starfall.blueprint.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("starfall_blueprint_lingchuan")
@PrefixGameTestTemplate(false)
public final class LingchuanGameTests {
    private static FakePlayer player(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"BlackDogTest"));
        p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getPersistentData().putInt("LingchuanResearch",0);p.getInventory().selected=0;
        return p;
    }
    private static void input(FakePlayer p,int slot,String key,int count){p.getInventory().setItem(slot,CatalogueContent.stack(key,count));}
    private static IndustryBlockEntity station(GameTestHelper h,String kind){
        var root=h.absolutePos(new BlockPos(1,1,1));var block=CatalogueContent.BLOCKS.get(kind).get();
        var state=block.defaultBlockState().setValue(IndustryBlock.FACING,Direction.NORTH);
        h.getLevel().setBlock(root,state,3);block.setPlacedBy(h.getLevel(),root,state,null,ItemStack.EMPTY);
        var be=(IndustryBlockEntity)h.getLevel().getBlockEntity(root);be.setPower(2_000_000);return be;
    }
    private static List<ItemStack> parts(int tier){
        var modules=new ArrayList<ItemStack>();for(int i=0;i<8;i++)modules.add(CatalogueContent.stack(Lingchuan.baseKey(i,tier),1));return modules;
    }
    private static ItemStack watch(FakePlayer p,List<ItemStack> parts){var s=BlackDogArmor.build(parts);BlackDogArmor.setCharge(s,BlackDogArmor.capacity(s));p.getInventory().setItem(1,s);return s;}
    private static ItemStack watch(FakePlayer p,List<ItemStack> parts,String... abilities){var s=BlackDogArmor.build(parts,Arrays.stream(abilities).map(a->CatalogueContent.stack("blackdog_component_"+a,1)).toList());BlackDogArmor.setCharge(s,BlackDogArmor.capacity(s));p.getInventory().setItem(1,s);return s;}
    @GameTest(template="empty",timeoutTicks=100)
    public static void researchIsPersonalAtomicAndPaysExactlyOneRoute(GameTestHelper h){
        var p=player(h);var be=station(h,"lc_research_station");
        h.assertTrue(!Lingchuan.research(p,be,1,false)&&be.getPower()==2_000_000,"Prerequisite rejection must not consume electricity");
        h.assertTrue(Lingchuan.research(p,be,0,false)&&be.getPower()==1_990_000,"Energy route unlocks exactly one node");
        h.assertTrue(!Lingchuan.research(p,be,0,false)&&be.getPower()==1_990_000,"Repeated research is free of side effects");
        input(p,0,"laminar_alloy",2);input(p,2,"common",4);
        h.assertTrue(Lingchuan.research(p,be,1,true)&&be.getPower()==1_990_000&&p.getInventory().getItem(0).isEmpty()&&p.getInventory().getItem(2).isEmpty(),"Material route consumes exact supplies and no power");
        h.assertTrue(!Lingchuan.knows(player(h),0),"Research is personal");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void materialResearchHasNoBootstrapCycle(GameTestHelper h){
        var p=player(h);var be=station(h,"lc_research_station");be.setPower(0);input(p,0,"common",4);
        h.assertTrue(Lingchuan.research(p,be,0,true)&&Lingchuan.knows(p,0),"Metallurgy can start with public crystal only, without laminar products");
        h.assertTrue(!Lingchuan.research(p,be,3,true),"Micro reactors cannot skip compressed cores");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void alloyForgeRequiresRealNetheriteAndReservesOutput(GameTestHelper h){
        var p=player(h);var be=station(h,"lc_alloy_forge");p.getPersistentData().putInt("LingchuanResearch",1);
        input(p,0,"laminar_crystal",2);input(p,2,"steel",4);
        h.assertTrue(!Lingchuan.forge(p,be,1)&&be.getPower()==2_000_000&&p.getInventory().getItem(0).getCount()==2,"Missing netherite must not reserve other inputs");
        input(p,3,"minecraft:netherite_ingot",1);
        h.assertTrue(Lingchuan.forge(p,be,1)&&be.getItem(9).is(CatalogueContent.item("laminar_alloy"))&&be.getItem(9).getCount()==4&&be.getPower()==1_960_000,"Dedicated forge creates four alloy from actual netherite");
        be.setItem(9,new ItemStack(Items.DIRT));input(p,0,"laminar_crystal",2);input(p,2,"steel",4);input(p,3,"minecraft:netherite_ingot",1);
        h.assertTrue(!Lingchuan.forge(p,be,1)&&p.getInventory().getItem(3).getCount()==1,"Full output rejects before consumption");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void moduleManufactureChecksKnowledgeAndOutput(GameTestHelper h){
        var p=player(h);var be=station(h,"lc_parts_fabricator");input(p,0,"laminar_alloy",4);input(p,2,"laminar_crystal",2);input(p,3,"board",2);
        h.assertTrue(!Lingchuan.manufacture(p,be,0)&&p.getInventory().getItem(0).getCount()==4,"An untrained operator cannot manufacture modules");
        p.getPersistentData().putInt("LingchuanResearch",3);
        h.assertTrue(Lingchuan.manufacture(p,be,0)&&be.getItem(9).is(CatalogueContent.item("blackdog_head_basic")),"Basic head fabrication succeeds");
        h.assertTrue(!Lingchuan.manufacture(p,be,1)&&p.getInventory().getItem(0).getCount()==2,"Occupied output preserves subsequent materials");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void eightBayAssemblyPersistsOwnedModules(GameTestHelper h){
        var p=player(h);p.getPersistentData().putInt("LingchuanResearch",65535);var be=station(h,"lc_armor_assembler");
        for(int i=0;i<7;i++)be.setItem(i,parts(1).get(i));
        h.assertTrue(!Lingchuan.assemble(p,be)&&!be.getItem(0).isEmpty(),"Missing eighth part preserves seven inputs");
        be.setItem(7,parts(1).get(7));h.assertTrue(Lingchuan.assemble(p,be),"Complete base armour assembles");var watch=be.getItem(9);
        h.assertTrue(BlackDogArmor.valid(watch)&&BlackDogArmor.charge(watch)==0&&BlackDogArmor.capacity(watch)==100000,"New watch is complete but never creates free stored charge");
        var restored=ItemStack.parse(h.getLevel().registryAccess(),watch.save(h.getLevel().registryAccess())).orElseThrow();
        h.assertTrue(BlackDogArmor.valid(restored)&&BlackDogArmor.modules(restored).size()==8,"Save/load retains real modules and suit identity");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void refitReturnsOldPartsAndRejectsOverloadWithoutMutation(GameTestHelper h){
        var p=player(h);p.getPersistentData().putInt("LingchuanResearch",65535);var be=station(h,"lc_armor_assembler");var watch=BlackDogArmor.build(parts(1));be.setItem(8,watch);be.setItem(10,CatalogueContent.stack("blackdog_component_flight",1));
        h.assertTrue(!Lingchuan.refit(p,be)&&be.getItem(10).is(CatalogueContent.item("blackdog_component_flight")),"Tier I back cannot accept a flight component; proposed component remains");
        be.setItem(2,CatalogueContent.stack("blackdog_core_compressed",1));be.setItem(6,CatalogueContent.stack("blackdog_back_reinforced",1));BlackDogArmor.setCharge(watch,50000);
        h.assertTrue(Lingchuan.refit(p,be)&&be.getItem(6).is(CatalogueContent.item("blackdog_back_basic"))&&be.getItem(2).is(CatalogueContent.item("blackdog_core_basic")),"Core and chassis swap atomically; both old bases return");
        h.assertTrue(BlackDogArmor.charge(watch)==50000&&BlackDogArmor.has(watch,"flight")&&be.getItem(10).isEmpty(),"Refit preserves charge and stores the actual separate component");
        be.setItem(6,ItemStack.EMPTY);be.setItem(2,ItemStack.EMPTY);long power=be.getPower();
        h.assertTrue(!Lingchuan.refit(p,be)&&power==be.getPower(),"Empty repeat swap creates no parts or charges");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void deploymentLocksIndividualSlotsAndRecallsWithFullInventory(GameTestHelper h){
        var p=player(h);var watch=watch(p,parts(1));p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.LEATHER_HELMET));
        h.assertTrue(!BlackDogArmor.toggle(p,watch)&&!BlackDogArmor.deployed(watch),"Occupied armour slots prevent deployment without losing existing equipment");p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        h.assertTrue(BlackDogArmor.toggle(p,watch)&&BlackDogArmor.complete(p,watch),"Watch deploys four linked armour projections");
        for(var slot:p.inventoryMenu.slots)if(slot.getItem().getItem() instanceof BlackDogArmor.Projection)h.assertTrue(!slot.mayPickup(p),"Individual armour slots must refuse extraction");
        h.assertTrue(!watch.getItem().onDroppedByPlayer(watch,p),"Deployed watch refuses the hotbar drop action");
        for(int i=0;i<36;i++)if(i!=1)p.getInventory().setItem(i,new ItemStack(Items.DIRT,64));
        h.assertTrue(BlackDogArmor.toggle(p,watch)&&!BlackDogArmor.deployed(watch)&&p.getItemBySlot(EquipmentSlot.CHEST).isEmpty()&&BlackDogArmor.valid(watch),"Full inventory cannot obstruct recall or duplicate stored modules");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void poweredBranchesCleanUpWhenRecalled(GameTestHelper h){
        var p=player(h);var watch=watch(p,parts(2),"night","sprint");BlackDogArmor.toggle(p,watch);p.setSprinting(true);p.tickCount=20;
        BlackDogArmor.tickPlayer(p);
        h.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)&&p.getAttributeValue(Attributes.ARMOR)>=14&&BlackDogArmor.charge(watch)<400000,"Installed branches consume real power and apply defense");
        BlackDogArmor.recall(p,watch);
        h.assertTrue(!p.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)&&p.getAttributeValue(Attributes.ARMOR)==0,"Recall removes module effects and armour bonuses immediately");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void damageConsumesFiniteEnergyAndLeavesPassiveArmour(GameTestHelper h){
        var p=player(h);var watch=watch(p,parts(2),"shield");BlackDogArmor.toggle(p,watch);BlackDogArmor.setCharge(watch,1000);BlackDogArmor.tickPlayer(p);
        // FakePlayer deliberately ignores damage; dispatch the engine damage event through the real bus.
        var event=new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(p,new net.neoforged.neoforge.common.damagesource.DamageContainer(p.damageSources().generic(),10));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        h.assertTrue(BlackDogArmor.charge(watch)==0&&event.getAmount()==8,"An undercharged shield cannot erase more damage than its energy budget");
        BlackDogArmor.tickPlayer(p);h.assertTrue(p.getAttributeValue(Attributes.ARMOR)>=14,"Passive armour remains after depletion");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void everyLargeMachinePartExposesSharedPowerAndSingleDrop(GameTestHelper h){
        var be=station(h,"lc_armor_charger");be.setPower(5000);var watch=BlackDogArmor.build(parts(1));be.setItem(8,watch);
        var side=be.getBlockPos().offset(LargeMachineBlock.offset(Direction.NORTH,7));var port=h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,side,Direction.UP);
        h.assertTrue(port!=null&&port.getEnergyStored()==5000,"Remote shell exposes the same shared energy store");
        Lingchuan.tickStation(be);h.assertTrue(BlackDogArmor.charge(watch)==4096&&be.getPower()==904,"Charging transfers, rather than creates, energy");
        var state=h.getLevel().getBlockState(side);var drops=net.minecraft.world.level.block.Block.getDrops(state,h.getLevel(),side,h.getLevel().getBlockEntity(side));
        h.assertTrue(drops.size()==1&&drops.getFirst().is(CatalogueContent.item("lc_armor_charger")),"Breaking any shell produces one saved controller, not eight machines");
        h.getLevel().removeBlock(side,false);h.assertTrue(h.getLevel().getBlockState(be.getBlockPos()).isAir(),"Breaking a shell dismantles its controller");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void fireControlRequiresInstalledWeaponsAndSpendsEnergy(GameTestHelper h){
        var p=player(h);var watch=watch(p,parts(2),"missile");BlackDogArmor.toggle(p,watch);
        var terminal=CatalogueContent.stack("blackdog_fire_control",1);p.setItemInHand(InteractionHand.MAIN_HAND,terminal);p.setPos(h.absolutePos(new BlockPos(2,2,2)).getCenter());
        h.assertTrue(terminal.getItem() instanceof BlackDogArmor.FireControl,"Fire control must be the real weapon item, not a passive module");
        terminal.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
        h.assertTrue(BlackDogArmor.charge(watch)==395000&&p.getCooldowns().isOnCooldown(terminal.getItem()),"Firing deducts exactly one missile charge and starts cooldown");
        h.assertTrue(!h.getLevel().getEntitiesOfClass(LargeFireball.class,p.getBoundingBox().inflate(5)).isEmpty(),"Missile launch creates an actual server projectile");
        terminal.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);h.assertTrue(BlackDogArmor.charge(watch)==395000,"Cooldown prevents duplicate firing and duplicate energy charges");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void cannonStopsAtBedrockAndCannotHitThroughProtectedBlocks(GameTestHelper h){
        var p=player(h);var watch=watch(p,parts(3),"cannon");BlackDogArmor.toggle(p,watch);
        p.setPos(h.absolutePos(new BlockPos(2,1,3)).getCenter());p.setYRot(180);p.setXRot(0);
        var direction=p.getLookAngle();var barrier=BlockPos.containing(p.getEyePosition().add(direction.scale(2)));
        h.getLevel().setBlock(barrier,Blocks.BEDROCK.defaultBlockState(),3);
        var target=new net.minecraft.world.entity.monster.Zombie(h.getLevel());target.setPos(p.position().add(direction.scale(4)));h.getLevel().addFreshEntity(target);float before=target.getHealth();
        var terminal=CatalogueContent.stack("blackdog_fire_control",1);p.setItemInHand(InteractionHand.MAIN_HAND,terminal);terminal.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
        h.assertTrue(h.getLevel().getBlockState(barrier).is(Blocks.BEDROCK)&&target.getHealth()==before,"Protected bedrock stops mining and the damage ray, including targets behind it");
        h.assertTrue(BlackDogArmor.charge(watch)==992000,"Blocked cannon spends its shot cost but no successful block-break cost");target.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void coreRegenerationAndDowngradeNeverCreateFreeCharge(GameTestHelper h){
        var p=player(h);var modules=parts(3);var watch=watch(p,modules);BlackDogArmor.setCharge(watch,0);p.tickCount=20;
        watch.getItem().inventoryTick(watch,h.getLevel(),p,1,false);h.assertTrue(BlackDogArmor.charge(watch)==800,"Tier III carried reactor restores its measured per-second budget");
        BlackDogArmor.setCharge(watch,900000);var be=station(h,"lc_armor_assembler");p.getPersistentData().putInt("LingchuanResearch",65535);be.setItem(8,watch);for(int i=0;i<8;i++)be.setItem(i,parts(1).get(i));
        h.assertTrue(Lingchuan.refit(p,be)&&BlackDogArmor.charge(watch)==100000&&be.getItem(2).is(CatalogueContent.item("blackdog_core_reactor")),"Downgrade returns the reactor but clamps stored energy to the real new capacity");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void nightVisionAndBreathingCoexistOnTheSameBasicHead(GameTestHelper h){
        var p=player(h);var watch=watch(p,parts(1),"night","breathing");BlackDogArmor.toggle(p,watch);p.tickCount=20;BlackDogArmor.tickPlayer(p);
        h.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)&&p.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING),"Independent night vision and breathing operate simultaneously on one head chassis");
        h.assertTrue(BlackDogArmor.charge(watch)==99800&&BlackDogArmor.modules(watch).get(0).is(CatalogueContent.item("blackdog_head_basic")),"Both functions consume their own power without replacing the real base helmet");
        BlackDogArmor.recall(p,watch);h.assertTrue(!p.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)&&!p.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING),"Recall clears both active components");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void componentInstallationAndRemovalPreserveOtherFunctions(GameTestHelper h){
        var p=player(h);p.getPersistentData().putInt("LingchuanResearch",65535);var be=station(h,"lc_armor_assembler");var watch=BlackDogArmor.build(parts(1));BlackDogArmor.setCharge(watch,12345);be.setItem(8,watch);
        be.setItem(10,CatalogueContent.stack("blackdog_component_night",1));be.setItem(11,CatalogueContent.stack("blackdog_component_breathing",1));
        h.assertTrue(Lingchuan.refit(p,be)&&BlackDogArmor.has(watch,"night")&&BlackDogArmor.has(watch,"breathing"),"One transaction installs both head functions");
        h.assertTrue(be.getItem(10).isEmpty()&&be.getItem(11).isEmpty()&&BlackDogArmor.components(watch).size()==2,"Actual components leave input bays exactly once");
        h.assertTrue(Lingchuan.removeComponent(p,be,0)&&be.getItem(10).is(CatalogueContent.item("blackdog_component_night")),"Removing night vision returns its real item to a free component bay");
        h.assertTrue(!BlackDogArmor.has(watch,"night")&&BlackDogArmor.has(watch,"breathing")&&BlackDogArmor.charge(watch)==12345,"Removing one component preserves breathing and stored energy");
        long power=be.getPower();h.assertTrue(!Lingchuan.removeComponent(p,be,0)&&be.getPower()==power,"Repeated removal cannot duplicate a component or charge again");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void componentTierUsesItsOwnChassisNotOnlyTheEnergyCore(GameTestHelper h){
        var bases=parts(1);bases.set(2,CatalogueContent.stack("blackdog_core_reactor",1));var flight=CatalogueContent.stack("blackdog_component_flight",1);
        h.assertTrue(!BlackDogArmor.compatible(bases,List.of(flight)),"A tier III core cannot bypass a tier I back chassis interface");
        bases.set(6,CatalogueContent.stack("blackdog_back_reinforced",1));h.assertTrue(BlackDogArmor.compatible(bases,List.of(flight)),"Upgrading the local back chassis opens the flight component");
        var cloak=CatalogueContent.stack("blackdog_component_cloak",1);h.assertTrue(!BlackDogArmor.compatible(bases,List.of(flight,cloak)),"Tier II back cannot accept tier III cloak or six interfaces");
        bases.set(6,CatalogueContent.stack("blackdog_back_elite",1));h.assertTrue(BlackDogArmor.compatible(bases,List.of(flight,cloak)),"Tier III back supports flight and cloak simultaneously");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void duplicateComponentsAndInvalidDowngradesAreAtomic(GameTestHelper h){
        var p=player(h);p.getPersistentData().putInt("LingchuanResearch",65535);var be=station(h,"lc_armor_assembler");var watch=watch(p,parts(3),"night","breathing","cannon");be.setItem(8,watch);var before=watch.copy();long power=be.getPower();
        be.setItem(10,CatalogueContent.stack("blackdog_component_night",1));h.assertTrue(!Lingchuan.refit(p,be)&&ItemStack.matches(before,watch)&&be.getPower()==power&&!be.getItem(10).isEmpty(),"Duplicate function is rejected before inputs or stored configuration mutate");
        be.setItem(10,ItemStack.EMPTY);be.setItem(5,CatalogueContent.stack("blackdog_hands_basic",1));
        h.assertTrue(!Lingchuan.refit(p,be)&&ItemStack.matches(before,watch)&&be.getPower()==power,"Downgrading hands cannot silently discard a cannon");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void legacyIntegratedPartsMigrateOnceWithoutLosingSuitIdentity(GameTestHelper h){
        var old=NonNullList.withSize(8,ItemStack.EMPTY);for(int i=0;i<8;i++)old.set(i,parts(1).get(i));old.set(2,CatalogueContent.stack("blackdog_core_compressed",1));old.set(0,CatalogueContent.stack("blackdog_head_night",1));old.set(6,CatalogueContent.stack("blackdog_back_flight",1));
        var watch=CatalogueContent.stack("blackdog_watch",1);var data=new net.minecraft.nbt.CompoundTag();var saved=new net.minecraft.nbt.CompoundTag();net.minecraft.world.ContainerHelper.saveAllItems(saved,old,h.getLevel().registryAccess());data.put("Modules",saved);var identity=UUID.randomUUID();data.putUUID("SuitId",identity);data.putInt("CoreTier",2);data.putInt("Energy",32109);CatalogueEquipment.setData(watch,data);
        h.assertTrue(BlackDogArmor.valid(watch)&&BlackDogArmor.components(watch).size()==2&&BlackDogArmor.has(watch,"night")&&BlackDogArmor.has(watch,"flight"),"An old integrated suit becomes chassis plus two real detachable function components");
        h.assertTrue(BlackDogArmor.modules(watch).get(0).is(CatalogueContent.item("blackdog_head_basic"))&&BlackDogArmor.modules(watch).get(6).is(CatalogueContent.item("blackdog_back_reinforced")),"Legacy parts select equivalent chassis tiers");
        h.assertTrue(BlackDogArmor.charge(watch)==32109&&CatalogueEquipment.getData(watch).getUUID("SuitId").equals(identity),"Migration preserves charge and suit identity");
        var snapshot=watch.copy();h.assertTrue(BlackDogArmor.valid(watch)&&ItemStack.matches(snapshot,watch),"Repeated validation cannot migrate or duplicate components again");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void personalKnowledgeSurvivesDeathAndOrphanProjectionIsRemoved(GameTestHelper h){
        var p=player(h);p.getPersistentData().putInt("LingchuanResearch",65535);var watch=watch(p,parts(1));BlackDogArmor.toggle(p,watch);var clone=player(h);
        BlackDogArmor.clonePlayer(new PlayerEvent.Clone(clone,p,true));h.assertTrue(Lingchuan.knowledge(clone)==65535,"Death cloning retains personal knowledge");
        p.getInventory().setItem(1,ItemStack.EMPTY);BlackDogArmor.tickPlayer(p);
        h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty()&&p.getItemBySlot(EquipmentSlot.FEET).isEmpty(),"Lost or creative-overwritten watch cannot leave reusable orphan projections");h.succeed();
    }
}
