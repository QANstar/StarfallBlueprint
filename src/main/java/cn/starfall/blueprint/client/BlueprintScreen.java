package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BlueprintScreen extends AbstractContainerScreen<BlueprintMenu> {
    public static void installExamples(){
        try{
            Path folder=BlueprintFiles.directory(net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath());Files.createDirectories(folder);
            try(var stream=BlueprintScreen.class.getResourceAsStream("/assets/starfall_blueprint/construction_blueprints/index.json")){
                if(stream==null)return;
                var names=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonArray();
                for(var name:names){String file=name.getAsString();if(!file.matches("L[0-9]{2}\\.sfbp\\.json"))continue;Path target=folder.resolve(file);if(Files.exists(target))continue;
                    try(var source=BlueprintScreen.class.getResourceAsStream("/assets/starfall_blueprint/construction_blueprints/"+file)){if(source!=null)Files.copy(source,target);}
                }
            }
        }catch(Exception error){org.slf4j.LoggerFactory.getLogger(BlueprintScreen.class).warn("Unable to install example construction blueprints",error);}
    }
    private EditBox filename;private Button build,export,rotate,importButton,back,previous,next;
    private final List<Button> fileButtons=new ArrayList<>();
    private List<BlueprintFiles.Entry> files=List.of();private boolean browsing;private int page;
    private int scroll;private String message="",scanMessage="";
    public BlueprintScreen(BlueprintMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=352;imageHeight=248;inventoryLabelY=154;}
    private Path directory()throws java.io.IOException{Path path=BlueprintFiles.directory(minecraft.gameDirectory.toPath());Files.createDirectories(path);return path;}
    @Override protected void init(){
        super.init();String old=filename==null?"":filename.getValue();
        if(old.isEmpty()&&menu.plan()!=null&&menu.plan().title().matches("L[0-9]{2} .*"))old=menu.plan().title().substring(0,3)+".sfbp.json";
        filename=new EditBox(font,leftPos+184,topPos+174,158,18,Component.literal("蓝图文件名"));filename.setMaxLength(255);
        if(old.isEmpty())try(var files=Files.list(directory())){old=files.filter(p->p.getFileName().toString().endsWith(".sfbp.json")).sorted().findFirst().map(p->p.getFileName().toString()).orElse("");}catch(Exception ignored){}
        filename.setValue(old);filename.setHint(Component.literal("例如 L25.sfbp.json"));addRenderableWidget(filename);
        build=button("开始建造",184,18,94,()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0));
        rotate=button("旋转90°",282,18,60,()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,1));
        importButton=button("导入文件",184,198,76,this::importFile);export=button("导出设计图",266,198,76,this::exportFile);
        button("扫描目录",184,220,76,this::scanDirectory);
        button("打开目录",266,220,76,()->{try{Util.getPlatform().openPath(directory());}catch(Exception e){message="无法打开蓝图文件夹";}});
        back=button("返回材料",282,18,60,()->{browsing=false;refreshFiles();});
        previous=button("上一页",184,198,76,()->{page--;refreshFiles();});next=button("下一页",266,198,76,()->{page++;refreshFiles();});
        fileButtons.clear();
        for(int row=0;row<7;row++){final int index=row;fileButtons.add(button("",184,49+row*20,158,()->selectFile(index)));}
        refreshFiles();
    }
    private Button button(String text,int x,int y,int width,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(leftPos+x,topPos+y,width,18).build());}
    private Path selectedFile()throws java.io.IOException{
        return BlueprintFiles.resolve(directory(),filename.getValue());
    }
    public void scanDirectory(){
        try{files=BlueprintFiles.scan(directory());scanMessage=files.isEmpty()?"未找到 .sfbp.json 蓝图文件":"";}
        catch(Exception error){files=List.of();scanMessage="扫描失败："+(error.getMessage()==null?"无法读取目录":error.getMessage());}
        page=0;browsing=true;refreshFiles();
    }
    private void refreshFiles(){
        build.visible=rotate.visible=importButton.visible=export.visible=filename.visible=!browsing;
        back.visible=previous.visible=next.visible=browsing;
        int pages=Math.max(1,(files.size()+6)/7);page=Math.clamp(page,0,pages-1);previous.active=page>0;next.active=page<pages-1;
        for(int row=0;row<fileButtons.size();row++){
            var button=fileButtons.get(row);int index=page*7+row;button.visible=browsing&&index<files.size();
            if(index>=files.size())continue;var file=files.get(index);button.active=file.valid();
            String label=file.valid()?file.title()+" · "+file.blocks()+"方块":"无效："+file.filename();
            button.setMessage(Component.literal(font.plainSubstrByWidth(label,146)));
            button.setTooltip(Tooltip.create(Component.literal(file.filename()+"\n"+(file.valid()?file.title()+" · "+file.blocks()+"个方块\n点击选用并导入":file.error()))));
        }
        if(browsing)setFocused(back);
    }
    private void selectFile(int row){
        int index=page*7+row;if(index>=files.size()||!files.get(index).valid())return;
        filename.setValue(files.get(index).filename());browsing=false;refreshFiles();importFile();
    }
    private void importFile(){
        if(!menu.container.getItem(0).is(Blueprints.PLAN.get())){message="请先在左上角放一张设计图";return;}
        try{ConstructionPlan plan=BlueprintFiles.read(directory(),filename.getValue());
            PacketDistributor.sendToServer(new BlueprintNetworking.Import(menu.containerId,plan.json()));message="已发送导入请求";scroll=0;
        }catch(Exception error){message="导入失败："+(error.getMessage()==null?"无法读取文件":error.getMessage());}
    }
    private void exportFile(){
        var plan=menu.plan();if(plan==null){message="请先放入有效设计图";return;}
        try{
            if(filename.getValue().isBlank())filename.setValue("shared-plan.sfbp.json");Path file=selectedFile();
            // Keep previous shared designs; exporting never silently overwrites a file.
            if(Files.exists(file)){String base=file.getFileName().toString().replace(".sfbp.json","");int n=2;do{file=directory().resolve(base+"-"+(n++)+".sfbp.json");}while(Files.exists(file));}
            Files.writeString(file,plan.json(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);filename.setValue(file.getFileName().toString());message="已导出，可把文件分享给其他玩家";
        }catch(Exception error){message="导出失败："+error.getMessage();}
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        int x=leftPos,y=topPos;g.fill(x,y,x+352,y+248,0xff293f43);g.fill(x+2,y+2,x+350,y+246,0xffe2e5dc);g.fill(x+2,y+2,x+350,y+17,0xff344d50);g.fill(x+176,y+20,x+177,y+243,0xffa9b7ad);
        for(var slot:menu.slots){g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff667a77);g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,slot.index==0?0xff9fbbb8:0xffb4bcb8);}
        var plan=menu.plan();line(g,plan==null?"放入设计图，然后导入文件":plan.title(),32,30,136,0xff304b43);
        line(g,plan==null?"也可用测绘笔制作设计图":plan.blocks().size()+"方块 · 旋转 "+menu.data.get(0)*90+"°",32,44,136,0xff546961);
        String[] statuses={"起点在正前方2格，与施工器同高","设计图已导入","需要有效设计图","缺少材料","场地被占用或不可施工","建造完成","已建成，无需重复投入","红石暂停"};
        line(g,message.isEmpty()?statuses[Math.floorMod(menu.data.get(1),statuses.length)]:message,8,61,160,0xff546961);
        line(g,"施工材料箱（含相邻箱子）",8,75,160,0xff43554d);line(g,menu.freeConstruction()?"创造核心已启用 · 免材料建造":"所需材料 / 已有（滚轮翻阅）",184,37,160,0xff43554d);
        if(browsing){
            g.fill(x+180,y+18,x+346,y+216,0xffe2e5dc);
            line(g,"蓝图列表 · "+files.size()+"份",184,23,94,0xff304b43);
            line(g,"点选导入 · 悬停查看文件名",184,39,158,0xff546961);
            if(files.isEmpty()){line(g,scanMessage,184,72,158,0xffa54d3d);line(g,"把蓝图放入目录后重新扫描",184,93,158,0xff546961);}
            else line(g,(page+1)+" / "+Math.max(1,(files.size()+6)/7)+"页 · 最多列出256份",184,190,158,0xff546961);
            return;
        }
        var entries=new ArrayList<>(menu.required().entrySet());scroll=Math.clamp(scroll,0,Math.max(0,entries.size()-7));
        for(int row=0;row<7&&scroll+row<entries.size();row++){
            var entry=entries.get(scroll+row);int rowY=49+row*15;ItemStack item=new ItemStack(entry.getKey());g.renderItem(item,x+182,y+rowY-2);
            line(g,item.getHoverName().getString(),200,rowY+2,92,0xff43554d);int have=menu.available(entry.getKey());String count=menu.freeConstruction()?entry.getValue()+" / ∞":entry.getValue()+" / "+have;
            g.drawString(font,count,x+342-font.width(count),y+rowY+2,menu.freeConstruction()||have>=entry.getValue()?0xff397853:0xffa54d3d,false);
        }
        if(plan!=null&&entries.isEmpty())line(g,"区域已建成，无需材料",184,58,158,0xff397853);
        line(g,"将分享文件放入 blueprints",184,158,158,0xff546961);
    }
    private void line(GuiGraphics g,String text,int x,int y,int width,int color){g.drawString(font,font.plainSubstrByWidth(text,width),leftPos+x,topPos+y,color,false);}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,title,8,6,0xffedf2e8,false);g.drawString(font,playerInventoryTitle,8,inventoryLabelY,0xff43554d,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){build.active=menu.ready();export.active=menu.plan()!=null;super.render(g,mx,my,delta);renderTooltip(g,mx,my);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(browsing&&key==256){browsing=false;refreshFiles();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(x>=leftPos+180&&x<=leftPos+345){if(browsing&&y>=topPos+40&&y<=topPos+212){page-=(int)Math.signum(vertical);refreshFiles();return true;}if(y>=topPos+40&&y<=topPos+154){scroll=Math.max(0,scroll-(int)Math.signum(vertical));return true;}}return super.mouseScrolled(x,y,horizontal,vertical);}
}
