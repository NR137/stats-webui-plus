"""Takes the images in screenshots/ from a dev server running with the sample players (see scripts/README.md).

Usage: python scripts/screenshots/take_screenshots.py [base URL] [name ...]
The base URL defaults to http://127.0.0.1:25590; names (e.g. player player-de) take just those shots.
Needs Playwright with its headless Chromium (scripts/requirements.txt).

For the shots it changes the dev server's config (<LOADER, default fabric>/runs/server/config/stats_webui_plus.json;
the mod reloads it on its own): the avatars don't turn (so they face the front, not whatever side they had turned
to), the ranking is the start page (so the main shots show it unscrolled) and the sample rules are on; only the
"home" shot turns on the sample start page, as the website's start page. The sample pages (start page and rules) are
shown as a server owner would have them, without their note and placeholders. Afterwards the config and the pages are
put back as they were.
"""
import copy
import json
import os
import re
import sys
import time
import urllib.request
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "screenshots"
# The running dev server's config: LOADER as for `docker compose up server`.
CONFIG = ROOT / os.environ.get("LOADER", "fabric") / "runs" / "server" / "config" / "stats_webui_plus.json"
# The English texts of the sample pages the shots show ("home" and "custom-page").
SAMPLE_PAGES = [CONFIG.parent / "stats_webui_plus" / "pages" / page / "en.md" for page in ("home", "rules")]
args = sys.argv[1:]
BASE = args.pop(0) if args and args[0].startswith("http") else "http://127.0.0.1:25590"
ONLY = set(args)

DESKTOP = {"viewport": {"width": 1440, "height": 900}, "device_scale_factor": 1, "max_height": 2000}
MOBILE = {"viewport": {"width": 390, "height": 844}, "device_scale_factor": 2, "is_mobile": True, "has_touch": True, "max_height": 2600}
WIDE = {"viewport": {"width": 1920, "height": 1080}, "device_scale_factor": 1, "max_height": 1080}
SHOTS = [
	("overview", "/", "en-US", "dark", WIDE),
	("ranking", "/", "en-US", "dark", DESKTOP),
	("home", "/", "en-US", "dark", DESKTOP),
	("advancements", "/advancements", "en-US", "dark", DESKTOP),
	("advancement", "/advancements/story/mine_stone", "en-US", "dark", DESKTOP),
	("players", "/players", "en-US", "dark", DESKTOP),
	("player", "/players/NetherNomadNix", "en-US", "dark", DESKTOP),
	("player-advancements", "/players/NetherNomadNix/advancements", "en-US", "dark", DESKTOP),
	("leaderboards", "/leaderboards", "en-US", "dark", DESKTOP),
	("leaderboard", "/leaderboards/distance-total", "en-US", "dark", DESKTOP),
	("compare", "/compare?a=NetherNomadNix&b=DeepslateDan", "en-US", "dark", DESKTOP),
	("how-we-rank", "/about", "en-US", "dark", DESKTOP),
	("ranking-light", "/", "en-US", "light", DESKTOP),
	("player-de", "/players/CompassCora", "de-DE", "dark", DESKTOP),
	("player-es", "/players/CreeperBaneKatie", "es-ES", "dark", DESKTOP),
	("player-fr", "/players/BlockSmithBea", "fr-FR", "dark", DESKTOP),
	("mobile-ranking", "/", "en-US", "dark", MOBILE),
	("mobile-player", "/players/NetherNomadNix", "en-US", "dark", MOBILE),
	# Some need a click first (the last entry: what to do on the page before the shot; done again after
	# the window is made as tall as the page).
	("stats", "/stats", "en-US", "dark", DESKTOP, lambda page: page.get_by_role("tab").filter(has_text="Mobs killed").first.click()),
	("advancements-tree", "/advancements", "en-US", "dark", DESKTOP, lambda page: page.get_by_role("button", name="Tree").first.click()),
	("player-advancement", "/players/DeepslateDan/advancements/adventure/adventuring_time", "en-US", "dark", DESKTOP),
	("player-advancement-mobs", "/players/DeepslateDan/advancements/adventure/kill_all_mobs", "en-US", "dark", DESKTOP),
	("custom-page", "/pages/rules", "en-US", "dark", DESKTOP),
	("mobile-menu", "/advancements", "en-US", "dark", MOBILE, lambda page: open_menu(page)),
]

def open_menu(page):
	"""Opens the phone menu, unless it is open already: a click on the visible menu button (the slim bar has one too,
	hidden while the full menu is on screen), in the page itself, since the window is as tall as the page by now."""
	page.evaluate("""() => {
		const button = [...document.querySelectorAll('button[aria-label^="Menu"]')]
			.find((node) => node.getBoundingClientRect().height > 0);
		if (button && button.getAttribute('aria-expanded') === 'false') button.click();
	}""")


# Shots taken with the sample start page as the website's start page; all others keep the ranking there.
WITH_START_PAGE = {"home"}


def shot_config(original, start_page):
	"""The config for the shots: the original with the avatars standing still, the sample rules switched on, and the
	sample start page only where it is the start page (elsewhere it would be a menu entry of its own)."""
	config = copy.deepcopy(original)
	config["avatarRotate"] = False
	config["startPage"] = start_page
	for page in config.get("pages", []):
		if page.get("id") == "rules":
			page["enabled"] = True
		elif page.get("id") == "home":
			page["enabled"] = start_page == "home"
	return config


def use_config(text, start_page, rotate):
	"""Writes the config and waits until the mod has reloaded it (it watches the file)."""
	CONFIG.write_text(text)
	deadline = time.monotonic() + 20
	while time.monotonic() < deadline:
		try:
			with urllib.request.urlopen(BASE + "/api/status", timeout=5) as response:
				status = json.load(response)
			if (status.get("startPage") or "") == start_page and status["branding"].get("avatarRotate") == rotate:
				return
		except OSError:
			pass
		time.sleep(0.5)
	sys.exit(f"The server did not pick up the changed config within 20 s ({CONFIG}).")


if not CONFIG.is_file():
	sys.exit(f"No {CONFIG}: start the dev server once and run scripts/sample_data/sample_data.py (see scripts/README.md).")
original_text = CONFIG.read_text()
original = json.loads(original_text)
applied = None
sample_texts = {page: page.read_text() for page in SAMPLE_PAGES if page.is_file()}


def finished(text):
	"""A sample page as a server owner would have it: without its note ("Sample page: ...", the quote under the
	title) and with its [placeholders] filled in (here: without their brackets)."""
	text = re.sub(r"\n> [^\n]*(\n>[^\n]*)*\n", "", text, count=1)
	return re.sub(r"\[([^\]\n]+)\](?!\()", r"\1", text)


try:
	for page, text in sample_texts.items():
		page.write_text(finished(text))
	with sync_playwright() as playwright:
		# Software WebGL, so the 3D skins render without a GPU.
		browser = playwright.chromium.launch(args=["--use-gl=angle", "--use-angle=swiftshader"])
		for name, path, locale, scheme, device, *action in SHOTS:
			if ONLY and name not in ONLY:
				continue
			start_page = "home" if name in WITH_START_PAGE else ""
			if applied != start_page:
				use_config(json.dumps(shot_config(original, start_page), indent=2) + "\n", start_page, False)
				applied = start_page
			options = {key: value for key, value in device.items() if key != "max_height"}
			context = browser.new_context(**options, locale=locale, color_scheme=scheme)
			page = context.new_page()
			page.goto(BASE + path, wait_until="networkidle")
			# No room kept for a scrollbar (the site's scrollbar-gutter), so the page is centred in the shot.
			page.add_style_tag(content="html { scrollbar-gutter: auto !important; scrollbar-width: none !important; }")
			page.evaluate("document.fonts.ready")
			# Skins, icons and the 3D avatar settle after the data arrives.
			page.wait_for_timeout(2500)
			if action:
				action[0](page)
				page.wait_for_load_state("networkidle")
				page.wait_for_timeout(1500)
			width = options["viewport"]["width"]
			if name == "overview":
				# 16:9, ending just below the podium: the README's first screen shows it whole, no buttons cut in half.
				podium = page.locator(".podium").first.bounding_box()
				height = round(podium["y"] + podium["height"] + 16)
				crop = round(height * 16 / 9)
				page.screenshot(path=OUT / f"{name}.png", clip={"x": round((width - crop) / 2), "y": 0, "width": crop, "height": height})
				print(name, f"{crop}x{height}")
			else:
				# A window as tall as the shot, so the fixed dirt background covers all of it (a full-page shot only
				# gets one screen of it).
				height = min(device["max_height"], page.evaluate("document.documentElement.scrollHeight"))
				page.set_viewport_size({"width": width, "height": height})
				page.wait_for_timeout(1200)
				# Again after the resize, which closes an open menu (each action is safe to repeat).
				if action:
					action[0](page)
					page.wait_for_timeout(800)
				page.screenshot(path=OUT / f"{name}.png")
				print(name, f"{width}x{height}")
			context.close()
		browser.close()
finally:
	# The mod picks it up on its own; page texts are read on every request.
	CONFIG.write_text(original_text)
	for page, text in sample_texts.items():
		page.write_text(text)
