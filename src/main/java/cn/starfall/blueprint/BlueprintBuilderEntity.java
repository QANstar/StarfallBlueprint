package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/** Synchronous transaction: validate the entire site, place, then debit exact materials. */
public final class BlueprintBuilderEntity extends BlockEntity implements WorldlyContainer,MenuProvider {
    private NonNullList<ItemStack> items=NonNullList.withSize(28,ItemStack.EMPTY);
    public int turns,status;
    private String cachedText="";private ConstructionPlan cachedPlan;
    private BlockPos cachedOrigin;private ConstructionPlan originPlan;private int originTurns=-1;private Direction originFacing;
    public final ContainerData data=new ContainerData(){
        public int get(int i){return switch(i){case 0->turns;case 1->status;case 2->worldPosition.getX()&65535;case 3->worldPosition.getX()>>16;case 4->worldPosition.getY();case 5->worldPosition.getZ()&65535;case 6->worldPosition.getZ()>>16;case 7->getBlockState().getValue(BlueprintBuilderBlock.FACING).get2DDataValue();default->0;};}
        public void set(int i,int value){}public int getCount(){return 8;}
    };
    public BlueprintBuilderEntity(BlockPos pos,BlockState state){super(Blueprints.ENTITY.get(),pos,state);}
    public ConstructionPlan plan(){String text=Blueprints.text(getItem(0));if(!text.equals(cachedText)){cachedText=text;cachedPlan=Blueprints.read(getItem(0));}return cachedPlan;}
    public static Rotation rotation(Direction facing,int turns){return Rotation.values()[Math.floorMod((facing==Direction.EAST?1:facing==Direction.SOUTH?2:facing==Direction.WEST?3:0)+turns,4)];}
    public Rotation rotation(){return rotation(getBlockState().getValue(BlueprintBuilderBlock.FACING),turns);}
    public static BlockPos origin(BlockPos pos,Direction facing,int turns,ConstructionPlan plan){
        int minX=0,maxZ=0;Rotation local=Rotation.values()[Math.floorMod(turns,4)];
        if(plan!=null)for(var cell:plan.blocks()){BlockPos rotated=cell.pos().rotate(local);minX=Math.min(minX,rotated.getX());maxZ=Math.max(maxZ,rotated.getZ());}
        return pos.relative(facing,2).offset(new BlockPos(-minX,0,-maxZ).rotate(rotation(facing,0)));
    }
    public BlockPos origin(){
        var plan=plan();Direction facing=getBlockState().getValue(BlueprintBuilderBlock.FACING);
        if(cachedOrigin==null||originPlan!=plan||originTurns!=turns||originFacing!=facing){originPlan=plan;originTurns=turns;originFacing=facing;cachedOrigin=origin(worldPosition,facing,turns,plan);}
        return cachedOrigin;
    }
    public BlockPos target(ConstructionPlan.Cell cell){return origin().offset(cell.pos().rotate(rotation()));}
    public Map<Item,Integer> required(){
        Map<Item,Integer> result=new LinkedHashMap<>();var plan=plan();if(plan==null)return result;
        for(var cell:plan.blocks())if(LargeMachineBlock.consumesMaterial(cell.state())&&(level==null||!level.hasChunkAt(target(cell))||!plan.matches(cell,level.getBlockState(target(cell)),rotation())))result.merge(cell.state().getBlock().asItem(),1,Integer::sum);
        return result;
    }
    public static boolean usable(ItemStack stack){return !stack.has(DataComponents.BLOCK_ENTITY_DATA)
            &&stack.getOrDefault(DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.EMPTY).stream().allMatch(ItemStack::isEmpty)
            &&!stack.has(DataComponents.BUNDLE_CONTENTS);}
    private record MaterialSlot(Container container,int index){
        ItemStack stack(){return container.getItem(index);}
    }
    private List<MaterialSlot> materialSlots(){
        List<MaterialSlot> slots=new ArrayList<>();for(int i=1;i<28;i++)slots.add(new MaterialSlot(this,i));
        if(level==null)return slots;
        Set<Container> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Direction direction:Direction.values()){
            BlockPos pos=worldPosition.relative(direction);if(!level.hasChunkAt(pos))continue;
            var entity=level.getBlockEntity(pos);
            if(entity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest){
                addStorage(slots,seen,chest);
                var state=chest.getBlockState();
                if(state.getValue(ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE){
                    BlockPos partner=pos.relative(ChestBlock.getConnectedDirection(state));
                    if(level.hasChunkAt(partner)&&level.getBlockEntity(partner) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity other
                            &&other.getBlockState().getBlock()==state.getBlock()&&other.getBlockState().getValue(ChestBlock.TYPE)!=state.getValue(ChestBlock.TYPE)
                            &&other.getBlockState().getValue(ChestBlock.FACING)==state.getValue(ChestBlock.FACING))addStorage(slots,seen,other);
                }
            }else if(entity instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel)addStorage(slots,seen,barrel);
            else if(entity instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity box)addStorage(slots,seen,box);
        }
        return slots;
    }
    private static void addStorage(List<MaterialSlot> slots,Set<Container> seen,Container storage){
        if(seen.add(storage))for(int i=0;i<storage.getContainerSize();i++)slots.add(new MaterialSlot(storage,i));
    }
    public Map<Item,Integer> availableMaterials(){
        Map<Item,Integer> counts=new HashMap<>();for(var slot:materialSlots()){var stack=slot.stack();if(!stack.isEmpty()&&usable(stack))counts.merge(stack.getItem(),stack.getCount(),Integer::sum);}return counts;
    }
    public int available(Item item){return availableMaterials().getOrDefault(item,0);}
    public boolean freeConstruction(){for(int i=1;i<28;i++)if(getItem(i).is(Blueprints.CREATIVE_CORE.get()))return true;return false;}
    public boolean build(Player player){
        if(!(level instanceof ServerLevel server)||player==null||!stillValid(player)||!player.getAbilities().mayBuild)return false;
        ConstructionPlan plan=plan();if(plan==null)return fail(player,2,"请先放入有效设计图");
        if(level.hasNeighborSignal(worldPosition))return fail(player,7,"红石信号正在暂停施工器");
        List<ConstructionPlan.Cell> pending=new ArrayList<>();
        for(var cell:plan.blocks()){
            BlockPos pos=target(cell);
            if(!server.hasChunkAt(pos)||server.isOutsideBuildHeight(pos)||!server.getWorldBorder().isWithinBounds(pos))return fail(player,4,"施工范围超出已加载区域或世界边界");
            if(plan.matches(cell,server.getBlockState(pos),rotation()))continue;
            if(!server.mayInteract(player,pos)||!player.mayUseItemAt(pos,Direction.UP,getItem(0)))return fail(player,4,"施工区域不可编辑");
            if(pos.equals(worldPosition)||!server.getBlockState(pos).canBeReplaced()||server.getBlockEntity(pos)!=null)return fail(player,4,"施工位置被占用："+pos.toShortString());
            if(!cell.recipe().isEmpty()){
                boolean valid=CatalogueContent.RECIPES.containsKey(cell.recipe())?CatalogueContent.isRecipeEnabled(CatalogueContent.recipe(cell.recipe())):
                        ResourceLocationHelper.hasRecipe(server,cell.recipe());
                if(!valid)return fail(player,4,"设计图使用尚未开放或不存在的配方："+cell.recipe());
            }
            pending.add(cell);
        }
        if(pending.isEmpty())return fail(player,6,"蓝图已建成，无需重复投入材料");
        Map<Item,Integer> bill=new LinkedHashMap<>();pending.stream().filter(c->LargeMachineBlock.consumesMaterial(c.state())).forEach(c->bill.merge(c.state().getBlock().asItem(),1,Integer::sum));
        // Snapshot sources before placement: newly built storage must not become a source.
        var sources=materialSlots();Map<Item,Integer> stock=new HashMap<>();
        for(var source:sources){var stack=source.stack();if(!stack.isEmpty()&&usable(stack))stock.merge(stack.getItem(),stack.getCount(),Integer::sum);}
        boolean free=freeConstruction();
        if(!free)for(var entry:bill.entrySet())if(stock.getOrDefault(entry.getKey(),0)<entry.getValue())return fail(player,3,"施工材料不足："+new ItemStack(entry.getKey()).getHoverName().getString());
        List<BlockSnapshot> placed=new ArrayList<>();
        try{
            for(var cell:pending){
                BlockPos pos=target(cell);var before=BlockSnapshot.create(server.dimension(),server,pos);placed.add(before);
                if(!server.setBlock(pos,cell.state().rotate(rotation()),18)||EventHooks.onBlockPlace(player,before,Direction.UP))throw new IllegalStateException("施工被保护规则阻止");
            }
            for(var cell:pending)if(!server.getBlockState(target(cell)).canSurvive(server,target(cell)))throw new IllegalStateException("部分方块缺少支撑，请准备地基");
        }catch(RuntimeException error){
            for(int i=placed.size()-1;i>=0;i--)LargeMachineBlock.restoreSnapshot(placed.get(i));
            return fail(player,4,error.getMessage()==null?"施工失败，已撤回本次放置":error.getMessage());
        }
        // Only successful complete placement consumes stacks; existing blocks are free to resume.
        if(!free)for(var entry:bill.entrySet()){
            int remaining=entry.getValue();for(var source:sources){if(remaining==0)break;var stack=source.stack();if(stack.is(entry.getKey())&&usable(stack)){int n=Math.min(remaining,stack.getCount());source.container().removeItem(source.index(),n);source.container().setChanged();remaining-=n;}}
        }
        for(var cell:pending){
            BlockPos pos=target(cell);if(server.getBlockEntity(pos)instanceof IndustryBlockEntity machine){
                machine.template=cell.recipe();if(machine instanceof PreparationBlockEntity preparation)preparation.materialMode=cell.materialMode();machine.setChanged();server.invalidateCapabilities(pos);
            }
        }
        for(var cell:pending){BlockPos pos=target(cell);server.updateNeighborsAt(pos,server.getBlockState(pos).getBlock());}
        status=5;setChanged();player.displayClientMessage(Component.literal("建造完成："+pending.size()+"个方块；设计图保留，可重复使用"),false);return true;
    }
    private boolean fail(Player player,int code,String message){status=code;setChanged();player.displayClientMessage(Component.literal(message),false);return false;}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider provider){super.saveAdditional(tag,provider);ContainerHelper.saveAllItems(tag,items,provider);tag.putInt("BlueprintTurns",turns);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider provider){super.loadAdditional(tag,provider);items=NonNullList.withSize(28,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items,provider);turns=Math.floorMod(tag.getInt("BlueprintTurns"),4);cachedText="";cachedPlan=null;}
    @Override public Component getDisplayName(){return Component.translatable("block.starfall_blueprint.blueprint_builder");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return new BlueprintMenu(id,inventory,this,data);}
    @Override public int getContainerSize(){return 28;}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return items.get(slot);}
    @Override public ItemStack removeItem(int slot,int count){var result=ContainerHelper.removeItem(items,slot,count);if(!result.isEmpty())setChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){return ContainerHelper.takeItem(items,slot);}
    @Override public void setItem(int slot,ItemStack stack){items.set(slot,stack);status=0;setChanged();}
    @Override public void clearContent(){items=NonNullList.withSize(28,ItemStack.EMPTY);setChanged();}
    @Override public boolean stillValid(Player player){return Container.stillValidBlockEntity(this,player);}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot==0?stack.is(Blueprints.PLAN.get()):stack.is(Blueprints.CREATIVE_CORE.get())||stack.getItem()instanceof BlockItem&&usable(stack);}
    @Override public int[] getSlotsForFace(Direction direction){return java.util.stream.IntStream.range(1,28).toArray();}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return slot>0&&canPlaceItem(slot,stack);}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return slot>0;}
    private static final class ResourceLocationHelper {
        static boolean hasRecipe(ServerLevel level,String id){try{return level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(id)).isPresent();}catch(RuntimeException e){return false;}}
    }
}
