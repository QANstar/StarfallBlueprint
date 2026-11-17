package cn.starfall.blueprint;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A transient, server-owned lift. The pilot remains the mech's only passenger. */
@net.neoforged.fml.common.EventBusSubscriber(modid = StarfallBlueprint.ID)
public final class BestMechLifting {
    @net.neoforged.bus.api.SubscribeEvent
    public static void onLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof BestMechEntity mech) HELD.remove(mech);
    }
    private static final Map<BestMechEntity, UUID> HELD = new WeakHashMap<>();
    private BestMechLifting() {}

    private static boolean operator(BestMechEntity mech, Player player) {
        return !mech.level().isClientSide && mech.isAlive() && mech.getControllingPassenger()==player
                && mech.qualifiedFor(player) && mech.ready(0) && mech.level().mayInteract(player,mech.blockPosition());
    }
    private static boolean eligible(BestMechEntity mech, Player pilot, Entity entity) {
        return entity!=mech && entity!=pilot && entity.isAlive() && !entity.isSpectator()
                && !(entity instanceof BestMechEntity) && !entity.isPassenger() && entity.getPassengers().isEmpty()
                && (entity instanceof LivingEntity || entity instanceof Boat || entity instanceof AbstractMinecart || entity instanceof Container)
                && entity.getBbWidth()<=2.8f && entity.getBbHeight()<=3.0f
                && (!(entity instanceof Player other) || other.isShiftKeyDown())
                && mech.level().getWorldBorder().isWithinBounds(entity.blockPosition())
                && mech.level().mayInteract(pilot,entity.blockPosition()) && !HELD.containsValue(entity.getUUID());
    }
    public static void toggle(BestMechEntity mech, Player pilot) {
        if(!operator(mech,pilot) || !(mech.level() instanceof ServerLevel level)) return;
        if(HELD.remove(mech)!=null) {
            pilot.displayClientMessage(Component.translatable("message.starfall_blueprint.mech_lift_release"),true);
            return;
        }
        Vec3 start=pilot.getEyePosition(), end=start.add(pilot.getLookAngle().scale(7));
        var block=level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,pilot));
        if(block.getType()!=HitResult.Type.MISS) end=block.getLocation();
        Entity found=null; double distance=start.distanceToSqr(end);
        for(Entity e:level.getEntities(mech,new AABB(start,end).inflate(1),e->eligible(mech,pilot,e))) {
            var hit=e.getBoundingBox().inflate(.2).clip(start,end);
            if(hit.isPresent() && start.distanceToSqr(hit.get())<distance) { found=e; distance=start.distanceToSqr(hit.get()); }
        }
        if(found==null) { pilot.displayClientMessage(Component.translatable("message.starfall_blueprint.mech_lift_none"),true); return; }
        HELD.put(mech,found.getUUID());
        tick(mech);
        if (HELD.containsKey(mech)) pilot.displayClientMessage(Component.translatable("message.starfall_blueprint.mech_lift_grab",found.getDisplayName()),true);
        else pilot.displayClientMessage(Component.translatable("message.starfall_blueprint.mech_lift_blocked"),true);
    }
    public static void tick(BestMechEntity mech) {
        if(!(mech.level() instanceof ServerLevel level)) return;
        UUID id=HELD.get(mech); if(id==null) return;
        Entity target=level.getEntity(id);
        if(!(mech.getControllingPassenger() instanceof Player pilot) || !operator(mech,pilot) || target==null
                || !target.isAlive() || target.isSpectator() || target instanceof Player carried && !carried.isShiftKeyDown()
                || target.isPassenger() || !target.getPassengers().isEmpty()
                || target.distanceToSqr(mech)>100 || !level.mayInteract(pilot,target.blockPosition())) { HELD.remove(mech); return; }
        Vec3 look=pilot.getLookAngle(); Vec3 forward=new Vec3(look.x,0,look.z).normalize();
        if(forward.lengthSqr()<.01) forward=Vec3.directionFromRotation(0,mech.getYRot());
        double distance=mech.getBbWidth()/2.0+target.getBbWidth()/2.0+.65;
        Vec3 destination=mech.position().add(forward.scale(distance)).add(0,.25,0);
        Vec3 delta=destination.subtract(target.position());
        var destPos=net.minecraft.core.BlockPos.containing(destination);
        if(delta.lengthSqr()>16 || !level.hasChunkAt(destPos) || !level.getWorldBorder().isWithinBounds(destPos)
                || target instanceof Player && (!level.getBlockState(destPos.below()).isFaceSturdy(level,destPos.below(),net.minecraft.core.Direction.UP)
                    || level.getFluidState(destPos).is(net.minecraft.tags.FluidTags.LAVA))
                || !level.mayInteract(pilot,destPos) || !level.noCollision(target,target.getBoundingBox().move(delta))
                || level.clip(new ClipContext(target.position().add(0,target.getBbHeight()*.5,0),destination.add(0,target.getBbHeight()*.5,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,target)).getType()!=HitResult.Type.MISS) {
            HELD.remove(mech); return;
        }
        if(target instanceof ServerPlayer player) player.connection.teleport(destination.x,destination.y,destination.z,player.getYRot(),player.getXRot());
        else target.moveTo(destination.x,destination.y,destination.z,target.getYRot(),target.getXRot());
        target.setDeltaMovement(Vec3.ZERO); target.fallDistance=0; target.hasImpulse=true;
    }
}
