package cn.starfall.blueprint;

import java.util.*;
import net.minecraft.core.BlockPos;

/** Compact, cached crater traversal: finish each horizontal layer from its center outwards. */
final class OrbitalTerrainPattern {
    private record Key(boolean laser,int radius,int depth){}
    private record Column(int x,int z,int bottom){int distance(){return x*x+z*z;}}
    private static final Map<Key,OrbitalTerrainPattern> CACHE=new LinkedHashMap<>(32,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Key,OrbitalTerrainPattern> entry){return size()>32;}
    };
    private final Column[] columns;
    private final int[] layers;
    private final int top;

    static synchronized OrbitalTerrainPattern get(boolean laser,int radius,int depth){
        return CACHE.computeIfAbsent(new Key(laser,radius,depth),OrbitalTerrainPattern::new);
    }
    private OrbitalTerrainPattern(Key key){
        top=key.laser?4:8;
        var list=new ArrayList<Column>();
        for(int x=-key.radius;x<=key.radius;x++)for(int z=-key.radius;z<=key.radius;z++){
            double distance=(x*x+z*z)/(double)(key.radius*key.radius);if(distance>1)continue;
            int bottom=-(int)Math.ceil(key.depth*(key.laser?Math.sqrt(1-distance):1-distance));
            list.add(new Column(x,z,bottom));
        }
        list.sort(Comparator.comparingInt(Column::distance).thenComparingDouble(c->Math.atan2(c.z,c.x)));
        columns=list.toArray(Column[]::new);layers=new int[top+key.depth+2];
        for(int layer=0;layer<layers.length-1;layer++){
            int count=0;while(count<columns.length&&top-layer>=columns[count].bottom)count++;
            layers[layer+1]=layers[layer]+count;
        }
    }
    int size(){return layers[layers.length-1];}
    BlockPos position(int cursor,BlockPos origin){
        int layer=Arrays.binarySearch(layers,cursor);if(layer<0)layer=-layer-2;
        var column=columns[cursor-layers[layer]];
        return origin.offset(column.x,top-layer,column.z);
    }
}
