"""Takes the images in screenshots/ from a dev server running with the sample players (see scripts/README.md).

Usage: python scripts/screenshots/take_screenshots.py [base URL] [name ...]
The base URL defaults to http://127.0.0.1:25590; names (e.g. player player-de) take just those shots.
Needs Playwright with its headless Chromium (scripts/requirements.txt).
"""
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "screenshots"
args = sys.argv[1:]
BASE = args.pop(0) if args and args[0].startswith("http") else "http://127.0.0.1:25590"
ONLY = set(args)

DESKTOP = {"viewport": {"width": 1440, "height": 900}, "device_scale_factor": 1, "max_height": 2000}
MOBILE = {"viewport": {"width": 390, "height": 844}, "device_scale_factor": 2, "is_mobile": True, "has_touch": True, "max_height": 2600}
WIDE = {"viewport": {"width": 1920, "height": 1080}, "device_scale_factor": 1, "max_height": 1080}
SHOTS = [
	("overview", "/", "en-US", "dark", WIDE),
	("ranking", "/", "en-US", "dark", DESKTOP),
	("advancements", "/advancements", "en-US", "dark", DESKTOP),
	("advancement", "/advancements/minecraft/story/mine_stone", "en-US", "dark", DESKTOP),
	("players", "/players", "en-US", "dark", DESKTOP),
	("player", "/players/NetherNomadNix", "en-US", "dark", DESKTOP),
	("player-advancements", "/players/NetherNomadNix/advancements", "en-US", "dark", DESKTOP),
	("leaderboards", "/leaderboards", "en-US", "dark", DESKTOP),
	("leaderboard", "/leaderboards/distance-total", "en-US", "dark", DESKTOP),
	("compare", "/compare?a=NetherNomadNix&b=DeepslateDan", "en-US", "dark", DESKTOP),
	("how-we-rank", "/about", "en-US", "dark", DESKTOP),
	("ranking-light", "/", "en-US", "light", DESKTOP),
	("player-de", "/players/CompassCora", "de-DE", "dark", DESKTOP),
	("player-es", "/players/CreeperBaneKat", "es-ES", "dark", DESKTOP),
	("player-fr", "/players/BlockSmithBea", "fr-FR", "dark", DESKTOP),
	("mobile-ranking", "/", "en-US", "dark", MOBILE),
	("mobile-player", "/players/NetherNomadNix", "en-US", "dark", MOBILE),
]

with sync_playwright() as playwright:
	# Software WebGL, so the 3D skins render without a GPU.
	browser = playwright.chromium.launch(args=["--use-gl=angle", "--use-angle=swiftshader"])
	for name, path, locale, scheme, device in SHOTS:
		if ONLY and name not in ONLY:
			continue
		options = {key: value for key, value in device.items() if key != "max_height"}
		context = browser.new_context(**options, locale=locale, color_scheme=scheme)
		page = context.new_page()
		page.goto(BASE + path, wait_until="networkidle")
		page.evaluate("document.fonts.ready")
		# Skins, icons and the 3D avatar settle after the data arrives.
		page.wait_for_timeout(2500)
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
			page.screenshot(path=OUT / f"{name}.png")
			print(name, f"{width}x{height}")
		context.close()
	browser.close()
