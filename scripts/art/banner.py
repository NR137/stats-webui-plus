"""The pixel-art banner the mod ships as its default header image: a floating island on a transparent background.

Drawn at 208 x 64 art pixels and scaled up 6x without smoothing, then trimmed: 1152 x 240, so the page's 120 px high
header box shows whole art pixels on high-DPI screens. Needs Pillow (scripts/requirements.txt).

Usage: python scripts/art/banner.py [text or -] [font] [output]
Defaults: no text, the web UI's Minecraft font, common/src/main/resources/assets/stats_webui_plus/header.png.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
LABEL = sys.argv[1] if len(sys.argv) > 1 else '-'
FONT = sys.argv[2] if len(sys.argv) > 2 and sys.argv[2] else str(ROOT / 'web' / 'src' / 'lib' / 'assets' / 'fonts' / 'Minecraft.ttf')
OUTPUT = sys.argv[3] if len(sys.argv) > 3 else str(ROOT / 'common' / 'src' / 'main' / 'resources' / 'assets' / 'stats_webui_plus' / 'header.png')
W, H, SCALE = 208, 64, 6
# With a text above it, the island moves down to make room.
LIFT = 0 if LABEL == '-' else 14
rng = random.Random(137)
img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
px = img.load()


def put(x, y, color):
	if 0 <= x < W and 0 <= y < H:
		px[x, y] = color + (255,) if len(color) == 3 else color


def pick(*colors):
	return rng.choice(colors)


GRASS = [(95, 159, 53), (106, 170, 64), (84, 143, 46)]
GRASS_SIDE = [(95, 159, 53), (84, 143, 46)]
DIRT = [(134, 96, 67), (121, 85, 58), (150, 108, 74), (108, 76, 52)]
STONE = [(125, 125, 125), (116, 116, 116), (136, 136, 136), (107, 107, 107)]
ORES = {
	'coal': (40, 40, 40),
	'iron': (216, 175, 147),
	'gold': (252, 238, 75),
	'diamond': (93, 236, 245),
	'redstone': (200, 20, 20),
	'emerald': (23, 221, 98),
}

left, right = 10, 198
top = {}
bottom = {}
for x in range(left, right):
	t = (x - left) / (right - left)
	top[x] = 27 + LIFT + round(math.sin(x / 9.0) * 1.2 + math.sin(x / 23.0) * 1.0)
	# Deepest in the middle, tapering to the ends, with a ragged edge.
	depth = 4 + (1 - abs(t - 0.5) * 2) ** 0.7 * 15
	bottom[x] = min(H - 2, round(top[x] + depth + rng.choice([-1, 0, 0, 1])))

for x in range(left, right):
	for y in range(top[x], bottom[x] + 1):
		d = y - top[x]
		if d < 2:
			put(x, y, pick(*GRASS))
		elif d < 3 and rng.random() < 0.5:
			put(x, y, pick(*GRASS_SIDE))
		elif d < 6:
			put(x, y, pick(*DIRT))
		else:
			put(x, y, pick(*STONE))

# Ore veins in the stone.
for _ in range(34):
	x = rng.randrange(left + 4, right - 4)
	y = rng.randrange(top[x] + 7, max(top[x] + 8, bottom[x] - 1))
	color = ORES[rng.choice(['coal', 'coal', 'coal', 'iron', 'iron', 'gold', 'redstone', 'diamond', 'emerald'])]
	for dx, dy in [(0, 0), (1, 0), (0, 1), (1, 1)]:
		if rng.random() < 0.7 and px[x + dx, y + dy][3] and y + dy <= bottom[x + dx] and y + dy - top[x + dx] >= 6:
			put(x + dx, y + dy, color)


def tree(x0):
	ground = top[x0]
	trunk = [(107, 81, 51), (94, 70, 43)]
	leaves = [(52, 122, 39), (63, 140, 47), (44, 104, 33)]
	for y in range(ground - 7, ground):
		put(x0, y, pick(*trunk))
		put(x0 + 1, y, pick(*trunk))
	for y in range(ground - 13, ground - 6):
		half = 4 if ground - 11 <= y <= ground - 8 else 2
		for x in range(x0 - half + 1, x0 + half + 1):
			if rng.random() < 0.93:
				put(x, y, pick(*leaves))


def house(x0):
	ground = min(top[x] for x in range(x0, x0 + 14))
	planks = [(162, 130, 78), (150, 119, 70)]
	logs = (107, 81, 51)
	roof = [(140, 60, 40), (122, 50, 33)]
	for x in range(x0, x0 + 14):
		for y in range(top[x] - 1, ground):
			put(x, y, pick(*planks))
	for x in range(x0, x0 + 14):
		for y in range(ground - 7, ground):
			put(x, y, logs if x in (x0, x0 + 13) else pick(*planks))
	for y in range(ground - 4, ground):
		put(x0 + 3, y, (90, 60, 35))
		put(x0 + 4, y, (90, 60, 35))
	for x in range(x0 + 8, x0 + 11):
		for y in range(ground - 5, ground - 3):
			put(x, y, (170, 210, 235))
	for i in range(8):
		for x in range(x0 - 1 + i, x0 + 15 - i):
			put(x, ground - 8 - i, pick(*roof))
	put(x0 + 11, ground - 14, (120, 120, 120))
	put(x0 + 11, ground - 15, (120, 120, 120))


def flowers():
	for x in range(left + 3, right - 3):
		if rng.random() < 0.10 and px[x, top[x] - 1][3] == 0:
			color = rng.choice([(220, 30, 30), (250, 220, 40), (240, 240, 255), (60, 120, 230)])
			put(x, top[x] - 1, (60, 130, 40))
			put(x, top[x] - 2, color)


def torch(x0):
	put(x0, top[x0] - 1, (107, 81, 51))
	put(x0, top[x0] - 2, (107, 81, 51))
	put(x0, top[x0] - 3, (255, 200, 60))


def text(label):
	font = ImageFont.truetype(FONT, 16)
	layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
	draw = ImageDraw.Draw(layer)
	box = draw.textbbox((0, 0), label, font=font)
	x = (W - (box[2] - box[0])) // 2 - box[0]
	draw.text((x, 3 - box[1]), label, font=font, fill=(255, 255, 255, 255))
	# Hard pixels only: the font is drawn at twice its native 8 px grid, with a 2 px shadow like Minecraft's.
	for yy in range(H):
		for xx in range(W):
			r, g, b, a = layer.getpixel((xx, yy))
			if a > 127:
				put(xx, yy, (255, 255, 255))
				[put(xx + d, yy + d, (63, 63, 63)) for d in (1, 2) if px[xx + d, yy + d][3] == 0]


tree(30)
tree(52)
house(92)
tree(150)
tree(171)
torch(88)
torch(110)
flowers()
if LABEL != '-':
	text(LABEL)

# One-pixel dark outline around everything, so it reads on light and dark pages alike.
solid = [[px[x, y][3] > 0 for y in range(H)] for x in range(W)]
for x in range(W):
	for y in range(H):
		if not solid[x][y] and any(
			0 <= x + dx < W and 0 <= y + dy < H and solid[x + dx][y + dy]
			for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]
		):
			put(x, y, (24, 18, 12))

# Trim empty rows and columns, keeping one pixel of air, so the page can show the island as large as possible.
l, t, r, b = img.getbbox()
img = img.crop((max(0, l - 1), max(0, t - 1), min(W, r + 1), min(H, b + 1)))
img.resize((img.width * SCALE, img.height * SCALE), Image.NEAREST).save(OUTPUT)
