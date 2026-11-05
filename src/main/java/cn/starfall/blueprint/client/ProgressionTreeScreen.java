package cn.starfall.blueprint.client;

import cn.starfall.blueprint.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Full-window dependency canvas. The existing server menu remains the transaction authority. */
public final class ProgressionTreeScreen extends Screen implements MenuAccess<ProgressionMenu> {
    private static final int CW=154,CH=62,INK=0xffe0e8eb,MUTED=0xff9baabd,GOOD=0xff77d3a1,RED=0xffe76a7e;
    private static final int[][] LC={{0,0},{230,0},{460,0},{690,0},{460,-300},{690,-300},{460,300},{690,300},{920,300},{690,-150},{920,-150},{1150,-150},{690,150},{920,150},{920,0},{1150,150}};
    private final ProgressionMenu menu;
    private final Map<Integer,double[]> positions=new LinkedHashMap<>();
    private String category="all";
    private int selected=-1,vx,vy,vw,vh,px,pw,detailScroll,detailHeight;
    private double zoom=.7,panX,panY,clickX,clickY;
    private boolean dragging,moved,first=true;
    private Button learn;
    public ProgressionTreeScreen(ProgressionMenu menu,Inventory inventory,Component title){super(title);this.menu=menu;}
    public ProgressionMenu getMenu(){return menu;}
    private static Component label(String key,Object... args){return Component.translatable("growth.starfall_blueprint."+key,args);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    @Override protected void init(){
        pw=Math.min(236,Math.max(142,width/3));vx=8;vy=57;px=width-pw-8;vw=px-vx-8;vh=height-vy-23;
        String[] categories={"all","best","lingchuan"};
        for(int i=0;i<categories.length;i++){
            String target=categories[i];var button=addRenderableWidget(Button.builder(label("category."+target),b->{category=target;selected=-1;detailScroll=0;layout();overview();rebuildWidgets();}).bounds(8+i*74,31,70,18).build());button.active=!category.equals(target);
        }
        addRenderableWidget(Button.builder(label("back"),b->onClose()).bounds(width-78,8,70,18).build());
        int controls=Math.max(vx,vx+vw-106);
        addRenderableWidget(Button.builder(Component.literal("−"),b->zoomAt(vx+vw*.5,vy+vh*.5,1/1.2)).bounds(controls,height-44,22,18).build());
        addRenderableWidget(Button.builder(Component.literal("+"),b->zoomAt(vx+vw*.5,vy+vh*.5,1.2)).bounds(controls+24,height-44,22,18).build());
        addRenderableWidget(Button.builder(Component.translatable("lc.canvas_overview"),b->overview()).bounds(controls+48,height-44,58,18).build());
        learn=addRenderableWidget(Button.builder(label("learn"),b->{if(selected>=0&&menu.preview(selected).executable()&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,selected);}).bounds(px+8,height-51,pw-16,21).build());
        layout();if(first){overview();first=false;}
    }
    private void layout(){
        positions.clear();var depths=new HashMap<String,Integer>();var rows=new HashMap<String,Integer>();
        for(int i=0;i<Progression.NODES.size();i++){
            var node=Progression.NODES.get(i);if(!category.equals("all")&&!category.equals(node.category()))continue;
            int depth=0;for(var parent:node.prerequisites())depth=Math.max(depth,depths.getOrDefault(parent,0)+1);depths.put(node.id(),depth);
            double nx,ny;
            if(node.category().equals("lingchuan")){
                int n=-1;for(int j=0;j<Lingchuan.RESEARCH.size();j++)if(node.id().equals(Progression.lingchuanId(j)))n=j;
                if(n>=0){nx=LC[n][0];ny=LC[n][1]+300;}else {nx=depth*230;ny=0;}
            }else {
                String lane=node.category()+depth;int row=rows.getOrDefault(lane,0);rows.put(lane,row+1);
                nx=depth*230;ny=row*100+(category.equals("all")?850:0);
            }
            positions.put(i,new double[]{nx,ny});
        }
        if(!positions.containsKey(selected))selected=positions.isEmpty()?-1:positions.keySet().iterator().next();
    }
    private boolean inside(double x,double y,int ax,int ay,int w,int h){return x>=ax&&x<ax+w&&y>=ay&&y<ay+h;}
    private boolean viewport(double x,double y){return inside(x,y,vx,vy,vw,vh);}
    private void zoomAt(double x,double y,double factor){double wx=(x-vx-panX)/zoom,wy=(y-vy-panY)/zoom;zoom=Math.clamp(zoom*factor,.18,2.2);panX=x-vx-wx*zoom;panY=y-vy-wy*zoom;}
    private void overview(){
        if(positions.isEmpty())return;
        double maxX=positions.values().stream().mapToDouble(p->p[0]+CW).max().orElse(CW),maxY=positions.values().stream().mapToDouble(p->p[1]+CH).max().orElse(CH);
        zoom=Math.clamp(Math.min((vw-24)/maxX,(vh-24)/maxY),.18,1);panX=(vw-maxX*zoom)/2;panY=(vh-maxY*zoom)/2;
    }
    private int hit(double x,double y){
        if(!viewport(x,y))return -1;double wx=(x-vx-panX)/zoom,wy=(y-vy-panY)/zoom;
        for(var e:positions.entrySet()){var p=e.getValue();if(wx>=p[0]&&wx<p[0]+CW&&wy>=p[1]&&wy<p[1]+CH)return e.getKey();}return -1;
    }
    private int color(int node){return menu.isLearned(Progression.NODES.get(node).id())?GOOD:Progression.NODES.get(node).prerequisites().stream().allMatch(menu::isLearned)?RED:0xff667183;}
    private static void line(GuiGraphics g,int x,int y,int x2,int y2,int color){g.fill(Math.min(x,x2),Math.min(y,y2),Math.max(x,x2)+1,Math.max(y,y2)+1,color);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xff10151e);g.fill(0,0,width,27,0xff242a38);
        g.drawString(font,label("title"),12,9,INK,false);
        g.drawString(font,label("progress",Progression.NODES.stream().filter(n->menu.isLearned(n.id())).count(),Progression.NODES.size()),Math.max(100,width/2-45),9,MUTED,false);
        g.fill(vx,vy,vx+vw,vy+vh,0xff141c29);g.enableScissor(vx,vy,vx+vw,vy+vh);
        double grid=32*zoom;if(grid>=7){for(double x=vx+panX%grid;x<vx+vw;x+=grid)g.fill((int)x,vy,(int)x+1,vy+vh,0xff202b3b);for(double y=vy+panY%grid;y<vy+vh;y+=grid)g.fill(vx,(int)y,vx+vw,(int)y+1,0xff202b3b);}
        g.pose().pushPose();g.pose().translate(vx+panX,vy+panY,0);g.pose().scale((float)zoom,(float)zoom,1);
        for(var e:positions.entrySet()){
            var child=Progression.NODES.get(e.getKey());var end=e.getValue();
            for(var parent:child.prerequisites())for(var from:positions.entrySet())if(Progression.NODES.get(from.getKey()).id().equals(parent)){
                int x=(int)from.getValue()[0]+CW,y=(int)from.getValue()[1]+CH/2,ex=(int)end[0],ey=(int)end[1]+CH/2,mid=(x+ex)/2,c=menu.isLearned(parent)?0xff649d8b:0xff46536b;
                line(g,x,y,mid,y,c);line(g,mid,y,mid,ey,c);line(g,mid,ey,ex-4,ey,c);for(int n=0;n<4;n++)g.fill(ex-5+n,ey-3+n,ex-4+n,ey+4-n,c);
            }
        }
        int hovered=hit(mx,my);
        for(var e:positions.entrySet()){
            int i=e.getKey(),x=(int)e.getValue()[0],y=(int)e.getValue()[1],accent=color(i);var node=Progression.NODES.get(i);
            g.fill(x+3,y+4,x+CW+3,y+CH+4,0x77000000);g.fill(x,y,x+CW,y+CH,i==selected?RED:i==hovered?0xffb3b4ca:0xff48546a);g.fill(x+1,y+1,x+CW-1,y+CH-1,i==selected?0xff352634:0xff222c3d);g.fill(x+1,y+1,x+4,y+CH-1,accent);
            g.renderItem(node.icon(),x+11,y+15);fit(g,node.title(),x+33,y+12,CW-40,INK);
            fit(g,label("card_cost",node.experienceLevels()),x+33,y+27,CW-40,MUTED);
            g.drawString(font,label(menu.isLearned(node.id())?"state.learned":node.prerequisites().stream().allMatch(menu::isLearned)?"state.available":"state.locked"),x+11,y+48,accent,false);
        }
        g.pose().popPose();g.disableScissor();
        g.drawString(font,Component.literal(Math.round(zoom*100)+"%"),vx+5,vy+vh-13,MUTED,false);
        details(g);
        g.drawString(font,font.plainSubstrByWidth(label("canvas_hint").getString(),width-16),8,height-15,MUTED,false);
        learn.active=selected>=0&&menu.preview(selected).executable();super.render(g,mx,my,partial);
    }
    private void fit(GuiGraphics g,Component text,int x,int y,int w,int color){float s=Math.min(1,w/(float)Math.max(1,font.width(text)));g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(s,s,1);g.drawString(font,text,0,0,color,false);g.pose().popPose();}
    private int wrapped(GuiGraphics g,Component text,int y,int color){for(var line:font.split(text,pw-20)){g.drawString(font,line,px+10,y,color,false);y+=11;}return y;}
    private void details(GuiGraphics g){
        g.fill(px,vy,px+pw,vy+vh,0xff202838);if(selected<0)return;
        var node=Progression.NODES.get(selected);g.fill(px,vy,px+pw,vy+2,color(selected));
        fit(g,node.title(),px+10,vy+11,pw-20,INK);
        g.enableScissor(px+5,vy+29,px+pw-5,height-80);int y=vy+32-detailScroll;
        y=wrapped(g,node.description(),y,MUTED)+10;
        y=wrapped(g,label("experience_summary",menu.xpLevels(),node.experienceLevels()),y,menu.xpLevels()>=node.experienceLevels()?GOOD:RED)+10;
        y=wrapped(g,label("prerequisites"),y,INK)+4;
        if(node.prerequisites().isEmpty())y=wrapped(g,label("none"),y,MUTED);
        for(String parent:node.prerequisites()){
            Component name=Progression.NODES.stream().filter(n->n.id().equals(parent)).findFirst().map(Progression.Node::title).orElse(Component.literal(parent));
            y=wrapped(g,label(menu.isLearned(parent)?"prerequisite_met":"prerequisite_missing",name),y,menu.isLearned(parent)?GOOD:RED)+3;
        }
        y=wrapped(g,label("materials_heading"),y+8,INK)+4;
        if(node.costs().isEmpty())y=wrapped(g,label("none"),y,MUTED);
        for(var cost:node.costs()){
            var stack=CatalogueContent.stack(cost.key(),1);int count=menu.slots.stream().mapToInt(s->CatalogueContent.matches(cost.key(),s.getItem())&&Progression.usableMaterial(s.getItem())?s.getItem().getCount():0).sum();
            g.renderItem(stack,px+10,y);fit(g,stack.getHoverName(),px+30,y,pw-40,INK);fit(g,label("material_count",count,cost.count()),px+30,y+10,pw-40,count>=cost.count()?GOOD:RED);y+=24;
        }
        y=wrapped(g,label("rewards_heading"),y+8,INK)+4;y=wrapped(g,label("rewards_once"),y,MUTED)+4;
        if(node.reward().experiencePoints()>0)y=wrapped(g,label("experience_reward",node.reward().experiencePoints()),y,GOOD)+4;
        for(var reward:node.reward().items()){var item=CatalogueContent.stack(reward.key(),1);g.renderItem(item,px+10,y);fit(g,item.getHoverName(),px+30,y,pw-40,INK);fit(g,label("item_reward",reward.count()),px+30,y+10,pw-40,GOOD);y+=24;}
        detailHeight=y-(vy+32-detailScroll);g.disableScissor();detailScroll=Math.clamp(detailScroll,0,Math.max(0,detailHeight-(height-80-vy-32)));
        fit(g,menu.preview(selected).reason(),px+8,height-70,pw-16,learn.active?GOOD:MUTED);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(viewport(x,y)){zoomAt(x,y,Math.pow(1.16,vertical));return true;}
        if(inside(x,y,px,vy,pw,vh)){detailScroll=Math.clamp(detailScroll-(int)(vertical*22),0,Math.max(0,detailHeight-(height-80-vy-32)));return true;}return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(viewport(x,y)&&button>=0&&button<=2){dragging=true;moved=false;clickX=x;clickY=y;return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging){if(Math.hypot(x-clickX,y-clickY)>3)moved=true;if(moved){panX+=dx;panY+=dy;}return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(dragging){dragging=false;if(!moved&&button==0){int n=hit(x,y);if(n>=0){selected=n;detailScroll=0;}}return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==82){overview();return true;}if(minecraft.options.keyInventory.matches(key,scan)){onClose();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void onClose(){if(minecraft.player!=null){minecraft.player.closeContainer();minecraft.setScreen(new InventoryScreen(minecraft.player));}}
    @Override public boolean isPauseScreen(){return false;}
}
