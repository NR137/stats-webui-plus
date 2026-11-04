"""Tests for the helpers of extract_mc_textures.py: python -m unittest discover -s scripts -p 'test_*.py'

The PNG reader is checked against Pillow, which decodes every PNG kind the way the PNG spec says; the CHANGELOG fixed
its transparency in grayscale and RGB images (tRNS) and wiki downloads (rate limits, 1x1 placeholders).
"""

import email.message
import io
import random
import sys
import tempfile
import unittest
import urllib.error
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
import extract_mc_textures as tex  # noqa: E402

RANDOM = random.Random(26)


def png_bytes(image: Image.Image, **options) -> bytes:
	buffer = io.BytesIO()
	image.save(buffer, format='PNG', **options)
	return buffer.getvalue()


def pillow_rgba(data: bytes) -> list[list[tuple[int, int, int, int]]]:
	image = Image.open(io.BytesIO(data)).convert('RGBA')
	width, height = image.size
	pixels = image.load()
	return [[tuple(pixels[x, y]) for x in range(width)] for y in range(height)]


def random_image(mode: str, size=(7, 5)) -> Image.Image:
	image = Image.new(mode, size)
	bands = len(image.getbands())
	limit = 1 if mode == '1' else 255
	data = [tuple(RANDOM.randint(0, limit) for _ in range(bands)) if bands > 1 else RANDOM.randint(0, limit)
		for _ in range(size[0] * size[1])]
	image.putdata(data)
	return image


class DecodePng(unittest.TestCase):
	def assert_like_pillow(self, data: bytes, what: str):
		self.assertEqual(tex.decode_png(data), pillow_rgba(data), what)

	def test_rgba(self):
		self.assert_like_pillow(png_bytes(random_image('RGBA')), 'RGBA')

	def test_rgb(self):
		self.assert_like_pillow(png_bytes(random_image('RGB')), 'RGB')

	def test_grayscale(self):
		self.assert_like_pillow(png_bytes(random_image('L')), 'grayscale')

	def test_grayscale_with_alpha(self):
		self.assert_like_pillow(png_bytes(random_image('LA')), 'grayscale + alpha')

	def test_one_bit(self):
		self.assert_like_pillow(png_bytes(random_image('1')), '1-bit')

	def test_palette(self):
		image = random_image('RGB').convert('P', palette=Image.Palette.ADAPTIVE, colors=200)
		self.assert_like_pillow(png_bytes(image), 'palette')

	def test_small_palettes_pack_several_pixels_per_byte(self):
		for colors, bits in ((2, 1), (4, 2), (16, 4)):
			image = random_image('RGB', (9, 3)).convert('P', palette=Image.Palette.ADAPTIVE, colors=colors)
			self.assert_like_pillow(png_bytes(image, bits=bits), f'{bits}-bit palette')

	def test_palette_with_transparency(self):
		image = random_image('RGB').convert('P', palette=Image.Palette.ADAPTIVE, colors=8)
		self.assert_like_pillow(png_bytes(image, transparency=bytes([0, 128, 255, 40])), 'palette + tRNS')

	def test_grayscale_with_transparent_colour(self):
		# CHANGELOG: transparency in grayscale images (one gray value marked transparent by tRNS) was ignored.
		image = random_image('L')
		image.putpixel((0, 0), 77)
		data = png_bytes(image, transparency=77)
		self.assertEqual(tex.decode_png(data)[0][0][3], 0)
		self.assert_like_pillow(data, 'grayscale + tRNS')

	def test_rgb_with_transparent_colour(self):
		# CHANGELOG: the same for RGB images (e.g. the spruce leaves came out black where they are see-through).
		image = random_image('RGB')
		image.putpixel((2, 1), (10, 20, 30))
		data = png_bytes(image, transparency=(10, 20, 30))
		self.assertEqual(tex.decode_png(data)[1][2][3], 0)
		self.assert_like_pillow(data, 'RGB + tRNS')

	def test_every_png_filter(self):
		# Pillow picks a filter per row; a larger, smooth image makes it use all five.
		image = Image.linear_gradient('L').resize((64, 64)).convert('RGBA')
		self.assert_like_pillow(png_bytes(image, optimize=True), 'filters')

	def test_not_a_png(self):
		self.assertIsNone(tex.decode_png(b'GIF89a not a png'))
		self.assertIsNone(tex.decode_png(b''))


class WritePng(unittest.TestCase):
	def test_round_trip(self):
		pixels = [[tuple(RANDOM.randint(0, 255) for _ in range(4)) for _ in range(6)] for _ in range(4)]
		with tempfile.TemporaryDirectory() as folder:
			path = Path(folder) / 'out.png'
			tex.write_png(path, pixels)
			data = path.read_bytes()
		self.assertEqual(tex.decode_png(data), pixels)
		self.assertEqual(pillow_rgba(data), pixels, 'Pillow reads the written file the same way')


class Pixels(unittest.TestCase):
	def test_scale_up_repeats_pixels(self):
		red, blue = (255, 0, 0, 255), (0, 0, 255, 255)
		scaled = tex.scale_nn([[red, blue]], 4, 2)
		self.assertEqual(scaled, [[red, red, blue, blue], [red, red, blue, blue]])

	def test_scale_keeps_size_when_equal(self):
		pixels = [[(1, 2, 3, 4), (5, 6, 7, 8)]]
		self.assertEqual(tex.scale_nn(pixels, 2, 1), pixels)

	def test_crop(self):
		pixels = [[(x, y, 0, 255) for x in range(4)] for y in range(3)]
		self.assertEqual(tex.crop_px(pixels, 1, 1, 2, 2), [[(1, 1, 0, 255), (2, 1, 0, 255)], [(1, 2, 0, 255), (2, 2, 0, 255)]])

	def test_shade_keeps_alpha_and_stays_in_range(self):
		self.assertEqual(tex.shade((100, 50, 200, 128), 0.5), (50, 25, 100, 128))
		brighter = tex.shade((200, 250, 10, 7), 2.0)
		self.assertTrue(all(0 <= channel <= 255 for channel in brighter), brighter)
		self.assertEqual(brighter[3], 7)

	def test_argb_colours_from_item_definitions(self):
		# Minecraft writes tint colours as signed 32-bit ARGB ints, e.g. -1 for white.
		self.assertEqual(tex.argb_to_rgb(0xFF336699), (0x33, 0x66, 0x99))
		self.assertEqual(tex.argb_to_rgb(-1), (255, 255, 255))
		self.assertEqual(tex.argb_to_rgb(-12012264), (0x48, 0xB5, 0x18))


class Models(unittest.TestCase):
	def test_model_path(self):
		self.assertEqual(tex.model_path('minecraft:block/stone'), 'assets/minecraft/models/block/stone.json')
		self.assertEqual(tex.model_path('block/stone'), 'assets/minecraft/models/block/stone.json')

	def test_texture_references_are_resolved(self):
		# Model format: "#name" refers to another texture variable, also in chains.
		resolved = tex.resolve_refs({'all': 'minecraft:block/stone', 'side': '#all', 'particle': '#side'})
		self.assertEqual(resolved['side'], 'minecraft:block/stone')
		self.assertEqual(resolved['particle'], 'minecraft:block/stone')
		self.assertEqual(resolved['all'], 'minecraft:block/stone')

	def test_unresolvable_and_circular_references_end(self):
		resolved = tex.resolve_refs({'a': '#b', 'b': '#a', 'c': '#missing', 'd': 'minecraft:block/dirt'})
		self.assertEqual(resolved.get('d'), 'minecraft:block/dirt')
		for key in ('a', 'b', 'c'):
			self.assertFalse(str(resolved.get(key, '')).startswith('minecraft:'), (key, resolved))


class WikiDownloads(unittest.TestCase):
	def test_image_types(self):
		self.assertEqual(tex.image_extension(png_bytes(random_image('RGBA'))), 'png')
		gif = io.BytesIO()
		random_image('RGB').save(gif, format='GIF')
		self.assertEqual(tex.image_extension(gif.getvalue()), 'gif')
		self.assertIsNone(tex.image_extension(b'<!doctype html>'))

	def test_one_pixel_placeholder_counts_as_missing(self):
		# CHANGELOG: the wiki's 1x1 placeholder images count as missing.
		self.assertTrue(tex.is_placeholder(png_bytes(Image.new('RGBA', (1, 1))), 'png'))
		self.assertFalse(tex.is_placeholder(png_bytes(Image.new('RGBA', (16, 16))), 'png'))
		gif = io.BytesIO()
		Image.new('P', (1, 1)).save(gif, format='GIF')
		self.assertTrue(tex.is_placeholder(gif.getvalue(), 'gif'))

	@staticmethod
	def http_error(status: int, retry_after: str | None) -> urllib.error.HTTPError:
		headers = email.message.Message()
		if retry_after is not None:
			headers['Retry-After'] = retry_after
		return urllib.error.HTTPError('https://minecraft.wiki/x.png', status, 'busy', headers, None)

	def test_pause_as_long_as_the_wiki_asks(self):
		# CHANGELOG: rate-limited downloads pause as long as the wiki asks (Retry-After), within sane bounds.
		self.assertEqual(tex.retry_after_seconds(self.http_error(429, '30'), 0), 30)
		self.assertEqual(tex.retry_after_seconds(self.http_error(429, '100000'), 0), tex.MAX_PAUSE_SECONDS)
		self.assertGreaterEqual(tex.retry_after_seconds(self.http_error(503, '0'), 0), 1)

	def test_pause_without_retry_after_grows(self):
		pauses = [tex.retry_after_seconds(self.http_error(429, None), attempt) for attempt in range(5)]
		self.assertTrue(all(1 <= pause <= tex.MAX_PAUSE_SECONDS for pause in pauses), pauses)
		self.assertEqual(pauses, sorted(pauses), pauses)
		self.assertGreater(pauses[-1], pauses[0], pauses)


if __name__ == '__main__':
	unittest.main()
