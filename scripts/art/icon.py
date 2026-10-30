"""The mod icon: a podium (gold, iron, copper) on a Minecraft inventory slot, with a "+" for Plus.

Drawn on a 32 x 32 grid and scaled up 4x without smoothing: 128 x 128. Needs Pillow (scripts/requirements.txt).

Usage: python scripts/art/icon.py [output]
Default output: common/src/main/resources/assets/stats_webui_plus/icon.png
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw

G = 32
img = Image.new("RGBA", (G, G), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

def rect(x0, y0, x1, y1, color):
    d.rectangle((x0, y0, x1, y1), fill=color)

# Slot: dark face, light bottom-right / dark top-left bevel like an inventory slot.
rect(0, 0, 31, 31, "#1e1e1e")
rect(1, 1, 30, 30, "#8b8b8b")
rect(2, 2, 29, 29, "#373737")
rect(2, 2, 29, 2, "#2a2a2a"); rect(2, 2, 2, 29, "#2a2a2a")
rect(3, 29, 29, 29, "#4a4a4a"); rect(29, 3, 29, 29, "#4a4a4a")

def block(x0, top, x1, light, mid, dark, label):
    """A podium block from `top` down to the floor, with a highlight and shade edge and a place number."""
    rect(x0, top, x1, 26, mid)
    rect(x0, top, x1, top, light); rect(x0, top, x0, 26, light)
    rect(x1, top + 1, x1, 26, dark); rect(x0 + 1, 26, x1, 26, dark)
    cx = (x0 + x1) // 2
    for dx, dy in label:
        d.point((cx + dx, top + 3 + dy), fill=dark)

def glyph(*rows):
    return [(x - 1, y) for y, row in enumerate(rows) for x, c in enumerate(row) if c == "X"]

one = glyph(".X.", "XX.", ".X.", ".X.", "XXX")
two = glyph("XX.", "..X", ".X.", "X..", "XXX")
three = glyph("XX.", "..X", ".X.", "..X", "XX.")
block(4, 15, 11, "#e8e8e8", "#c6c6c6", "#8a8a8a", two)        # 2nd: iron
block(12, 10, 19, "#fff3a0", "#e6c35c", "#a07a1c", one)        # 1st: gold
block(20, 18, 27, "#f0a77e", "#c96f45", "#8c4526", three)      # 3rd: copper
rect(3, 27, 28, 27, "#262626")                                 # floor shadow

# "+" (emerald green) in the top right corner.
rect(24, 5, 25, 12, "#1d7a32"); rect(21, 8, 28, 9, "#1d7a32")
rect(24, 6, 25, 11, "#55ff55"); rect(22, 8, 27, 9, "#55ff55")
rect(24, 8, 25, 9, "#b6ffb6")

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = sys.argv[1] if len(sys.argv) > 1 else ROOT / "common" / "src" / "main" / "resources" / "assets" / "stats_webui_plus" / "icon.png"
img.resize((G * 4, G * 4), Image.NEAREST).save(OUTPUT)
