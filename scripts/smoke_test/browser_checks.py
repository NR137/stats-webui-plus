"""Browser checks for smoke_test.py --browser: the real web UI in headless Chromium, against the running server.

Needs Playwright (scripts/requirements.txt) and its Chromium: scripts/.venv/bin/playwright install --only-shell chromium
"""

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

	page.goto(base + '/about')
	check(_shows(page, 'Real combat, 22 %'), 'the How we rank page explains the weights')
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
