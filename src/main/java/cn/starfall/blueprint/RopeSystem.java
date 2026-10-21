package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.registries.DeferredBlock;

/** One actual, climbable, owned rope per player; removal never returns free string. */
public final class RopeSystem {
    public static void initialize(){}
    public static final DeferredBlock<Block> ROPE=StarfallBlueprint.BLOCKS.register("rescue_rope",()->new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(.2f).noOcclusion().noLootTable().sound(SoundType.WOOL)){
        protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Block.box(6,0,6,10,16,10);}
        protected VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
        public boolean isLadder(BlockState s,LevelReader l,BlockPos p,LivingEntity e){return true;}
        protected void tick(BlockState s,ServerLevel l,BlockPos p,net.minecraft.util.RandomSource r){Ledger ledger=Ledger.get(l);var link=ledger.links.values().stream().filter(x->x.dimension.equals(l.dimension().location().toString())&&x.positions.contains(p)).findFirst();if(link.isEmpty()){l.removeBlock(p,false);return;}Link rope=link.get();if(!l.hasChunkAt(rope.anchor)||!l.getBlockState(rope.anchor).isFaceSturdy(l,rope.anchor,rope.face)){ledger.remove(l,rope.owner);return;}l.scheduleTick(p,this,20);}
        protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moving){if(!l.isClientSide)l.scheduleTick(p,this,20);}
    });
    private record Link(UUID owner,String dimension,BlockPos anchor,Direction face,List<BlockPos>positions){}
    public static final class Ledger extends SavedData {
        private final Map<UUID,Link>links=new HashMap<>();
        static Ledger get(ServerLevel l){return l.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Ledger::new,Ledger::load),"starfall_rescue_ropes");}
        static Ledger load(CompoundTag tag,HolderLookup.Provider lookup){var out=new Ledger();for(Tag t:tag.getList("Links",10)){CompoundTag n=(CompoundTag)t;UUID owner=n.getUUID("Owner");List<BlockPos>positions=new ArrayList<>();for(long p:n.getLongArray("Positions"))positions.add(BlockPos.of(p));out.links.put(owner,new Link(owner,n.getString("Dimension"),BlockPos.of(n.getLong("Anchor")),Direction.from3DDataValue(n.getInt("Face")),positions));}return out;}
        public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){ListTag list=new ListTag();for(Link link:links.values()){CompoundTag n=new CompoundTag();n.putUUID("Owner",link.owner);n.putString("Dimension",link.dimension);n.putLong("Anchor",link.anchor.asLong());n.putInt("Face",link.face.ordinal());n.putLongArray("Positions",link.positions.stream().mapToLong(BlockPos::asLong).toArray());list.add(n);}tag.put("Links",list);return tag;}
        void remove(ServerLevel current,UUID owner){Link link=links.remove(owner);if(link==null)return;setDirty();ServerLevel l=current.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.ResourceLocation.parse(link.dimension)));if(l!=null)for(BlockPos p:link.positions)if(l.hasChunkAt(p)&&l.getBlockState(p).is(ROPE.get()))l.removeBlock(p,false);}
    }
    public static InteractionResultHolder<ItemStack>useAir(Level l,Player p,InteractionHand hand){Vec3 from=p.getEyePosition(),to=from.add(p.getLookAngle().scale(8));if(!l.hasChunkAt(BlockPos.containing(to)))return InteractionResultHolder.fail(p.getItemInHand(hand));var hit=l.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(hit.getType()!=HitResult.Type.BLOCK)return InteractionResultHolder.pass(p.getItemInHand(hand));var result=use(new UseOnContext(p,hand,hit));return new InteractionResultHolder<>(result,p.getItemInHand(hand));}
    public static InteractionResult use(UseOnContext c){
        if(c.getPlayer()==null||c.getPlayer().getEyePosition().distanceToSqr(c.getClickLocation())>64)return InteractionResult.FAIL;
        if(c.getLevel().isClientSide)return InteractionResult.SUCCESS;
        var l=(ServerLevel)c.getLevel();Player p=c.getPlayer();var ledger=Ledger.get(l);Link old=ledger.links.get(p.getUUID());
        if(old!=null&&(old.anchor.equals(c.getClickedPos())||old.positions.contains(c.getClickedPos()))){ledger.remove(l,p.getUUID());return InteractionResult.CONSUME;}
        BlockPos anchor=c.getClickedPos();Direction face=c.getClickedFace();if(!l.getBlockState(anchor).isFaceSturdy(l,anchor,face))return InteractionResult.FAIL;
        List<BlockPos>positions=new ArrayList<>();BlockPos first=anchor.relative(face);
        for(int i=0;i<8;i++){BlockPos next=first.below(i);if(!l.hasChunkAt(next)||!l.isInWorldBounds(next))break;boolean own=old!=null&&old.dimension.equals(l.dimension().location().toString())&&old.positions.contains(next);if(!l.isEmptyBlock(next)&&!own)break;if(!p.mayUseItemAt(next,face,c.getItemInHand())||!l.mayInteract(p,next))return InteractionResult.FAIL;positions.add(next);}
        if(positions.isEmpty())return InteractionResult.FAIL;
        List<BlockSnapshot>snapshots=new ArrayList<>();boolean success=true;
        for(BlockPos next:positions){var snapshot=BlockSnapshot.create(l.dimension(),l,next);snapshots.add(snapshot);l.setBlock(next,ROPE.get().defaultBlockState(),3);if(EventHooks.onBlockPlace(p,snapshot,face)){success=false;break;}}
        if(!success){for(var snapshot:snapshots)snapshot.restore();return InteractionResult.FAIL;}
        // Old links are removed after all new placements pass permission checks.
        if(old!=null){ledger.links.remove(p.getUUID());ServerLevel oldLevel=l.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.ResourceLocation.parse(old.dimension)));if(oldLevel!=null)for(BlockPos pos:old.positions)if(!old.dimension.equals(l.dimension().location().toString())||!positions.contains(pos))if(oldLevel.hasChunkAt(pos)&&oldLevel.getBlockState(pos).is(ROPE.get()))oldLevel.removeBlock(pos,false);}
        ledger.links.put(p.getUUID(),new Link(p.getUUID(),l.dimension().location().toString(),anchor,face,List.copyOf(positions)));ledger.setDirty();
        c.getItemInHand().hurtAndBreak(1,p,c.getHand()==InteractionHand.MAIN_HAND?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND);return InteractionResult.CONSUME;
    }
    public static void playerTick(Player p){if(p.level().getBlockState(p.blockPosition()).is(ROPE.get())){Vec3 v=p.getDeltaMovement();p.setDeltaMovement(v.x,p.isShiftKeyDown()?Math.max(0,v.y):Math.clamp(v.y,-.1,.1),v.z);}}
}
