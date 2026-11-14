package cn.starfall.blueprint;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.shapes.*;

/** Geometry shared with the resource generator, in units of one sixteenth of a block. */
public final class OrbitalStructure {
    public static final List<String> KEYS=List.of("relay","assembler","launchpad","console");
    private static final Map<String,OrbitalStructure> ALL=new HashMap<>();
    static {
        try(var stream=OrbitalStructure.class.getResourceAsStream("/assets/starfall_blueprint/haijia_structures.json")){
            if(stream==null)throw new IllegalStateException("Missing orbital structure geometry");
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            for(String key:KEYS)ALL.put(key,new OrbitalStructure(root.getAsJsonObject(key)));
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    public final int width,height,depth;
    private final List<double[]> boxes=new ArrayList<>();
    private final Map<String,VoxelShape> shapes=new java.util.concurrent.ConcurrentHashMap<>();
    private OrbitalStructure(JsonObject json){var size=json.getAsJsonArray("size");width=size.get(0).getAsInt();height=size.get(1).getAsInt();depth=size.get(2).getAsInt();for(var raw:json.getAsJsonArray("boxes")){var obj=raw.getAsJsonObject();double[] b=new double[6];for(int i=0;i<3;i++){b[i]=obj.getAsJsonArray("from").get(i).getAsDouble();b[i+3]=obj.getAsJsonArray("to").get(i).getAsDouble();}boxes.add(b);}}
    public static OrbitalStructure of(String key){return ALL.get(key);}
    public int parts(){return width*height*depth;}
    public BlockPos offset(Direction facing,int part){return BlockPos.ZERO.relative(facing.getClockWise(),part%width).relative(facing.getOpposite(),(part/width)%depth).above(part/(width*depth));}
    public VoxelShape shape(Direction facing,int part){return shapes.computeIfAbsent(facing.getName()+part,k->{
        if(part>=parts())return Shapes.empty();var result=Shapes.empty();int px=part%width,py=part/(width*depth),pz=(part/width)%depth;
        for(double[] b:boxes){double x=Math.max(b[0]/16-px,0),y=Math.max(b[1]/16-py,0),z=Math.max(b[2]/16-pz,0),X=Math.min(b[3]/16-px,1),Y=Math.min(b[4]/16-py,1),Z=Math.min(b[5]/16-pz,1);if(x>=X||y>=Y||z>=Z)continue;
            for(int i=0;i<(facing==Direction.NORTH?0:facing==Direction.EAST?1:facing==Direction.SOUTH?2:3);i++){double nx=1-Z,nX=1-z;z=x;Z=X;x=nx;X=nX;}
            result=Shapes.or(result,Shapes.box(x,y,z,X,Y,Z));
        }return result.optimize();
    });}
}
