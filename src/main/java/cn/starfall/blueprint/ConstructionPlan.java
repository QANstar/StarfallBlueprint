package cn.starfall.blueprint;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.Property;

/** Portable, bounded block-state format. It deliberately contains no inventory or entity NBT. */
public record ConstructionPlan(String title,List<Cell> blocks) {
    public static final int MAX_TEXT=60000,MAX_BLOCKS=512,MAX_SIDE=48;
    public record Cell(BlockPos pos,BlockState state,String recipe,boolean materialMode){}
    private static final Set<String> DYNAMIC=Set.of("lit","powered","north","south","east","west","up","down");
    public ConstructionPlan {blocks=List.copyOf(blocks);LargeMachineBlock.validatePlan(blocks);}
    public static ConstructionPlan parse(String text){
        if(text.length()>MAX_TEXT)throw new IllegalArgumentException("蓝图文件过大（最多60000字符）");
        try{
            JsonObject root=JsonParser.parseString(text).getAsJsonObject();
            if(!root.get("format").getAsString().equals("starfall_blueprint")||root.get("version").getAsInt()!=1)throw new IllegalArgumentException("不支持的蓝图格式或版本");
            String title=root.get("name").getAsString();if(title.isBlank()||title.length()>64)throw new IllegalArgumentException("蓝图名称需为1至64字");
            JsonArray array=root.getAsJsonArray("blocks");if(array.isEmpty()||array.size()>MAX_BLOCKS)throw new IllegalArgumentException("蓝图需要1至512个方块");
            List<Cell> cells=new ArrayList<>();Set<BlockPos> seen=new HashSet<>();
            for(JsonElement value:array){
                var obj=value.getAsJsonObject();var xyz=obj.getAsJsonArray("pos");if(xyz.size()!=3)throw new IllegalArgumentException("坐标需要三个整数");
                int x=integer(xyz.get(0)),y=integer(xyz.get(1)),z=integer(xyz.get(2));
                if(x<0||x>=MAX_SIDE||y<0||y>=32||z>0||z<=-MAX_SIDE)throw new IllegalArgumentException("蓝图范围最多48×32×48，前方坐标使用负Z");
                var pos=new BlockPos(x,y,z);if(!seen.add(pos))throw new IllegalArgumentException("蓝图含重复坐标");
                ResourceLocation id=ResourceLocation.parse(obj.get("block").getAsString());
                if(!BuiltInRegistries.BLOCK.containsKey(id))throw new IllegalArgumentException("缺少方块："+id);
                Block block=BuiltInRegistries.BLOCK.get(id);checkBlock(block);
                BlockState state=block.defaultBlockState();
                if(obj.has("properties"))for(var entry:obj.getAsJsonObject("properties").entrySet()){
                    var property=block.getStateDefinition().getProperty(entry.getKey());
                    if(property==null)throw new IllegalArgumentException("无效的方块属性："+entry.getKey());
                    if(!DYNAMIC.contains(entry.getKey()))state=property(state,property,entry.getValue().getAsString());
                }
                String recipe=obj.has("recipe")?obj.get("recipe").getAsString():"";
                if(recipe.length()>128||!recipe.isEmpty()&&!recipe.matches("[A-Za-z0-9_./:-]+"))throw new IllegalArgumentException("配方编号无效");
                cells.add(new Cell(pos,state,recipe,obj.has("material_mode")&&obj.get("material_mode").getAsBoolean()));
            }
            return new ConstructionPlan(title,cells);
        }catch(IllegalArgumentException e){throw e;}catch(RuntimeException e){throw new IllegalArgumentException("蓝图JSON结构不完整或无效");}
    }
    private static int integer(JsonElement value){int n=value.getAsInt();if(value.getAsDouble()!=n)throw new IllegalArgumentException("方块坐标必须为整数");return n;}
    private static <T extends Comparable<T>> BlockState property(BlockState state,Property<T> property,String value){return state.setValue(property,property.getValue(value).orElseThrow(()->new IllegalArgumentException("属性值无效："+property.getName())));}
    public static void checkBlock(Block block){
        if(!(block.asItem() instanceof BlockItem)||block instanceof GameMasterBlock||block instanceof LiquidBlock
                ||block instanceof DoorBlock||block instanceof BedBlock||block instanceof DoublePlantBlock
                ||block==Blocks.MOVING_PISTON||block==Blocks.PISTON_HEAD)
            throw new IllegalArgumentException("该方块不支持蓝图施工："+BuiltInRegistries.BLOCK.getKey(block));
    }
    public static BlockState stable(BlockState state){
        for(var property:state.getProperties())if(DYNAMIC.contains(property.getName()))state=reset(state,property);
        return state;
    }
    private static <T extends Comparable<T>> BlockState reset(BlockState state,Property<T> property){return state.setValue(property,state.getBlock().defaultBlockState().getValue(property));}
    public boolean matches(Cell cell,BlockState actual,Rotation rotation){return stable(cell.state.rotate(rotation)).equals(stable(actual));}
    public Map<Item,Integer> materials(){Map<Item,Integer> result=new LinkedHashMap<>();blocks.stream().filter(c->LargeMachineBlock.consumesMaterial(c.state())).forEach(c->result.merge(c.state.getBlock().asItem(),1,Integer::sum));return result;}
    public String json(){
        JsonObject root=new JsonObject();root.addProperty("format","starfall_blueprint");root.addProperty("version",1);root.addProperty("name",title);
        JsonArray array=new JsonArray();
        for(Cell cell:blocks){
            JsonObject obj=new JsonObject();JsonArray pos=new JsonArray();pos.add(cell.pos.getX());pos.add(cell.pos.getY());pos.add(cell.pos.getZ());obj.add("pos",pos);
            obj.addProperty("block",BuiltInRegistries.BLOCK.getKey(cell.state.getBlock()).toString());JsonObject properties=new JsonObject();
            for(var entry:cell.state.getValues().entrySet())if(!DYNAMIC.contains(entry.getKey().getName()))properties.addProperty(entry.getKey().getName(),name(entry.getKey(),entry.getValue()));
            if(!properties.isEmpty())obj.add("properties",properties);
            if(!cell.recipe.isEmpty())obj.addProperty("recipe",cell.recipe);if(cell.materialMode)obj.addProperty("material_mode",true);array.add(obj);
        }
        root.add("blocks",array);return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static String name(Property p,Comparable value){return p.getName(value);}
}
