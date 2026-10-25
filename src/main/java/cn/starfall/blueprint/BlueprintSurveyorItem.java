package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.LiquidBlock;

public final class BlueprintSurveyorItem extends Item {
    public BlueprintSurveyorItem(Properties properties){super(properties.stacksTo(1));}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags){lines.add(Component.literal("右键两个角记录布局；需一张空白设计图；潜行右键重选"));}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();if(player==null)return InteractionResult.PASS;if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
        var level=context.getLevel();var stack=context.getItemInHand();var data=CatalogueEquipment.getData(stack);
        if(player.isShiftKeyDown()){data.remove("SurveyCorner");CatalogueEquipment.setData(stack,data);player.displayClientMessage(Component.literal("已清除选区，请右键第一个角"),true);return InteractionResult.CONSUME;}
        if(!data.contains("SurveyCorner")||!data.getString("SurveyDimension").equals(level.dimension().location().toString())){
            data.putLong("SurveyCorner",context.getClickedPos().asLong());data.putString("SurveyDimension",level.dimension().location().toString());CatalogueEquipment.setData(stack,data);
            player.displayClientMessage(Component.literal("已选第一个角："+context.getClickedPos().toShortString()+"；右键另一个角完成"),false);return InteractionResult.CONSUME;
        }
        BlockPos first=BlockPos.of(data.getLong("SurveyCorner")),second=context.getClickedPos();
        int minX=Math.min(first.getX(),second.getX()),minY=Math.min(first.getY(),second.getY()),minZ=Math.min(first.getZ(),second.getZ());
        int maxX=Math.max(first.getX(),second.getX()),maxY=Math.max(first.getY(),second.getY()),maxZ=Math.max(first.getZ(),second.getZ());
        try{
            if(maxX-minX>=48||maxY-minY>=32||maxZ-minZ>=48)throw new IllegalArgumentException("选区最多48×32×48");
            ItemStack paper=ItemStack.EMPTY;
            for(int i=0;i<player.getInventory().getContainerSize();i++){var candidate=player.getInventory().getItem(i);if(candidate.is(Blueprints.PLAN.get())&&Blueprints.text(candidate).isEmpty()){paper=candidate;break;}}
            if(paper.isEmpty())throw new IllegalArgumentException("请在背包准备一张空白设计图");
            List<ConstructionPlan.Cell> cells=new ArrayList<>();
            for(BlockPos pos:BlockPos.betweenClosed(minX,minY,minZ,maxX,maxY,maxZ)){
                if(!level.hasChunkAt(pos))throw new IllegalArgumentException("请先加载整个选区");
                var state=level.getBlockState(pos);if(state.isAir()||state.getBlock()instanceof LiquidBlock)continue;
                ConstructionPlan.checkBlock(state.getBlock());
                String recipe="";boolean material=false;
                if(level.getBlockEntity(pos)instanceof IndustryBlockEntity machine){recipe=machine.template;material=machine instanceof PreparationBlockEntity prep&&prep.materialMode;}
                cells.add(new ConstructionPlan.Cell(new BlockPos(pos.getX()-minX,pos.getY()-minY,pos.getZ()-maxZ),ConstructionPlan.stable(state),recipe,material));
                if(cells.size()>ConstructionPlan.MAX_BLOCKS)throw new IllegalArgumentException("设计图最多记录512个方块，请缩小选区");
            }
            if(cells.isEmpty())throw new IllegalArgumentException("选区内没有可建造的方块");
            var plan=ConstructionPlan.parse(new ConstructionPlan("测绘蓝图 · "+cells.size()+"方块",cells).json());
            Blueprints.write(paper,plan);player.getInventory().setChanged();data.remove("SurveyCorner");CatalogueEquipment.setData(stack,data);
            player.displayClientMessage(Component.literal("已记录设计图："+cells.size()+"个方块；不复制库存、储能或液体"),false);
        }catch(RuntimeException error){player.displayClientMessage(Component.literal("测绘失败："+error.getMessage()),false);}
        return InteractionResult.CONSUME;
    }
}
