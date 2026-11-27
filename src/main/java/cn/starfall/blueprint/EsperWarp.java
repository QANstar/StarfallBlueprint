package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Real block displacement, spread over ticks within the shared terrain budget. */
final class EsperWarp {
    private static final Map<ServerLevel,Deque<Job>> JOBS = new WeakHashMap<>();
    private static final class Job {
        final UUID owner;
        final Vec3 from, axis, u, v;
        final List<BlockPos> cells;
        final Set<BlockPos> destinations = new HashSet<>();
        final long until;
        int cursor, moved;
        Job(ServerPlayer p, Vec3 from, Vec3 axis, List<BlockPos> cells) {
            owner=p.getUUID();this.from=from;this.axis=axis;this.cells=cells;
            u=axis.cross(Math.abs(axis.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
            v=axis.cross(u).normalize();until=p.level().getGameTime()+200;
        }
    }
    static boolean cast(ServerPlayer p,int level) {
        var world=p.serverLevel();
        var jobs=JOBS.computeIfAbsent(world,w->new ArrayDeque<>());
        if(jobs.size()>=16)return false;
        Vec3 from=p.getEyePosition(),axis=p.getLookAngle(),end=from;
        Set<BlockPos> cells=new LinkedHashSet<>();
        for(double d=2;d<=10+level*3;d+=.5) {
            Vec3 point=from.add(axis.scale(d));
            if(!world.hasChunkAt(BlockPos.containing(point)))break;
            end=point;
            for(var pos:BlockPos.betweenClosed(BlockPos.containing(point).offset(-1,-1,-1),BlockPos.containing(point).offset(1,1,1)))
                cells.add(pos.immutable());
            if(((int)(d*2))%3==0)EsperNetworking.fracture(world,EsperNetworking.Pattern.WARP,point,axis,1.15F,24);
        }
        if(cells.isEmpty())return false;
        if(PlayerManual.allows(p,PlayerManual.ESPER))jobs.addLast(new Job(p,from,axis,List.copyOf(cells)));
        Vec3 stop=end;
        for(var target:world.getEntitiesOfClass(LivingEntity.class,new AABB(from,stop).inflate(1),
                e->EsperEffects.canAffect(p,e)&&e.getBoundingBox().inflate(.7).clip(from,stop).isPresent())) {
            target.hurt(p.damageSources().playerAttack(p),10+level*4);
            EsperNetworking.fracture(world,EsperNetworking.Pattern.TARGET_SLASH,target.getBoundingBox().getCenter(),axis,1.3F,18);
        }
        return true;
    }
    static void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel world))return;
        var jobs=JOBS.get(world);if(jobs==null)return;
        int count=jobs.size();
        for(int i=0;i<count;i++) {
            Job job=jobs.removeFirst();
            if(world.getPlayerByUUID(job.owner) instanceof ServerPlayer owner && owner.isAlive() && !owner.isSpectator()
                    && PlayerManual.allows(owner,PlayerManual.ESPER) && Esper.school(Esper.data(owner))==Esper.School.SPACE && world.getGameTime()<job.until
                    && !advance(owner,job))jobs.addLast(job);
        }
        if(jobs.isEmpty())JOBS.remove(world);
    }
    private static boolean advance(ServerPlayer p,Job job) {
        var world=p.serverLevel();var budget=OrbitalTerrainBudget.get(world.getServer());long start=System.nanoTime();
        try {
            for(int scanned=0;scanned<128&&job.cursor<job.cells.size()&&job.moved<64&&budget.available(start);scanned++) {
                BlockPos src=job.cells.get(job.cursor++);budget.scans--;
                if(job.destinations.contains(src)||!allowed(p,src))continue;
                var state=world.getBlockState(src);
                if(state.isAir()||state.hasBlockEntity()||!state.getFluidState().isEmpty()||state.getDestroySpeed(world,src)<0
                        ||state.getDestroySpeed(world,src)>50||!state.isCollisionShapeFullBlock(world,src))continue;
                if(NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world,src,state,p)).isCanceled())continue;
                double distance=Vec3.atCenterOf(src).subtract(job.from).dot(job.axis);
                Vec3 point=job.from.add(job.axis.scale(distance));
                for(int attempt=0;attempt<8;attempt++) {
                    double angle=world.random.nextDouble()*Math.TAU,radius=2.5+world.random.nextDouble()*2+.35*Math.sin(distance*1.7);
                    BlockPos dest=BlockPos.containing(point.add(job.u.scale(Math.cos(angle)*radius)).add(job.v.scale(Math.sin(angle)*radius)));
                    if(!allowed(p,dest)||!world.getBlockState(dest).isAir()||job.cells.contains(dest)||!state.canSurvive(world,dest))continue;
                    if(world.setBlock(dest,state,2)) {
                        if(world.setBlock(src,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2)) {
                            job.destinations.add(dest);job.moved++;budget.blocks--;
                            EsperNetworking.fracture(world,EsperNetworking.Pattern.RIFT,Vec3.atCenterOf(dest),job.axis,.55F,15);
                        } else world.setBlock(dest,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
                    }
                    break;
                }
            }
        } finally {budget.spent+=System.nanoTime()-start;}
        return job.cursor>=job.cells.size()||job.moved>=64;
    }
    private static boolean allowed(ServerPlayer p,BlockPos pos) {
        var w=p.serverLevel();
        return w.hasChunkAt(pos)&&w.isInWorldBounds(pos)&&w.getWorldBorder().isWithinBounds(pos)
                &&w.mayInteract(p,pos)&&EsperDomain.allows(w,p,Vec3.atCenterOf(pos));
    }
    private EsperWarp(){}
}
