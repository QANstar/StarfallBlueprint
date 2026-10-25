package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.*;

public final class Blueprints {
    public static final DeferredBlock<BlueprintBuilderBlock> BUILDER=StarfallBlueprint.BLOCKS.register("blueprint_builder",()->new BlueprintBuilderBlock(BlockBehaviour.Properties.of().strength(3).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredItem<BlockItem> BUILDER_ITEM=StarfallBlueprint.ITEMS.registerSimpleBlockItem(BUILDER);
    public static final DeferredItem<Item> PLAN=StarfallBlueprint.ITEMS.registerItem("construction_plan",p->new Item(p.stacksTo(1)){
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags){
            var plan=read(stack);lines.add(Component.literal(plan==null?"空白设计图 · 在施工器导入文件，或用测绘笔记录":plan.title()+" · "+plan.blocks().size()+"个方块"));
        }
    });
    public static final DeferredItem<Item> SURVEYOR=StarfallBlueprint.ITEMS.registerItem("blueprint_surveyor",BlueprintSurveyorItem::new);
    public static final DeferredItem<Item> CREATIVE_CORE=StarfallBlueprint.ITEMS.registerItem("creative_construction_core",p->new Item(p.stacksTo(1)){
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags){
            lines.add(Component.literal("仅创造物品栏获取 · 放入施工材料箱后免材料建造"));
            lines.add(Component.literal("核心不消耗；仍需设计图、空地与合法施工权限"));
        }
    });
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<BlueprintBuilderEntity>> ENTITY=StarfallBlueprint.ENTITIES.register("blueprint_builder",()->BlockEntityType.Builder.of(BlueprintBuilderEntity::new,BUILDER.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<BlueprintMenu>> MENU=StarfallBlueprint.MENUS.register("blueprint_builder",()->new MenuType<>(BlueprintMenu::new,net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static void initialize(){}
    public static String text(ItemStack stack){return CatalogueEquipment.getData(stack).getString("ConstructionPlan");}
    public static ConstructionPlan read(ItemStack stack){
        if(!stack.is(PLAN.get()))return null;
        try{return ConstructionPlan.parse(text(stack));}catch(RuntimeException e){return null;}
    }
    public static void write(ItemStack stack,ConstructionPlan plan){
        String json=plan.json();if(json.length()>ConstructionPlan.MAX_TEXT)throw new IllegalArgumentException("蓝图内容超过可分享大小，请缩小选区");
        CompoundTag data=CatalogueEquipment.getData(stack);data.putString("ConstructionPlan",json);CatalogueEquipment.setData(stack,data);
        stack.set(DataComponents.CUSTOM_NAME,Component.literal(plan.title()));
    }
}
