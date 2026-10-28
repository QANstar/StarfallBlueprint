package cn.starfall.blueprint.client;

import cn.starfall.blueprint.IndustryMenu;
import cn.starfall.blueprint.IndustryBlockEntity;
import cn.starfall.blueprint.AggregateMachines;
import cn.starfall.blueprint.AutomaticCrafting;
import cn.starfall.blueprint.MachineUpgrades;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

/** Only recipe selection needs a button. Ports are physical; redstone stops work. */
public final class IndustryScreen extends AbstractContainerScreen<IndustryMenu>{
    private final List<Button> craftingButtons=new ArrayList<>();
    private Button previousUpgradePage,nextUpgradePage;
    private int previewIndex=Integer.MIN_VALUE,previewChoiceCount;
    private Level previewLevel;
    private RecipeHolder<?> previewRecipe;
    private ItemStack previewResult=ItemStack.EMPTY;
    private List<Ingredient> previewCells=List.of();
    private String previewPosition="";
    public IndustryScreen(IndustryMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=176;imageHeight=256;inventoryLabelY=163;}
    @Override protected void init(){
        super.init();craftingButtons.clear();
        addCraftingButton("‹",0,8,20,"上一个成品配方");addCraftingButton("›",1,150,20,"下一个成品配方");
        addCraftingButton("‹",16,132,116,"上一页原料库存");addCraftingButton("›",17,150,116,"下一页原料库存");
        previousUpgradePage=addUpgradePageButton("‹",18,-64,"上一页升级模块");
        nextUpgradePage=addUpgradePageButton("›",19,-24,"下一页升级模块");
        updateButtons();
    }
    private void addCraftingButton(String label,int id,int x,int y,String tip){
        craftingButtons.add(addRenderableWidget(Button.builder(Component.literal(label),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id))
                .bounds(leftPos+x,topPos+y,18,16).tooltip(Tooltip.create(Component.literal(tip))).build()));
    }
    private Button addUpgradePageButton(String label,int id,int x,String tip){
        return addRenderableWidget(Button.builder(Component.literal(label),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id))
                .bounds(leftPos+x,topPos+108,12,14).tooltip(Tooltip.create(Component.literal(tip))).build());
    }
    private void updateButtons(){
        boolean crafting=menu.kind().equals("autocrafter"),stonecutting=menu.kind().equals("auto_stonecutter"),sample=!menu.container.getItem(IndustryBlockEntity.CRAFTING_SAMPLE).isEmpty();
        int stonecuttingChoices=stonecutting?menu.stonecuttingChoices(minecraft.level).size():0;
        int sidebar=MachineUpgrades.supports(menu.kind())?68:0;
        leftPos=Math.max(4+sidebar,(width-(crafting?324:176)-sidebar)/2+sidebar);
        refreshPreview();
        for(int i=0;i<craftingButtons.size();i++){
            craftingButtons.get(i).setX(leftPos+(i==0?8:i==1?(crafting||stonecutting?28:150):i==2?132:150));
            craftingButtons.get(i).active=i>=2||!crafting&&!stonecutting||(crafting?previewChoiceCount:stonecuttingChoices)>1&&menu.data.get(2)==0;
        }
        for(int i=0;i<craftingButtons.size();i++)craftingButtons.get(i).visible=i<2?menu.canSelectRecipe()&&(!crafting||sample):crafting;
        craftingButtons.get(1).setX(leftPos+(crafting||stonecutting?28:150));
        for(int i=0;i<2;i++)craftingButtons.get(i).setTooltip(Tooltip.create(Component.literal(crafting?"切换该样品对应的其他工作台配方":stonecutting?"切换同一成品的其他切石配方；样品可选":"切换成品配方")));
        previousUpgradePage.setX(leftPos-64);nextUpgradePage.setX(leftPos-24);
        boolean pagedUpgrades=sidebar>0&&menu.upgradePages()>1;
        previousUpgradePage.visible=pagedUpgrades;nextUpgradePage.visible=pagedUpgrades;
        previousUpgradePage.active=menu.upgradePage()>0;
        nextUpgradePage.active=menu.upgradePage()+1<menu.upgradePages();
    }
    private void refreshPreview(){
        if(!menu.kind().equals("autocrafter")||minecraft.level==null)return;
        int selected=menu.data.get(12);
        if(previewLevel==minecraft.level&&previewIndex==selected)return;
        previewLevel=minecraft.level;previewIndex=selected;
        var recipes=AutomaticCrafting.recipes(minecraft.level);
        previewRecipe=selected>=0&&selected<recipes.size()?recipes.get(selected):null;
        previewCells=previewRecipe==null?List.of():AutomaticCrafting.previewGrid(previewRecipe.value());
        previewResult=previewRecipe==null?ItemStack.EMPTY:previewRecipe.value().getResultItem(minecraft.level.registryAccess()).copy();
        var choices=AutomaticCrafting.forResult(minecraft.level,previewResult);previewChoiceCount=choices.size();
        previewPosition=previewRecipe==null?"":(choices.indexOf(previewRecipe)+1)+" / "+choices.size();
    }
    /** Isolated native-client visual fixture, never used by a player menu. */
    void reviewRecipe(RecipeHolder<?> recipe,ItemStack result,String position,int choices){
        if(!Boolean.getBoolean("starfall.visualCheck"))throw new IllegalStateException("Visual review not enabled");
        previewRecipe=recipe;previewCells=AutomaticCrafting.previewGrid(recipe.value());previewResult=result.copy();previewPosition=position;previewChoiceCount=choices;
    }
    private ItemStack ingredientExample(Ingredient ingredient){
        var items=ingredient.getItems();return items.length==0?ItemStack.EMPTY:items[(int)(System.currentTimeMillis()/1000%items.length)];
    }
    private void renderCraftingPreview(GuiGraphics g){
        if(!menu.kind().equals("autocrafter"))return;
        int x=leftPos+184,y=topPos;
        g.fill(x,y,x+140,y+142,0xff26373b);g.fill(x+2,y+2,x+138,y+140,0xffe2e5dc);
        g.fill(x+2,y+2,x+138,y+18,0xff344d50);g.drawString(font,"工作台配方",x+8,y+6,0xffedf2e8,false);
        g.drawString(font,previewPosition,x+8,y+25,0xff43554d,false);
        for(int i=0;i<9;i++){
            int cx=x+10+i%3*18,cy=y+42+i/3*18;previewCell(g,cx,cy);
            if(i<previewCells.size())g.renderItem(ingredientExample(previewCells.get(i)),cx,cy);
        }
        g.drawString(font,"→",x+70,y+65,0xff43554d,false);previewCell(g,x+104,y+60);
        if(!previewResult.isEmpty()){g.renderItem(previewResult,x+104,y+60);g.renderItemDecorations(font,previewResult,x+104,y+60);}
        String name=previewResult.isEmpty()?(menu.container.getItem(IndustryBlockEntity.CRAFTING_SAMPLE).isEmpty()?"放入目标成品查看":"该物品无工作台配方"):previewResult.getHoverName().getString();
        g.drawString(font,font.plainSubstrByWidth(name,124),x+8,y+105,0xff304b43,false);
        g.drawString(font,"仅预览 · 不放入物品",x+8,y+123,0xff546961,false);
    }
    private void previewCell(GuiGraphics g,int x,int y){g.fill(x-1,y-1,x+17,y+17,0xff667a77);g.fill(x,y,x+16,y+16,0xffb4bcb8);}
    private void previewTooltip(GuiGraphics g,int mx,int my){
        if(!menu.kind().equals("autocrafter"))return;
        int x=leftPos+184,y=topPos;
        for(int i=0;i<previewCells.size();i++){
            int cx=x+10+i%3*18,cy=y+42+i/3*18;
            if(mx>=cx&&mx<cx+16&&my>=cy&&my<cy+16){var stack=ingredientExample(previewCells.get(i));if(!stack.isEmpty())g.renderTooltip(font,stack,mx,my);return;}
        }
        if(mx>=x+104&&mx<x+120&&my>=y+60&&my<y+76&&!previewResult.isEmpty())g.renderTooltip(font,previewResult,mx,my);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(menu.kind().equals("autocrafter")&&mx>=leftPos+184&&mx<leftPos+324&&my>=topPos&&my<topPos+142)return true;
        return super.mouseClicked(mx,my,button);
    }
    @Override protected boolean hasClickedOutside(double mx,double my,int left,int top,int button){
        if(MachineUpgrades.supports(menu.kind())&&mx>=left-68&&mx<left-8&&my>=top&&my<top+144)return false;
        return super.hasClickedOutside(mx,my,left,top,button);
    }
    private void text(GuiGraphics g,String text,int x,int y,int color){g.drawString(font,text,leftPos+x,topPos+y,color,false);}
    private void renderUpgrades(GuiGraphics g){
        if(!MachineUpgrades.supports(menu.kind()))return;
        int x=leftPos-68,y=topPos;
        g.fill(x,y,x+60,y+144,0xff26373b);g.fill(x+2,y+2,x+58,y+142,0xffe2e5dc);
        g.fill(x+2,y+2,x+58,y+18,0xff344d50);
        text(g,"升级",-60,6,0xffedf2e8);text(g,"数量叠加",-64,24,0xff43554d);
        if(menu.upgradePages()>1){
            String page=font.plainSubstrByWidth((menu.upgradePage()+1)+"/"+menu.upgradePages(),26);
            text(g,page,-38-font.width(page)/2,111,0xff304b43);
        }else text(g,"按需增槽",-64,111,0xff304b43);
        text(g,"停工取放",-64,128,0xff546961);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;String kind=menu.kind();boolean crafting=kind.equals("autocrafter"),plant=kind.equals("plant_cultivator"),filter=menu.hasItemFilter();
        g.fill(x,y,x+176,y+256,0xff26373b);g.fill(x+2,y+2,x+174,y+254,0xffe2e5dc);
        g.fill(x+2,y+2,x+174,y+18,0xff344d50);g.fill(x+6,y+160,x+170,y+161,0xffadb6ad);
        renderUpgrades(g);
        for(var slot:menu.slots)if(slot.isActive()){
            int color=menu.isUpgradeMenuSlot(slot.index)?0xffc7d9bd:slot.index>=63&&slot.index<72?0xffc7d9bd:slot.index>=9&&slot.index<=12?0xffb6cdb7:0xffb4bcb8;
            g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff667a77);g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,color);
        }
        if(kind.equals("supply_crate")){text(g,"物品库存",8,34,0xff43554d);text(g,"白名单：持物点击；空手清除",8,133,0xff43554d);return;}
        if(filter){text(g,kind.equals("item_router")?"背面输入 → 左 / 前 / 右输出":"背面抽取 → 正面输出",8,34,0xff43554d);
            if(kind.equals("item_router"))text(g,"每列筛选左 / 前 / 右分支",8,118,0xff43554d);
            text(g,"白名单：持物点击；空手清除",8,133,0xff43554d);return;}
        if(crafting){boolean sample=!menu.container.getItem(IndustryBlockEntity.CRAFTING_SAMPLE).isEmpty();text(g,font.plainSubstrByWidth(menu.templateLabel(minecraft.level),sample?94:136),sample?50:8,24,0xff304b43);text(g,"样品",128,36,0xff43554d);}
        else if(kind.equals("auto_stonecutter")){text(g,font.plainSubstrByWidth(menu.templateLabel(minecraft.level),94),50,24,0xff304b43);text(g,"样品",128,36,0xff43554d);}
        else if(kind.equals("auto_brewer"))text(g,"自动酿造 · 每瓶槽限1件",8,24,0xff304b43);
        else if(kind.equals("auto_smith"))text(g,"自动锻造 · 按槽位投入",8,24,0xff304b43);
        else if(kind.equals("auto_blast_furnace"))text(g,"电驱高炉 · 按来料加工",8,24,0xff304b43);
        else if(kind.equals("auto_smoker"))text(g,"电驱烟熏 · 按来料加工",8,24,0xff304b43);
        else if(kind.equals("prep"))text(g,"按来料自动整备",8,24,0xff304b43);
        else if(kind.equals("quarry")){text(g,"四块同种基底 · 前方留空",8,24,0xff304b43);text(g,"基础：石头 / 铜 / 铁",8,45,0xff546961);}
        else if(kind.equals("phase_extractor")){text(g,"四块同种凝矿基座 · 持续再生",8,24,0xff304b43);text(g,"20秒 / 500mB / 80000XH",8,45,0xff546961);}
        else if(kind.equals("crusher"))text(g,"按来料破碎 · 2秒 / 2000XH",8,24,0xff304b43);
        else if(kind.equals("press"))text(g,"自动辊压板材 · 2秒 / 1000XH",8,24,0xff304b43);
        else if(menu.canSelectRecipe())text(g,font.plainSubstrByWidth(menu.templateLabel(minecraft.level),116),30,24,0xff304b43);
        else text(g,"顶部进料 · 底部出料 · 红石停机",8,22,0xff546961);
        if(kind.equals("auto_brewer")){String[] roles={"瓶1","瓶2","瓶3","材料","燃粉"};for(int i=0;i<roles.length;i++)text(g,roles[i],8+i*18,35,0xff43554d);}
        else if(kind.equals("auto_smith")){String[] roles={"模板","装备","材料"};for(int i=0;i<roles.length;i++)text(g,roles[i],8+i*18,35,0xff43554d);}
        else if(plant)text(g,"环境 · 样本 · 水桶",8,35,0xff43554d);else if(menu.visible(0))text(g,"原料",8,35,0xff43554d);
        if(menu.visible(9))text(g,plant?"培养产物 / 空桶":"成品",8,72,0xff43554d);
        if(menu.visible(13))text(g,"燃料",80,72,0xff43554d);if(menu.visible(14))text(g,"附件",104,72,0xff43554d);
        int progress=(int)Math.min(100,(long)menu.data.get(2)*100/Math.max(1,menu.data.get(3)));
        g.fill(x+8,y+106,x+168,y+111,0xffb9c4ba);g.fill(x+8,y+106,x+8+160*progress/100,y+111,0xff48977b);
        if(menu.slots.get(IndustryMenu.BATTERY_MENU_SLOT).isActive())text(g,"电池",8,109,0xff43554d);
        if(crafting)text(g,"库存 "+(menu.craftingPage()+1)+" / 6",65,120,0xff43554d);
        text(g,menu.energy()+" XH",32,crafting?135:122,0xff8c662e);
        if(kind.equals("auto_stonecutter"))text(g,"样品可选 · 无预设按来料",32,135,0xff546961);
        if(plant||Set.of("phase_extractor","prep","refiner","bio_processor","pump","dew","g05").contains(kind))
            text(g,plant?"培养水 "+menu.data.get(9)+" mB":"液体 "+menu.data.get(9)+" / "+menu.data.get(10)+" / "+menu.data.get(11)+" mB",32,135,0xff386f85);
        g.drawString(font,Component.translatable("gui.starfall_blueprint.industry.state."+menu.data.get(4)),x+32,y+148,0xff43554d,false);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,font.plainSubstrByWidth(title.getString(),menu.kind().equals("prep")?100:160),8,6,0xffedf2e8,false);g.drawString(font,playerInventoryTitle,8,inventoryLabelY,0xff43554d,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        updateButtons();super.render(g,mx,my,delta);renderCraftingPreview(g);renderTooltip(g,mx,my);previewTooltip(g,mx,my);
        if(hoveredSlot==null){
            int ry=my-topPos,rx=mx-leftPos;
            if(rx>=30&&rx<172&&ry>=120&&ry<145){var lines=new ArrayList<Component>();lines.add(Component.literal("储能："+menu.energy()+" / "+menu.energyCapacity()+" XH"));if(Set.of("phase_extractor","prep","refiner","bio_processor","pump","dew","g05","plant_cultivator").contains(menu.kind()))lines.add(Component.literal("每个液槽容量："+menu.fluidCapacity()+" mB"));g.renderComponentTooltip(font,lines,mx,my);}return;
        }int i=hoveredSlot.index;String label="";
        if(menu.isUpgradeMenuSlot(i)){
            if(hoveredSlot.hasItem())return; // Keep the installed module's own item tooltip readable.
            var lines=new ArrayList<Component>();
            lines.add(Component.literal("升级槽 · 仅停工可取放"));
            lines.add(Component.literal("放入升级后自动增加一个空位"));
            lines.add(Component.literal("加工：超频驱动 / 节能回路 / 高效核心"));
            lines.add(Component.literal("扩容：储能矩阵 / 能流增幅 / 流体容仓"));
            lines.add(Component.literal("按设备适配；通用每格64件，效果按数量叠加"));
            lines.add(Component.literal("扩容件取出前须腾出对应储存空间"));
            if(menu.kind().equals("quarry")){lines.add(Component.literal("专属：广谱共振 / 富矿采集 / 精准采集 / 炽热熔炼"));lines.add(Component.literal("专属每格1件；时运取最高，精准与时运、熔炼互斥"));}
            if(menu.kind().equals("auto_furnace"))lines.add(Component.literal("专属：并行冶炼"));
            g.renderComponentTooltip(font,lines,mx,my);return;
        }
        else if(i==IndustryMenu.SAMPLE_MENU_SLOT)label=menu.kind().equals("auto_stonecutter")?"切石成品样品（可选）：保留不消耗，可随时更换；加工中更换只影响下一批":"配方样品：放入想制作的成品；保留不消耗，可随时取回";
        else if(i>=63&&i<72)label=menu.kind().equals("item_router")?"分流白名单：本列对应左 / 前 / 右；持物点击设置，空手清除":"物品白名单：持物点击设置，空手清除；全空接受所有物品";
        else if(i==IndustryMenu.BATTERY_MENU_SLOT)label=menu.kind().equals("supply_crate")?"补给箱库存":"小型电池";
        else if(i>IndustryMenu.BATTERY_MENU_SLOT&&i<IndustryMenu.PREPARATION_UPGRADE_MENU_SLOT)label="补给箱库存";
        else if(!hoveredSlot.hasItem()&&i<27&&menu.kind().equals("auto_brewer"))label=i<3?"药瓶槽 "+(i+1)+"：每格1瓶（侧面投入）":i==3?"酿造材料（顶部投入）":i==4?"烈焰粉（顶部投入）":i>=9&&i<=12?"酿造成品 / 余物（底部取出）":"";
        else if(!hoveredSlot.hasItem()&&i<27&&menu.kind().equals("auto_smith"))label=i==0?"锻造模板（顶部投入）":i==1?"待锻造装备（侧面投入）":i==2?"锻造材料（背面投入）":i>=9&&i<=12?"锻造成品 / 余物（底部取出）":"";
        else if(!hoveredSlot.hasItem()&&i==0&&menu.kind().equals("auto_stonecutter"))label="切石原料；未指定配方时按来料选择第一条";
        else if(!hoveredSlot.hasItem()&&i<27)
            label=menu.kind().equals("plant_cultivator")?(i==0?"环境方块（保留）":i==1?"植物样本（保留）":i==2?"水桶（返还空桶）":i<13?"培养产物 / 空桶":"小型电池"):
                    menu.kind().equals("supply_crate")?"补给箱库存":i<9?"原料（顶部投入）":i<13?"成品（底部取出）":i==13?"燃料":i<16?"工序附件":i==16?"提速模块":i==17?"扩批模块":"小型电池";
        if(!label.isEmpty())g.renderTooltip(font,Component.literal(label),mx,my);
    }
}
