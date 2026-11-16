package cn.starfall.blueprint;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;

/** Shared by every orbital excavation, including strikes in different dimensions. Server thread only. */
final class OrbitalTerrainBudget {
    private static final Map<MinecraftServer,OrbitalTerrainBudget> SERVERS=new WeakHashMap<>();
    static final int BLOCKS_PER_TICK=1024,SCANS_PER_TICK=8192;
    static final int BLOCKS_PER_STRIKE=512;
    static final long NANOS_PER_TICK=3_000_000;
    private int tick=Integer.MIN_VALUE;
    int blocks,scans;
    long spent;

    static OrbitalTerrainBudget get(MinecraftServer server){
        var budget=SERVERS.computeIfAbsent(server,key->new OrbitalTerrainBudget());
        if(budget.tick!=server.getTickCount()){
            budget.tick=server.getTickCount();budget.blocks=BLOCKS_PER_TICK;budget.scans=SCANS_PER_TICK;budget.spent=0;
        }
        return budget;
    }
    boolean available(long started){return blocks>0&&scans>0&&spent+(System.nanoTime()-started)<NANOS_PER_TICK;}
}
