package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** A wiring canvas for assembly and a filtered manufacturing catalogue. */
public final class LingchuanScreen extends AbstractContainerScreen<LingchuanMenu> {
    private String shownKind="",query="";
    private int category,selected=-1,dragging=-1,listOffset;
    private float modelYaw=155;
    private float uiScale=1;
    private boolean rotating;
    private BlackDogArmorModel model;
    private Button make,assemble,refit;
    public LingchuanScreen(LingchuanMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=460;imageHeight=294;inventoryLabelX=150;inventoryLabelY=201;}
    private static Component text(String key,Object...args){return Component.translatable("lc.workshop."+key,args);}
    private void send(int action){if(Boolean.getBoolean("starfall.blackdogWorkshopCheck"))org.slf4j.LoggerFactory.getLogger(getClass()).info("WORKSHOP QA sending container={} action={}",menu.containerId,action);if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    private Button button(Component label,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(label,b->action.run()).bounds(leftPos+x,topPos+y,w,18).build());}
    @Override protected void init(){
        super.init();uiScale=Math.min(1,Math.min((width-12)/460f,(height-12)/294f));leftPos=(int)((width/uiScale-460)/2);topPos=(int)((height/uiScale-294)/2);shownKind=menu.kind();model=new BlackDogArmorModel(minecraft.getEntityModels().bakeLayer(BlackDogArmorModel.LAYER));
        if(shownKind.equals("lc_armor_assembler")){
            assemble=button(text("assemble"),332,249,56,()->send(50));assemble.setTooltip(Tooltip.create(Component.translatable("lc.assemble")));refit=button(text("refit"),394,249,56,()->send(51));refit.setTooltip(Tooltip.create(Component.translatable("lc.refit")));
        }else if(shownKind.equals("lc_parts_fabricator")){
            var search=addRenderableWidget(new EditBox(font,leftPos+16,topPos+29,196,16,text("search")));search.setHint(text("search"));search.setValue(query);search.setResponder(value->{query=value;listOffset=0;});
            for(int i=0;i<3;i++){final int tab=i;button(text("category."+i),16+i*68,49,64,()->{category=tab;listOffset=0;});}
            make=button(text("manufacture"),246,166,106,()->{if(selected>=0)send(selected==36?180:100+selected);});
        }else if(shownKind.equals("lc_alloy_forge")){
            button(Component.translatable("lc.forge_crystal"),20,60,180,()->send(40));button(Component.translatable("lc.forge_alloy"),20,95,180,()->send(41));
        }else if(shownKind.equals("lc_research_station"))button(ProgressionInventoryTab.text("open"),100,80,260,ProgressionInventoryTab::open);
    }
    @Override protected void containerTick(){
        super.containerTick();if(!shownKind.equals(menu.kind())){rebuildWidgets();return;}
        if(make!=null)make.active=selected>=0&&menu.researched(selected==36?12:Lingchuan.MODULES.get(selected).research());
        if(assemble!=null)assemble.active=menu.watch().isEmpty();if(refit!=null)refit.active=menu.watch().getItem() instanceof BlackDogArmor.Watch;
    }
    private List<Integer> products(){var result=new ArrayList<Integer>();for(int i=0;i<=36;i++){
        if(category==1&&i>=24||category==2&&i<24)continue;
        if(name(i).getString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))result.add(i);
    }return result;}
    private ItemStack product(int index){return CatalogueContent.stack(index==36?"blackdog_fire_control":Lingchuan.MODULES.get(index).key(),1);}
    private Component name(int index){return product(index).getHoverName();}
    private boolean contains(double mx,double my,int x,int y,int w,int h){return mx>=leftPos+x&&mx<leftPos+x+w&&my>=topPos+y&&my<topPos+y+h;}
    private void label(GuiGraphics g,Component value,int x,int y,int max,int color){g.drawString(font,font.plainSubstrByWidth(value.getString(),max),leftPos+x,topPos+y,color,false);}
    private void box(GuiGraphics g,int x,int y,int w,int h,int edge,int fill){g.fill(leftPos+x-1,topPos+y-1,leftPos+x+w+1,topPos+y+h+1,edge);g.fill(leftPos+x,topPos+y,leftPos+x+w,topPos+y+h,fill);}
    private void bar(GuiGraphics g,int x,int y,int w,int used,int max,int color){g.fill(leftPos+x,topPos+y,leftPos+x+w,topPos+y+3,0xff343e4b);g.fill(leftPos+x,topPos+y,leftPos+x+(int)(w*Math.clamp(used/(double)Math.max(1,max),0,1)),topPos+y+3,used>max?0xfff07865:color);}
    private void wire(GuiGraphics g,int x1,int y1,int x2,int y2,int color){int steps=Math.max(Math.abs(x2-x1),Math.abs(y2-y1));for(int i=0;i<=steps;i++){float t=steps==0?0:i/(float)steps;int x=Math.round(x1+(x2-x1)*t),y=Math.round(y1+(y2-y1)*t);g.fill(leftPos+x,topPos+y,leftPos+x+1,topPos+y+1,color);}}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        box(g,0,0,460,294,0xff674858,0xff101722);g.fill(leftPos,topPos,leftPos+460,topPos+23,0xff272636);
        label(g,title,12,8,265,0xfff0dce4);label(g,Component.literal(String.format(Locale.ROOT,"%,d XH",menu.power())),328,8,120,0xff8dd4d6);
        g.fill(leftPos+8,topPos+198,leftPos+452,topPos+199,0xff354051);
        for(var slot:menu.slots)if(slot.isActive())box(g,slot.x,slot.y,16,16,slot.index<22?0xff765367:0xff394455,0xff17212e);
        if(shownKind.equals("lc_armor_assembler"))assembly(g);else if(shownKind.equals("lc_parts_fabricator"))fabricator(g,mx,my);else if(shownKind.equals("lc_armor_charger"))charger(g);
        else if(shownKind.equals("lc_research_station"))label(g,ProgressionInventoryTab.text("station_hint"),20,44,420,0xffbbc4d1);
        else if(shownKind.equals("lc_alloy_forge")){label(g,Component.translatable("lc.forge_crystal_cost"),220,65,222,0xffb8c6d5);label(g,Component.translatable("lc.forge_alloy_cost"),220,100,222,0xffb8c6d5);}
        label(g,playerInventoryTitle,150,201,160,0xffa7b6ca);
        if(menu.slots.get(8).isActive())label(g,Component.translatable("lc.watch_slot"),370,207,46,0xffd5adba);
        if(menu.slots.get(9).isActive())label(g,Component.translatable("lc.output_slot"),415,207,35,0xffd5adba);
    }
    private void assembly(GuiGraphics g){
        var data=menu.preview();var tiers=data.getIntArray("ChassisTiers");int mask=menu.connectedAbilities();label(g,text("drag_hint"),12,29,436,0xff899db5);
        for(int i=0;i<12;i++){
            if((mask&(1<<i))==0)continue;
            var component=Lingchuan.MODULES.get(24+i);int part=component.slot(),x=BlackDogWorkstations.optionX(i),y=BlackDogWorkstations.optionY(i);boolean installed=(mask&(1<<i))!=0,available=owned(component.key())>0;
            wire(g,x+8,y+8,BlackDogWorkstations.PART_X[part]+8,BlackDogWorkstations.PART_Y[part]+8,installed?0xff64bdb2:0xff354252);
            box(g,x-2,y-2,20,20,installed?0xff67cdbb:available?0xffaa7d91:0xff3a4658,0xff1c2736);g.renderItem(CatalogueContent.stack(component.key(),1),leftPos+x,topPos+y);
            if(installed)g.fill(leftPos+x+12,topPos+y+13,leftPos+x+17,topPos+y+17,0xff8ce8bd);else if(!available)g.fill(leftPos+x,topPos+y,leftPos+x+16,topPos+y+16,0x88202a39);
        }
        for(int i=0;i<8;i++){
            int x=BlackDogWorkstations.PART_X[i],y=BlackDogWorkstations.PART_Y[i],bx=x<200?x-4:x-42;var carried=Lingchuan.module(menu.getCarried());boolean target=dragging>=0&&Lingchuan.MODULES.get(24+dragging).slot()==i||carried!=null&&!carried.ability().isEmpty()&&carried.slot()==i;
            box(g,bx,y-4,62,28,target?0xffe7b36f:0xff4b5268,0xff1c2432);
            if(menu.slots.get(i).getItem().isEmpty()&&tiers[i]>0)g.renderItem(CatalogueContent.stack(Lingchuan.baseKey(i,tiers[i]),1),leftPos+x,topPos+y);
            int nameX=x<200?x+20:bx+3;label(g,Component.translatable("lc.short_part."+Lingchuan.PARTS.get(i)),nameX,y,36,0xffc8d3e3);
            int load=i==2?data.getInt("Load"):BlackDogWorkstations.interfaceLoad(data,i),max=tiers[i]>0?(i==2?BlackDogArmor.budget(tiers[i]):Lingchuan.interfaces(tiers[i])):0;bar(g,bx+3,y+18,56,load,max,0xff69c4ba);
            if(tiers[i]>0)label(g,Component.literal("I".repeat(tiers[i])),nameX,y+9,32,0xffdfb17e);
        }
        BlackDogStationRenderer.preview(g,model,data,leftPos+230,topPos+163,52,modelYaw);
        int load=data.getInt("Load"),budget=tiers[2]>0?BlackDogArmor.budget(tiers[2]):0;label(g,text("load",load,budget),172,176,120,0xffcbd5e2);bar(g,170,188,120,load,budget,0xffd28da8);
        label(g,text("staging"),12,207,125,0xff9eafc7);label(g,text("rotation_hint"),12,280,126,0xff7e90aa);
    }
    private int owned(String key){int count=0;for(int i=10;i<22;i++)if(menu.slots.get(i).getItem().is(CatalogueContent.item(key)))count+=menu.slots.get(i).getItem().getCount();return count+countInventory(key);}
    private void charger(GuiGraphics g){
        var data=menu.preview();BlackDogStationRenderer.preview(g,model,data,leftPos+155,topPos+176,68,modelYaw);
        int tier=data.getInt("CoreTier"),max=BlackDogArmor.capacity(menu.watch()),charge=data.getInt("Energy");label(g,text(menu.watch().isEmpty()?"insert_watch":"charging"),262,53,182,0xffd9beca);
        label(g,Component.literal(String.format(Locale.ROOT,"%,d / %,d XH",charge,max)),262,85,182,0xff88d5d9);bar(g,262,104,175,charge,max,0xff83d4ce);
        label(g,Component.translatable("lc.charger_rate"),262,124,180,0xff9babbd);label(g,text("rotation_hint"),262,151,180,0xff8295ae);
    }
    private void fabricator(GuiGraphics g,int mx,int my){
        var products=products();listOffset=Math.clamp(listOffset,0,Math.max(0,products.size()-5));
        for(int row=0;row<5&&row+listOffset<products.size();row++){
            int index=products.get(row+listOffset),y=73+row*24;boolean known=menu.researched(index==36?12:Lingchuan.MODULES.get(index).research());box(g,16,y,196,21,selected==index?0xffce8aa4:0xff303f52,contains(mx,my,16,y,196,21)?0xff29364a:0xff182331);g.renderItem(product(index),leftPos+20,topPos+y+2);label(g,name(index),42,y+6,154,known?0xffdce2ec:0xff718298);
        }
        if(selected<0){label(g,text("select_product"),246,82,196,0xff9eadc3);return;}
        var stack=product(selected);g.pose().pushPose();g.pose().translate(leftPos+247,topPos+36,0);g.pose().scale(2,2,1);g.renderItem(stack,0,0);g.pose().popPose();label(g,name(selected),286,47,158,0xffead3df);
        int alloy=selected==36?2:Lingchuan.MODULES.get(selected).alloy(),crystals=selected==36?0:Lingchuan.MODULES.get(selected).tier(),boards=selected==36?2:crystals,power=selected==36?10000:Lingchuan.MODULES.get(selected).power();
        int y=83;for(var input:List.of(new Object[]{"laminar_alloy",alloy},new Object[]{"laminar_crystal",crystals},new Object[]{"board",boards},new Object[]{"minecraft:redstone",selected==36?4:0})){
            String key=(String)input[0];int needed=(Integer)input[1];if(needed==0)continue;g.renderItem(CatalogueContent.stack(key,1),leftPos+246,topPos+y-3);int have=countInventory(key);label(g,Component.literal(have+" / "+needed).append(" ").append(CatalogueContent.stack(key,1).getHoverName()),268,y,175,have>=needed?0xff9ad5be:0xffdd9c96);y+=20;
        }
        label(g,Component.literal(String.format(Locale.ROOT,"%,d XH",power)),362,171,84,menu.power()>=power?0xff87ccce:0xffdb968c);
    }
    private int countInventory(String key){if(minecraft.player==null)return 0;int n=0;for(int i=0;i<36;i++)if(CatalogueContent.matches(key,minecraft.player.getInventory().getItem(i)))n+=minecraft.player.getInventory().getItem(i).getCount();return n;}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        mx=(int)(mx/uiScale);my=(int)(my/uiScale);g.pose().pushPose();g.pose().scale(uiScale,uiScale,1);
        super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        if(shownKind.equals("lc_armor_assembler"))for(int i=0;i<12;i++)if((menu.connectedAbilities()&(1<<i))!=0&&contains(mx,my,BlackDogWorkstations.optionX(i)-2,BlackDogWorkstations.optionY(i)-2,20,20)){
            var m=Lingchuan.MODULES.get(24+i);var lines=new ArrayList<Component>();lines.add(m.name());lines.add(Component.translatable("lc.module_spec",Component.translatable("lc.part."+Lingchuan.PARTS.get(m.slot())),m.tier(),m.load()));lines.add(text("component_hint",owned(m.key())));g.renderComponentTooltip(font,lines,mx,my);
        }
        if(dragging>=0){int part=Lingchuan.MODULES.get(24+dragging).slot();wire(g,BlackDogWorkstations.PART_X[part]+8,BlackDogWorkstations.PART_Y[part]+8,mx-leftPos,my-topPos,0xffffc789);g.pose().pushPose();g.pose().translate(0,0,400);g.renderItem(product(24+dragging),mx-8,my-8);g.pose().popPose();}
        g.pose().popPose();
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        mx/=uiScale;my/=uiScale;
        if(shownKind.equals("lc_armor_assembler")&&button==0&&!menu.getCarried().isEmpty()){
            var component=Lingchuan.module(menu.getCarried());if(component!=null&&!component.ability().isEmpty()){
                int part=component.slot(),x=BlackDogWorkstations.PART_X[part],y=BlackDogWorkstations.PART_Y[part];
                if(contains(mx,my,x<200?x-4:x-42,y-4,62,28)){send(300+Lingchuan.ABILITIES.indexOf(component.ability()));return true;}
            }
        }
        if(shownKind.equals("lc_armor_assembler"))for(int i=0;i<12;i++)if((menu.connectedAbilities()&(1<<i))!=0&&contains(mx,my,BlackDogWorkstations.optionX(i)-2,BlackDogWorkstations.optionY(i)-2,20,20)){
            if(button==1){send(200+i);return true;}if(button==0&&menu.getCarried().isEmpty()){dragging=i;return true;}
        }
        if(shownKind.equals("lc_parts_fabricator")&&button==0){var products=products();for(int row=0;row<5&&row+listOffset<products.size();row++)if(contains(mx,my,16,73+row*24,196,21)){selected=products.get(row+listOffset);return true;}}
        if(button==0&&(shownKind.equals("lc_armor_assembler")&&contains(mx,my,188,46,94,120)||shownKind.equals("lc_armor_charger")&&contains(mx,my,65,35,170,155))){rotating=true;return true;}
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(rotating){modelYaw+=(float)(dx/uiScale)*1.5f;return true;}if(dragging>=0)return true;return super.mouseDragged(mx/uiScale,my/uiScale,button,dx/uiScale,dy/uiScale);}
    @Override public boolean mouseReleased(double mx,double my,int button){
        mx/=uiScale;my/=uiScale;
        if(dragging>=0){int part=Lingchuan.MODULES.get(24+dragging).slot(),x=BlackDogWorkstations.PART_X[part],y=BlackDogWorkstations.PART_Y[part];if(contains(mx,my,x<200?x-4:x-42,y-4,62,28))send(300+dragging);dragging=-1;return true;}
        if(rotating){rotating=false;return true;}return super.mouseReleased(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){mx/=uiScale;my/=uiScale;if(shownKind.equals("lc_parts_fabricator")&&contains(mx,my,12,70,212,123)){listOffset=Math.clamp(listOffset-(int)Math.signum(vertical),0,Math.max(0,products().size()-5));return true;}return super.mouseScrolled(mx,my,horizontal,vertical);}
    void reviewConnect(int ability){int part=Lingchuan.MODULES.get(24+ability).slot();for(var slot:menu.slots)if(slot.index>=22&&slot.getItem().is(CatalogueContent.item(Lingchuan.MODULES.get(24+ability).key()))){minecraft.gameMode.handleInventoryMouseClick(menu.containerId,slot.index,0,net.minecraft.world.inventory.ClickType.PICKUP,minecraft.player);break;}mouseClicked((leftPos+BlackDogWorkstations.PART_X[part]+8)*uiScale,(topPos+BlackDogWorkstations.PART_Y[part]+8)*uiScale,0);}
    void reviewDetach(int ability){mouseClicked((leftPos+BlackDogWorkstations.optionX(ability)+8)*uiScale,(topPos+BlackDogWorkstations.optionY(ability)+8)*uiScale,1);}
    void reviewProduct(){category=2;selected=24;}
}
