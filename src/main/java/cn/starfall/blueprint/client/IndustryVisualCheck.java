package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Explicit opt-in asset review; runs in a separate game directory without a world. */
public final class IndustryVisualCheck {
    private static final boolean MATERIALS=Boolean.getBoolean("starfall.materialVisualCheck");
    private static final boolean PICKER=Boolean.getBoolean("starfall.blueprintPickerVisualCheck");
    private static final boolean SAMPLE=Boolean.getBoolean("starfall.autocrafterSampleVisualCheck");
    private static final boolean PREPARATION=Boolean.getBoolean("starfall.unifiedPreparationVisualCheck");
    private static final boolean AGGREGATES=Boolean.getBoolean("starfall.aggregateVisualCheck");
    private static final boolean MOTION=Boolean.getBoolean("starfall.machineMotionVisualCheck");
    private static final boolean RECIPE_PREVIEW=Boolean.getBoolean("starfall.autocrafterRecipePreviewVisualCheck");
    private static IndustryScreen recipeReview;
    private static int phase,ticks;
    public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.getOverlay()!=null)return;
        if(phase==0){
            if(!(mc.screen instanceof TitleScreen))return;
            mc.setScreen(MOTION?new MotionGallery():new Gallery());phase=1;ticks=0;
        }else if(++ticks==30){
            if(RECIPE_PREVIEW&&phase==2){Screenshot.grab(mc.gameDirectory,"autocrafter-recipe-a.png",mc.getMainRenderTarget(),message->{});return;}
            Screenshot.grab(mc.gameDirectory,phase==1?(MOTION?"machine-motion-a.png":AGGREGATES?"aggregate-machines.png":MATERIALS?"material-gallery.png":"industrial-machines.png"):phase==2?(MOTION?"press-interface.png":AGGREGATES?"crusher-interface.png":PREPARATION?"unified-preparation-interface.png":SAMPLE?"autocrafter-sample-interface.png":"cultivator-interface.png"):(PICKER?"blueprint-directory-picker.png":MATERIALS?"creative-blueprint-constructor.png":"blueprint-constructor.png"),mc.getMainRenderTarget(),message->{});
        }else if(MOTION&&phase==1&&ticks==37){
            Screenshot.grab(mc.gameDirectory,"machine-motion-b.png",mc.getMainRenderTarget(),message->{});
        }else if(RECIPE_PREVIEW&&phase==2&&ticks==34){
            reviewStickRecipe(recipeReview,true);
        }else if(RECIPE_PREVIEW&&phase==2&&ticks==40){
            Screenshot.grab(mc.gameDirectory,"autocrafter-recipe-b.png",mc.getMainRenderTarget(),message->{});
        }else if(ticks==50){
            if(phase==1){
                var data=new SimpleContainerData(15);data.set(8,IndustryBlockEntity.KINDS.indexOf("plant_cultivator"));data.set(0,8000);data.set(3,400);data.set(9,1000);
                var container=new SimpleContainer(27);container.setItem(0,new ItemStack(Items.DIRT));container.setItem(1,new ItemStack(Items.WHEAT_SEEDS));container.setItem(2,new ItemStack(Items.WATER_BUCKET));
                if(SAMPLE||RECIPE_PREVIEW){data.set(8,IndustryBlockEntity.KINDS.indexOf("autocrafter"));data.set(12,0);container.clearContent();container.setItem(0,new ItemStack(Items.OAK_PLANKS,64));container.setItem(9,new ItemStack(Items.CHEST,4));container.setItem(IndustryBlockEntity.CRAFTING_SAMPLE,new ItemStack(Items.CHEST));}
                if(PREPARATION){data.set(8,IndustryBlockEntity.KINDS.indexOf("prep"));data.set(12,-1);container.clearContent();container.setItem(0,new ItemStack(Items.WHITE_CONCRETE_POWDER,8));container.setItem(1,new ItemStack(StarfallBlueprint.RAW.get()));container.setItem(9,new ItemStack(Items.WHITE_CONCRETE,8));container.setItem(10,new ItemStack(StarfallBlueprint.CRYSTAL.get(),10));container.setItem(13,new ItemStack(Items.COAL));container.setItem(19,new ItemStack(StarfallBlueprint.CONNECTOR.get()));}
                if(AGGREGATES){data.set(8,IndustryBlockEntity.KINDS.indexOf("crusher"));data.set(12,-1);container.clearContent();container.setItem(0,new ItemStack(Items.BONE));container.setItem(1,new ItemStack(Items.GRAVEL));container.setItem(9,new ItemStack(Items.BONE_MEAL,6));container.setItem(10,new ItemStack(Items.SAND));}
                if(MOTION){data.set(8,IndustryBlockEntity.KINDS.indexOf("press"));data.set(12,-1);container.clearContent();container.setItem(0,new ItemStack(Items.IRON_INGOT,8));container.setItem(1,new ItemStack(Items.COPPER_INGOT,8));container.setItem(9,new ItemStack(StarfallBlueprint.structural("iron_plate")));container.setItem(10,new ItemStack(StarfallBlueprint.structural("copper_plate")));}
                var screen=new IndustryScreen(new IndustryMenu(0,new Inventory(null),container,data),new Inventory(null),Component.literal(MOTION?"电动辊压机":AGGREGATES?"辊式破碎机":PREPARATION?"整备台":SAMPLE||RECIPE_PREVIEW?"自动合成台":"作物植物培养机"));
                if(RECIPE_PREVIEW){container.setItem(IndustryBlockEntity.CRAFTING_SAMPLE,new ItemStack(Items.STICK));container.setItem(9,new ItemStack(Items.STICK,4));recipeReview=screen;reviewStickRecipe(screen,false);}
                mc.setScreen(new ContainerPreview(screen));
                phase=2;ticks=0;
            }else if(phase==2){
                try(var stream=IndustryVisualCheck.class.getResourceAsStream("/assets/starfall_blueprint/construction_blueprints/L25.sfbp.json")){
                    var plan=ConstructionPlan.parse(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                    var inventory=new Inventory(null);var container=new SimpleContainer(28);var paper=new ItemStack(Blueprints.PLAN.get());Blueprints.write(paper,plan);container.setItem(0,paper);
                    int slot=1;for(var entry:plan.materials().entrySet()){int amount=entry.getValue();while(amount>0){int n=Math.min(64,amount);container.setItem(slot++,new ItemStack(entry.getKey(),n));amount-=n;}}
                    if(MATERIALS){for(int i=1;i<28;i++)container.setItem(i,ItemStack.EMPTY);container.setItem(1,new ItemStack(Blueprints.CREATIVE_CORE.get()));}
                    var data=new SimpleContainerData(8);data.set(7,net.minecraft.core.Direction.NORTH.get2DDataValue());
                    mc.setScreen(new ContainerPreview(new BlueprintScreen(new BlueprintMenu(0,inventory,container,data),inventory,Component.literal("蓝图施工器"))));phase=3;ticks=0;
                }catch(Exception error){throw new IllegalStateException("Blueprint visual fixture failed",error);}
            }else{phase=4;mc.stop();}
        }
    }
    private static void reviewStickRecipe(IndustryScreen screen,boolean bamboo){
        var result=new ItemStack(Items.STICK,bamboo?1:4);
        var pattern=net.minecraft.world.item.crafting.ShapedRecipePattern.of(java.util.Map.of('P',net.minecraft.world.item.crafting.Ingredient.of(bamboo?Items.BAMBOO:Items.OAK_PLANKS)),"P","P");
        var recipe=new net.minecraft.world.item.crafting.ShapedRecipe("",net.minecraft.world.item.crafting.CraftingBookCategory.MISC,pattern,result);
        screen.reviewRecipe(new net.minecraft.world.item.crafting.RecipeHolder<>(net.minecraft.resources.ResourceLocation.withDefaultNamespace(bamboo?"stick_from_bamboo_item":"stick"),recipe),result,bamboo?"2 / 2":"1 / 2",2);
    }
    /** Opt-in QA only: render active baked block models, using the real shared texture atlas. */
    private static final class MotionGallery extends Screen {
        MotionGallery(){super(Component.literal("轻量动画验收 · 共享图集／10帧每秒"));}
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){}
        @Override public void render(GuiGraphics g,int mx,int my,float delta){
            g.fill(0,0,width,height,0xffe5e7dc);g.fill(0,0,width,24,0xff293f43);g.drawString(font,title,12,8,0xfff3efd9,false);
            String[] kinds={"quarry","crusher","g02","press"};int cellW=width/2,cellH=(height-30)/2;
            for(int i=0;i<kinds.length;i++){
                int x=i%2*cellW,y=30+i/2*cellH;String kind=kinds[i];g.fill(x+3,y,x+cellW-3,y+cellH-4,0xfff5f4e9);g.flush();
                var state=CatalogueContent.BLOCKS.get(kind).get().defaultBlockState().setValue(IndustryBlock.ACTIVE,true);boolean large=AggregateMachines.large(kind);float size=large?2:1,scale=Math.min(cellW/(size*1.7f),(cellH-30)/1.7f);
                com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
                g.pose().pushPose();g.pose().translate(x+cellW/2f,y+cellH*.54f,150);g.pose().scale(scale,-scale,scale);g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(25));g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(-45));g.pose().translate(-size/2,-.5f,-size/2);
                for(int part=0;part<(large?4:1);part++){
                    g.pose().pushPose();if(large){var offset=LargeMachineBlock.offset(net.minecraft.core.Direction.NORTH,part);g.pose().translate(offset.getX(),0,offset.getZ());}
                    minecraft.getBlockRenderer().renderSingleBlock(large?state.setValue(LargeMachineBlock.PART,part):state,g.pose(),g.bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);g.pose().popPose();
                }
                g.flush();g.pose().popPose();com.mojang.blaze3d.platform.Lighting.setupForFlatItems();com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
                g.drawString(font,CatalogueContent.stack(kind,1).getHoverName(),x+8,y+cellH-18,0xff344d48,false);
            }
        }
    }
    private static final class ContainerPreview extends Screen {
        private final Screen preview;
        ContainerPreview(Screen preview){super(Component.literal("机器界面预览"));this.preview=preview;}
        @Override protected void init(){preview.init(minecraft,width,height);if(PICKER&&preview instanceof BlueprintScreen screen)verifyPicker(screen);}
        private void verifyPicker(BlueprintScreen screen){
            screen.scanDirectory();String first=firstDesign(screen).getMessage().getString();
            pickerButton(screen,"下一页").onPress();
            if(firstDesign(screen).getMessage().getString().equals(first))throw new IllegalStateException("Picker next page did not change designs");
            pickerButton(screen,"上一页").onPress();
            if(!firstDesign(screen).getMessage().getString().equals(first))throw new IllegalStateException("Picker previous page did not restore designs");
            var paper=screen.getMenu().container.getItem(0);screen.getMenu().container.setItem(0,ItemStack.EMPTY);
            firstDesign(screen).onPress();
            var filename=screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.EditBox).map(c->(net.minecraft.client.gui.components.EditBox)c).findFirst().orElseThrow();
            if(!filename.visible||!filename.getValue().equals("L01.sfbp.json"))throw new IllegalStateException("Picker selection did not populate filename and return to materials");
            screen.getMenu().container.setItem(0,paper);screen.scanDirectory();
            if(!screen.keyPressed(256,0,0)||!filename.visible)throw new IllegalStateException("Escape should close the picker without closing the constructor");
            screen.scanDirectory();org.slf4j.LoggerFactory.getLogger(IndustryVisualCheck.class).info("Blueprint picker client checks passed: scan, paging, selection without paper, Escape, rescan");
        }
        private net.minecraft.client.gui.components.Button pickerButton(BlueprintScreen screen,String label){return screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.visible&&b.getMessage().getString().equals(label)).map(c->(net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow();}
        private net.minecraft.client.gui.components.Button firstDesign(BlueprintScreen screen){return screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.visible&&b.active&&b.getMessage().getString().contains("方块")).map(c->(net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow();}
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){}
        @Override public void render(GuiGraphics g,int mx,int my,float delta){preview.render(g,mx,my,delta);}
    }
    private static final class Gallery extends Screen {
        private final List<String> keys=AGGREGATES?List.of("quarry","crusher"):MATERIALS?List.of("raw_meteor_phase","stable_crystal","refined","wet_tailings","rutile","titan","borate","boron",
                "rutile_tail","borate_tail","refine_tail","reclaim_copper","reclaim_crystal","residue","iron_plate","copper_plate",
                "mixed_steel_billet","ordinary_steel","ceramic","insulation","binder","isolation_substrate","control_board","circuit_breaker",
                "ceramic_sheath","iron_fasteners","steel_fasteners","sealed_box","coated_box","machine_frame","conductor","load",
                "laminar","coating","sensor","directed","wear","efficient_connector","tool_brace","shield_lining","creative_construction_core"):
                List.of("blueprint_builder","construction_plan","blueprint_surveyor","plant_cultivator","refiner","recycler","bio_processor","autocrafter","auto_furnace",
                "g01","g02","g03","g04","g05","g06","g07","s02","pump","dew","supply_crate","terminal",
                "cable","pipe","item_duct","fluid_valve","item_extractor","item_router");
        Gallery(){super(Component.literal(AGGREGATES?"星落蓝图 · 2×2大型采石与破碎设备":MATERIALS?"星落蓝图 · 矿物材料与创造施工核心":"星落蓝图 · 机器与管线"));}
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){}
        @Override public void render(GuiGraphics g,int mx,int my,float delta){
            g.fill(0,0,width,height,0xffe5e7dc);g.fill(0,0,width,30,0xff293f43);g.drawString(font,title,16,10,0xfff2efd9,false);
            int columns=AGGREGATES?2:MATERIALS?8:7;int cellW=width/columns,cellH=(height-36)/(AGGREGATES?1:MATERIALS?6:4);
            for(int i=0;i<keys.size();i++){
                int x=i%columns*cellW,y=36+i/columns*cellH;var stack=MATERIALS?new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(StarfallBlueprint.id(keys.get(i)))):switch(keys.get(i)){case "blueprint_builder"->new ItemStack(Blueprints.BUILDER_ITEM.get());case "construction_plan"->new ItemStack(Blueprints.PLAN.get());case "blueprint_surveyor"->new ItemStack(Blueprints.SURVEYOR.get());default->CatalogueContent.stack(keys.get(i),1);};
                g.fill(x+3,y,x+cellW-3,y+cellH-4,0xfff5f4e9);
                float scale=Math.min(AGGREGATES?12f:4f,(cellH-25)/16f);
                g.pose().pushPose();g.pose().translate(x+cellW/2f-scale*8,y+4,0);g.pose().scale(scale,scale,1);g.renderItem(stack,0,0);g.pose().popPose();
                String label=font.plainSubstrByWidth(stack.getHoverName().getString(),cellW-10);g.drawString(font,label,x+6,y+cellH-18,0xff344d48,false);
            }
        }
    }
}
