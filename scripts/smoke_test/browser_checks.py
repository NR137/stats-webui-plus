"""Browser checks for smoke_test.py --browser: the real web UI in headless Chromium, against the running server.

Needs Playwright (scripts/requirements.txt) and its Chromium: scripts/.venv/bin/playwright install --only-shell chromium
"""

import json
import re
from urllib.parse import quote

from playwright.sync_api import Page, sync_playwright

TIMEOUT_MS = 20_000


def run(base: str, names: list[str], check) -> None:
	print('Browser', flush=True)
	with sync_playwright() as playwright:
		# WebGL in software, for the 3D avatar (as in scripts/screenshots/take_screenshots.py).
		browser = playwright.chromium.launch(args=['--use-gl=angle', '--use-angle=swiftshader'])
		try:
			_checks(browser, base, names, check)
			_ui_checks(browser, base, names, check)
			_menu_checks(browser, base, names, check)
			_advancement_checks(browser, base, names, check)
			_player_advancement_checks(browser, base, names, check)
			_tree_checks(browser, base, names, check)
		finally:
			browser.close()


def _page(browser, locale='en-US', **options) -> tuple[Page, list[str]]:
	context = browser.new_context(locale=locale, **options)
	page = context.new_page()
	page.set_default_timeout(TIMEOUT_MS)
	errors: list[str] = []
	page.on('pageerror', lambda error: errors.append(f'uncaught: {error}'))
	# A 404 from the API logs "Failed to load resource" on purpose (not-found pages).
	page.on('console', lambda message: message.type == 'error' and 'Failed to load resource' not in message.text
		and errors.append(message.text))
	return page, errors


def _shows(page: Page, text: str) -> bool:
	try:
		page.get_by_text(text, exact=False).first.wait_for(state='visible')
		return True
	except Exception:
		return False


def _checks(browser, base: str, names: list[str], check) -> None:
	page, errors = _page(browser)

	page.goto(base + '/')
	check(_shows(page, names[0]), 'the home page shows the ranking with the sample players')
	check(page.locator('a[href="https://map.example.com"]').count() == 1, 'the Map menu button is there')
	check(page.locator('a[href*="evil.example.com"]').count() == 0, 'the //evil.example.com link is not')

	# A click inside the app is no full page load: this marker survives it.
	page.evaluate('window.__smoke = 1')
	page.locator(f'a[href="/players/{quote(names[0], safe="")}"]').first.click()
	page.wait_for_url(re.compile(r'/players/'))
	check(_shows(page, names[0]) and page.evaluate('window.__smoke') == 1, 'clicking a player opens their page inside the app')
	check(names[0] in page.title(), f'the window title names the player ({page.title()})')
	page.go_back()
	page.wait_for_url(base + '/')
	check(_shows(page, names[1]), 'Back returns to the ranking')

	page.goto(f'{base}/players/{quote(names[1], safe="")}/advancements')
	check(_shows(page, names[1]), 'a player\'s advancements tab opens directly (typed URL)')
	page.reload()
	check(_shows(page, names[1]), '... and after a reload')

	for path, text in [
		('/players/NoSuchPlayer', 'There is no player with this name on this server.'),
		('/players/.NoSuchBedrockPlayer', 'There is no player with this name on this server.'),
		('/leaderboards/mined/minecraft%3Ano_such_block', 'This leaderboard does not exist.'),
		('/advancements/minecraft/no/such_thing', 'This advancement does not exist.'),
	]:
		page.goto(base + path)
		check(_shows(page, text), f'{path} says "{text}"')

	page.goto(base + '/leaderboards')
	board = page.locator('a[href^="/leaderboards/"]').first
	board.click()
	page.wait_for_url(re.compile(r'/leaderboards/.+'))
	check(any(_shows(page, name) for name in names[:3]), 'a leaderboard from the overview lists players')

	# CHANGELOG 1.4.0: "How we rank" left the menu; the footer and the ranking link to it.
	page.goto(base + '/')
	check(page.locator('nav a[href="/about"]').count() == 0, 'How we rank is no longer in the menu')
	page.get_by_role('link', name='How is this calculated?').click()
	page.wait_for_url(re.compile(r'/about$'))
	check(_shows(page, 'Real combat, 22 %'), 'the ranking links to the How we rank page, which explains the weights')
	check(page.locator('footer a[href="/about"]').count() == 1, 'the footer links to How we rank')
	check(not errors, f'no JavaScript errors on any page ({"; ".join(errors[:3]) or "none"})')
	page.context.close()

	for locale, word in [('de-DE', 'Bestenlisten'), ('fr-FR', 'Classements'), ('es-ES', 'Clasificaciones'),
			('nl-NL', 'Leaderboards')]:
		page, _ = _page(browser, locale=locale)
		page.goto(base + '/')
		check(_shows(page, word), f'a {locale} browser gets "{word}"')
		page.context.close()

	page, errors = _page(browser, viewport={'width': 390, 'height': 844}, is_mobile=True, has_touch=True)
	for path in ['/', f'/players/{quote(names[0], safe="")}', '/leaderboards', '/about']:
		page.goto(base + path)
		page.wait_for_load_state('networkidle')
		wide = page.evaluate('document.documentElement.scrollWidth - window.innerWidth')
		check(wide <= 0, f'{path} fits a 390 px phone screen ({wide} px too wide)')

	# CHANGELOG 1.4.0: on phones the pages are behind one button naming the current page; it closes after choosing.
	page.goto(base + '/')
	toggle = page.get_by_role('button', name='Menu: Ranking')
	check(toggle.is_visible() and not page.get_by_role('link', name='Leaderboards').is_visible(),
		'the phone menu is one button naming the page, the pages are hidden')
	toggle.click()
	page.get_by_role('link', name='Leaderboards').click()
	page.wait_for_url(re.compile(r'/leaderboards$'))
	check(page.get_by_role('button', name='Menu: Leaderboards').is_visible()
		and not page.get_by_role('link', name='Leaderboards').is_visible(),
		'choosing a page in the phone menu opens it and closes the menu')
	check(page.get_by_role('button', name=re.compile(r'^Language:')).is_visible()
		and page.get_by_role('button', name=re.compile(r'^Theme:')).is_visible(),
		'language and theme stay reachable next to the phone menu')
	check(not errors, f'no JavaScript errors on the phone ({"; ".join(errors[:3]) or "none"})')
	page.context.close()


def _ui_checks(browser, base: str, names: list[str], check) -> None:
	"""Switchers, compare, search, the advancements tab, the avatar, header image and status bar."""
	page, errors = _page(browser)
	page.goto(base + '/')

	# Theme: README "light / dark / system theme", remembered for the next visit.
	page.get_by_role('button', name=re.compile(r'^Theme:')).click()
	page.get_by_role('option', name='Dark').click()
	check(page.evaluate('document.documentElement.dataset.theme') == 'dark', 'the theme switcher sets the dark theme')
	page.get_by_role('button', name=re.compile(r'^Theme:')).click()
	page.get_by_role('option', name='Light').click()
	page.reload()
	check(page.evaluate('document.documentElement.dataset.theme') == 'light', 'the chosen theme is kept after a reload')

	# Language: a language picked in the switcher wins over the browser's and is remembered (CHANGELOG).
	page.get_by_role('button', name=re.compile(r'^Language:')).click()
	page.get_by_role('option', name='Deutsch').click()
	check(_shows(page, 'Bestenlisten'), 'the language switcher changes the UI to German')
	page.reload()
	check(_shows(page, 'Bestenlisten') and page.evaluate('document.documentElement.lang') == 'de',
		'the picked language is kept after a reload, although the browser asks for English')
	page.evaluate("localStorage.removeItem('mc-stats-locale-choice')")

	# Status bar and header image (README, Configuration).
	page.goto(base + '/')
	check(_shows(page, 'smoke.example.com'), 'the status bar shows the server address')
	check(page.locator('.status-bar').count() == 1, 'the status bar is there')
	image = page.locator('img.header-image')
	check(image.count() == 1 and page.evaluate("document.querySelector('img.header-image').naturalWidth") > 0,
		'the header image loads')
	check(page.locator('a[href="https://map.example.com"]').first.get_attribute('target') == '_blank',
		'menu links open in a new tab by default')

	# Compare two players side by side.
	page.goto(f'{base}/compare?a={quote(names[0], safe="")}&b={quote(names[1], safe="")}')
	check(_shows(page, 'Unique advancements'), 'the compare page compares two players')
	check(page.get_by_text(names[0]).count() > 0 and page.get_by_text(names[1]).count() > 0, 'both players are on it')

	# Search on the players list.
	page.goto(base + '/players')
	page.get_by_text(names[0]).first.wait_for()
	needle = names[0][:6]
	page.locator('input.search').first.fill(needle)
	page.wait_for_timeout(600)
	visible = [name for name in names if page.locator(f'a[href="/players/{quote(name, safe="")}"]').count() > 0]
	check(names[0] in visible and all(needle.lower() in name.lower() for name in visible),
		f'searching "{needle}" leaves only matching players ({visible})')

	# A player's advancements tab: finished ones with their completion time.
	page.goto(f'{base}/players/{quote(names[0], safe="")}/advancements')
	check(_shows(page, 'Stone Age'), 'the advancements tab lists a finished advancement (Stone Age)')
	check(page.locator('time').count() > 0 or page.get_by_text(re.compile(r'\d{4}|\d{1,2}:\d{2}')).count() > 0,
		'finished advancements show when they were completed')

	# The 3D avatar renders (WebGL) with the player's skin.
	page.goto(f'{base}/players/{quote(names[0], safe="")}')
	try:
		page.locator('.skin.body canvas').first.wait_for(state='visible')
		page.wait_for_function("!document.querySelector('.skin.body').classList.contains('loading')", timeout=TIMEOUT_MS)
		drawn = page.evaluate("""() => {
			const canvas = document.querySelector('.skin.body canvas');
			return canvas && canvas.width > 0 && canvas.height > 0;
		}""")
	except Exception:
		drawn = False
	check(drawn and page.locator('.skin.body.failed').count() == 0, 'the 3D avatar is drawn with the player\'s skin')
	check(not errors, f'no JavaScript errors in these checks ({"; ".join(errors[:3]) or "none"})')
	page.context.close()


def _header_height(browser, base: str, locale: str, links=None, width=1280) -> tuple[Page, float]:
	"""A page in the given language, after the fonts and the splash text are laid out; links replaces the server's
	custom links (as /api/status sends them)."""
	page, _ = _page(browser, locale=locale, viewport={'width': width, 'height': 800})
	if links is not None:
		def status(route, request):
			response = route.fetch()
			body = response.json()
			body['links'] = links
			route.fulfill(response=response, body=json.dumps(body))
		page.route('**/api/status', status)
	page.goto(base + '/')
	page.locator('.splash.ready').wait_for()
	page.evaluate('document.fonts.ready')
	page.wait_for_timeout(300)
	return page, page.evaluate("document.querySelector('header.top').getBoundingClientRect().height")


def _boxes(page: Page, selector: str) -> list[dict]:
	return page.evaluate(f"""() => [...document.querySelectorAll('{selector}')].map(element => {{
		const box = element.getBoundingClientRect();
		return {{ top: Math.round(box.top), width: Math.round(box.width), height: Math.round(box.height),
			fits: element.scrollWidth <= element.clientWidth }};
	}})""")


def _menu_checks(browser, base: str, names: list[str], check) -> None:
	"""CHANGELOG 1.4.0: the header and the menu stay put when the language changes; the menu on phones."""
	# The default taglines differ in length per language; the header keeps its height anyway.
	for width in (1280, 390):
		heights = {}
		for locale in ('en-US', 'de-DE', 'es-ES', 'fr-FR'):
			page, heights[locale] = _header_height(browser, base, locale, width=width)
			page.context.close()
		check(len(set(heights.values())) == 1, f'the header is equally high in every language at {width} px ({heights})')

	# Custom link buttons: as wide as the longest label in any language, the same in every language, and a label
	# too long even for that wraps, with the whole row growing to the same height.
	links = [
		{'label': {'en': 'Map', 'de': 'Gemeinschafts-Wiki'}, 'url': 'https://map.example.com', 'newTab': True},
		{'label': 'Discord', 'url': 'https://discord.example.com', 'newTab': True},
	]
	rows = {}
	for locale in ('en-US', 'de-DE'):
		page, _ = _header_height(browser, base, locale, links)
		rows[locale] = _boxes(page, '.links a')
		page.context.close()
	widths = {box['width'] for row in rows.values() for box in row}
	check(len(widths) == 1 and min(widths) > 128 and all(box['fits'] for row in rows.values() for box in row),
		f'link buttons are as wide as the longest label in any language, in every language ({rows})')
	long_label = 'A very long custom link label that cannot fit on one line of the button'
	page, _ = _header_height(browser, base, 'en-US', [{'label': long_label, 'url': 'https://wiki.example.com'}] + links)
	row = _boxes(page, '.links a, .settings .trigger')
	page.context.close()
	# Buttons that don't fit next to them move to the next line, which keeps its own height.
	line = [box for box in row if box['top'] == row[0]['top']]
	check(row and row[0]['width'] <= 256 and row[0]['height'] > 40 and len({box['height'] for box in line}) == 1,
		f'a label too long for 16rem wraps, and its whole line grows with it ({row})')

	# Player page: the grades and the advancement tier link to How we rank.
	page, errors = _page(browser)
	page.goto(f'{base}/players/{quote(names[0], safe="")}')
	page.get_by_role('link', name='What are tiers?').click()
	page.wait_for_url(re.compile(r'/about#tiers$'))
	check(_shows(page, 'Advancement tiers'), 'a player\'s advancement tier links to the tiers on How we rank')
	page.go_back()
	check(_shows(page, 'How is this calculated?'), 'a player\'s grades link to How we rank')
	check(not errors, f'no JavaScript errors on the player page ({"; ".join(errors[:3]) or "none"})')
	page.context.close()

	# Phones: Escape and a tap outside close the menu too.
	page, errors = _page(browser, viewport={'width': 390, 'height': 844}, is_mobile=True, has_touch=True)
	page.goto(base + '/')
	toggle = page.get_by_role('button', name='Menu: Ranking')
	pages_link = page.get_by_role('link', name='Leaderboards')
	toggle.click()
	opened = pages_link.is_visible()
	page.keyboard.press('Escape')
	check(opened and not pages_link.is_visible(), 'Escape closes the phone menu')
	toggle.click()
	page.locator('main').tap(position={'x': 10, 'y': 10})
	check(not pages_link.is_visible(), 'a tap outside closes the phone menu')
	check(toggle.get_attribute('aria-expanded') == 'false', 'the menu button tells screen readers it is closed')
	check(not errors, f'no JavaScript errors in the phone menu ({"; ".join(errors[:3]) or "none"})')
	page.context.close()


def _across_tabs(page: Page, script: str) -> list:
	"""Runs a script on each advancement tab in turn (one is shown at a time) and returns its results per tab."""
	results = []
	tabs = page.locator('.tab-bar [role=tab]')
	for index in range(tabs.count()):
		tabs.nth(index).click()
		page.wait_for_timeout(250)
		results.append(page.evaluate(script))
	return results


def _advancement_checks(browser, base: str, names: list[str], check) -> None:
	"""CHANGELOG 1.4.0: advancements on the game's frames (shape = type, gold = obtained), titles coloured like the
	game's chat, and the Ominous Banner icon."""
	page, errors = _page(browser)
	page.goto(f'{base}/players/{quote(names[0], safe="")}/advancements')
	page.locator('.advancement-icon').first.wait_for()
	tiles = [tile for tab in _across_tabs(page, """async () => {
		const catalog = await (await fetch('/api/advancements')).json();
		const frames = Object.fromEntries(catalog.advancements.map((item) => [item.id, item.frame]));
		return [...document.querySelectorAll('.tile')].map((tile) => {
			// The advancement's own page or the player's page for it: the ID is what follows /advancements/.
			const id = decodeURIComponent(new URL(tile.href).pathname.replace(/^.*\\/advancements\\//, ''))
				.replace('/', ':');
			const image = getComputedStyle(tile.querySelector('.advancement-icon')).backgroundImage;
			return { id, frame: frames[id], image, done: Boolean(tile.querySelector('.when')),
				color: getComputedStyle(tile.querySelector('.label')).color };
		});
	}""") for tile in tab]
	wrong_shape = [tile['id'] for tile in tiles if f"/{tile['frame']}_frame_" not in tile['image']]
	check(tiles and not wrong_shape, f'every advancement sits on the frame of its type ({len(tiles)} tiles, wrong: {wrong_shape[:3]})')
	wrong_colour = [tile['id'] for tile in tiles if ('_frame_obtained' in tile['image']) != tile['done']]
	check(any(tile['done'] for tile in tiles) and any(not tile['done'] for tile in tiles) and not wrong_colour,
		f'the frame is gold exactly for the player\'s finished advancements (wrong: {wrong_colour[:3]})')
	done = [tile for tile in tiles if tile['done']]
	colours = {kind: {tile['color'] for tile in done if (tile['frame'] == 'challenge') == (kind == 'challenge')}
		for kind in ('challenge', 'other')}
	grey = {tile['color'] for tile in tiles if not tile['done']}
	check(len(colours['challenge']) == 1 and len(colours['other']) == 1 and colours['challenge'] != colours['other'],
		f'obtained challenge titles have one colour, task and goal titles another ({colours})')
	check(len(grey) == 1 and not grey & (colours['challenge'] | colours['other']),
		f'titles of advancements the player lacks are grey ({grey})')
	frame_files = page.evaluate("""async () => Promise.all(['task', 'goal', 'challenge'].flatMap((shape) =>
		['obtained', 'unobtained'].map(async (state) => {
			const response = await fetch(`/mc/advancement/${shape}_frame_${state}.png`);
			return response.ok && response.headers.get('content-type').startsWith('image/');
		})))""")
	check(all(frame_files), 'all six frame images are served')

	page.goto(f'{base}/advancements/minecraft/adventure/voluntary_exile')
	page.locator('.advancement-icon img').first.wait_for(state='attached')
	check('ominous_banner' in (page.locator('.advancement-icon img').first.get_attribute('src') or ''),
		'Voluntary Exile shows the Ominous Banner')
	check(not errors, f'no JavaScript errors on the advancement pages ({"; ".join(errors[:3]) or "none"})')
	page.context.close()


def _player_advancement_checks(browser, base: str, names: list[str], check) -> None:
	"""CHANGELOG 1.4.0: every advancement on a player's page leads to the player's own page for it: when it was done,
	and for advancements with several parts which are done (with dates) and which are missing, by name."""
	page, errors = _page(browser)
	# A player with an unfinished advancement with several parts (the sample data gives everyone one).
	found = None
	for name in names:
		progress = page.request.get(f'{base}/api/players/{quote(name, safe="")}').json()['advancements'].get('progress', {})
		started = {key: value for key, value in progress.items() if value['done'] > 0}
		if started:
			found = (name, *next(iter(started.items())))
			break
	check(found is not None, 'the API reports progress for unfinished advancements with several parts')
	if found is None:
		page.context.close()
		return
	name, advancement, progress = found
	tab = f'{base}/players/{quote(name, safe="")}/advancements'
	page.goto(tab)
	page.locator('.tile').first.wait_for()
	hrefs = [href for tab in _across_tabs(page, "[...document.querySelectorAll('.tile')].map((tile) => tile.getAttribute('href'))")
		for href in tab]
	prefix = f'/players/{quote(name, safe="")}/advancements/'
	check(hrefs and all(href.startswith(prefix) for href in hrefs),
		f'the advancements tab still lists the tiles, and each leads to the player\'s own page ({len(hrefs)} tiles)')
	path = advancement.replace(':', '/', 1)
	tab_ids = [item['id'] for item in page.request.get(f'{base}/api/advancements').json()['tabs']]
	page.locator('.tab-bar [role=tab]').nth(tab_ids.index(advancement.split(':')[1].split('/')[0])).click()
	tile = page.locator(f'.tile[href="{prefix}{path}"]')
	counter = f"{progress['done']} / {progress['total']}"
	check(counter in tile.inner_text(), f'the tile of {advancement} shows its progress {counter}')

	tile.click()
	page.wait_for_url(re.compile(re.escape(path) + '$'))
	page.locator('.part').first.wait_for()
	check(_shows(page, f"{progress['done']} of {progress['total']} done"), 'the player\'s page shows the same progress')
	parts = page.evaluate("""() => [...document.querySelectorAll('.parts')].map((section) => ({
		dated: section.querySelectorAll('.part .when').length,
		labels: [...section.querySelectorAll('.part .label')].map((label) => label.textContent.trim())
	}))""")
	labels = [label for section in parts for label in section['labels']]
	check(len(parts) == 2 and parts[0]['dated'] == progress['done'] == len(parts[0]['labels'])
		and len(labels) == progress['total'],
		f'done parts are listed with their dates, missing ones without ({[len(s["labels"]) for s in parts]})')
	check(labels and not any(':' in label or '_' in label for label in labels),
		f'parts have names, not IDs ({labels[:4]})')
	page.go_back()
	page.wait_for_url(tab)
	page.locator('.tile').first.wait_for()
	check(page.locator('.tile').count() > 0, 'Back returns to the advancements tab')

	# A finished single-step advancement says when; variants the game has no names for are named in German.
	page.goto(f'{base}/players/{quote(names[0], safe="")}/advancements/minecraft/story/mine_stone')
	check(_shows(page, 'Done:'), 'a finished single-step advancement shows when it was done')
	german, _ = _page(browser, locale='de-DE')
	german.goto(f'{base}/players/{quote(names[0], safe="")}/advancements/minecraft/husbandry/complete_catalogue')
	check(_shows(german, 'Panther') and _shows(german, 'Britisch-Kurzhaar'), 'cat variants are named in German')
	german.context.close()
	# Every advancement with several parts: no two parts with the same name (all smithing templates are called
	# "Smithing Template" in the game), each with an icon, and none named by its raw ID.
	catalog = page.request.get(f'{base}/api/advancements').json()['advancements']
	player_base = f'{base}/players/{quote(names[0], safe="")}/advancements/'
	problems = []
	pages = 0
	for advancement in catalog:
		path = advancement['id'].replace(':', '/', 1)
		requirements = page.request.get(f'{base}/api/players/{quote(names[0], safe="")}/advancements/{path}').json()['requirements']
		if len(requirements) < 2 and len(requirements[0]) < 2:
			continue
		pages += 1
		page.goto(player_base + path)
		page.locator('.part').first.wait_for()
		shown = page.evaluate("""() => [...document.querySelectorAll('.part')].map((part) => ({
			label: part.querySelector('.label').textContent.trim(), icon: Boolean(part.querySelector('img'))
		}))""")
		labels = [part['label'] for part in shown]
		if len(set(labels)) != len(labels) or not all(part['icon'] for part in shown) \
				or any(re.search(r'[_:]', label) for label in labels):
			problems.append(f"{advancement['id']}: {labels[:6]}")
	check(pages > 0 and not problems,
		f'parts of all {pages} advancements with several have their own name and an icon ({problems[:2]})')
	page.goto(f'{base}/players/{quote(names[0], safe="")}/advancements/minecraft/no/such_thing')
	check(_shows(page, 'This advancement does not exist.'), 'an unknown advancement on a player\'s page says so')
	check(not errors, f'no JavaScript errors on players\' advancement pages ({"; ".join(errors[:3]) or "none"})')
	page.context.close()


def _tree_checks(browser, base: str, names: list[str], check) -> None:
	"""CHANGELOG 1.4.0: advancements can be shown as the game's tree (a switch remembered per visitor), on a player's
	advancements tab and on the advancements overview."""
	page, errors = _page(browser)
	name = quote(names[0], safe='')
	player = page.request.get(f'{base}/api/players/{name}').json()
	catalog = page.request.get(f'{base}/api/advancements').json()
	page.goto(f'{base}/players/{name}/advancements')
	page.locator('.tile').first.wait_for()
	page.get_by_role('button', name='Tree').click()
	page.locator('.tree-box').first.wait_for()
	nodes = _across_tabs(page, """() => [...document.querySelectorAll('.tree-box .node')].map((node) => ({
		href: node.getAttribute('href'), left: node.getBoundingClientRect().left,
		gold: getComputedStyle(node.querySelector('.advancement-icon')).backgroundImage.includes('_frame_obtained') }))""")
	flat = [node for tab in nodes for node in tab]
	done = set(player['advancements']['ids'])
	gold = {node['href'].split('/advancements/', 1)[1].replace('/', ':', 1) for node in flat if node['gold']}
	check(len(nodes) == len(catalog['tabs']) and len(flat) == len(catalog['advancements']),
		f'the tree shows every tab with all its advancements ({len(nodes)} tabs, {len(flat)} of {len(catalog["advancements"])})')
	check(all(node['href'].startswith(f'/players/{name}/advancements/') for node in flat) and gold == done,
		f'each leads to the player\'s page, gold exactly for the ones they have ({len(gold)} of {len(done)})')
	lines = _across_tabs(page, "document.querySelectorAll('.tree-box svg rect').length")
	roots = {item['tab']: item['id'] for item in catalog['advancements'] if not item.get('parent')}
	leftmost = [min(tab, key=lambda node: node['left'])['href'].split('/advancements/', 1)[1].replace('/', ':', 1)
		for tab in nodes]
	check(all(count > 0 for count in lines) and set(leftmost) == set(roots.values()),
		f'lines join the advancements, each tab\'s root is on the far left ({leftmost})')
	page.locator('input.search').first.fill('diamond')
	page.wait_for_timeout(600)
	faded = page.locator('.tree-box .node.faded').count()
	shown = page.locator('.tree-box .node').count()
	check(0 < faded < shown, f'a search fades the others in the tree instead of hiding them ({shown - faded} of {shown} bright)')
	page.locator('input.search').first.fill('')
	page.get_by_text('Achieved only').click()
	page.wait_for_timeout(600)
	left = sum(_across_tabs(page, "document.querySelectorAll('.tree-box .node').length"))
	check(left == len(done), f'with "Achieved only" the tree shows only the player\'s advancements ({left} of {len(done)})')
	page.get_by_text('Achieved only').click()
	page.reload()
	page.locator('.tree-box').first.wait_for()
	check(page.locator('.tile').count() == 0, 'the tree view is remembered after a reload')
	page.goto(f'{base}/advancements')
	page.locator('.tree-box').first.wait_for()
	check(sum(_across_tabs(page, "document.querySelectorAll('.tree-box .node').length")) == len(catalog['advancements']),
		'the advancements overview shows the tree too')
	page.get_by_role('button', name='Tiles').click()
	page.locator('.tile').first.wait_for()
	check(page.locator('.tree-box').count() == 0, 'the switch goes back to tiles')
	# One tab at a time, like the game's screen; a search shows every tab with matches and hides the tab bar.
	shown_tabs = page.locator('.pack').count()
	page.locator('.tab-bar [role=tab]').nth(1).click()
	page.wait_for_timeout(300)
	second = page.evaluate("[...document.querySelectorAll('.tile')].map((tile) => tile.getAttribute('href'))")
	nether = [item['id'] for item in catalog['advancements'] if item['tab'] == catalog['tabs'][1]['id']]
	check(page.locator('.tab-bar [role=tab]').count() == len(catalog['tabs']) and shown_tabs == 1
		and len(second) == len(nether), f'a tab bar shows one advancement tab at a time ({len(second)} in the second)')
	page.locator('input.search').first.fill('nether')
	page.wait_for_timeout(600)
	check(page.locator('.tab-bar').count() == 0 and page.locator('.pack').count() > 1,
		f'a search shows every tab with matches ({page.locator(".pack").count()})')
	check(not errors, f'no JavaScript errors in the tree view ({"; ".join(errors[:3]) or "none"})')
	page.context.close()
