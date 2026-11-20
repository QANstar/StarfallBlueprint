"""Draw the original Best inventory sprites and facility faces with Pillow.

Run after the Best resource generators. The RE_BFL sync can import render_items()
for the canonical item-art IDs or pass --item-dir to export only inventory PNGs.
"""
from argparse import ArgumentParser
import json
from pathlib import Path

from PIL import Image, ImageDraw

ART = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/starfall_blueprint'
MOD = 'starfall_blueprint'
S = 32
INK = '#131922'
BLACK = '#1c2732'
STEEL = ('#354452', '#586e7a', '#9aacac')
YELLOW = ('#9e5628', '#e9a638', '#ffdc76')
RED = ('#602d39', '#a74548', '#e17763')
NAVY = ('#263c5e', '#405e80', '#769bb5')
WHITE = ('#6a859b', '#c4d5d9', '#f2f2de')
CYAN = ('#226a80', '#4bc9d1', '#b2fae8')
GREEN = ('#397461', '#76bb83', '#c2eda2')
PURPLE = ('#514369', '#a084ad', '#dbc6e1')


class Sprite:
    """Integer-grid shapes with a one-pixel ink edge and no resampling."""

    def __init__(self, background=None):
        self.image = Image.new('RGBA', (S, S), background)
        self.draw = ImageDraw.Draw(self.image)

    def poly(self, color, *points, edge=INK):
        self.draw.polygon(points, fill=color, outline=edge)

    def box(self, color, box, edge=None):
        self.draw.rectangle(box, fill=color, outline=edge)

    def line(self, color, *points, width=1):
        self.draw.line(points, fill=color, width=width)

    def dot(self, color, *points):
        self.draw.point(points, fill=color)

    def oval(self, color, box, edge=INK):
        self.draw.ellipse(box, fill=color, outline=edge)

    def rivet(self, x, y, light=STEEL[2]):
        self.box(BLACK, (x, y, x + 2, y + 2))
        self.dot(light, (x + 1, y + 1))

    def vent(self, x, y, length, count, light=STEEL[1]):
        for index in range(count):
            row = y + index * 2
            self.line(BLACK, (x, row), (x + length - 1, row))
            self.line(light, (x + 1, row + 1), (x + length - 1, row + 1))


def engineering_mech():
    s = Sprite()
    # Rear shin, raised hip and grounded near foot.
    s.poly(STEEL[0], (17, 17), (23, 16), (25, 21), (24, 25), (27, 27), (26, 29), (19, 29), (17, 26))
    s.poly(YELLOW[0], (20, 21), (24, 21), (23, 25), (19, 26), edge=None)
    s.line(STEEL[2], (21, 26), (24, 26))
    s.poly(STEEL[0], (10, 16), (17, 17), (18, 22), (15, 25), (16, 28), (13, 30), (6, 30), (5, 28), (8, 25), (9, 21))
    s.poly(YELLOW[1], (10, 21), (14, 22), (13, 25), (8, 27), (8, 24), edge=None)
    s.line(YELLOW[2], (10, 22), (13, 22))
    s.line(STEEL[1], (7, 28), (12, 28), (14, 27))
    # Right shoulder is a compact fork assembly, set behind the cockpit.
    s.poly(STEEL[0], (19, 8), (23, 6), (27, 7), (29, 11), (29, 16), (27, 18), (27, 21), (25, 23), (23, 21), (24, 17), (21, 14))
    s.poly(YELLOW[1], (23, 7), (26, 8), (28, 11), (26, 13), (22, 11), edge=None)
    s.line(YELLOW[2], (24, 8), (26, 9), (27, 11))
    s.line(STEEL[2], (26, 15), (27, 16), (25, 19), (25, 21))
    # Three-quarter cockpit glazing and a slanted yellow front hull.
    s.poly(STEEL[0], (11, 4), (17, 2), (22, 5), (23, 11), (21, 17), (17, 21), (11, 19), (7, 15), (8, 8))
    s.poly(YELLOW[2], (11, 5), (17, 3), (21, 5), (16, 7), (11, 7), edge=None)
    s.poly(YELLOW[0], (21, 6), (22, 11), (20, 16), (17, 18), (17, 13), edge=None)
    s.poly(YELLOW[1], (9, 11), (13, 12), (17, 12), (17, 18), (12, 18), (8, 15))
    s.poly(BLACK, (11, 7), (16, 7), (20, 6), (20, 9), (17, 12), (11, 11), (10, 10))
    s.line(CYAN[0], (11, 9), (16, 10), (19, 8), width=2)
    s.line(CYAN[2], (12, 8), (16, 9), (18, 8))
    s.line(YELLOW[2], (12, 13), (15, 14))
    s.line(STEEL[2], (18, 17), (20, 15))
    s.rivet(12, 15, YELLOW[2])
    # Heavy near forearm housing and work clamp give SG-E its asymmetric outline.
    s.poly(STEEL[0], (3, 9), (7, 8), (10, 11), (10, 16), (8, 18), (8, 23), (6, 25), (3, 25), (1, 22), (1, 16))
    s.poly(YELLOW[1], (3, 11), (7, 9), (9, 12), (8, 16), (4, 17), (2, 15), edge=None)
    s.line(YELLOW[2], (3, 12), (7, 10), (8, 12))
    s.poly(YELLOW[0], (2, 17), (7, 16), (7, 21), (5, 24), (2, 22), edge=None)
    s.line(YELLOW[1], (3, 18), (3, 22), (4, 23))
    s.line(INK, (3, 19), (6, 18))
    s.line(INK, (3, 22), (6, 21))
    return s.image


def combat_mech():
    s = Sprite()
    s.poly(STEEL[0], (17, 18), (22, 17), (25, 20), (25, 25), (29, 27), (29, 29), (20, 29), (18, 27), (17, 23))
    s.poly(RED[0], (20, 21), (24, 21), (24, 25), (21, 26), (19, 24), edge=None)
    s.line(STEEL[2], (22, 27), (26, 27))
    s.poly(STEEL[0], (8, 17), (16, 17), (17, 20), (14, 24), (13, 27), (15, 28), (14, 30), (5, 30), (4, 28), (6, 25), (6, 21))
    s.poly(RED[1], (8, 20), (13, 21), (12, 25), (7, 27), (7, 23), edge=None)
    s.line(RED[2], (8, 22), (11, 22))
    s.line(STEEL[1], (6, 28), (11, 28), (13, 27))
    # Back shoulder sits above the offset face and tracks into a tight gauntlet.
    s.poly(STEEL[0], (20, 5), (25, 2), (28, 4), (30, 8), (29, 13), (30, 17), (28, 22), (24, 22), (22, 19), (23, 15), (20, 12))
    s.poly(RED[0], (24, 4), (27, 4), (29, 8), (28, 11), (25, 11), (22, 7), edge=None)
    s.line(RED[1], (24, 5), (26, 4), (28, 7))
    s.line(STEEL[2], (25, 14), (27, 14), (28, 17))
    s.line(RED[1], (25, 16), (25, 20), (26, 21))
    # Sunk helmet and narrow amber optical slit.
    s.poly(STEEL[0], (10, 7), (15, 5), (21, 6), (24, 11), (23, 16), (19, 19), (14, 21), (9, 18), (7, 13))
    s.poly(RED[0], (11, 8), (15, 6), (20, 7), (22, 9), (19, 12), (12, 12), edge=None)
    s.line(RED[2], (12, 8), (15, 7), (18, 7))
    s.poly(BLACK, (10, 10), (17, 10), (21, 9), (22, 12), (19, 14), (13, 14), (10, 13))
    s.line(YELLOW[1], (11, 12), (17, 12), (20, 11), width=2)
    s.line(YELLOW[2], (12, 12), (17, 12), (20, 11))
    s.poly(RED[1], (13, 15), (19, 15), (21, 14), (21, 16), (16, 19), (12, 17), edge=None)
    s.line(STEEL[2], (12, 17), (15, 19))
    # Towering near pauldron is the tallest point and overhangs the torso.
    s.poly(STEEL[0], (5, 2), (9, 1), (14, 3), (14, 8), (12, 12), (10, 13), (10, 18), (8, 22), (4, 23), (2, 20), (2, 15), (1, 12), (2, 7))
    s.poly(RED[1], (5, 3), (9, 2), (13, 4), (12, 8), (6, 9), (3, 7), edge=None)
    s.line(RED[2], (5, 4), (9, 3), (12, 4))
    s.poly(RED[0], (3, 9), (7, 10), (12, 9), (10, 12), (7, 13), (2, 11), edge=None)
    s.poly(STEEL[1], (4, 14), (8, 15), (8, 19), (6, 21), (4, 20), edge=None)
    s.line(RED[1], (5, 15), (5, 19))
    s.line(INK, (4, 17), (8, 17))
    return s.image


def universal_mech():
    s = Sprite()
    # Separate navy nacelles read as two towers above the low cockpit.
    s.poly(NAVY[0], (3, 5), (7, 2), (10, 3), (12, 6), (12, 13), (8, 16), (3, 13))
    s.poly(WHITE[1], (4, 5), (7, 3), (10, 4), (7, 7), (4, 7), edge=None)
    s.line(WHITE[2], (5, 5), (7, 4), (9, 4))
    s.line(CYAN[1], (4, 9), (4, 13), width=2)
    s.poly(NAVY[0], (20, 3), (24, 1), (28, 3), (29, 7), (29, 13), (24, 16), (20, 12))
    s.poly(WHITE[1], (21, 4), (24, 2), (27, 3), (26, 6), (21, 7), edge=None)
    s.line(WHITE[2], (22, 4), (24, 3), (26, 3))
    s.line(CYAN[1], (26, 7), (27, 8), (27, 12), width=2)
    s.line(CYAN[2], (27, 7), (27, 9))
    # Heavy broad feet, separated by a deep midline cutout.
    s.poly(NAVY[0], (18, 18), (24, 17), (27, 21), (26, 25), (30, 27), (29, 29), (20, 29), (18, 25))
    s.poly(WHITE[0], (20, 21), (25, 21), (25, 25), (21, 27), (19, 24), edge=None)
    s.line(WHITE[2], (20, 23), (23, 23), (25, 22))
    s.line(NAVY[2], (22, 27), (27, 27))
    s.poly(NAVY[0], (8, 17), (16, 18), (17, 22), (14, 26), (15, 28), (13, 30), (4, 30), (2, 28), (6, 25), (5, 22))
    s.poly(WHITE[1], (7, 21), (13, 22), (13, 25), (8, 27), (5, 27), (7, 24), edge=None)
    s.line(WHITE[2], (8, 22), (12, 23))
    s.line(CYAN[1], (8, 26), (10, 26))
    s.line(NAVY[2], (4, 28), (12, 28))
    # Chest wings taper inward from the wide shoulders to the low center of mass.
    s.poly(NAVY[0], (1, 12), (5, 8), (11, 9), (15, 8), (21, 7), (25, 8), (30, 11), (30, 19), (27, 22), (24, 20), (23, 16), (20, 19), (16, 22), (12, 21), (8, 18), (6, 22), (2, 22), (1, 19))
    s.poly(WHITE[0], (22, 10), (27, 10), (29, 12), (29, 17), (26, 19), (24, 16), edge=None)
    s.poly(WHITE[1], (22, 10), (25, 9), (28, 11), (27, 13), (23, 13), edge=None)
    s.poly(WHITE[0], (2, 13), (7, 12), (9, 15), (6, 19), (3, 20), (2, 18), edge=None)
    s.poly(WHITE[1], (2, 12), (5, 9), (10, 10), (11, 12), (7, 15), (3, 15), edge=None)
    s.line(WHITE[2], (3, 12), (5, 10), (9, 11))
    s.poly(WHITE[1], (12, 10), (16, 9), (21, 9), (23, 13), (21, 16), (16, 19), (10, 16), edge=None)
    s.line(WHITE[2], (13, 10), (18, 10), (20, 9))
    s.poly(BLACK, (12, 12), (16, 12), (21, 10), (22, 13), (20, 15), (16, 17), (11, 15))
    s.line(CYAN[1], (12, 14), (16, 15), (20, 12), width=2)
    s.line(CYAN[2], (14, 14), (16, 14), (20, 12))
    s.line(NAVY[2], (9, 17), (13, 19), (16, 20), (20, 18))
    s.line(CYAN[1], (3, 17), (5, 17), (6, 16))
    s.line(CYAN[1], (26, 16), (28, 15))
    return s.image


def loaded(image):
    """Put the loadout badge in the upper-right, clear of stack count text."""
    s = Sprite()
    s.image.paste(image)
    s.poly(BLACK, (24, 0), (29, 0), (31, 2), (31, 7), (29, 9), (24, 9), (22, 7), (22, 2))
    s.line(CYAN[0], (23, 3), (23, 2), (25, 1), (28, 1), (30, 3), (30, 6), (28, 8), (25, 8), (23, 6))
    s.line(CYAN[2], (24, 4), (26, 6), (29, 3))
    s.line(CYAN[1], (24, 5), (26, 7), (29, 4))
    return s.image


def chip(style, palette):
    """Keyed card housing for the three upgrade families, with distinct hardware."""
    dark, body, glow = palette
    s = Sprite()
    if style in ('engineering', 'mobility', 'endurance'):
        hull = ((3, 9), (8, 4), (25, 4), (28, 7), (28, 23), (23, 27), (8, 27), (3, 22))
        top = ((5, 9), (9, 5), (25, 5), (27, 7), (24, 8), (9, 8))
        pins = (10, 14, 18, 22)
    elif style in ('fire', 'armor', 'sustained'):
        hull = ((4, 6), (10, 3), (22, 3), (28, 7), (28, 21), (21, 27), (11, 27), (4, 21))
        top = ((6, 7), (11, 4), (22, 4), (26, 7), (23, 8), (11, 7))
        pins = (12, 15, 18, 21)
    else:
        hull = ((2, 11), (8, 7), (11, 3), (21, 3), (24, 7), (29, 11), (29, 21), (23, 26), (9, 26), (2, 21))
        top = ((4, 11), (9, 8), (12, 4), (20, 4), (23, 8), (19, 7), (12, 7), (8, 11))
        pins = (10, 14, 18, 22)
    for x in pins:
        s.box(STEEL[0], (x, 25, x + 2, 29), INK)
        s.dot(glow, (x + 1, 28))
    s.poly(dark, *hull)
    s.poly(body, *top, edge=None)
    s.line(glow, top[0], top[1], top[2])
    s.poly(BLACK, (8, 11), (11, 8), (21, 8), (24, 11), (24, 20), (21, 24), (11, 24), (8, 20))
    s.rivet(5, 18, glow)
    s.rivet(24, 18, glow)
    if style == 'engineering':
        s.poly(body, (11, 17), (15, 13), (17, 13), (19, 10), (21, 12), (20, 15), (17, 16), (13, 21), (11, 20), edge=None)
        s.line(glow, (12, 18), (15, 15), (17, 15), (19, 12))
    elif style == 'mobility':
        for x in (11, 16):
            s.poly(body, (x, 12), (x + 3, 12), (x + 7, 16), (x + 3, 21), (x, 21), (x + 4, 16), edge=None)
            s.line(glow, (x + 1, 12), (x + 5, 16))
    elif style == 'endurance':
        s.box(body, (12, 12, 20, 21))
        s.box(glow, (14, 10, 18, 12))
        for y in (14, 17, 20):
            s.line(glow, (14, y), (18, y))
    elif style == 'fire':
        s.oval(body, (11, 11, 21, 21), edge=glow)
        s.oval(BLACK, (13, 13, 19, 19), edge=None)
        s.line(glow, (16, 10), (16, 13))
        s.line(glow, (16, 19), (16, 22))
        s.line(glow, (10, 16), (13, 16))
        s.line(glow, (19, 16), (22, 16))
        s.dot(glow, (16, 16))
    elif style == 'armor':
        s.poly(body, (11, 12), (16, 10), (21, 12), (21, 17), (16, 22), (11, 17), edge=None)
        s.line(glow, (12, 13), (16, 11), (20, 13))
        s.line(dark, (12, 16), (16, 18), (20, 16), width=2)
    elif style == 'sustained':
        for x, rise in ((11, 3), (15, 6), (19, 9)):
            s.box(body, (x, 21 - rise, x + 2, 21))
            s.line(glow, (x, 21 - rise), (x + 2, 21 - rise))
        s.line(dark, (11, 22), (21, 22))
    elif style == 'zhenyuan':
        s.poly(body, (16, 9), (21, 15), (19, 21), (16, 23), (12, 20), (11, 15), edge=None)
        s.line(glow, (16, 10), (12, 15), (14, 19), (16, 21), (19, 16))
        s.line(dark, (16, 14), (16, 20), width=2)
    elif style == 'tianpu':
        s.poly(body, (18, 9), (12, 16), (15, 16), (13, 22), (21, 14), (17, 14), edge=None)
        s.line(glow, (17, 10), (13, 15), (16, 15), (14, 20))
    else:  # Changchuan: an open return loop of coolant.
        s.line(body, (14, 10), (19, 10), (22, 13), (22, 19), (19, 22), (13, 22), (10, 19), (10, 14), (12, 12), width=3)
        s.line(glow, (13, 11), (18, 10), (21, 13), (21, 18))
        s.poly(glow, (17, 14), (17, 18), (14, 18), (14, 15), edge=None)
    return s.image


def tool_arm(style):
    heavy = style.startswith('heavy')
    dig = style.endswith('excavator')
    dark, body, light = YELLOW if not heavy else CYAN
    s = Sprite()
    s.poly(STEEL[0], (4, 4), (11, 3), (15, 7), (14, 13), (19, 15), (21, 20), (17, 25), (11, 24), (7, 18), (4, 14), (2, 9))
    s.poly(body, (4, 5), (11, 4), (14, 7), (12, 10), (5, 10), (3, 8), edge=None)
    s.line(light, (5, 5), (10, 5), (12, 6))
    s.line(STEEL[2], (6, 12), (10, 13), (15, 19), width=2)
    s.oval(dark, (11, 14, 20, 23))
    s.oval(body, (13, 15, 18, 20))
    s.dot(light, (15, 16), (16, 16))
    if dig and heavy:
        s.poly(STEEL[0], (18, 17), (24, 13), (30, 16), (30, 24), (28, 28), (22, 30), (17, 25))
        s.poly(STEEL[1], (20, 19), (26, 15), (29, 17), (27, 23), (23, 27), (19, 24), edge=None)
        s.line(light, (21, 19), (26, 16), (28, 17))
        for x, y in ((21, 26), (24, 24), (27, 21)):
            s.line(STEEL[2], (x, y), (x + 1, y + 2))
    elif dig:
        s.poly(STEEL[1], (18, 18), (22, 16), (27, 17), (29, 21), (27, 27), (20, 29), (17, 25))
        s.poly(STEEL[2], (19, 24), (24, 23), (28, 20), (26, 26), (21, 28), edge=None)
        s.line(light, (20, 19), (24, 18), (27, 19))
        s.line(BLACK, (20, 25), (23, 25), (26, 23))
    elif heavy:
        s.poly(NAVY[0], (14, 21), (19, 16), (26, 16), (30, 20), (30, 25), (25, 30), (19, 30), (15, 27))
        s.line(STEEL[2], (16, 22), (20, 18), (26, 18), (28, 20))
        for x, y in ((19, 21), (25, 21), (22, 26)):
            s.poly(CYAN[1], (x - 1, y - 2), (x + 2, y), (x + 1, y + 3), (x - 2, y + 1))
            s.dot(CYAN[2], (x, y))
    else:
        s.poly(STEEL[2], (18, 17), (23, 17), (28, 23), (30, 28), (25, 28), (18, 25), (16, 22))
        s.poly(STEEL[1], (21, 19), (29, 27), (25, 27), (19, 23), edge=None)
        s.line(INK, (18, 21), (23, 20))
        s.line(INK, (21, 24), (26, 23))
        s.dot(light, (19, 19))
    return s.image


def weapon(style):
    s = Sprite()
    if style == 'blade':
        s.poly(STEEL[0], (3, 29), (2, 25), (9, 18), (13, 22), (7, 29))
        s.line(STEEL[2], (5, 25), (9, 21))
        s.poly(WHITE[1], (8, 17), (21, 4), (29, 2), (29, 10), (19, 20), (13, 23))
        s.poly(CYAN[0], (11, 18), (23, 7), (28, 4), (27, 10), (17, 20), (13, 21), edge=None)
        s.line(CYAN[2], (13, 17), (22, 8), (27, 5))
        s.poly(RED[1], (6, 15), (9, 15), (16, 22), (16, 25), (13, 25), (6, 18))
        s.line(RED[2], (8, 16), (14, 22))
        return s.image
    s.poly(STEEL[0], (7, 18), (16, 18), (16, 23), (13, 28), (7, 28), (5, 25))
    s.line(STEEL[2], (8, 23), (12, 23), (11, 26))
    if style == 'gun':
        s.poly(STEEL[1], (14, 11), (28, 8), (30, 9), (30, 14), (29, 17), (15, 19))
        s.line(WHITE[1], (20, 11), (28, 9))
        s.line(INK, (19, 14), (29, 12), width=2)
        for x in (24, 27):
            s.line(RED[1], (x, 10), (x, 16))
        s.poly(RED[0], (2, 13), (8, 9), (15, 9), (20, 12), (20, 17), (16, 21), (5, 23), (2, 20))
        s.line(RED[2], (3, 14), (8, 11), (14, 11))
        s.oval(STEEL[1], (6, 14, 15, 21))
        s.oval(STEEL[0], (9, 16, 13, 19))
        s.dot(YELLOW[2], (11, 17))
    elif style == 'sniper':
        s.poly(STEEL[0], (12, 11), (26, 4), (29, 4), (30, 7), (28, 10), (14, 18))
        s.line(STEEL[2], (16, 12), (27, 6))
        s.line(RED[2], (27, 5), (29, 6), (28, 8))
        s.poly(NAVY[0], (2, 17), (9, 13), (14, 11), (18, 13), (17, 18), (8, 24), (3, 24))
        s.line(WHITE[1], (4, 18), (9, 15), (14, 13))
        s.poly(STEEL[1], (7, 11), (16, 6), (19, 7), (19, 10), (9, 15), (7, 14))
        s.line(STEEL[2], (9, 11), (16, 8), (18, 8))
        s.dot(YELLOW[2], (17, 9), (18, 9))
    elif style == 'laser':
        s.poly(NAVY[0], (3, 13), (8, 8), (20, 7), (26, 9), (30, 7), (30, 11), (27, 14), (30, 17), (30, 20), (26, 19), (21, 21), (7, 23), (3, 20))
        s.poly(WHITE[1], (4, 13), (8, 9), (20, 8), (24, 10), (22, 13), (10, 15), edge=None)
        s.line(WHITE[2], (9, 10), (19, 9))
        s.poly(CYAN[0], (9, 15), (22, 12), (27, 14), (22, 17), (10, 19))
        s.line(CYAN[2], (12, 16), (22, 14), (26, 14))
        s.line(CYAN[1], (19, 18), (23, 17), (27, 17))
    else:  # Broad twin-aperture launcher, viewed from the muzzle end.
        s.poly(RED[0], (2, 9), (8, 5), (21, 5), (29, 9), (29, 20), (22, 25), (8, 24), (2, 19))
        s.poly(RED[1], (3, 9), (9, 6), (21, 6), (27, 9), (23, 11), (8, 12), edge=None)
        s.line(RED[2], (7, 8), (10, 7), (20, 7))
        s.poly(STEEL[0], (18, 11), (23, 8), (28, 10), (30, 14), (30, 20), (25, 24), (18, 22))
        for box in ((19, 12, 25, 18), (24, 16, 29, 21)):
            s.oval(BLACK, box, edge=YELLOW[0])
        s.dot(YELLOW[2], (22, 14), (26, 18))
        s.vent(5, 15, 8, 3)
    return s.image


def drone(style, palette):
    dark, body, light = palette
    s = Sprite()
    if style in ('maintenance', 'healing'):
        s.poly(STEEL[0], (4, 8), (9, 6), (14, 11), (19, 10), (23, 6), (28, 7), (30, 12), (27, 17), (22, 18), (21, 23), (17, 27), (13, 25), (10, 20), (5, 17), (1, 13))
        for x, y in ((2, 9), (22, 8)):
            s.oval(dark, (x, y, x + 7, y + 6))
            s.oval(STEEL[1], (x + 1, y + 1, x + 6, y + 4), edge=None)
            s.dot(light, (x + 3, y + 2))
        s.poly(body, (8, 15), (13, 11), (19, 11), (24, 15), (21, 21), (16, 24), (11, 21))
        s.line(light, (11, 14), (14, 12), (19, 12))
        if style == 'maintenance':
            s.poly(BLACK, (12, 17), (14, 15), (16, 17), (19, 15), (20, 18), (17, 21), (14, 20), edge=None)
            s.line(light, (13, 16), (15, 18), (18, 18), (19, 16))
            s.line(STEEL[2], (16, 21), (16, 25))
        else:
            s.box(WHITE[2], (15, 15, 17, 21))
            s.box(WHITE[2], (13, 17, 19, 19))
            s.line(CYAN[0], (13, 22), (16, 24), (20, 22))
    elif style == 'attack':
        s.poly(dark, (1, 9), (10, 10), (15, 6), (20, 8), (29, 6), (29, 14), (24, 20), (20, 20), (17, 27), (13, 27), (10, 20), (6, 19), (2, 15))
        s.poly(body, (3, 11), (11, 12), (15, 8), (19, 10), (28, 8), (25, 14), (18, 16), (15, 20), (11, 15), (6, 15), edge=None)
        s.line(light, (5, 12), (11, 13), (15, 10), (19, 11), (25, 10))
        s.line(YELLOW[2], (13, 16), (16, 17), (19, 15))
        s.line(STEEL[2], (14, 21), (15, 25), (17, 22))
    else:
        s.poly(NAVY[0], (3, 10), (6, 7), (12, 8), (16, 4), (21, 7), (26, 7), (29, 11), (29, 20), (22, 25), (16, 28), (9, 25), (3, 20))
        s.poly(WHITE[1], (4, 11), (7, 8), (12, 10), (16, 6), (21, 9), (26, 8), (28, 12), (24, 16), (20, 13), (12, 13), (8, 16), edge=None)
        s.line(WHITE[2], (7, 9), (12, 11), (16, 7), (21, 10), (25, 9))
        s.poly(dark, (10, 16), (16, 13), (22, 16), (21, 21), (16, 25), (11, 21))
        s.line(light, (12, 17), (16, 16), (20, 17), (19, 20), (16, 22), (13, 20), (12, 17))
        s.line(body, (5, 19), (8, 21), (10, 22))
        s.line(body, (22, 22), (27, 19))
    return s.image


def ammo(style):
    s = Sprite()
    if style == 'explosive':
        positions, length, rim, metal = [(7, 11), (17, 5)], 15, RED, STEEL
    elif style == 'sniper':
        positions, length, rim, metal = [(7, 10), (15, 5), (22, 2)], 16, CYAN, STEEL
    else:
        positions, length, rim, metal = [(5, 12), (11, 9), (17, 6), (23, 3)], 13, YELLOW, YELLOW
    for x, y in positions:
        wide = 7 if style == 'explosive' else 5
        cone = 2 if style == 'explosive' else 5
        s.poly(metal[0], (x, y + cone), (x + wide // 2, y), (x + wide, y + cone),
               (x + wide, y + length), (x + wide - 1, y + length + 2), (x + 1, y + length + 2), (x, y + length))
        s.line(metal[1], (x + 1, y + cone), (x + 1, y + length - 1))
        s.line(metal[2], (x + 2, y + cone), (x + 2, y + cone + 3))
        s.line(rim[1], (x + 1, y + cone + 4), (x + wide - 1, y + cone + 4))
        s.line(rim[2], (x + 1, y + length), (x + wide - 1, y + length))
    return s.image


def core(style):
    s = Sprite()
    if style == 'fuel':
        s.poly(NAVY[0], (6, 24), (7, 20), (19, 6), (23, 3), (28, 5), (29, 10), (26, 14), (14, 27), (10, 29))
        s.poly(CYAN[0], (9, 21), (19, 9), (24, 8), (25, 12), (13, 25), (10, 26))
        s.line(CYAN[2], (11, 22), (20, 11), (23, 10), width=2)
        s.line(PURPLE[2], (21, 6), (24, 5), (27, 6), (27, 9))
        s.line(WHITE[1], (7, 24), (11, 27), (13, 26))
    elif style == 'fusion':
        s.poly(NAVY[0], (2, 12), (6, 7), (12, 6), (13, 3), (21, 3), (22, 7), (28, 11), (29, 20), (26, 25), (21, 26), (18, 29), (10, 28), (9, 24), (4, 21))
        s.oval(PURPLE[0], (6, 7, 26, 25), edge=PURPLE[1])
        s.poly(WHITE[0], (3, 12), (6, 9), (10, 12), (9, 18), (5, 20), (3, 18))
        s.poly(WHITE[0], (22, 13), (27, 12), (28, 19), (25, 23), (21, 23), (21, 19))
        s.line(WHITE[2], (5, 12), (7, 12), (8, 14))
        s.line(WHITE[2], (23, 14), (26, 13))
        s.poly(CYAN[0], (16, 7), (23, 15), (21, 21), (16, 25), (11, 21), (9, 15))
        s.poly(CYAN[1], (16, 9), (21, 15), (16, 23), (12, 16), edge=None)
        s.line(CYAN[2], (16, 10), (14, 16), (16, 21), width=2)
        s.line(WHITE[2], (16, 14), (16, 16), (19, 16))
    else:
        s.poly(STEEL[0], (3, 9), (9, 4), (24, 4), (29, 9), (29, 24), (24, 28), (7, 28), (3, 24))
        s.poly(YELLOW[0], (4, 9), (10, 5), (23, 5), (27, 9), (23, 12), (7, 12), edge=None)
        s.line(YELLOW[2], (6, 8), (11, 6), (22, 6))
        s.poly(BLACK, (8, 12), (23, 11), (25, 14), (25, 22), (21, 25), (9, 25), (7, 22))
        s.poly(RED[0], (10, 22), (11, 19), (13, 17), (14, 20), (17, 14), (20, 19), (22, 22), edge=None)
        s.line(YELLOW[1], (11, 22), (15, 22), (17, 17), (20, 22), width=2)
        s.vent(10, 12, 12, 1, STEEL[2])
        s.dot(CYAN[1], (25, 9), (26, 9))
    return s.image


def radiator(weaponized=False):
    dark, body, glow = RED if weaponized else CYAN
    s = Sprite()
    s.poly(STEEL[0], (3, 10), (8, 5), (24, 5), (29, 10), (29, 22), (23, 27), (8, 27), (3, 22))
    s.poly(dark, (4, 10), (8, 6), (23, 6), (27, 9), (23, 11), (8, 11), edge=None)
    s.line(glow, (6, 9), (9, 7), (23, 7))
    for x in (6, 10, 14, 18, 22, 26):
        s.line(BLACK, (x, 12), (x, 20), (x - 2, 23), width=2)
        s.line(STEEL[2], (x + 1, 12), (x + 1, 21), (x - 1, 24))
    s.line(body, (8, 25), (19, 25), (25, 22))
    return s.image


def pistons(stride=False):
    s = Sprite()
    dark, color, hi = YELLOW if stride else CYAN
    for x, y in ((4, 9), (14, 4)):
        s.poly(STEEL[0], (x, y + 4), (x + 5, y), (x + 11, y), (x + 12, y + 4), (x + 9, y + 9),
               (x + 10, y + 14), (x + 7, y + 19), (x + 1, y + 18), (x, y + 15), (x + 2, y + 10))
        s.poly(color, (x + 1, y + 4), (x + 5, y + 1), (x + 10, y + 1), (x + 11, y + 4), (x + 8, y + 7), (x + 3, y + 7), edge=None)
        s.line(hi, (x + 3, y + 4), (x + 6, y + 2), (x + 10, y + 2))
        s.line(STEEL[2], (x + 4, y + 9), (x + 7, y + 10), (x + 6, y + 15), width=2)
        s.line(dark, (x + 2, y + 15), (x + 6, y + 17), (x + 8, y + 16))
    return s.image


def armor(load_phase=False):
    s = Sprite()
    dark, body, glow = RED if load_phase else YELLOW
    s.poly(STEEL[0], (3, 11), (8, 5), (16, 7), (24, 4), (29, 9), (29, 17), (24, 24), (16, 29), (8, 24), (3, 18))
    s.poly(dark, (4, 12), (9, 7), (15, 9), (16, 13), (16, 26), (9, 22), (4, 17), edge=None)
    s.poly(body, (17, 10), (24, 6), (28, 9), (27, 15), (22, 19), (17, 19), edge=None)
    s.line(glow, (6, 11), (9, 8), (13, 10))
    s.line(glow, (19, 10), (24, 7), (27, 9))
    s.line(INK, (5, 15), (12, 17), (16, 20), (22, 18), (27, 14), width=2)
    s.line(STEEL[2], (9, 20), (13, 22), (16, 24), (22, 21))
    s.rivet(7, 11, glow)
    s.rivet(23, 10, glow)
    return s.image


def wheels():
    s = Sprite()
    s.poly(STEEL[0], (7, 12), (19, 4), (25, 5), (29, 9), (29, 17), (25, 23), (11, 29), (4, 26), (2, 21), (2, 15))
    s.oval(BLACK, (15, 4, 29, 23))
    s.oval(STEEL[1], (18, 7, 27, 20))
    s.oval(YELLOW[0], (20, 10, 26, 16))
    s.line(STEEL[2], (11, 16), (22, 12), width=3)
    s.oval(BLACK, (2, 12, 17, 29))
    s.oval(STEEL[1], (4, 14, 15, 27))
    s.oval(YELLOW[1], (7, 17, 13, 24))
    s.dot(YELLOW[2], (10, 19), (11, 20))
    s.line(STEEL[2], (5, 18), (6, 16))
    s.line(STEEL[2], (19, 8), (22, 7))
    return s.image


def coupler(combat=False):
    s = Sprite()
    dark, body, light = RED if combat else YELLOW
    s.poly(STEEL[0], (3, 10), (6, 6), (12, 6), (15, 9), (14, 14), (20, 17), (24, 15), (28, 17), (29, 23), (25, 27), (19, 27), (15, 24), (16, 20), (10, 17), (6, 18), (3, 16))
    s.line(body, (7, 8), (11, 8), (12, 11), (10, 15), (6, 15), width=2)
    s.line(body, (19, 23), (21, 19), (26, 19), (27, 23), (24, 25), width=2)
    s.line(light, (7, 8), (11, 8), (12, 10))
    s.line(light, (22, 19), (26, 19), (27, 21))
    s.line(dark, (10, 13), (24, 22), width=3)
    s.line(STEEL[2], (11, 13), (23, 21))
    s.rivet(6, 12, light)
    s.rivet(22, 22, light)
    return s.image


def pack(style):
    s = Sprite()
    if style == 'wireless':
        s.poly(NAVY[0], (3, 21), (11, 17), (22, 17), (29, 20), (29, 24), (23, 28), (9, 28), (3, 25))
        s.poly(STEEL[1], (4, 21), (11, 18), (22, 18), (27, 20), (22, 23), (9, 23), edge=None)
        s.line(CYAN[2], (13, 20), (18, 20), (21, 19))
        for points in (((8, 9), (12, 6), (19, 6), (23, 9)), ((10, 13), (13, 10), (18, 10), (21, 13)), ((13, 15), (15, 13), (18, 14))):
            s.line(CYAN[1], *points, width=2)
        s.dot(CYAN[2], (15, 16), (16, 16))
        return s.image
    if style == 'diagnostic' or style == 'fire_control':
        dark, body, light = CYAN if style == 'diagnostic' else RED
        s.poly(STEEL[0], (5, 11), (20, 3), (26, 5), (28, 10), (28, 19), (13, 28), (7, 26), (4, 21))
        s.poly(body, (5, 11), (20, 4), (25, 6), (24, 9), (10, 17), (5, 15), edge=None)
        s.line(light, (7, 11), (20, 5), (23, 6))
        s.poly(BLACK, (10, 13), (21, 7), (25, 8), (25, 13), (13, 21), (10, 19))
        s.line(light, (12, 17), (15, 15), (17, 16), (21, 11), (23, 11))
        s.line(dark, (13, 19), (23, 13))
        s.line(STEEL[2], (12, 23), (15, 23), (17, 22))
        for x, y in ((19, 20), (22, 18), (25, 16)):
            s.dot(light, (x, y))
        return s.image
    palettes = {'service': YELLOW, 'repair': GREEN, 'friendly': CYAN, 'medical': RED}
    dark, body, light = palettes[style]
    # The silhouette differentiates the service case, canister and repair units.
    if style == 'medical':
        s.poly(WHITE[0], (10, 5), (13, 2), (21, 2), (24, 5), (24, 11), (27, 14), (27, 25), (23, 29), (9, 29), (6, 25), (6, 15), (10, 11))
        s.poly(WHITE[1], (9, 15), (13, 11), (22, 11), (25, 15), (23, 25), (10, 25), edge=None)
        s.line(WHITE[2], (10, 15), (13, 12), (21, 12))
        s.line(body, (12, 6), (22, 6), width=3)
        s.box(body, (14, 15, 18, 23))
        s.box(body, (12, 17, 20, 21))
        s.dot(light, (14, 17), (14, 18), (15, 16))
        return s.image
    if style == 'service':
        s.poly(STEEL[0], (8, 10), (8, 6), (12, 3), (20, 3), (23, 6), (23, 10), (20, 10), (18, 7), (12, 7), (11, 11))
    elif style == 'friendly':
        s.poly(NAVY[0], (12, 12), (17, 6), (25, 5), (29, 10), (29, 21), (23, 22), (17, 20))
        s.line(light, (17, 8), (24, 6), (27, 9))
    s.poly(STEEL[0], (3, 13), (8, 9), (24, 9), (29, 13), (29, 24), (25, 28), (8, 28), (3, 24))
    s.poly(dark, (4, 13), (9, 10), (23, 10), (27, 13), (22, 15), (8, 15), edge=None)
    s.line(light, (6, 13), (10, 11), (22, 11))
    if style == 'service':
        s.box(BLACK, (7, 17, 23, 24))
        s.line(body, (9, 22), (13, 18), (18, 18), (21, 20), width=2)
        s.dot(light, (13, 18), (17, 18))
        s.rivet(24, 18, light)
    else:
        s.box(body, (13, 16, 17, 25))
        s.box(body, (10, 19, 20, 22))
        s.line(light, (13, 18), (13, 16), (16, 16))
        s.dot(light, (10, 19), (11, 19))
        s.vent(22, 18, 4, 3)
    return s.image


def assist_frame():
    s = Sprite()
    s.poly(STEEL[0], (3, 10), (8, 5), (12, 5), (12, 10), (16, 12), (20, 10), (20, 5), (24, 4), (29, 8), (29, 15), (24, 17), (22, 21), (22, 28), (17, 30), (15, 29), (15, 23), (12, 24), (10, 28), (5, 29), (3, 25), (7, 19), (7, 16), (3, 15))
    s.line(YELLOW[1], (4, 11), (8, 7), (10, 7), (10, 12), (14, 15), width=2)
    s.line(YELLOW[1], (22, 7), (24, 6), (27, 9), (27, 13), width=2)
    s.line(STEEL[2], (7, 15), (12, 16), (15, 19), (19, 15), (24, 14))
    s.line(CYAN[1], (15, 20), (18, 18), (20, 17), width=2)
    s.line(STEEL[2], (8, 22), (6, 26), (9, 26))
    s.line(STEEL[2], (19, 22), (19, 27), (21, 26))
    return s.image


def cutter():
    s = Sprite()
    s.poly(STEEL[0], (2, 27), (4, 22), (11, 18), (16, 23), (9, 29), (4, 30))
    s.line(STEEL[2], (4, 26), (9, 22), (11, 22))
    s.poly(YELLOW[0], (3, 18), (6, 13), (13, 9), (20, 11), (23, 16), (20, 21), (11, 24), (6, 22))
    s.poly(YELLOW[1], (5, 17), (7, 14), (13, 10), (19, 13), (16, 17), (9, 20), edge=None)
    s.line(YELLOW[2], (8, 14), (13, 11), (16, 12))
    s.poly(STEEL[2], (15, 10), (19, 5), (24, 4), (30, 2), (27, 8), (26, 11), (22, 15), (18, 16))
    s.line(STEEL[1], (18, 10), (25, 8), (28, 5), width=2)
    s.line(INK, (19, 13), (25, 11))
    s.rivet(10, 17, CYAN[2])
    return s.image


def facility_icon(platform=False):
    s = Sprite()
    if platform:
        s.poly(STEEL[0], (1, 15), (17, 7), (30, 13), (30, 20), (15, 28), (1, 22))
        s.poly(NAVY[0], (1, 15), (15, 21), (15, 27), (1, 22))
        s.poly(STEEL[1], (1, 15), (17, 6), (30, 13), (15, 22))
        s.poly(STEEL[0], (5, 15), (17, 9), (26, 13), (15, 19))
        s.poly(BLACK, (11, 15), (17, 12), (21, 14), (15, 17))
        s.line(CYAN[2], (11, 15), (15, 17), (21, 14))
        s.line(CYAN[1], (17, 12), (21, 14))
        for x, y in ((5, 15), (9, 17), (23, 12), (25, 14)):
            s.line(YELLOW[2], (x, y), (x + 2, y + 1))
        s.line(STEEL[2], (3, 21), (12, 25))
        s.line(CYAN[1], (18, 24), (27, 19))
    else:
        for x, y in ((7, 15), (24, 13), (14, 18)):
            s.poly(STEEL[0], (x, y), (x + 4, y), (x + 4, y + 9), (x + 2, y + 11), (x, y + 10))
            s.line(STEEL[2], (x + 1, y + 3), (x + 1, y + 9))
        s.poly(STEEL[0], (4, 21), (16, 24), (27, 18), (26, 21), (16, 27), (4, 24))
        s.poly(STEEL[0], (1, 12), (17, 4), (30, 8), (30, 14), (15, 22), (1, 17))
        s.poly(STEEL[1], (1, 12), (17, 4), (30, 8), (15, 16))
        s.line(STEEL[2], (3, 12), (17, 5), (28, 8))
        s.line(YELLOW[1], (3, 15), (13, 18), (19, 15))
        s.line(CYAN[1], (19, 17), (24, 14), (26, 14))
        s.poly(STEEL[0], (7, 8), (12, 5), (17, 7), (17, 10), (12, 13), (7, 11))
        s.poly(YELLOW[1], (7, 8), (12, 5), (17, 7), (12, 10))
        s.line(YELLOW[2], (8, 8), (12, 6), (15, 7))
        s.poly(NAVY[0], (18, 8), (21, 6), (26, 8), (23, 10))
        s.line(CYAN[2], (20, 8), (22, 7), (24, 8))
    return s.image


def hazard(s, x, y, count, height=3):
    for index in range(count):
        offset = x + 5 * index
        s.poly(YELLOW[1], (offset, y), (offset + 2, y), (offset + height + 2, y + height),
               (offset + height, y + height), edge=None)
        s.dot(YELLOW[2], (offset + 1, y))


def bench_faces():
    top = Sprite(STEEL[0])
    top.box(BLACK, (1, 1, 30, 30))
    top.box(STEEL[1], (2, 2, 29, 29))
    top.box(STEEL[0], (4, 4, 27, 27), INK)
    top.line(STEEL[2], (2, 29), (2, 2), (29, 2))
    for y in (7, 13, 19, 25):
        top.line(STEEL[1], (6, y), (25, y))
        top.line(BLACK, (6, y + 1), (25, y + 1))
    top.box(BLACK, (12, 11, 21, 20))
    top.line(CYAN[0], (12, 20), (12, 11), (21, 11))
    top.line(CYAN[2], (15, 16), (18, 16))
    hazard(top, 5, 26, 4, height=2)

    structure = Sprite(STEEL[0])
    structure.box(BLACK, (2, 2, 29, 29))
    structure.box(STEEL[0], (4, 4, 27, 27))
    structure.line(STEEL[2], (4, 26), (4, 4), (27, 4))
    structure.line(STEEL[1], (6, 24), (19, 11), (25, 11), width=3)
    structure.rivet(5, 5)
    structure.rivet(24, 23)
    structure.box(BLACK, (3, 27, 28, 31))
    hazard(structure, 4, 28, 4)

    control = Sprite(BLACK)
    control.box(STEEL[0], (0, 0, 31, 31), INK)
    control.box(STEEL[1], (2, 3, 29, 28), INK)
    control.line(STEEL[2], (3, 4), (28, 4), width=2)
    control.box(BLACK, (5, 8, 20, 23), INK)
    control.box(CYAN[0], (7, 10, 18, 21))
    control.line(CYAN[2], (8, 16), (11, 16), (13, 13), (15, 18), (17, 16), width=2)
    control.box(BLACK, (23, 8, 27, 13))
    control.box(YELLOW[2], (24, 9, 26, 11))
    control.box(BLACK, (23, 15, 27, 20))
    control.box(GREEN[1], (24, 16, 26, 18))
    control.line(STEEL[2], (6, 25), (26, 25))
    return {'best_workbench_top': top.image, 'best_workbench_structure': structure.image,
            'best_workbench_control': control.image}


def platform_faces():
    deck = Sprite(BLACK)
    deck.box(STEEL[0], (1, 1, 30, 30))
    deck.box(STEEL[1], (3, 3, 28, 28), INK)
    deck.line(STEEL[2], (2, 29), (2, 2), (29, 2))
    deck.poly(STEEL[0], (8, 4), (23, 4), (27, 8), (27, 23), (23, 27), (8, 27), (4, 23), (4, 8))
    deck.poly(NAVY[0], (10, 7), (21, 7), (24, 10), (24, 21), (21, 24), (10, 24), (7, 21), (7, 10))
    deck.oval(STEEL[1], (9, 9, 22, 22), edge=CYAN[0])
    deck.oval(BLACK, (11, 11, 20, 20), edge=CYAN[1])
    deck.line(CYAN[2], (13, 17), (17, 13), (19, 13), width=2)
    deck.line(CYAN[1], (13, 16), (16, 16), (16, 19))
    for x, y in ((4, 4), (25, 4), (4, 25), (25, 25)):
        deck.rivet(x, y, YELLOW[2])
    deck.line(YELLOW[1], (10, 5), (20, 5))
    deck.line(YELLOW[1], (10, 26), (20, 26))

    side = Sprite(NAVY[0])
    side.box(STEEL[1], (0, 0, 31, 7), BLACK)
    side.line(STEEL[2], (0, 1), (31, 1))
    side.box(BLACK, (0, 5, 31, 9))
    hazard(side, 1, 5, 6)
    side.box(STEEL[0], (2, 10, 29, 28), BLACK)
    side.box(BLACK, (5, 13, 11, 24))
    side.box(BLACK, (20, 13, 26, 24))
    side.vent(6, 14, 5, 5)
    side.vent(21, 14, 5, 5)
    side.box(NAVY[0], (12, 11, 19, 26))
    side.line(CYAN[0], (14, 14), (14, 24), (18, 24), width=2)
    side.line(CYAN[2], (15, 15), (15, 21))
    side.line(STEEL[1], (1, 30), (30, 30))

    bottom = Sprite(BLACK)
    bottom.box(STEEL[0], (1, 1, 30, 30))
    bottom.box(NAVY[0], (4, 4, 27, 27), BLACK)
    bottom.line(STEEL[1], (4, 26), (4, 4), (27, 4))
    bottom.box(BLACK, (9, 9, 22, 22))
    bottom.vent(11, 11, 10, 5)
    for x, y in ((5, 5), (24, 5), (5, 24), (24, 24)):
        bottom.rivet(x, y)
    return {'mech_maintenance_platform': deck.image, 'mech_maintenance_platform_side': side.image,
            'mech_maintenance_platform_bottom': bottom.image}


def render_items():
    """Return original inventory art keyed by the model and catalogue basenames."""
    chassis = {
        'best_mech': engineering_mech(),
        'best_combat_mech': combat_mech(),
        'best_universal_mech': universal_mech(),
    }
    return {
        **chassis,
        **{f'{key}_loaded': loaded(image) for key, image in chassis.items()},
        'best_canglan_engineering': chip('engineering', YELLOW),
        'best_canglan_mobility': chip('mobility', YELLOW),
        'best_canglan_endurance': chip('endurance', YELLOW),
        'best_nutao_fire_control': chip('fire', RED),
        'best_nutao_armor': chip('armor', RED),
        'best_nutao_sustained': chip('sustained', RED),
        'best_zhenyuan_module': chip('zhenyuan', NAVY),
        'best_tianpu_module': chip('tianpu', CYAN),
        'best_changchuan_module': chip('changchuan', WHITE),
        'best_engineering_confluence': coupler(),
        'best_combat_confluence': coupler(combat=True),
        'best_fire_control': pack('fire_control'),
        'best_load_armor': armor(load_phase=True),
        'best_weapon_radiator': radiator(weaponized=True),
        'best_frame': assist_frame(),
        'best_cutter': cutter(),
        'best_service_kit': pack('service'),
        'best_stride_module': pistons(stride=True),
        'best_diagnostic_tool': pack('diagnostic'),
        'best_mech_drill_arm': tool_arm('drill'),
        'best_mech_excavator_arm': tool_arm('excavator'),
        'best_mech_heavy_drill_arm': tool_arm('heavy_drill'),
        'best_mech_heavy_excavator_arm': tool_arm('heavy_excavator'),
        'best_mech_radiator': radiator(),
        'best_mech_drive': pistons(),
        'best_mech_reinforced_body': armor(),
        'best_mech_cruise_wheels': wheels(),
        'best_mech_generator_core': core('generator'),
        'best_mech_wireless_charger': pack('wireless'),
        'best_mech_heavy_fusion_core': core('fusion'),
        'best_mech_heavy_fusion_fuel': core('fuel'),
        'best_mech_repair_module': pack('repair'),
        'best_mech_friendly_repair_module': pack('friendly'),
        'best_mech_maintenance_drone': drone('maintenance', GREEN),
        'best_mech_healing_drone': drone('healing', CYAN),
        'best_mech_medical_supply': pack('medical'),
        'best_mech_melee_weapon': weapon('blade'),
        'best_mech_machine_gun': weapon('gun'),
        'best_mech_sniper': weapon('sniper'),
        'best_mech_laser': weapon('laser'),
        'best_mech_explosive_launcher': weapon('launcher'),
        'best_mech_attack_drone': drone('attack', RED),
        'best_mech_defense_drone': drone('defense', CYAN),
        'best_mech_bullet': ammo('bullet'),
        'best_mech_sniper_round': ammo('sniper'),
        'best_mech_explosive_round': ammo('explosive'),
        'best_workbench': facility_icon(),
        'mech_maintenance_platform': facility_icon(platform=True),
    }


def workbench_model():
    directions = ('north', 'south', 'east', 'west', 'up', 'down')

    def box(start, end, texture='surface', **overrides):
        return {
            'from': start, 'to': end,
            'faces': {face: {'texture': f'#{overrides.get(face, texture)}', 'uv': [0, 0, 16, 16]}
                      for face in directions},
        }

    return {
        'parent': 'minecraft:block/block',
        'ambientocclusion': True,
        'textures': {
            'surface': f'{MOD}:block/best_workbench_structure',
            'top': f'{MOD}:block/best_workbench_top',
            'control': f'{MOD}:block/best_workbench_control',
            'particle': '#surface',
        },
        'elements': [
            box([0, 10, 0], [16, 12, 16], up='top'),
            box([1, 0, 1], [4, 10, 4]), box([12, 0, 1], [15, 10, 4]),
            box([1, 0, 12], [4, 10, 15]), box([12, 0, 12], [15, 10, 15]),
            box([3, 2, 3], [13, 4, 14], up='top'),
            box([3, 5, 1], [13, 9, 3], north='control'),
            box([2, 12, 4], [6, 14, 9]), box([3, 14, 5], [5, 16, 8]),
        ],
    }


def save_pngs(directory, images):
    directory.mkdir(parents=True, exist_ok=True)
    for key, image in images.items():
        image.save(directory / f'{key}.png')


def save_model(path, model):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8', newline='\n')


def write_assets():
    """Regenerate the Minecraft visual assets without reading old output."""
    items = render_items()
    save_pngs(ART / 'textures/item', items)
    save_pngs(ART / 'textures/block', {**bench_faces(), **platform_faces()})
    for key in items:
        variant = f'{key}_loaded'
        if variant not in items and not key.endswith('_loaded') and key not in ('best_workbench', 'mech_maintenance_platform'):
            continue
        model = {
            'parent': 'minecraft:item/generated',
            'textures': {'layer0': f'{MOD}:item/{key}'},
        }
        if variant in items:
            model['overrides'] = [{
                'predicate': {f'{MOD}:creative_loadout': 1.0},
                'model': f'{MOD}:item/{variant}',
            }]
        save_model(ART / 'models/item' / f'{key}.json', model)
    save_model(ART / 'models/block/best_workbench.json', workbench_model())
    save_model(ART / 'models/block/mech_maintenance_platform.json', {
        'parent': 'minecraft:block/cube_bottom_top',
        'textures': {
            'top': f'{MOD}:block/mech_maintenance_platform',
            'side': f'{MOD}:block/mech_maintenance_platform_side',
            'bottom': f'{MOD}:block/mech_maintenance_platform_bottom',
        },
    })


def main():
    parser = ArgumentParser(description=__doc__)
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument('--list', action='store_true', help='list item export keys')
    actions.add_argument('--item-dir', type=Path, help='export item sprites only to this directory')
    options = parser.parse_args()
    if options.list:
        print('\n'.join(render_items()))
    elif options.item_dir is not None:
        save_pngs(options.item_dir, render_items())
    else:
        write_assets()


if __name__ == '__main__':
    main()
