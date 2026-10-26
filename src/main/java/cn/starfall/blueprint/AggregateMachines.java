package cn.starfall.blueprint;

import net.minecraft.core.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class AggregateMachines {
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<LargeMachinePartEntity>> PART_ENTITY=StarfallBlueprint.ENTITIES.register("large_machine_part",()->BlockEntityType.Builder.of(LargeMachinePartEntity::new,CatalogueContent.BLOCKS.get("quarry").get(),CatalogueContent.BLOCKS.get("crusher").get(),CatalogueContent.BLOCKS.get("phase_extractor").get()).build(null));
    public static void initialize(){}
    /** One transition update per part; animation frames themselves are client atlas data. */
    public static void syncActive(IndustryBlockEntity be,boolean active){
        var level=be.getLevel();if(level==null||level.isClientSide)return;
        for(int part=0;part<LargeMachineBlock.parts(be.kind());part++){
            var pos=be.getBlockPos().offset(LargeMachineBlock.offset(be.facing(),part));if(!level.hasChunkAt(pos))continue;
            var state=level.getBlockState(pos);if(state.getBlock()==be.getBlockState().getBlock()&&state.getValue(LargeMachineBlock.PART)==part&&state.getValue(IndustryBlock.ACTIVE)!=active)level.setBlock(pos,state.setValue(IndustryBlock.ACTIVE,active),2);
        }
    }
    public static boolean large(String kind){return kind.equals("quarry")||kind.equals("crusher")||kind.equals("phase_extractor");}
    public static boolean ready(IndustryBlockEntity be){
        var level=be.getLevel();if(level==null)return false;var root=be.getBlockPos();var facing=be.facing();
        for(int part=0;part<LargeMachineBlock.parts(be.kind());part++){
            var pos=root.offset(LargeMachineBlock.offset(facing,part));
            if(!level.hasChunkAt(pos)){be.status=9;return false;}var state=level.getBlockState(pos);
            if(state.getBlock()!=be.getBlockState().getBlock()||state.getValue(LargeMachineBlock.PART)!=part||state.getValue(IndustryBlock.FACING)!=facing){be.status=9;return false;}
            if(be.kind().equals("quarry")){
                var below=pos.below();if(!level.hasChunkAt(below)||ResonanceBases.foundation(be).isEmpty()||!ResonanceBases.key(level.getBlockState(below)).equals(ResonanceBases.foundation(be))){be.status=10;return false;}
                if(part<2){var front=pos.relative(facing);if(!level.hasChunkAt(front)||!level.isEmptyBlock(front)){be.status=11;return false;}}
            }
        }
        if(be.kind().equals("phase_extractor")){
            String foundation="";
            for(int part=0;part<4;part++){
                var below=root.offset(LargeMachineBlock.offset(facing,part)).below();
                if(!level.hasChunkAt(below)){be.status=10;return false;}
                var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(below).getBlock());
                String key=id.getNamespace().equals(StarfallBlueprint.ID)?id.getPath():"";
                if(!java.util.Set.of("phase_matrix_meteor","phase_matrix_rutile","phase_matrix_borate").contains(key)||(part>0&&!key.equals(foundation))){be.status=10;return false;}
                foundation=key;
            }
        }
        return true;
    }
    private AggregateMachines(){}
}
