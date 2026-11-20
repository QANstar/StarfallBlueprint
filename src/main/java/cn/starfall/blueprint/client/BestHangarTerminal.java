package cn.starfall.blueprint.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Shared screen housing, inventory wells and illuminated bus bars for hangar terminals. */
final class BestHangarTerminal {
    static final int INK=0xff0e1922, PANEL=0xff192e3b, EDGE=0xff38525c, CYAN=0xff85e0d1, WHITE=0xffe1efed;
    static void frame(GuiGraphics g, Font font, int x, int y, int w, int h, String id) {
        g.fill(x-2,y-2,x+w+2,y+h+2,0xb0081117);
        g.fill(x,y,x+w,y+h,EDGE);
        g.fill(x+1,y+1,x+w-1,y+h-1,INK);
        g.fillGradient(x+2,y+2,x+w-2,y+20,0xff31515c,PANEL);
        g.fill(x+2,y+2,x+5,y+19,CYAN);
        g.fill(x+2,y+19,x+w-2,y+20,EDGE);
        g.drawString(font,id,x+w-font.width(id)-8,y+7,CYAN,false);
        g.fill(x+2,y+h-3,x+22,y+h-2,CYAN);
    }
    static void well(GuiGraphics g,int x,int y) {
        g.fill(x-1,y-1,x+17,y+17,EDGE);
        g.fillGradient(x,y,x+16,y+16,0xff142533,0xff273e4b);
        g.fill(x,y,x+4,y+1,0xff688990);
    }
}
