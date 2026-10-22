package cn.starfall.blueprint;

import com.google.gson.*;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Real 3x3 recipe, including material checks and inherited quality for composite assemblies. */
public final class CatalogueCraftingRecipe extends CustomRecipe {
    public final String id;
    private final JsonObject definition;
    public CatalogueCraftingRecipe(String id){super(CraftingBookCategory.MISC);this.id=id;definition=CatalogueContent.recipe(id);}
    public boolean matches(CraftingInput input,Level level) {
        if (!CatalogueContent.isRecipeEnabled(definition)) return false;
        JsonObject craft=definition.getAsJsonObject("craft");
        if(craft.get("mode").getAsString().startsWith("定形")) {
            var cells=craft.getAsJsonArray("cells");int minX=3,minY=3,maxX=0,maxY=0;
            for(int i=0;i<9;i++)if(!cells.get(i).isJsonNull()){minX=Math.min(minX,i%3);maxX=Math.max(maxX,i%3);minY=Math.min(minY,i/3);maxY=Math.max(maxY,i/3);}
            int w=maxX-minX+1,h=maxY-minY+1;if(input.width()!=w||input.height()!=h)return false;
            for(boolean mirror:new boolean[]{false,true}) {
                boolean match=true;
                for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                    var cell=cells.get((minY+y)*3+minX+(mirror?w-1-x:x));ItemStack s=input.getItem(x+y*w);
                    if(cell.isJsonNull()?!s.isEmpty():!CatalogueContent.matches(CatalogueContent.key(cell),s))match=false;
                }
                if(match&&quality(input))return true;
            }
            return false;
        }
        java.util.Map<String,Integer> needed=new java.util.LinkedHashMap<>();
        for(var in:definition.getAsJsonArray("inputs"))needed.put(CatalogueContent.key(in),CatalogueContent.qty(in));
        for(int i=0;i<input.size();i++)if(!input.getItem(i).isEmpty()) {
            String found=null;for(var e:needed.entrySet())if(e.getValue()>0&&CatalogueContent.matches(e.getKey(),input.getItem(i))){found=e.getKey();break;}
            if(found==null)return false;needed.put(found,needed.get(found)-1);
        }
        return needed.values().stream().allMatch(n->n==0)&&quality(input);
    }
    private boolean quality(CraftingInput input) {
        for(int i=0;i<input.size();i++) {
            ItemStack s=input.getItem(i);
            if(s.is(StarfallBlueprint.CRYSTAL.get())){var b=s.get(StarfallBlueprint.BATCH.get());if(b==null||!b.isQ0())return false;}
            if(id.equals("RT-02")&&s.is(Items.IRON_PICKAXE)&&(s.isDamaged()||s.isEnchanted()||s.has(StarfallBlueprint.REINFORCED.get())))return false;
            if(id.equals("RT-08")&&CatalogueContent.matches("anima_basic",s)){var q=MaterialQuality.of(s);if(!q.route().equals("anima")||q.integrity()<20||q.contamination()!=0)return false;}
        }
        return true;
    }
    public ItemStack assemble(CraftingInput input,HolderLookup.Provider provider) {
        if (!CatalogueContent.isRecipeEnabled(definition)) return ItemStack.EMPTY;
        ItemStack out=getResultItem(provider).copy();
        if(id.equals("ST-15")||id.equals("ST-16"))for(int i=0;i<input.size();i++)if(SealedSampleItem.isBox(input.getItem(i)))out.set(net.minecraft.core.component.DataComponents.CONTAINER,input.getItem(i).getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.EMPTY));
        for(int i=0;i<input.size();i++){var s=input.getItem(i);if(s.has(CatalogueContent.QUALITY.get())){out.set(CatalogueContent.QUALITY.get(),MaterialQuality.of(s));break;}}
        return out;
    }
    public ItemStack getResultItem(HolderLookup.Provider provider){var o=definition.getAsJsonArray("outputs").get(0);return CatalogueContent.stack(CatalogueContent.key(o),CatalogueContent.qty(o));}
    public NonNullList<Ingredient> getIngredients(){var out=NonNullList.<Ingredient>create();for(var in:definition.getAsJsonArray("inputs"))for(int i=0;i<CatalogueContent.qty(in);i++)out.add(Ingredient.of(CatalogueContent.item(CatalogueContent.key(in))));return out;}
    public boolean isSpecial(){return false;}
    public boolean canCraftInDimensions(int w,int h){return w*h>=getIngredients().size();}
    public RecipeSerializer<?> getSerializer(){return CatalogueContent.CRAFTING.get();}
    public static final class Serializer implements RecipeSerializer<CatalogueCraftingRecipe> {
        public MapCodec<CatalogueCraftingRecipe> codec(){return Codec.STRING.fieldOf("recipe").xmap(CatalogueCraftingRecipe::new,r->r.id);}
        public StreamCodec<RegistryFriendlyByteBuf,CatalogueCraftingRecipe> streamCodec(){return StreamCodec.of((b,r)->b.writeUtf(r.id),b->new CatalogueCraftingRecipe(b.readUtf()));}
    }
}
