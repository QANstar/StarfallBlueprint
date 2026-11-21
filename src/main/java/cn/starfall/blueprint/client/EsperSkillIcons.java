package cn.starfall.blueprint.client;

import cn.starfall.blueprint.Esper;
import net.minecraft.client.gui.GuiGraphics;

/** Original 15 px glyphs, rasterized once to keep outlines sharp at Minecraft GUI scales. */
final class EsperSkillIcons {
    private static final int SIZE = 15;
    private static final byte[][] GLYPHS = new byte[Esper.SKILLS.length][];

    static {
        for (Esper.Skill skill : Esper.SKILLS) GLYPHS[skill.ordinal()] = make(skill);
    }

    static void draw(GuiGraphics graphics, Esper.Skill skill, int x, int y) {
        byte[] glyph = GLYPHS[skill.ordinal()];
        boolean electric = skill.school == Esper.School.ELECTRIC;
        int light = electric ? 0xffeaf7ff : 0xffeffffc;
        int color = electric ? 0xff65b8ff : 0xff63d7d5;
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE;) {
                byte tone = glyph[row * SIZE + col];
                int end = col + 1;
                while (end < SIZE && glyph[row * SIZE + end] == tone) end++;
                if (tone != 0)
                    graphics.fill(x + col, y + row, x + end, y + row + 1, tone == 2 ? light : color);
                col = end;
            }
        }
    }

    private static byte[] make(Esper.Skill skill) {
        Glyph g = new Glyph();
        switch (skill) {
            case BLINK -> g.accent(1, 10, 5, 10).accent(2, 13, 6, 13).ink(9, 2, 9, 12, 14, 7, 9, 2);
            case PORTAL -> g.accent(3, 1, 11, 1, 13, 4, 13, 10, 11, 13, 3, 13, 1, 10, 1, 4, 3, 1)
                    .ink(5, 3, 8, 2, 10, 5, 10, 9, 8, 12, 5, 11, 4, 7, 5, 3);
            case SLASH -> g.accent(2, 3, 6, 7, 12, 11).accent(3, 3, 7, 7, 12, 10)
                    .ink(2, 12, 5, 10, 9, 6, 12, 2).ink(4, 12, 8, 9, 12, 4);
            case STORAGE -> g.accent(2, 5, 7, 2, 12, 5, 7, 8, 2, 5).ink(2, 5, 2, 10, 7, 13, 12, 10, 12, 5)
                    .ink(7, 8, 7, 13).ink(5, 5, 9, 5);
            case COLLAPSE -> g.accent(5, 2, 9, 2, 12, 5, 12, 9, 9, 12, 5, 12, 2, 9, 2, 5, 5, 2)
                    .ink(4, 4, 6, 6).ink(10, 4, 8, 6).ink(4, 10, 6, 8).ink(10, 10, 8, 8).ink(7, 6, 7, 8);
            case VOLLEY -> g.accent(1, 9, 4, 6, 6, 5).accent(3, 12, 6, 9, 9, 8)
                    .ink(5, 4, 12, 4, 10, 2).ink(12, 4, 10, 6).ink(8, 9, 13, 9, 11, 7).ink(13, 9, 11, 11);
            case SLING -> g.accent(2, 11, 2, 6, 6, 3, 10, 4, 12, 8, 9, 11, 5, 10, 5, 7, 8, 6, 9, 8)
                    .ink(1, 13, 13, 1);
            case SHIFT -> g.accent(1, 11, 4, 8, 7, 7, 10, 8, 13, 11).ink(4, 6, 7, 2, 10, 6)
                    .ink(7, 2, 7, 11).accent(4, 13, 10, 13);
            case PULL -> g.accent(12, 2, 12, 5).accent(12, 9, 12, 12).ink(12, 7, 2, 7, 6, 3).ink(2, 7, 6, 11);
            case DOMAIN -> g.accent(7, 1, 13, 7, 7, 13, 1, 7, 7, 1).ink(7, 4, 10, 7, 7, 10, 4, 7, 7, 4);
            case BARRIER -> g.ink(7, 1, 12, 3, 12, 7, 10, 11, 7, 13, 4, 11, 2, 7, 2, 3, 7, 1)
                    .accent(4, 6, 6, 8, 10, 4).accent(6, 8, 6, 10);
            case LOCK -> g.accent(4, 3, 2, 3, 2, 6).accent(10, 3, 12, 3, 12, 6)
                    .accent(2, 9, 2, 12, 5, 12).accent(12, 9, 12, 12, 9, 12)
                    .ink(5, 5, 9, 5, 9, 10, 5, 10, 5, 5).ink(6, 5, 6, 2, 8, 2, 8, 5).ink(7, 7, 7, 8);
            case ARC -> g.ink(1, 11, 5, 7, 4, 4, 8, 5, 12, 2).accent(7, 5, 9, 9, 13, 10).accent(11, 12, 13, 10, 13, 8);
            case INDUCTION -> g.ink(2, 8, 2, 12, 12, 12, 12, 8).accent(4, 10, 10, 10)
                    .ink(7, 1, 7, 8, 4, 5).ink(7, 8, 10, 5);
            case SPARK -> g.ink(7, 1, 7, 5, 13, 7, 8, 8, 7, 13, 6, 8, 1, 7, 6, 6, 7, 1)
                    .accent(2, 2, 4, 4).accent(10, 10, 12, 12);
            case BOOST -> g.accent(1, 3, 5, 7, 1, 11).ink(5, 3, 9, 7, 5, 11).ink(9, 3, 13, 7, 9, 11);
            case FEED -> g.accent(2, 3, 2, 11, 6, 11, 6, 3, 2, 3).ink(5, 7, 12, 7, 10, 4).ink(12, 7, 10, 10);
            case INSULATE -> g.accent(7, 1, 12, 3, 12, 8, 7, 13, 2, 8, 2, 3, 7, 1)
                    .ink(7, 3, 5, 7, 9, 7, 7, 10);
            case SCAN -> g.accent(1, 9, 2, 5, 6, 2, 10, 3, 13, 7, 12, 11, 8, 13, 4, 12, 1, 9)
                    .ink(7, 8, 12, 4).ink(7, 7, 7, 9).accent(3, 8, 4, 8);
            case PARALYZE -> g.accent(2, 2, 2, 12, 12, 12, 12, 2, 2, 2).ink(5, 4, 5, 10).ink(9, 4, 9, 10);
            case PULSE -> g.accent(1, 7, 4, 4, 7, 3, 10, 4, 13, 7, 10, 10, 7, 11, 4, 10, 1, 7)
                    .ink(4, 7, 6, 7, 7, 5, 8, 9, 9, 7, 11, 7);
            case OVERDRIVE -> g.accent(2, 10, 2, 7, 5, 4, 9, 4, 12, 7, 12, 10).ink(7, 9, 10, 5).ink(4, 12, 10, 12);
            case STORM -> g.accent(1, 5, 3, 3, 6, 3, 8, 1, 11, 3, 13, 5, 13, 6, 1, 6, 1, 5)
                    .ink(6, 8, 4, 11, 7, 11, 5, 13).ink(11, 8, 9, 11, 12, 11, 10, 13);
            case SURGE -> g.ink(7, 12, 7, 6, 2, 2).ink(7, 6, 7, 2).ink(7, 6, 12, 2)
                    .accent(2, 5, 2, 2, 5, 2).accent(9, 2, 12, 2, 12, 5);
            case LANCE -> g.accent(1, 10, 7, 4).accent(4, 13, 10, 7).ink(2, 12, 12, 2, 8, 2).ink(12, 2, 12, 6);
            case IONIZE -> g.accent(5, 2, 9, 2, 12, 5, 12, 9, 9, 12, 5, 12, 2, 9, 2, 5, 5, 2)
                    .ink(7, 4, 5, 7, 9, 7, 7, 10);
            case CAPACITOR -> g.accent(2, 3, 12, 3, 12, 11, 2, 11, 2, 3).ink(5, 7, 9, 7).ink(7, 5, 7, 9);
            case RECYCLING -> g.ink(2, 7, 3, 3, 9, 3, 12, 5, 12, 2).accent(12, 7, 11, 11, 5, 11, 2, 9, 2, 12);
            case ANCHOR -> g.ink(7, 2, 7, 12, 3, 11, 1, 8).ink(7, 12, 11, 11, 13, 8).accent(4, 5, 10, 5);
            case RESONANCE -> g.accent(1, 9, 4, 6, 7, 9, 10, 6, 13, 9).ink(1, 6, 4, 3, 7, 6, 10, 3, 13, 6);
        }
        return g.pixels;
    }

    private static final class Glyph {
        private final byte[] pixels = new byte[SIZE * SIZE];

        Glyph ink(int... points) { return stroke((byte) 2, points); }
        Glyph accent(int... points) { return stroke((byte) 1, points); }

        private Glyph stroke(byte tone, int[] points) {
            for (int i = 2; i < points.length; i += 2) {
                int x = points[i - 2], y = points[i - 1];
                int dx = points[i] - x, dy = points[i + 1] - y;
                int steps = Math.max(Math.abs(dx), Math.abs(dy));
                for (int step = 0; step <= steps; step++) {
                    float t = steps == 0 ? 0 : (float) step / steps;
                    int index = (y + Math.round(dy * t)) * SIZE + x + Math.round(dx * t);
                    pixels[index] = (byte) Math.max(pixels[index], tone);
                }
            }
            return this;
        }
    }

    private EsperSkillIcons() {}
}
