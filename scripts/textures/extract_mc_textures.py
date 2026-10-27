import argparse
import json
import os
import shutil
import struct
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
import zlib
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STATIC = ROOT / "web" / "static" / "mc"
ICONS = ROOT / "web" / "src" / "lib" / "mc" / "icons.json"
ADVANCEMENT_LANG = ROOT / "web" / "src" / "lib" / "mc" / "advancementLang.json"
# minecraft.wiki downloads can change between Minecraft versions, so they are cached in <root>/<version>/.
INV_CACHE_ROOT = ROOT / "scripts" / ".cache" / "invicons"
MOB_CACHE_ROOT = ROOT / "scripts" / ".cache" / "mob-sprites"
JAR_CACHE = ROOT / "scripts" / ".cache" / "minecraft"
MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
ASSET_CDN = "https://resources.download.minecraft.net"
WIKI_FILE = "https://minecraft.wiki/w/Special:FilePath/"
USER_AGENT = "stats-webui-plus texture extract"
CUBE_HINTS = (
	"block/cube",
	"block/cube_all",
	"block/cube_column",
	"block/cube_column_horizontal",
	"block/cube_bottom_top",
	"block/cube_top",
	"block/cube_mirrored",
	"block/orientable",
	"block/leaves",
)
LANG_FILES = {
	"en": "assets/minecraft/lang/en_us.json",
	"es": "assets/minecraft/lang/es_es.json",
	"fr": "assets/minecraft/lang/fr_fr.json",
	"de": "assets/minecraft/lang/de_de.json",
}
_VERSION_META: dict[str, dict] = {}


def default_jar(version: str) -> Path:
	return JAR_CACHE / version / "minecraft-client.jar"


def fetch_json(url: str):
	request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
	with urllib.request.urlopen(request) as response:
		return json.load(response)


def version_meta(version: str) -> dict:
	cached = _VERSION_META.get(version)
	if cached:
		return cached
	manifest = fetch_json(MANIFEST_URL)
	entry = next((item for item in manifest["versions"] if item["id"] == version), None)
	if not entry:
		raise SystemExit(f"Minecraft version not found in manifest: {version}")
	meta = fetch_json(entry["url"])
	_VERSION_META[version] = meta
	return meta


def download_client_jar(version: str, dest: Path) -> None:
	print(f"Downloading Minecraft {version} client jar…")
	meta = version_meta(version)
	client = meta.get("downloads", {}).get("client")
	if not client or "url" not in client:
		raise SystemExit(f"No client download for Minecraft {version}")
	dest.parent.mkdir(parents=True, exist_ok=True)
	tmp = dest.with_suffix(".jar.tmp")
	urllib.request.urlretrieve(client["url"], tmp)
	tmp.replace(dest)
	print(f"Saved {dest}")


def download_lang_asset(version: str, filename: str) -> dict | None:
	cache = default_jar(version).parent / "lang" / filename
	if cache.is_file():
		try:
			data = json.loads(cache.read_text(encoding="utf-8"))
			if isinstance(data, dict) and data:
				return data
		except json.JSONDecodeError:
			pass
	meta = version_meta(version)
	index = meta.get("assetIndex")
	if not isinstance(index, dict) or "url" not in index:
		return None
	assets = fetch_json(index["url"])
	obj = assets.get("objects", {}).get(f"minecraft/lang/{filename}")
	if not isinstance(obj, dict) or "hash" not in obj:
		return None
	digest = str(obj["hash"])
	url = f"{ASSET_CDN}/{digest[:2]}/{digest}"
	print(f"Downloading {filename}…")
	data = fetch_json(url)
	if not isinstance(data, dict):
		return None
	cache.parent.mkdir(parents=True, exist_ok=True)
	cache.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
	return data


def resolve_jar(version: str, jar_arg: str | None) -> Path:
	jar = Path(jar_arg) if jar_arg else default_jar(version)
	if jar.is_file():
		return jar
	download_client_jar(version, jar)
	if not jar.is_file():
		raise SystemExit(f"Missing Minecraft client jar: {jar}")
	return jar


def tex_url(tex: str) -> str | None:
	value = tex.replace("minecraft:", "")
	if value.startswith("#"):
		return None
	if value.startswith("item/"):
		return "/mc/item/" + value[5:] + ".png"
	if value.startswith("block/"):
		return "/mc/block/" + value[6:] + ".png"
	if "/" not in value:
		return "/mc/item/" + value + ".png"
	return "/mc/" + value + ".png"


def load_json(jar: zipfile.ZipFile, path: str):
	try:
		return json.loads(jar.read(path))
	except KeyError:
		return None


def first_string_model(node) -> str | None:
	models = collect_model_ids(node)
	return models[0] if models else None


def collect_model_ids(node) -> list[str]:
	found: list[str] = []
	if isinstance(node, dict):
		kind = str(node.get("type", "")).replace("minecraft:", "")
		model = node.get("model")
		if kind == "model" and isinstance(model, str):
			found.append(model)
		for value in node.values():
			found.extend(collect_model_ids(value))
	elif isinstance(node, list):
		for value in node:
			found.extend(collect_model_ids(value))
	return found


def node_has_type(node, *types: str) -> bool:
	wanted = {item.replace("minecraft:", "") for item in types}
	if isinstance(node, dict):
		kind = str(node.get("type", "")).replace("minecraft:", "")
		if kind in wanted:
			return True
		return any(node_has_type(value, *types) for value in node.values())
	if isinstance(node, list):
		return any(node_has_type(value, *types) for value in node)
	return False


def model_is_cube(jar: zipfile.ZipFile, model_id: str) -> bool:
	seen: set[str] = set()
	current = model_id
	while current and current not in seen:
		seen.add(current)
		path = current.replace("minecraft:", "")
		if any(hint in path for hint in CUBE_HINTS):
			return True
		data = load_json(jar, model_path(current))
		if not data:
			return False
		parent = data.get("parent")
		if not isinstance(parent, str):
			return False
		current = parent
	return False


def item_models_are_cube(jar: zipfile.ZipFile, data: dict) -> bool:
	if node_has_type(data, "composite", "special"):
		return False
	models = collect_model_ids(data)
	return bool(models) and all(model_is_cube(jar, model) for model in models)


def model_path(model_id: str) -> str:
	value = model_id.replace("minecraft:", "")
	if not value.startswith("block/") and not value.startswith("item/"):
		value = "item/" + value
	return "assets/minecraft/models/" + value + ".json"


def textures_from_model(jar: zipfile.ZipFile, model_id: str, seen: set[str]) -> dict:
	if not model_id or model_id in seen:
		return {}
	seen.add(model_id)
	data = load_json(jar, model_path(model_id))
	if not data:
		return {}
	merged = {}
	parent = data.get("parent")
	if isinstance(parent, str):
		merged.update(textures_from_model(jar, parent, seen))
	textures = data.get("textures")
	if isinstance(textures, dict):
		merged.update(textures)
	return merged


def resolve_refs(textures: dict) -> dict[str, str]:
	resolved = {key: value for key, value in textures.items() if isinstance(value, str)}
	changed = True
	while changed:
		changed = False
		for key, value in list(resolved.items()):
			if value.startswith("#"):
				ref = resolved.get(value[1:])
				if isinstance(ref, str) and not ref.startswith("#"):
					resolved[key] = ref
					changed = True
	return {key: value for key, value in resolved.items() if not value.startswith("#")}


def first_tex(textures: dict[str, str], *keys: str) -> str | None:
	for key in keys:
		value = textures.get(key)
		if value:
			return value
	return None


def cube_faces(textures: dict) -> tuple[str, str, str] | None:
	resolved = resolve_refs(textures)
	if "layer0" in resolved or "cross" in resolved or "plant" in resolved:
		return None
	fill = first_tex(resolved, "all", "particle", "texture")
	up = first_tex(resolved, "up", "top", "end") or fill
	left = first_tex(resolved, "south", "front", "north", "side") or fill
	right = first_tex(resolved, "east", "west", "side") or fill
	if up and left and right:
		return up, left, right
	return None


def static_path(url: str) -> Path:
	relative = url[4:] if url.startswith("/mc/") else url
	return STATIC / relative


def read_png(path: Path) -> list[list[tuple[int, int, int, int]]] | None:
	try:
		data = path.read_bytes()
	except OSError:
		return None
	return decode_png(data)


def decode_png(data: bytes) -> list[list[tuple[int, int, int, int]]] | None:
	if data[:8] != b"\x89PNG\r\n\x1a\n":
		return None
	pos = 8
	width = height = None
	raw = b""
	palette = []
	alpha = None
	color_type = 6
	while pos + 8 <= len(data):
		size, kind = struct.unpack(">I4s", data[pos : pos + 8])
		chunk = data[pos + 8 : pos + 8 + size]
		pos += 12 + size
		if kind == b"IHDR":
			width, height, bit, color_type = struct.unpack(">IIBB", chunk[:10])
			if bit not in (1, 2, 4, 8):
				return None
		elif kind == b"PLTE":
			palette = [tuple(chunk[i : i + 3]) for i in range(0, len(chunk), 3)]
		elif kind == b"tRNS":
			alpha = chunk
		elif kind == b"IDAT":
			raw += chunk
		elif kind == b"IEND":
			break
	if width is None or height is None:
		return None
	try:
		decoded = zlib.decompress(raw)
	except zlib.error:
		return None
	channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}.get(color_type)
	if channels is None:
		return None
	bpp = max(1, (channels * bit + 7) // 8)
	stride = (width * channels * bit + 7) // 8
	rows = []
	src = 0
	prev = bytearray(stride)
	for _ in range(height):
		if src + 1 + stride > len(decoded):
			return None
		filter_type = decoded[src]
		scan = bytearray(decoded[src + 1 : src + 1 + stride])
		src += 1 + stride
		for i, value in enumerate(scan):
			left = scan[i - bpp] if i >= bpp else 0
			up = prev[i]
			up_left = prev[i - bpp] if i >= bpp else 0
			if filter_type == 1:
				scan[i] = (value + left) & 255
			elif filter_type == 2:
				scan[i] = (value + up) & 255
			elif filter_type == 3:
				scan[i] = (value + ((left + up) >> 1)) & 255
			elif filter_type == 4:
				p = left + up - up_left
				pa, pb, pc = abs(p - left), abs(p - up), abs(p - up_left)
				pr = left if pa <= pb and pa <= pc else up if pb <= pc else up_left
				scan[i] = (value + pr) & 255
			elif filter_type != 0:
				return None
		prev = scan
		row = []
		for x in range(width):
			if color_type == 3:
				index = sample_bits(scan, x, bit)
				rgb = palette[index] if index < len(palette) else (0, 0, 0)
				a = alpha[index] if alpha and index < len(alpha) else 255
				row.append((rgb[0], rgb[1], rgb[2], a))
			elif color_type == 0:
				sample = sample_bits(scan, x, bit)
				# For grayscale images tRNS names one sample value that is fully transparent.
				a = 0 if alpha and len(alpha) >= 2 and sample == int.from_bytes(alpha[:2], "big") else 255
				value = sample * (255 // ((1 << bit) - 1)) if bit < 8 else sample
				row.append((value, value, value, a))
			else:
				i = x * channels
				if color_type == 6:
					row.append((scan[i], scan[i + 1], scan[i + 2], scan[i + 3]))
				elif color_type == 2:
					# For RGB images tRNS names one (16-bit per channel) color that is fully transparent.
					key = (alpha[1], alpha[3], alpha[5]) if alpha and len(alpha) >= 6 else None
					rgb = (scan[i], scan[i + 1], scan[i + 2])
					row.append((*rgb, 0 if rgb == key else 255))
				else:
					row.append((scan[i], scan[i], scan[i], scan[i + 1]))
		rows.append(row)
	return rows


def sample_bits(scan: bytearray, index: int, bit: int) -> int:
	if bit == 8:
		return scan[index]
	ppb = 8 // bit
	byte = scan[index // ppb]
	shift = 8 - bit * (index % ppb + 1)
	return (byte >> shift) & ((1 << bit) - 1)


def write_png(path: Path, pixels: list[list[tuple[int, int, int, int]]]) -> None:
	height = len(pixels)
	width = len(pixels[0]) if height else 0
	raw = b"".join(b"\x00" + bytes(channel for pixel in row for channel in pixel) for row in pixels)

	def chunk(tag: bytes, body: bytes) -> bytes:
		return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)

	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_bytes(
		b"\x89PNG\r\n\x1a\n"
		+ chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
		+ chunk(b"IDAT", zlib.compress(raw, 9))
		+ chunk(b"IEND", b"")
	)


def scale_nn(
	pixels: list[list[tuple[int, int, int, int]]], width: int, height: int
) -> list[list[tuple[int, int, int, int]]]:
	source_h = len(pixels)
	source_w = len(pixels[0]) if source_h else 0
	if source_w == width and source_h == height:
		return [row[:] for row in pixels]
	if source_w == 0 or source_h == 0:
		return [[(0, 0, 0, 0)] * width for _ in range(height)]
	return [
		[pixels[int(y * source_h / height)][int(x * source_w / width)] for x in range(width)]
		for y in range(height)
	]


def crop_px(
	pixels: list[list[tuple[int, int, int, int]]], x: int, y: int, width: int, height: int
) -> list[list[tuple[int, int, int, int]]]:
	return [row[x : x + width] for row in pixels[y : y + height]]


def shade(pixel: tuple[int, int, int, int], factor: float) -> tuple[int, int, int, int]:
	return (min(255, int(pixel[0] * factor)), min(255, int(pixel[1] * factor)), min(255, int(pixel[2] * factor)), pixel[3])


def put_pixel(
	canvas: list[list[tuple[int, int, int, int]]], x: int, y: int, pixel: tuple[int, int, int, int]
) -> None:
	if pixel[3] == 0 or y < 0 or x < 0 or y >= len(canvas) or x >= len(canvas[0]):
		return
	if pixel[3] == 255:
		canvas[y][x] = pixel
		return
	dst = canvas[y][x]
	a = pixel[3] / 255
	canvas[y][x] = (
		int(pixel[0] * a + dst[0] * (1 - a)),
		int(pixel[1] * a + dst[1] * (1 - a)),
		int(pixel[2] * a + dst[2] * (1 - a)),
		min(255, pixel[3] + dst[3]),
	)


def render_iso(
	top: list[list[tuple[int, int, int, int]]],
	left: list[list[tuple[int, int, int, int]]],
	right: list[list[tuple[int, int, int, int]]],
) -> list[list[tuple[int, int, int, int]]]:
	top = scale_nn(top, 16, 16)
	left = scale_nn(left, 16, 16)
	right = scale_nn(right, 16, 16)
	canvas = [[(0, 0, 0, 0) for _ in range(32)] for _ in range(32)]
	for y in range(16):
		for x in range(16):
			put_pixel(canvas, x, (x + 15) // 2 + y, shade(left[y][x], 0.8))
			put_pixel(canvas, 15 + x, (30 - x) // 2 + y, shade(right[y][x], 0.55))
	for y in range(16):
		for x in range(16):
			put_pixel(canvas, 15 + x - y, (x + y) // 2, shade(top[y][x], 1.0))
	return canvas


def load_tex(url: str) -> list[list[tuple[int, int, int, int]]] | None:
	path = static_path(url)
	if not path.is_file():
		return None
	return read_png(path)


def write_iso_icon(name: str, top_url: str, left_url: str, right_url: str) -> str | None:
	top = load_tex(top_url)
	left = load_tex(left_url)
	right = load_tex(right_url)
	if not top or not left or not right:
		return None
	url = f"/mc/iso/{name}.png"
	write_png(static_path(url), render_iso(top, left, right))
	return url


def stack_v(
	top: list[list[tuple[int, int, int, int]]], bottom: list[list[tuple[int, int, int, int]]]
) -> list[list[tuple[int, int, int, int]]]:
	return top + bottom


def render_chest_icon(name: str, sheet_url: str) -> str | None:
	sheet = load_tex(sheet_url)
	if not sheet or len(sheet) < 43 or len(sheet[0]) < 42:
		return None
	top = scale_nn(crop_px(sheet, 14, 0, 14, 14), 16, 16)
	front = scale_nn(stack_v(crop_px(sheet, 14, 14, 14, 5), crop_px(sheet, 14, 33, 14, 10)), 16, 16)
	side = scale_nn(stack_v(crop_px(sheet, 0, 14, 14, 5), crop_px(sheet, 0, 33, 14, 10)), 16, 16)
	url = f"/mc/iso/{name}.png"
	write_png(static_path(url), render_iso(top, front, side))
	return url


def render_shield_icon() -> str | None:
	sheet = load_tex("/mc/entity/shield.png")
	if not sheet or len(sheet) < 22 or len(sheet[0]) < 12:
		return None
	wood = scale_nn(crop_px(sheet, 2, 2, 10, 20), 20, 28)
	canvas = [[(0, 0, 0, 0) for _ in range(32)] for _ in range(32)]
	for y in range(28):
		if y < 18:
			half = 10
		else:
			half = max(1, int(10 * (1 - (y - 18) / 10)))
		for x in range(10 - half, 10 + half):
			if 0 <= x < 20:
				put_pixel(canvas, 6 + x, 2 + y, wood[y][x])
	url = "/mc/iso/shield.png"
	write_png(static_path(url), canvas)
	return url


def pick_texture(textures: dict) -> str | None:
	for key in (
		"layer0",
		"all",
		"particle",
		"texture",
		"front",
		"side",
		"top",
		"end",
		"up",
		"north",
		"plant",
		"cross",
		"rail",
		"fan",
		"panicle",
		"crop",
		"wool",
		"fire",
		"lantern",
		"torch",
	):
		value = textures.get(key)
		if isinstance(value, str) and not value.startswith("#"):
			return value
	for value in textures.values():
		if isinstance(value, str) and not value.startswith("#"):
			return value
	return None


def load_en_us(jar: zipfile.ZipFile, version: str) -> dict[str, str]:
	data = load_json(jar, "assets/minecraft/lang/en_us.json")
	if not isinstance(data, dict) or not data:
		data = download_lang_asset(version, "en_us.json")
	if not isinstance(data, dict):
		return {}
	return {key: value for key, value in data.items() if isinstance(key, str) and isinstance(value, str)}


def invicon_titles(item_id: str, lang: dict[str, str]) -> list[str]:
	name = item_id.replace("minecraft:", "")
	titles: list[str] = []
	for key in (f"item.minecraft.{name}", f"block.minecraft.{name}"):
		value = lang.get(key)
		if value and value not in titles:
			titles.append(value)
	pretty = name.replace("_", " ").title()
	if pretty not in titles:
		titles.append(pretty)
	return titles


# minecraft.wiki serves the inventory icons of animated blocks (prismarine, sculk, stonecutter) as GIFs,
# even when the .png file name is requested.
IMAGE_SIGNATURES = {b"\x89PNG\r\n\x1a\n": "png", b"GIF87a": "gif", b"GIF89a": "gif"}
INVICON_EXTENSIONS = ("png", "gif")


def image_extension(data: bytes) -> str | None:
	for signature, extension in IMAGE_SIGNATURES.items():
		if data.startswith(signature):
			return extension
	return None


def is_placeholder(data: bytes, extension: str) -> bool:
	"""minecraft.wiki serves a 1x1 image for invisible things (air, markers, display entities). Such a download
	counts as missing, so a stand-in icon is used instead of an empty one."""
	if extension == "png" and len(data) >= 24:
		width, height = int.from_bytes(data[16:20], "big"), int.from_bytes(data[20:24], "big")
	elif extension == "gif" and len(data) >= 10:
		width, height = int.from_bytes(data[6:8], "little"), int.from_bytes(data[8:10], "little")
	else:
		return False
	return width <= 1 or height <= 1


# minecraft.wiki rate-limits bursts of downloads (HTTP 429/503). When that happens every download thread
# pauses until _pause_until, honouring the wiki's Retry-After header.
_pause_lock = threading.Lock()
_pause_until = 0.0
_failures: Counter[str] = Counter()
MAX_PAUSE_SECONDS = 60.0


def wait_for_pause() -> None:
	while True:
		with _pause_lock:
			delay = _pause_until - time.monotonic()
		if delay <= 0:
			return
		time.sleep(delay)


def pause_all(seconds: float) -> None:
	global _pause_until
	with _pause_lock:
		_pause_until = max(_pause_until, time.monotonic() + seconds)


def retry_after_seconds(error: urllib.error.HTTPError, attempt: int) -> float:
	header = error.headers.get("Retry-After") if error.headers else None
	try:
		seconds = float(header)
	except (TypeError, ValueError):
		seconds = 2.0 ** (attempt + 1)
	return min(max(seconds, 1.0), MAX_PAUSE_SECONDS)


def fetch_image(url: str) -> tuple[bytes, str] | None:
	"""Downloads a PNG or GIF. Gives up at once on 404, waits out rate limits, retries other errors briefly."""
	reason = "unknown"
	rate_limited_attempts = 0
	other_attempts = 0
	while rate_limited_attempts < 8 and other_attempts < 5:
		wait_for_pause()
		request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
		try:
			with urllib.request.urlopen(request, timeout=25) as response:
				data = response.read()
			extension = image_extension(data)
			if extension and is_placeholder(data, extension):
				return None
			if extension:
				return data, extension
			reason = "not a PNG/GIF"
		except urllib.error.HTTPError as error:
			if error.code == 404:
				return None
			reason = f"HTTP {error.code}"
			if error.code in (429, 503):
				pause_all(retry_after_seconds(error, rate_limited_attempts))
				rate_limited_attempts += 1
				continue
		except (urllib.error.URLError, TimeoutError, OSError) as error:
			reason = type(error).__name__
		other_attempts += 1
		time.sleep(0.6 * other_attempts)
	with _pause_lock:
		_failures[reason] += 1
	return None


def install_cached_image(cache_dir: Path, folder: str, name: str) -> str | None:
	"""Copies a cached wiki download to /mc/<folder>/ and returns its URL."""
	for extension in INVICON_EXTENSIONS:
		url = f"/mc/{folder}/{name}.{extension}"
		dest = static_path(url)
		if dest.is_file():
			return url
		cache = cache_dir / f"{name}.{extension}"
		if cache.is_file():
			dest.parent.mkdir(parents=True, exist_ok=True)
			dest.write_bytes(cache.read_bytes())
			return url
	return None


def download_wiki_image(cache_dir: Path, folder: str, name: str, filenames: list[str]) -> str | None:
	existing = install_cached_image(cache_dir, folder, name)
	if existing:
		return existing
	for filename in filenames:
		image = fetch_image(WIKI_FILE + urllib.parse.quote(filename))
		if not image:
			continue
		data, extension = image
		cache = cache_dir / f"{name}.{extension}"
		cache.parent.mkdir(parents=True, exist_ok=True)
		cache.write_bytes(data)
		return install_cached_image(cache_dir, folder, name)
	return None


def download_wiki_images(
	label: str, cache_dir: Path, folder: str, wanted: dict[str, tuple[str, list[str]]]
) -> dict[str, str]:
	"""wanted maps a key (e.g. an item ID) to (file name, wiki file names to try in order).
	Returns key -> URL for every image that is cached or could be downloaded."""
	found: dict[str, str] = {}
	pending: dict[str, tuple[str, list[str]]] = {}
	for key, (name, filenames) in wanted.items():
		cached = install_cached_image(cache_dir, folder, name)
		if cached:
			found[key] = cached
		else:
			pending[key] = (name, filenames)
	if not pending:
		return found
	_failures.clear()
	print(f"Downloading {len(pending)} {label} from minecraft.wiki…")
	with ThreadPoolExecutor(max_workers=2) as pool:
		futures = {
			pool.submit(download_wiki_image, cache_dir, folder, name, filenames): key
			for key, (name, filenames) in pending.items()
		}
		for future in as_completed(futures):
			key = futures[future]
			try:
				url = future.result()
			except Exception:
				url = None
			if url:
				found[key] = url
	print(f"Got {len(found)} {label}")
	if _failures:
		summary = ", ".join(f"{reason} x{count}" for reason, count in _failures.most_common())
		print(f"Some downloads failed and will be retried on the next build: {summary}")
	return found


def invicon_filenames(item_id: str, lang: dict[str, str]) -> list[str]:
	return ["Invicon_" + title.replace(" ", "_") + ".png" for title in invicon_titles(item_id, lang)]


# Some textures (leaves, grass, vines, ...) are grayscale and colored by the game. Since 1.21.4 each item
# definition lists these colors in "tints"; tint i applies to model faces/layers with tintindex i.
_colormaps: dict[str, list[list[tuple[int, int, int, int]]] | None] = {}


def colormap(jar: zipfile.ZipFile, name: str) -> list[list[tuple[int, int, int, int]]] | None:
	if name not in _colormaps:
		try:
			_colormaps[name] = decode_png(jar.read(f"assets/minecraft/textures/colormap/{name}.png"))
		except KeyError:
			_colormaps[name] = None
	return _colormaps[name]


def grass_color(jar: zipfile.ZipFile, temperature: float, downfall: float) -> tuple[int, int, int] | None:
	"""Same lookup as the game's GrassColor.get."""
	pixels = colormap(jar, "grass")
	if not pixels:
		return None
	temperature = min(max(temperature, 0.0), 1.0)
	downfall = min(max(downfall, 0.0), 1.0) * temperature
	x = int((1.0 - temperature) * 255)
	y = int((1.0 - downfall) * 255)
	if y >= len(pixels) or x >= len(pixels[y]):
		return None
	return pixels[y][x][:3]


def argb_to_rgb(value: int) -> tuple[int, int, int]:
	value &= 0xFFFFFF
	return (value >> 16) & 255, (value >> 8) & 255, value & 255


def tint_color(jar: zipfile.ZipFile, tint) -> tuple[int, int, int] | None:
	"""RGB for a tint. Tints that depend on the item stack (dye, potion, firework, ...) use their default color."""
	if not isinstance(tint, dict):
		return None
	kind = str(tint.get("type", "")).replace("minecraft:", "")
	if kind == "constant" and isinstance(tint.get("value"), int):
		return argb_to_rgb(tint["value"])
	if kind == "grass":
		return grass_color(jar, float(tint.get("temperature", 0.5)), float(tint.get("downfall", 1.0)))
	if isinstance(tint.get("default"), int):
		return argb_to_rgb(tint["default"])
	return None


def primary_model(node) -> dict | None:
	"""The plain model of an item definition. Follows "fallback"/"on_false" first, so e.g. leather armor
	uses its untrimmed model rather than the first armor-trim case."""
	if isinstance(node, dict):
		kind = str(node.get("type", "")).replace("minecraft:", "")
		if kind == "model" and isinstance(node.get("model"), str):
			return node
		for key in ("fallback", "on_false"):
			if key in node:
				found = primary_model(node[key])
				if found:
					return found
		for value in node.values():
			found = primary_model(value)
			if found:
				return found
	elif isinstance(node, list):
		for value in node:
			found = primary_model(value)
			if found:
				return found
	return None


def model_elements(jar: zipfile.ZipFile, model_id: str, seen: set[str]) -> list | None:
	"""Elements of the model or of the nearest parent defining them (a child's elements replace its parent's)."""
	if not model_id or model_id in seen:
		return None
	seen.add(model_id)
	data = load_json(jar, model_path(model_id))
	if not data:
		return None
	if isinstance(data.get("elements"), list):
		return data["elements"]
	parent = data.get("parent")
	return model_elements(jar, parent, seen) if isinstance(parent, str) else None


def tint_pixels(
	pixels: list[list[tuple[int, int, int, int]]], color: tuple[int, int, int]
) -> list[list[tuple[int, int, int, int]]]:
	r, g, b = color
	return [[(p[0] * r // 255, p[1] * g // 255, p[2] * b // 255, p[3]) for p in row] for row in pixels]


def tinted_url(url: str, color: tuple[int, int, int]) -> str | None:
	pixels = load_tex(url)
	if not pixels:
		return None
	r, g, b = color
	out = f"/mc/tinted/{Path(url).stem}_{r:02x}{g:02x}{b:02x}.png"
	write_png(static_path(out), tint_pixels(pixels, color))
	return out


def layered_icon(name: str, resolved: dict[str, str], tints: list[tuple[int, int, int] | None]) -> str | None:
	"""Stacks layer0, layer1, ... like the game's generated item models, tinting layer i with tint i."""
	layers = []
	index = 0
	while f"layer{index}" in resolved:
		url = tex_url(resolved[f"layer{index}"])
		pixels = load_tex(url) if url else None
		if not pixels:
			return None
		color = tints[index] if index < len(tints) else None
		layers.append(tint_pixels(pixels, color) if color else pixels)
		index += 1
	if not layers:
		return None
	height, width = len(layers[0]), len(layers[0][0])
	canvas = [list(row) for row in layers[0]]
	for layer in layers[1:]:
		if len(layer) != height or len(layer[0]) != width:
			layer = scale_nn(layer, width, height)
		for y in range(height):
			for x in range(width):
				src = layer[y][x]
				alpha = src[3] / 255
				if alpha <= 0:
					continue
				dst = canvas[y][x]
				out_alpha = alpha + dst[3] / 255 * (1 - alpha)
				canvas[y][x] = tuple(
					round((src[c] * alpha + dst[c] * dst[3] / 255 * (1 - alpha)) / out_alpha) for c in range(3)
				) + (round(out_alpha * 255),)
	url = f"/mc/layered/{name}.png"
	write_png(static_path(url), canvas)
	return url


def tint_cube_faces(
	jar: zipfile.ZipFile, model_id: str, urls: list[str], tints: list[tuple[int, int, int] | None]
) -> list[str]:
	"""Tints the top/left/right face textures of a single-element cube model like the game would."""
	elements = model_elements(jar, model_id, set())
	if not any(tints) or not elements or len(elements) != 1:
		return urls
	faces = elements[0].get("faces", {})
	result = []
	for url, face in zip(urls, ("up", "south", "east")):
		index = faces.get(face, {}).get("tintindex")
		color = tints[index] if isinstance(index, int) and 0 <= index < len(tints) else None
		result.append((tinted_url(url, color) or url) if color else url)
	return result


def resolve_item(jar: zipfile.ZipFile, item_id: str, data: dict) -> str | None:
	name = item_id.replace("minecraft:", "")
	node = primary_model(data)
	model = node["model"] if node else None
	tints = [tint_color(jar, tint) for tint in node.get("tints", [])] if node and isinstance(node.get("tints"), list) else []
	textures = textures_from_model(jar, model, set()) if model else {}
	resolved = resolve_refs(textures)
	# Several layers or a tint (leather armor, potions, grass, ...): build the icon like the game does.
	if "layer0" in resolved and ("layer1" in resolved or any(tints)):
		icon = layered_icon(name, resolved, tints)
		if icon:
			return icon
	if file_exists(f"/mc/item/{name}.png"):
		return f"/mc/item/{name}.png"
	if model:
		if "layer0" in resolved:
			url = tex_url(resolved["layer0"])
			if url and file_exists(url):
				return url
		if item_models_are_cube(jar, data):
			faces = cube_faces(textures)
			if faces:
				urls = [tex_url(face) for face in faces]
				if all(url and file_exists(url) for url in urls):
					urls = tint_cube_faces(jar, model, urls, tints)
					iso = write_iso_icon(name, urls[0], urls[1], urls[2])
					if iso:
						return iso
	if file_exists(f"/mc/item/{name}_spawn_egg.png"):
		return f"/mc/item/{name}_spawn_egg.png"
	return None


def special_icon(item_id: str) -> str | None:
	if item_id == "minecraft:shield":
		return render_shield_icon()
	chests = {
		"minecraft:chest": "/mc/entity/chest.png",
		"minecraft:trapped_chest": "/mc/entity/trapped_chest.png",
		"minecraft:ender_chest": "/mc/entity/ender_chest.png",
		"minecraft:copper_chest": "/mc/entity/copper_chest.png",
	}
	sheet = chests.get(item_id)
	if sheet:
		return render_chest_icon(item_id.replace("minecraft:", ""), sheet)
	if item_id == "minecraft:shulker_box" and file_exists("/mc/block/shulker_box.png"):
		return write_iso_icon(
			"shulker_box",
			"/mc/block/shulker_box.png",
			"/mc/block/shulker_box.png",
			"/mc/block/shulker_box.png",
		)
	return None


def copy_pngs(jar: zipfile.ZipFile) -> None:
	if STATIC.exists():
		shutil.rmtree(STATIC)
	for name in jar.namelist():
		if not name.endswith(".png"):
			continue
		dest = None
		if name.startswith("assets/minecraft/textures/item/"):
			dest = STATIC / "item" / Path(name).name
		elif name.startswith("assets/minecraft/textures/block/"):
			dest = STATIC / "block" / Path(name).name
		elif name == "assets/minecraft/textures/entity/chest/normal.png":
			dest = STATIC / "entity" / "chest.png"
		elif name == "assets/minecraft/textures/entity/chest/ender.png":
			dest = STATIC / "entity" / "ender_chest.png"
		elif name == "assets/minecraft/textures/entity/chest/trapped.png":
			dest = STATIC / "entity" / "trapped_chest.png"
		elif name == "assets/minecraft/textures/entity/chest/copper.png":
			dest = STATIC / "entity" / "copper_chest.png"
		elif name == "assets/minecraft/textures/entity/shield/shield_base_nopattern.png":
			dest = STATIC / "entity" / "shield.png"
		elif name == "assets/minecraft/textures/entity/bed/red.png":
			dest = STATIC / "entity" / "bed.png"
		elif name == "assets/minecraft/textures/entity/shulker/shulker.png":
			dest = STATIC / "entity" / "shulker.png"
		elif name.startswith(("assets/minecraft/textures/entity/player/slim/", "assets/minecraft/textures/entity/player/wide/")):
			# Default skins (Steve, Alex, ...) for players without one of their own: skins/<slim|wide>/<name>.png.
			dest = STATIC / "skins" / Path(name).parent.name / Path(name).name
		if dest is None:
			continue
		dest.parent.mkdir(parents=True, exist_ok=True)
		dest.write_bytes(jar.read(name))
	player_head = ROOT / "web" / "src" / "lib" / "assets" / "mc" / "player_head.png"
	if player_head.is_file():
		item_dir = STATIC / "item"
		item_dir.mkdir(parents=True, exist_ok=True)
		shutil.copyfile(player_head, item_dir / "player_head.png")


# Minecraft has no data saying what these blocks and entities look like as an icon, so this is hand-maintained:
# each one is shown with the icon of the closest item, block or entity. Used only when nothing better exists.
BLOCK_ICON_ALIASES = {
	"redstone_wire": "redstone",
	"piston_head": "piston",
	"moving_piston": "piston",
	"bamboo_sapling": "bamboo",
	"bubble_column": "water",
	"end_portal": "end_portal_frame",
	# The item has a shorter name than the block.
	"potted_azalea_bush": "azalea",
	"potted_flowering_azalea_bush": "flowering_azalea",
	"tall_seagrass": "seagrass",
}
# Crop blocks whose wiki icon is their first growth stage, a sprout nobody recognizes: they show the item they grow.
CROP_ITEMS = {
	"carrots": "carrot",
	"potatoes": "potato",
	"beetroots": "beetroot",
	"cocoa": "cocoa_beans",
	"pitcher_crop": "pitcher_pod",
	"torchflower_crop": "torchflower",
}
ENTITY_ICON_ALIASES = {
	"giant": "zombie",
	"illusioner": "evoker",
	"killer_bunny": "rabbit",
	"creaking_transient": "creaking",
	"mannequin": "player_head",
	"boat": "oak_boat",
	"chest_boat": "oak_chest_boat",
	"spawner_minecart": "spawner",
	"cushion": "white_cushion",
	"breeze_wind_charge": "wind_charge",
	"fireball": "fire_charge",
	"small_fireball": "fire_charge",
	"dragon_fireball": "dragon_breath",
	"wither_skull": "wither_skeleton_skull",
	"shulker_bullet": "shulker_shell",
	"llama_spit": "llama",
	"evoker_fangs": "evoker",
	"eye_of_ender": "ender_eye",
	"experience_orb": "experience_bottle",
	"fishing_bobber": "fishing_rod",
	"leash_knot": "lead",
	"lightning_bolt": "lightning_rod",
	"falling_block": "sand",
	"area_effect_cloud": "lingering_potion",
	"ominous_item_spawner": "ominous_trial_key",
	# Technical entities that never appear in survival stats, so every stat ID still has an icon.
	"marker": "barrier",
	"interaction": "barrier",
	"block_display": "item_frame",
	"item_display": "item_frame",
	"text_display": "oak_sign",
	"item": "bundle",
}


# Mobs without a spawn egg (and the player); they still get a face sprite (see main()).
EXTRA_MOBS = {"giant", "illusioner", "player"}


def icon_alias(name: str) -> str | None:
	"""Which existing icon a block or entity without its own icon should use."""
	if name in BLOCK_ICON_ALIASES:
		return BLOCK_ICON_ALIASES[name]
	if name in ENTITY_ICON_ALIASES:
		return ENTITY_ICON_ALIASES[name]
	if name.startswith("potted_"):
		return name.removeprefix("potted_")
	if name == "candle_cake" or name.endswith("_candle_cake"):
		return "cake"
	if name.endswith("_cauldron"):
		return "cauldron"
	if "_wall_" in name:
		return name.replace("_wall_", "_", 1)
	return None


def fill_missing(icons: dict[str, str], item_id: str) -> None:
	if item_id in icons:
		return
	name = item_id.replace("minecraft:", "")
	candidates = []
	swaps = (
		("_wall_sign", "_sign"),
		("_wall_hanging_sign", "_hanging_sign"),
		("_wall_fan", "_fan"),
		("_wall_coral", ""),
	)
	for suffix, replacement in swaps:
		if name.endswith(suffix):
			renamed = name[: -len(suffix)] + replacement
			candidates.append(f"/mc/item/{renamed}.png")
			candidates.append(f"/mc/block/{renamed}.png")
	if name.startswith("wall_"):
		rest = name[5:]
		candidates.append(f"/mc/item/{rest}.png")
		candidates.append(f"/mc/block/{rest}.png")
	crops = {
		"potatoes": "potato",
		"carrots": "carrot",
		"beetroots": "beetroot",
		"sweet_berry_bush": "sweet_berries",
		"melon_stem": "melon_seeds",
		"attached_melon_stem": "melon_seeds",
		"pumpkin_stem": "pumpkin_seeds",
		"attached_pumpkin_stem": "pumpkin_seeds",
		"pitcher_crop": "pitcher_pod",
		"torchflower_crop": "torchflower",
		"cocoa": "cocoa_beans",
		"nether_wart": "nether_wart",
		"wheat": "wheat",
		"decorated_pot": "flower_pot",
	}
	if name in crops:
		candidates.append(f"/mc/item/{crops[name]}.png")
		candidates.append(f"/mc/block/{crops[name]}.png")
	candidates.extend(
		(
			f"/mc/item/{name}.png",
			f"/mc/inv/{name}.png",
			f"/mc/iso/{name}.png",
			f"/mc/block/{name}.png",
			f"/mc/block/{name}_stage3.png",
			f"/mc/block/{name}_stage7.png",
			f"/mc/block/{name}_0.png",
		)
	)
	for url in candidates:
		if file_exists(url):
			icons[item_id] = url
			return


def file_exists(url: str) -> bool:
	relative = url[4:] if url.startswith("/mc/") else url
	return (STATIC / relative).is_file()


def adopt_unversioned_invicons(version: str) -> None:
	"""Icons used to be cached directly in INV_CACHE_ROOT; move them into this version's folder once."""
	if not INV_CACHE_ROOT.is_dir():
		return
	legacy = [path for path in INV_CACHE_ROOT.iterdir() if path.is_file()]
	if not legacy:
		return
	target = INV_CACHE_ROOT / version
	target.mkdir(parents=True, exist_ok=True)
	for path in legacy:
		dest = target / path.name
		if dest.exists():
			path.unlink()
		else:
			path.replace(dest)
	print(f"Moved {len(legacy)} cached inventory icons to {target}")


def clear_version_cache(version: str) -> None:
	for path in (JAR_CACHE / version, INV_CACHE_ROOT / version, MOB_CACHE_ROOT / version):
		if path.exists():
			shutil.rmtree(path)
			print(f"Deleted cached downloads in {path}")


def main() -> None:
	parser = argparse.ArgumentParser()
	parser.add_argument("--version", default=os.environ.get("MC_VERSION", "26.3"))
	parser.add_argument("--jar")
	parser.add_argument("--lang-only", action="store_true")
	parser.add_argument(
		"--refresh",
		action="store_true",
		help="delete this version's cached downloads (client JAR, language files, inventory icons) and fetch them again",
	)
	args = parser.parse_args()
	inv_cache = INV_CACHE_ROOT / args.version
	mob_cache = MOB_CACHE_ROOT / args.version
	adopt_unversioned_invicons(args.version)
	if args.refresh:
		clear_version_cache(args.version)
	jar_path = resolve_jar(args.version, args.jar)
	with zipfile.ZipFile(jar_path) as jar:
		if args.lang_only:
			write_advancement_lang(jar, args.version)
			write_name_lang(jar, args.version)
			return
		copy_pngs(jar)
		en_us = load_en_us(jar, args.version)
		icons: dict[str, str] = {}
		pending: list[str] = []
		for entry in jar.namelist():
			if not entry.startswith("assets/minecraft/items/") or not entry.endswith(".json"):
				continue
			item_id = "minecraft:" + Path(entry).stem
			data = load_json(jar, entry)
			if not data:
				continue
			url = resolve_item(jar, item_id, data)
			if url and file_exists(url):
				icons[item_id] = url
			else:
				pending.append(item_id)
		icons.update(
			download_wiki_images(
				"inventory icons",
				inv_cache,
				"inv",
				{item_id: (item_id.removeprefix("minecraft:"), invicon_filenames(item_id, en_us)) for item_id in pending},
			)
		)
		# Blocks without an item of their own (portals, fire, crops, ...) get the wiki's icon of the block itself
		# first; a raw texture or a related item's icon (below) is only the fallback.
		itemless = sorted(
			block_id
			for block_id in (
				"minecraft:" + Path(entry).stem
				for entry in jar.namelist()
				if entry.startswith("assets/minecraft/blockstates/") and entry.endswith(".json")
			)
			if block_id not in icons and block_id.removeprefix("minecraft:") not in CROP_ITEMS
		)
		for crop, item in CROP_ITEMS.items():
			if f"minecraft:{item}" in icons:
				icons[f"minecraft:{crop}"] = icons[f"minecraft:{item}"]
		icons.update(
			download_wiki_images(
				"block icons",
				inv_cache,
				"inv",
				{block_id: (block_id.removeprefix("minecraft:"), invicon_filenames(block_id, en_us)) for block_id in itemless},
			)
		)
		specials = {
			"minecraft:white_banner": "/mc/block/white_wool.png",
			"minecraft:water": "/mc/block/water_still.png",
			"minecraft:lava": "/mc/block/lava_still.png",
			"minecraft:fire": "/mc/block/fire_0.png",
			"minecraft:soul_fire": "/mc/block/soul_fire_0.png",
			"minecraft:air": "/mc/item/barrier.png",
			"minecraft:cave_air": "/mc/item/barrier.png",
			"minecraft:void_air": "/mc/item/barrier.png",
			"minecraft:player": "/mc/item/player_head.png",
			"minecraft:zombie_head": "/mc/item/zombie_spawn_egg.png",
			"minecraft:skeleton_skull": "/mc/item/skeleton_spawn_egg.png",
			"minecraft:creeper_head": "/mc/item/creeper_spawn_egg.png",
			"minecraft:dragon_head": "/mc/item/ender_dragon_spawn_egg.png",
			"minecraft:piglin_head": "/mc/item/piglin_spawn_egg.png",
			"minecraft:wither_skeleton_skull": "/mc/item/wither_skeleton_spawn_egg.png",
			"minecraft:decorated_pot": "/mc/item/flower_pot.png",
			"minecraft:player_head": "/mc/item/player_head.png",
			"minecraft:potatoes": "/mc/item/potato.png",
			"minecraft:carrots": "/mc/item/carrot.png",
			"minecraft:beetroots": "/mc/item/beetroot.png",
		}
		for item_id in (
			"minecraft:shield",
			"minecraft:chest",
			"minecraft:trapped_chest",
			"minecraft:ender_chest",
			"minecraft:copper_chest",
			"minecraft:shulker_box",
		):
			if item_id in icons:
				continue
			url = special_icon(item_id)
			if url:
				icons[item_id] = url
		for png in (STATIC / "block").glob("*_shulker_box.png"):
			item_id = "minecraft:" + png.stem
			if item_id in icons:
				continue
			url = write_iso_icon(
				png.stem,
				f"/mc/block/{png.name}",
				f"/mc/block/{png.name}",
				f"/mc/block/{png.name}",
			)
			if url:
				icons[item_id] = url
		conduit = write_iso_icon(
			"conduit",
			"/mc/block/conduit.png",
			"/mc/block/conduit.png",
			"/mc/block/conduit.png",
		)
		if conduit:
			specials["minecraft:conduit"] = conduit
		for item_id, url in specials.items():
			if item_id not in icons and file_exists(url):
				icons[item_id] = url
		for png in (STATIC / "item").glob("*_spawn_egg.png"):
			entity = "minecraft:" + png.stem.removesuffix("_spawn_egg")
			icons.setdefault(entity, "/mc/item/" + png.name)
		for png in (STATIC / "block").glob("*.png"):
			stem = png.stem
			if stem.endswith("_top") or stem.endswith("_side") or stem.endswith("_front") or stem.endswith("_bottom"):
				continue
			icons.setdefault("minecraft:" + stem, "/mc/block/" + png.name)
		# Blocks and entities without an item or spawn egg of their own (wall heads, potted plants, projectiles, ...)
		alias_ids = {
			Path(entry).stem
			for entry in jar.namelist()
			if entry.startswith("assets/minecraft/blockstates/") and entry.endswith(".json")
		}
		alias_ids |= {key.removeprefix("entity.minecraft.") for key in en_us if key.startswith("entity.minecraft.") and key.count(".") == 2}
		for name in sorted(alias_ids):
			target = icon_alias(name)
			if target and f"minecraft:{name}" not in icons and f"minecraft:{target}" in icons:
				icons[f"minecraft:{name}"] = icons[f"minecraft:{target}"]
		# Entities get the wiki's sprite of the entity itself under "entity:minecraft:<id>", separate from items
		# sharing the ID (minecraft:chicken is also raw chicken). The web UI uses these keys for kill and death
		# stats. Mobs without a sprite fall back to their spawn egg or an alias; other entities (arrows, boats,
		# ...) to the icon of the item with their ID.
		mobs = {
			Path(entry).stem.removesuffix("_spawn_egg")
			for entry in jar.namelist()
			if entry.startswith("assets/minecraft/items/") and entry.endswith("_spawn_egg.json")
		} | EXTRA_MOBS
		entities = mobs | {
			key.removeprefix("entity.minecraft.") for key in en_us if key.startswith("entity.minecraft.") and key.count(".") == 2
		}
		sprites = download_wiki_images(
			"entity sprites",
			mob_cache,
			"mob",
			{entity: (entity, [f"EntitySprite_{entity.replace('_', '-')}.png"]) for entity in sorted(entities)},
		)
		for entity in sorted(entities):
			icon = sprites.get(entity)
			if not icon and entity in mobs and file_exists(f"/mc/item/{entity}_spawn_egg.png"):
				icon = f"/mc/item/{entity}_spawn_egg.png"
			if not icon and entity in mobs:
				target = icon_alias(entity)
				icon = (sprites.get(target) or icons.get(f"minecraft:{target}")) if target else None
			if icon:
				icons[f"entity:minecraft:{entity}"] = icon
		if "entity:minecraft:player" not in icons and "minecraft:player_head" in icons:
			icons["entity:minecraft:player"] = icons["minecraft:player_head"]
		for entry in jar.namelist():
			if not entry.endswith(".json"):
				continue
			if (
				entry.startswith("assets/minecraft/items/")
				or entry.startswith("assets/minecraft/models/block/")
				or entry.startswith("assets/minecraft/blockstates/")
			):
				fill_missing(icons, "minecraft:" + Path(entry).stem)
		ICONS.parent.mkdir(parents=True, exist_ok=True)
		ICONS.write_text(json.dumps(icons, indent="\t", sort_keys=True) + "\n", encoding="utf-8")
		write_advancement_lang(jar, args.version)
		write_name_lang(jar, args.version)
		print(f"Wrote {len(icons)} icons and textures to {STATIC}")


def lang_entries(data) -> dict[str, str]:
	if not isinstance(data, dict):
		return {}
	return {
		key: value
		for key, value in data.items()
		if isinstance(key, str) and key.startswith("advancements.") and isinstance(value, str)
	}


def load_lang(jar: zipfile.ZipFile, version: str, path: str) -> dict[str, str]:
	"""A full Minecraft language file. Only en_us is inside the client JAR; others come from the asset index."""
	data = load_json(jar, path)
	if not isinstance(data, dict) or not data:
		data = download_lang_asset(version, Path(path).name)
	return data if isinstance(data, dict) else {}


def write_advancement_lang(jar: zipfile.ZipFile, version: str) -> None:
	body: dict[str, dict[str, str]] = {}
	for locale, path in LANG_FILES.items():
		body[locale] = lang_entries(load_lang(jar, version, path))
	ADVANCEMENT_LANG.parent.mkdir(parents=True, exist_ok=True)
	ADVANCEMENT_LANG.write_text(json.dumps(body, indent="\t", ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
	print(f"Wrote advancement lang to {ADVANCEMENT_LANG}")


def write_name_lang(jar: zipfile.ZipFile, version: str) -> None:
	"""Official block, item and entity names per language, for stat labels in the web UI (/mc/lang/<locale>.json).
	Kept out of the JS bundle and fetched for the visitor's language only."""
	items = sorted(Path(n).stem for n in jar.namelist() if n.startswith("assets/minecraft/items/") and n.endswith(".json"))
	blocks = sorted(
		Path(n).stem for n in jar.namelist() if n.startswith("assets/minecraft/blockstates/") and n.endswith(".json")
	)
	for locale, path in LANG_FILES.items():
		lang = load_lang(jar, version, path)
		names: dict[str, dict[str, str]] = {"item": {}, "block": {}, "entity": {}}
		for name in items:
			# Block items (stone, oak_planks, ...) only have a block.* name.
			value = lang.get(f"item.minecraft.{name}") or lang.get(f"block.minecraft.{name}")
			if isinstance(value, str):
				names["item"][f"minecraft:{name}"] = value
		for name in blocks:
			value = lang.get(f"block.minecraft.{name}")
			if isinstance(value, str):
				names["block"][f"minecraft:{name}"] = value
		for key, value in lang.items():
			if key.startswith("entity.minecraft.") and key.count(".") == 2 and isinstance(value, str):
				names["entity"]["minecraft:" + key.removeprefix("entity.minecraft.")] = value
		out = STATIC / "lang" / f"{locale}.json"
		out.parent.mkdir(parents=True, exist_ok=True)
		out.write_text(json.dumps(names, ensure_ascii=False, sort_keys=True, separators=(",", ":")), encoding="utf-8")
	print(f"Wrote block/item/entity names for {', '.join(LANG_FILES)} to {STATIC / 'lang'}")


if __name__ == "__main__":
	main()
